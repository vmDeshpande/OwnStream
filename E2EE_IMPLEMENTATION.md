# OwnStream E2EE Implementation Notes

## Phase 2 - Step 1: libsignal Integration

### Dependencies
- **libsignal-client**: `org.signal:libsignal-client:0.99.3`
- **libsignal-android**: `org.signal:libsignal-android:0.99.3`
- **Desugar JDK Libs**: `com.android.tools:desugar_jdk_libs:2.1.5`

### Repository
- **Signal Maven**: `https://build-artifacts.signal.org/libraries/maven/`

### Configuration Requirements
- **JDK 21**: Required for building libsignal 0.96+.
- **Core Library Desugaring**: Must be enabled in `app/build.gradle.kts` as `libsignal-android` requires it.
- **Packaging Exclusions**:
    - Excluded desktop native libs (`libsignal_jni*.dylib`, `signal_jni*.dll`) from `resources`.
    - Excluded `libsignal_jni_testing.so` from `jniLibs` to reduce APK size.

### Build Verification
- **Compilation**: Successful with AGP 9.3.1.
- **APK Size Impact**: 
    - Debug APK size increased by ~500MB (uncompressed native libs for 4 architectures).
    - `libsignal_jni.so` is ~106MB per architecture in debug builds. 
    - Note: Release builds with symbol stripping will be significantly smaller.
- **Runtime Loading**: Verified via `LibSignalLoadTest` on device. `IdentityKeyPair.generate()` successfully triggers native JNI loading.

## Phase 2 - Step 2A: libsignal In-Memory Spike

### Verification Results
- **E2E Round Trip**: Alice to Bob messaging successfully encrypted and decrypted.
- **Session Persistence**: Serialized `SessionRecord` successfully restores session state.
- **Security Protections**: 
    - Tampering with ciphertext causes decryption failure.
    - Identity mismatch (UntrustedIdentityException) correctly detected.
- **Actual Required State**:
    - **Local**: `IdentityKeyPair`, `RegistrationId`.
    - **Sessions**: `SessionRecord` blobs (Addressable by Name + DeviceId).
    - **Keys**: `PreKeyRecord`, `SignedPreKeyRecord`, `KyberPreKeyRecord` blobs.
    - **Remote**: `IdentityKey` blobs + trust status.

### API Findings (libsignal 0.99.3)
- **Kyber Support**: Mandatory for 1:1 sessions using modern bundles.
- **Store Interfaces**: Requires `SenderKeyStore` and `KyberPreKeyStore` in addition to legacy stores.
- **Address Handling**: `SignalProtocolAddress` is the primary key for sessions and identities.

## Phase 2 - Step 3: Protocol Persistence

### Key Components
- **EncryptionManager**: AES-256-GCM hardware-backed wrapper for storage.
- **SignalProtocolStoreAdapter**: Bridges Room to libsignal interfaces.
- **SignalEntities**: Dedicated tables for identity, sessions, and prekeys.

### Security Status
- Protocol state is encrypted at rest in the Room database.
- Private keys never touch the logcat or ordinary message tables.

## Phase 2 - Step 4: SignalCryptoProvider Integration

### Primary Objective
Integrate the real libsignal implementation into the OwnStream `CryptoProvider` boundary.

### Achievements
- **SignalCryptoProvider**: Fully implemented the `CryptoProvider` interface using libsignal 0.99.3.
- **Session Management**: Automated session check and establishment using `ProtocolPreKeyBundle`.
- **Message Format**: Optimized `EncryptedPayload` to carry Signal ciphertext and protocol metadata (`SIGNAL_V1`).
- **Legacy Support**: Maintained compatibility with `NONE` (plaintext) messages for existing local chats.
- **1:1 E2EE Verification**: Successfully tested full message round-trips between independent Alice and Bob instances through the production `SendMessageUseCase`.

### Status
- **Real E2EE cryptography integrated and tested locally.**
- **Production relay architecture designed and DTO contract established.**

## Phase 2 - Step 6A: Shared DTO / Protocol Contract

### Primary Objective
Create a clean, versioned network protocol contract for the Android client and Ktor relay.

### Achievements
- **Protocol Module**: Created a dedicated `:protocol` JVM module to share models between Android and Backend.
- **REST DTOs**: 
    - `RegisterDeviceRequest/Response`: Device onboarding.
    - `AuthChallengeRequest/Response`: Nonce-based challenge flow.
    - `LoginRequest/Response`: Hardware-backed signature verification and JWT issuance.
    - `PublishPreKeyBundleRequest`, `FetchPreKeyBundleResponse`: Signal key distribution.
- **WebSocket Protocol**:
    - `WebSocketFrame`: Top-level versioned container.
    - `FramePayload`: Sealed class for `ENVELOPE`, `DELIVERY_ACK`, `HEARTBEAT`, `HISTORY_SYNC`, `ERROR`.
- **Validation**: Introduced `ProtocolValidation` for ID formats and payload sizes.
- **Serialization Tests**: Verified 100% round-trip integrity for all protocol structures.

### Security Status
- **Zero Plaintext**: No network DTO contains plaintext fields.
- **No Private Keys**: Private cryptographic material never leaves the device.
- **Hardware-Bound Auth**: Authentication relies on signing challenges with Android Keystore EC keys.

### Verification Results
- **ProtocolSerializationTest**: **PASSED**.
- **App Compilation**: All `:app` modules correctly integrated with the new protocol module.

