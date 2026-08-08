package com.ownstream.app.data.local

import androidx.room.*

@Dao
interface SignalDao {
    // Identity
    @Query("SELECT * FROM signal_identities WHERE id = 1")
    suspend fun getLocalIdentity(): SignalIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalIdentity(entity: SignalIdentityEntity)

    // Sessions
    @Query("SELECT * FROM signal_sessions WHERE addressName = :name AND deviceId = :deviceId")
    suspend fun getSession(name: String, deviceId: Int): SignalSessionEntity?

    @Query("SELECT * FROM signal_sessions WHERE addressName = :name")
    suspend fun getSessionsForName(name: String): List<SignalSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(entity: SignalSessionEntity)

    @Query("DELETE FROM signal_sessions WHERE addressName = :name AND deviceId = :deviceId")
    suspend fun deleteSession(name: String, deviceId: Int)

    @Query("DELETE FROM signal_sessions WHERE addressName = :name")
    suspend fun deleteSessionsForName(name: String)

    // PreKeys
    @Query("SELECT * FROM signal_prekeys WHERE preKeyId = :id")
    suspend fun getPreKey(id: Int): SignalPreKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreKey(entity: SignalPreKeyEntity)

    @Query("DELETE FROM signal_prekeys WHERE preKeyId = :id")
    suspend fun deletePreKey(id: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM signal_prekeys WHERE preKeyId = :id)")
    suspend fun hasPreKey(id: Int): Boolean

    // Signed PreKeys
    @Query("SELECT * FROM signal_signed_prekeys WHERE signedPreKeyId = :id")
    suspend fun getSignedPreKey(id: Int): SignalSignedPreKeyEntity?

    @Query("SELECT * FROM signal_signed_prekeys")
    suspend fun getAllSignedPreKeys(): List<SignalSignedPreKeyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignedPreKey(entity: SignalSignedPreKeyEntity)

    @Query("DELETE FROM signal_signed_prekeys WHERE signedPreKeyId = :id")
    suspend fun deleteSignedPreKey(id: Int)

    // Kyber PreKeys
    @Query("SELECT * FROM signal_kyber_prekeys WHERE kyberPreKeyId = :id")
    suspend fun getKyberPreKey(id: Int): SignalKyberPreKeyEntity?

    @Query("SELECT * FROM signal_kyber_prekeys")
    suspend fun getAllKyberPreKeys(): List<SignalKyberPreKeyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKyberPreKey(entity: SignalKyberPreKeyEntity)

    @Query("DELETE FROM signal_kyber_prekeys WHERE kyberPreKeyId = :id")
    suspend fun deleteKyberPreKey(id: Int)

    // Trusted Identities
    @Query("SELECT * FROM signal_trusted_identities WHERE addressName = :name AND deviceId = :deviceId")
    suspend fun getTrustedIdentity(name: String, deviceId: Int): SignalTrustedIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrustedIdentity(entity: SignalTrustedIdentityEntity)
}
