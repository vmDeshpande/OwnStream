# OwnStream

**Your chats. Your data. Your choice.**

OwnStream is a privacy-first messaging application where the user controls where their conversation data lives. It separates identity, encryption, message transport, and message storage.

## Core Principles
1. **User Ownership**: You decide where your data is stored (Local, Self-hosted, or Cloud).
2. **Strict Separation**: Storage, Cryptography, and Transport are independent layers.
3. **Privacy First**: Designed for end-to-end encryption and authenticated identities.

## Current MVP Status

### ✅ Implemented
- **Android Client**: Modern Kotlin + Jetpack Compose (Material 3) app.
- **Clean Architecture**: Use Cases, Repositories, and layered abstractions.
- **Local Identity**: Choose a username and generate a cryptographic identity (ID prefixed with `os_`).
- **Local Storage**: Fully functional persistence using Room (SQLite).
- **Storage Abstraction**: `StorageAdapter` allows for future pluggable providers.
- **Crypto Abstraction**: `CryptoProvider` isolates the app from specific E2EE protocols.
- **Transport Abstraction**: `MessageTransport` isolates the app from network logic.
- **Messaging Pipeline**: Functional chat UI with local persistence.
- **Storage Indicators**: Visual feedback on where each conversation's data is stored.
- **Dependency Injection**: Hilt for modularity and testability.

### ⚠️ Planned / Under Development
- **Real E2EE Protocol**: Integration of a vetted messaging protocol (e.g., Signal Protocol) is pending. Current crypto layer uses Android Keystore for identity protection but does not yet implement a full messaging protocol.
- **Remote Storage**: Self-hosted (Postgres/WebDAV) and Cloud providers.
- **Remote Transport**: WebSocket/Relay for internet messaging.
- **Migration**: Tools to move conversations between different storage providers.
- **QR Verification**: Out-of-band identity verification.

## Architecture

```text
Compose UI
    ↓
ViewModels
    ↓
Use Cases (Business Logic)
    ↓
Repositories (Coordination)
    ↓
┌───────────────┬───────────────┬───────────────┐
│               │               │               │
StorageAdapter  CryptoProvider  MessageTransport
    ↓               ↓               ↓
Room/SQLite     Android         No-Op (MVP)
                Keystore
```

## Getting Started

1. Open the project in Android Studio (Koala or newer).
2. Build and run the `:app` module.
3. Complete the onboarding to create your identity.
4. Use the "+" button to create a local demo conversation.
5. Send messages and observe they persist across app restarts.

## Security Disclaimer
This is an MVP/Alpha version. While the architecture is designed for security, the current version **does not yet implement a production-grade E2EE messaging protocol**. Do not use this for highly sensitive communication until a vetted protocol is integrated.
