package com.ownstream.app

import com.ownstream.app.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class PersistenceTest {

    @Test
    fun `test message model and deterministic ordering`() {
        val conversationId = UUID.randomUUID().toString()
        val timestamp = 1000L
        val messages = listOf(
            Message(
                id = "b",
                conversationId = conversationId,
                senderId = "user1",
                payload = MessagePayload.Text("Second"),
                timestamp = timestamp
            ),
            Message(
                id = "a",
                conversationId = conversationId,
                senderId = "user1",
                payload = MessagePayload.Text("First"),
                timestamp = timestamp
            )
        )

        assertEquals(2, messages.size)
        
        // Sorting by timestamp then ID should be deterministic
        val sorted = messages.sortedWith(compareBy({ it.timestamp }, { it.id }))
        assertEquals("a", sorted[0].id)
        assertEquals("b", sorted[1].id)
    }

    @Test
    fun `test storage configuration model`() {
        val config = StorageConfiguration(
            conversationId = "conv1",
            providerType = StorageProviderType.LOCAL,
            connectionDetails = mapOf("path" to "/local/path")
        )
        
        assertEquals(StorageProviderType.LOCAL, config.providerType)
        assertEquals("/local/path", config.connectionDetails["path"])
    }

    @Test
    fun `test encrypted payload structure`() {
        val encryptedPayload = com.ownstream.app.core.crypto.EncryptedPayload(
            data = "Secret".toByteArray(),
            algorithm = "NONE",
            isEncrypted = false
        )
        val message = Message(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            payload = MessagePayload.Encrypted(encryptedPayload)
        )

        assert(message.payload is MessagePayload.Encrypted)
        val payload = message.payload as MessagePayload.Encrypted
        assertEquals("Secret", String(payload.encryptedPayload.data))
    }
}
