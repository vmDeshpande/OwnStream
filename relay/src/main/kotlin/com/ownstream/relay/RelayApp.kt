package com.ownstream.relay

import com.ownstream.protocol.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    println("[E2E-Relay] Module starting...")
    
    // Shared components
    val relayService = RelayService()
    val connectionRegistry = ConnectionRegistry()
    val json = Json { ignoreUnknownKeys = true }

    // In-memory H2 for local dev if Postgres not provided
    val dbUrl = environment.config.propertyOrNull("storage.jdbcUrl")?.getString() ?: "jdbc:h2:mem:ownstream_relay;DB_CLOSE_DELAY=-1"
    val dbUser = environment.config.propertyOrNull("storage.user")?.getString() ?: "sa"
    val dbPass = environment.config.propertyOrNull("storage.password")?.getString() ?: ""
    
    println("[E2E-Relay] Database type: ${if (dbUrl.startsWith("jdbc:h2")) "H2" else "Postgres"}")
    
    // We'll use H2 for local tests/dev if Postgres isn't configured
    try {
        if (dbUrl.startsWith("jdbc:h2")) {
            println("[E2E-Relay] Connecting to H2...")
            org.jetbrains.exposed.sql.Database.connect(dbUrl, driver = "org.h2.Driver", user = dbUser, password = dbPass)
            println("[E2E-Relay] Creating schema...")
            org.jetbrains.exposed.sql.transactions.transaction {
                org.jetbrains.exposed.sql.SchemaUtils.create(Users, Devices, PreKeyBundles, OneTimePreKeys, QueuedMessages)
            }
        } else {
            println("[E2E-Relay] Initializing DatabaseFactory...")
            DatabaseFactory.init(dbUrl, dbUser, dbPass)
        }
        println("[E2E-Relay] Database initialized.")
    } catch (e: Exception) {
        println("[E2E-Relay] Database initialization FAILED: ${e.message}")
        e.printStackTrace()
    }

    install(ContentNegotiation) {
        json(json)
    }
    // ... rest of the function

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = OwnStreamProtocol.MAX_MESSAGE_SIZE_BYTES.toLong()
        masking = false
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(io.ktor.http.HttpStatusCode.BadRequest, cause.message ?: "Invalid request")
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(io.ktor.http.HttpStatusCode.Conflict, cause.message ?: "State conflict")
        }
    }

    // Custom Auth for Step 6B Spike
    install(Authentication) {
        register(object : AuthenticationProvider(object : AuthenticationProvider.Config("relay-auth") {}) {
            override suspend fun onAuthenticate(context: AuthenticationContext) {
                val authHeader = context.call.request.headers["Authorization"]
                val token = authHeader?.removePrefix("Bearer ")
                if (token != null && token.startsWith("mockjwt:")) {
                    val parts = token.split(":")
                    if (parts.size >= 2) {
                        context.principal(RelayPrincipal(parts[1]))
                    }
                }
            }
        })
    }

    routing {
        get("/") {
            call.respondText("OwnStream Relay Active")
        }

        post("/v1/register") {
            val request = call.receive<RegisterDeviceRequest>()
            val response = relayService.registerDevice(request)
            call.respond(response)
        }

        get("/v1/auth/challenge") {
            val ownStreamId = call.request.queryParameters["ownStreamId"] ?: return@get call.respond(io.ktor.http.HttpStatusCode.BadRequest, "Missing ownStreamId")
            val response = relayService.generateChallenge(ownStreamId)
            call.respond(response)
        }

        post("/v1/auth/login") {
            val request = call.receive<LoginRequest>()
            try {
                val response = relayService.login(request)
                call.respond(response)
            } catch (e: Exception) {
                call.respond(io.ktor.http.HttpStatusCode.Unauthorized, e.message ?: "Auth failed")
            }
        }

        authenticate("relay-auth") {
            post("/v1/prekeys") {
                val principal = call.principal<RelayPrincipal>()
                val ownStreamId = principal?.ownStreamId ?: return@post call.respond(io.ktor.http.HttpStatusCode.Unauthorized)
                
                val request = call.receive<PublishPreKeyBundleRequest>()
                relayService.publishPreKeyBundle(ownStreamId, request.bundle)
                call.respond(io.ktor.http.HttpStatusCode.OK)
            }

            get("/v1/prekeys/{id}") {
                val targetId = call.parameters["id"] ?: return@get call.respond(io.ktor.http.HttpStatusCode.BadRequest)
                try {
                    val response = relayService.fetchPreKeyBundle(targetId)
                    call.respond(response)
                } catch (e: Exception) {
                    call.respond(io.ktor.http.HttpStatusCode.NotFound, e.message ?: "Not found")
                }
            }

            webSocket("/v1/ws") {
                val principal = call.principal<RelayPrincipal>()
                val ownStreamId = principal?.ownStreamId ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                
                connectionRegistry.addConnection(ownStreamId, this)
                
                try {
                    // Send queued messages upon connection
                    val queued = relayService.fetchQueuedMessages(ownStreamId)
                    queued.forEach { envelope ->
                        val frame = WebSocketFrame(
                            type = com.ownstream.protocol.FrameType.ENVELOPE,
                            payload = FramePayload.Envelope(envelope)
                        )
                        send(Frame.Text(json.encodeToString(frame)))
                    }
                    if (queued.isNotEmpty()) {
                        send(Frame.Text(json.encodeToString(WebSocketFrame(type = com.ownstream.protocol.FrameType.HISTORY_SYNC_COMPLETE, payload = FramePayload.HistorySyncComplete))))
                    }

                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val wsFrame = json.decodeFromString<WebSocketFrame>(frame.readText())
                            handleWsFrame(ownStreamId, wsFrame, relayService, connectionRegistry, json)
                        }
                    }
                } finally {
                    connectionRegistry.removeConnection(ownStreamId)
                }
            }
        }
    }
}

