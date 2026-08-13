package com.ownstream.app.core.network

import android.util.Log
import com.ownstream.app.core.crypto.KeyStoreProvider
import com.ownstream.app.core.crypto.SignalProtocolStoreAdapter
import com.ownstream.app.core.storage.SecureStorage
import com.ownstream.protocol.FrameType as ProtocolFrameType
import com.ownstream.protocol.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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
    private val keyStoreProvider: KeyStoreProvider,
    private val signalStore: SignalProtocolStoreAdapter
) : MessageTransport {

    private val TAG = "NetworkTransport"
    private val json = Json { ignoreUnknownKeys = true }
    
    private val _incomingMessages = MutableSharedFlow<MessageEnvelope>(extraBufferCapacity = 100)
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wsSession: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null

    override fun observeConnectionStatus(): Flow<ConnectionStatus> = _connectionStatus

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
                throw e
            }
        } else {
            Log.w(TAG, "WebSocket not connected. Cannot send.")
            throw IllegalStateException("WebSocket not connected")
        }
    }

    override fun observeIncomingMessages(): Flow<MessageEnvelope> = _incomingMessages

    override suspend fun publishPreKeyBundle(identityId: String, bundle: ProtocolPreKeyBundle) {
        Log.d(TAG, "publishPreKeyBundle: Starting for $identityId")
        try {
            ensureAuthenticated(identityId)
            val token = secureStorage.getAuthToken() ?: throw IllegalStateException("Not authenticated")
            
            Log.d(TAG, "publishPreKeyBundle: Sending bundle to ${relayConfig.baseUrl}/v1/prekeys")
            client.post("${relayConfig.baseUrl}/v1/prekeys") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(PublishPreKeyBundleRequest(bundle))
            }
            Log.d(TAG, "publishPreKeyBundle: Finished")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish prekey bundle", e)
            throw e
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

    fun connect(ownStreamId: String) {
        // Cancel existing job if any
        connectionJob?.cancel()
        
        connectionJob = scope.launch {
            // Listen for configuration changes and restart connection
            relayConfig.baseUrlFlow.collectLatest { url ->
                Log.i(TAG, "Config changed to $url, restarting connection flow...")
                
                // Clear existing session and token for the new server
                wsSession?.close()
                wsSession = null
                secureStorage.clearAuthToken()
                
                var attempt = 0
                while (isActive) {
                    _connectionStatus.value = ConnectionStatus.CONNECTING
                    try {
                        ensureAuthenticated(ownStreamId)
                        val token = secureStorage.getAuthToken() ?: throw IllegalStateException("Token missing")
                        
                        client.webSocket(relayConfig.wsUrl, {
                            header(HttpHeaders.Authorization, "Bearer $token")
                        }) {
                            wsSession = this
                            _connectionStatus.value = ConnectionStatus.CONNECTED
                            Log.i(TAG, "WebSocket connected for $ownStreamId to $url")
                            attempt = 0
                            
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    val text = frame.readText()
                                    val wsFrame = json.decodeFromString<WebSocketFrame>(text)
                                    handleIncomingFrame(wsFrame)
                                }
                            }
                        }
                        Log.i(TAG, "WebSocket session ended normally")
                        _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    } catch (e: Exception) {
                        Log.e(TAG, "Connection error for $ownStreamId: ${e.message}")
                        wsSession = null
                        _connectionStatus.value = ConnectionStatus.ERROR
                    }
                    
                    attempt++
                    val delayMs = (Math.pow(2.0, attempt.toDouble()) * 1000).toLong().coerceAtMost(30000)
                    Log.i(TAG, "Retrying connection in ${delayMs}ms...")
                    delay(delayMs)
                    _connectionStatus.value = ConnectionStatus.CONNECTING
                }
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
            
            // Get current registration ID from Signal store
            val regId = signalStore.localRegistrationId

            val regResponse = client.post("${relayConfig.baseUrl}/v1/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterDeviceRequest(ownStreamId, publicKey.encoded, regId))
            }
            
            if (regResponse.status != HttpStatusCode.OK && regResponse.status != HttpStatusCode.Conflict) {
                throw IllegalStateException("Registration failed: ${regResponse.status}")
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
            Log.e(TAG, "Authentication failed for $ownStreamId", e)
            throw e
        }
    }

    override suspend fun disconnect() {
        connectionJob?.cancel()
        wsSession?.close()
        wsSession = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }
}
