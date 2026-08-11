package com.ownstream.app.core.network

import android.util.Log
import com.ownstream.app.core.crypto.KeyStoreProvider
import com.ownstream.app.core.storage.SecureStorage
import com.ownstream.protocol.FrameType as ProtocolFrameType
import com.ownstream.protocol.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.security.Signature
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMessageTransport @Inject constructor(
    private val relayConfig: RelayConfig,
    private val secureStorage: SecureStorage,
    @InternalHttpClient private val client: HttpClient,
    @com.ownstream.app.core.crypto.IdentityKeyAlias private val identityKeyAlias: String,
    private val keyStoreProvider: KeyStoreProvider
) : MessageTransport {

    private val TAG = "NetworkTransport"
    private val json = Json { ignoreUnknownKeys = true }
    
    private val _incomingMessages = MutableSharedFlow<MessageEnvelope>(extraBufferCapacity = 100)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wsSession: DefaultClientWebSocketSession? = null

    override suspend fun send(envelope: MessageEnvelope) {
        val session = wsSession
        if (session != null && session.isActive) {
            try {
                val frame = WebSocketFrame(
                    type = ProtocolFrameType.ENVELOPE,
                    payload = FramePayload.Envelope(envelope)
                )
                session.sendSerialized(frame)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send envelope", e)
            }
        } else {
            Log.w(TAG, "WebSocket not connected. Cannot send.")
        }
    }

    override fun observeIncomingMessages(): Flow<MessageEnvelope> = _incomingMessages

    override suspend fun publishPreKeyBundle(identityId: String, bundle: ProtocolPreKeyBundle) {
        ensureAuthenticated(identityId)
        val token = secureStorage.getAuthToken() ?: throw IllegalStateException("Not authenticated")
        
        client.post("${relayConfig.baseUrl}/v1/prekeys") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(PublishPreKeyBundleRequest(bundle))
        }
    }

    override suspend fun fetchPreKeyBundle(identityId: String): ProtocolPreKeyBundle? {
        val token = secureStorage.getAuthToken() ?: return null
        
        return try {
            val response: FetchPreKeyBundleResponse = client.get("${relayConfig.baseUrl}/v1/prekeys/$identityId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body()
            response.bundle
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch prekey bundle for $identityId", e)
            null
        }
    }

    override suspend fun connect() { }

    suspend fun connect(ownStreamId: String) {
        ensureAuthenticated(ownStreamId)
        val token = secureStorage.getAuthToken() ?: return

        scope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    client.webSocket(relayConfig.wsUrl, {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }) {
                        wsSession = this
                        Log.i(TAG, "WebSocket connected for $ownStreamId")
                        attempt = 0
                        
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val wsFrame = json.decodeFromString<WebSocketFrame>(frame.readText())
                                handleIncomingFrame(wsFrame)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "WebSocket connection error", e)
                    wsSession = null
                }
                
                attempt++
                val delayMs = (Math.pow(2.0, attempt.toDouble()) * 1000).toLong().coerceAtMost(30000)
                Log.i(TAG, "Reconnecting in ${delayMs}ms (attempt $attempt)")
                delay(delayMs)
            }
        }
    }

    private suspend fun handleIncomingFrame(frame: WebSocketFrame) {
        when (val payload = frame.payload) {
            is FramePayload.Envelope -> {
                _incomingMessages.emit(payload.envelope)
                sendAck(payload.envelope.messageId)
            }
            is FramePayload.DeliveryAck -> {
                Log.d(TAG, "Received ACK for ${payload.messageId}: ${payload.status}")
            }
            is FramePayload.Heartbeat -> {
                Log.v(TAG, "Heartbeat")
            }
            else -> {}
        }
    }

    private suspend fun sendAck(messageId: String) {
        val session = wsSession
        if (session != null && session.isActive) {
            val frame = WebSocketFrame(
                type = ProtocolFrameType.DELIVERY_ACK,
                payload = FramePayload.DeliveryAck(messageId, AckStatus.DELIVERED_TO_RECIPIENT)
            )
            session.sendSerialized(frame)
        }
    }

    private suspend fun ensureAuthenticated(ownStreamId: String) {
        if (secureStorage.getAuthToken() != null) return

        try {
            val keyStore = keyStoreProvider.getKeyStore()
            val entry = keyStore.getEntry(identityKeyAlias, null) as? KeyStore.PrivateKeyEntry
            val publicKey = entry?.certificate?.publicKey ?: throw IllegalStateException("Identity key missing")
            
            client.post("${relayConfig.baseUrl}/v1/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterDeviceRequest(ownStreamId, publicKey.encoded))
            }

            val challenge: AuthChallengeResponse = client.get("${relayConfig.baseUrl}/v1/auth/challenge") {
                parameter("ownStreamId", ownStreamId)
            }.body()

            val signature = Signature.getInstance("SHA256withECDSA").run {
                initSign(entry.privateKey)
                update(challenge.nonce)
                sign()
            }

            val loginResponse: LoginResponse = client.post("${relayConfig.baseUrl}/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(ownStreamId, challenge.nonce, signature))
            }.body()

            secureStorage.saveAuthToken(loginResponse.token)
            Log.i(TAG, "Authenticated successfully as $ownStreamId")
        } catch (e: Exception) {
            Log.e(TAG, "Authentication failed", e)
        }
    }

    override suspend fun disconnect() {
        scope.coroutineContext.cancelChildren()
        wsSession?.close()
        wsSession = null
    }
}