private suspend fun DefaultWebSocketServerSession.handleWsFrame(
    senderId: String,
    frame: WebSocketFrame,
    relayService: RelayService,
    connectionRegistry: ConnectionRegistry,
    json: Json
) {
    when (val payload = frame.payload) {
        is FramePayload.Envelope -> {
            val envelope = payload.envelope
            // Security: verify senderId matches authenticated ID
            if (envelope.senderId != senderId) {
                send(Frame.Text(json.encodeToString(WebSocketFrame(type = com.ownstream.protocol.FrameType.ERROR, payload = FramePayload.Error("AUTH_ERR", "Sender mismatch")))))
                return
            }

            // Route to recipient
            val recipientSession = connectionRegistry.getConnection(envelope.recipientId)
            if (recipientSession != null) {
                try {
                    recipientSession.send(Frame.Text(json.encodeToString(frame)))
                    // Acknowledge to sender that it was forwarded (not yet delivered/acked by recipient)
                    send(Frame.Text(json.encodeToString(WebSocketFrame(type = com.ownstream.protocol.FrameType.DELIVERY_ACK, payload = FramePayload.DeliveryAck(envelope.messageId, AckStatus.RECEIVED_BY_RELAY)))))
                } catch (e: Exception) {
                    // Recipient session might have died, enqueue instead
                    relayService.enqueueMessage(envelope)
                }
            } else {
                // Recipient offline, enqueue
                relayService.enqueueMessage(envelope)
                send(Frame.Text(json.encodeToString(WebSocketFrame(type = com.ownstream.protocol.FrameType.DELIVERY_ACK, payload = FramePayload.DeliveryAck(envelope.messageId, AckStatus.RECEIVED_BY_RELAY)))))
            }
        }
        is FramePayload.DeliveryAck -> {
            // Recipient acknowledges delivery, remove from queue
            relayService.acknowledgeMessage(senderId, payload.messageId)
        }
        is FramePayload.Heartbeat -> {
            send(Frame.Text(json.encodeToString(WebSocketFrame(type = com.ownstream.protocol.FrameType.HEARTBEAT, payload = FramePayload.Heartbeat))))
        }
        else -> { }
    }
}

data class RelayPrincipal(val ownStreamId: String)
