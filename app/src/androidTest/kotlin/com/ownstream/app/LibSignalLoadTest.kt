package com.ownstream.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.libsignal.protocol.IdentityKeyPair
import org.junit.Assert.assertNotNull

@RunWith(AndroidJUnit4::class)
class LibSignalLoadTest {

    @Test
    fun verifyLibSignalLoads() {
        // Generating an IdentityKeyPair triggers native library loading
        val identityKeyPair = IdentityKeyPair.generate()
        assertNotNull(identityKeyPair)
        println("Successfully generated IdentityKeyPair using libsignal native library.")
    }
}