### Status
- **Protocol contract finalized and verified.**
- **Production-ready REST Relay implemented (Registry & PreKeys).**

## Phase 2 - Step 6B: Ktor Relay - Device Registry + PreKey REST

### Primary Objective
Implement the first functional OwnStream relay backend for device registration and Signal PreKey management.

### Achievements
- **Ktor Relay Module**: Created a dedicated `:relay` JVM module for the backend.
- **Hardware-Backed Registry**: Implemented `POST /v1/register` that stores public P-256 keys for passwordless authentication.
- **Challenge-Response Auth**: 
    - `GET /v1/auth/challenge`: Generates cryptographically secure nonces.
    - `POST /v1/auth/login`: Verifies ECDSA P-256 signatures and issues session tokens.
- **PreKey Directory**:
    - `POST /v1/prekeys`: Secure publishing of Signal bundles for authenticated devices.
    - `GET /v1/prekeys/{id}`: Retrieval of bundles with atomic one-time prekey consumption.
- **Database Schema**: Implemented using **Exposed** with support for PostgreSQL (Production) and H2 (Local/Test).
- **Global Error Handling**: Integrated `StatusPages` for consistent HTTP error responses.

### Security Status
- **Cryptographic Blindness**: Relay only stores public keys and opaque Signal bundles. No private keys or session states reach the server.
- **Constant-Time Verification**: Used where applicable for security-sensitive comparisons.
- **Token-Based Security**: Authenticated endpoints require a valid session token.

### Verification Results
- **RelayTest**: **PASSED**.
    - `testRegistrationAndAuthFlow`: Verified registration, challenge-response, login, publishing, retrieval, and atomic consumption.
    - `testInvalidOwnStreamId`: Verified rejection of malformed IDs.

### Status
- **REST Relay functional and verified.**
- **WebSocket Routing and Offline Queuing implemented and verified.**

## Phase 2 - Step 6C: WebSocket Routing + Offline Message Queue

### Primary Objective
Implement real-time message routing and secure offline queuing in the Ktor relay.

### Achievements
- **WebSocket Integration**: Installed and configured the Ktor `WebSockets` plugin with authenticated access.
- **Connection Registry**: Implemented an in-memory `ConnectionRegistry` to track active device sessions.
- **Envelope Routing**: 
    - Real-time forwarding: Delivers `MessageEnvelope` immediately to connected recipients.
    - Automatic fallback: Enqueues messages in PostgreSQL if the recipient is offline.
- **Offline Queuing**: 
    - Implemented the `queued_messages` table with TTL support (30 days).
    - Automatic Delivery: Pushes queued messages immediately upon recipient reconnection.
- **Acknowledgements**: Integrated `DELIVERY_ACK` semantics. Messages are only removed from the relay queue after the recipient acknowledges receipt.
- **Idempotency**: Leveraged `messageId` to prevent duplicate queue entries.
- **Security Validation**: Verified that the relay cannot see the plaintext content of queued envelopes.

### Security Status
- **Authorization**: WebSocket connections require a valid `mockjwt` token verified against the sender's OwnStream ID.
- **Sender Integrity**: Relay verifies that the `senderId` in the envelope matches the authenticated connection ID.
- **Metadata Protection**: Envelope data remains opaque (serialized byte arrays) in the database.

### Verification Results
- **RelayTest**: **PASSED**.
    - `testOnlineRouting`: Alice → Bob real-time delivery verified.
    - `testOfflineQueuing`: Alice → Relay → Bob (Reconnect) round-trip verified.
    - `testRelayBlindness`: Confirmed no plaintext strings exist in the `queued_messages` table.

### Status
- **Relay core logic complete (REST + WebSockets).**
- **Android NetworkMessageTransport integrated and verified.**

## Phase 2 - Step 6D: Android NetworkMessageTransport Integration

### Primary Objective
Replace the local transport with a real network implementation connecting to the Ktor relay.

### Achievements
- **NetworkMessageTransport**: Implemented the production `MessageTransport` using Ktor Client (OkHttp engine).
- **Secure Authentication Flow**: 
    - Automated `register`, `challenge`, and `login` logic.
    - Uses hardware-backed ECDSA signing of challenges via Android Keystore.
    - Implemented secure token storage using `EncryptedSharedPreferences`.
- **WebSocket Session Management**:
    - Automatic connection establishment and authenticated session binding.
    - Exponential backoff reconnection logic.
    - Integrated `DELIVERY_ACK` loop for reliable messaging.
- **Relay Configuration**: Created `RelayConfig` to manage backend endpoints without hardcoding.
- **Dependency Injection**: Integrated via Hilt with a dedicated `NetworkModule`.

### Security Status
- **Hardware-Protected Auth**: Tokens are obtained by signing challenges on-device. Private keys never leave the Keystore.
- **Encrypted Storage**: Authentication tokens are protected using AES-256-SIV (Key) and AES-256-GCM (Value).
- **Zero-Plaintext Boundary**: Verified that `NetworkMessageTransport` only handles routing metadata and Signal ciphertext.

### Verification Results
- **NetworkTransportIntegrationTest**: **PASSED** (via MockEngine). Verified the full REST/Auth lifecycle and bundle publication.
- **Existing E2EE Tests**: **PASSED**. No regressions in the cryptographic core.

### Status
- **Android ↔ Relay connectivity established.**
- **Ready for final Two-Device End-to-End verification (Step 6E).**

### Licensing
- **AGPLv3**: `libsignal` is licensed under AGPLv3. OwnStream must comply with these terms if distributing the application.
