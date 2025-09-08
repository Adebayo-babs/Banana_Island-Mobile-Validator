package com.example.cardapp.model.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.cardapp.model.BatchCard
import com.example.cardapp.model.VerifiedCard
import kotlinx.coroutines.flow.Flow

@Dao
interface VerifiedCardDao {
    @Query("SELECT * FROM verified_cards ORDER BY verified_at DESC")
    fun getAllVerifiedCards(): Flow<List<VerifiedCard>>

    // In BatchCardDao
    @Query("SELECT * FROM batch_cards WHERE card_id = :cardId LIMIT 1")
    suspend fun getBatchCardById(cardId: String): BatchCard?

    // NEW: Get verified card IDs by batch (for filtering unverified cards)
    @Query("SELECT card_id FROM verified_cards WHERE batch_name = :batchName")
    suspend fun getVerifiedCardIdsByBatch(batchName: String): List<String>

    @Query("SELECT * FROM verified_cards WHERE batch_name = :batchName ORDER BY verified_at DESC")
    fun getVerifiedCardsByBatch(batchName: String): Flow<List<VerifiedCard>>

    @Query("SELECT * FROM verified_cards WHERE card_id = :cardId")
    suspend fun getVerifiedCardById(cardId: String): VerifiedCard?

    @Insert
    suspend fun insertVerifiedCard(verifiedCard: VerifiedCard): Long

    @Query("DELETE FROM verified_cards WHERE card_id = :cardId")
    suspend fun deleteVerifiedCard(cardId: String)

    @Query("DELETE FROM verified_cards WHERE batch_name = :batchName")
    suspend fun deleteVerifiedCardsByBatch(batchName: String)

    @Query("DELETE FROM verified_cards")
    suspend fun deleteAllVerifiedCards()

    @Query("SELECT COUNT(*) FROM verified_cards WHERE batch_name = :batchName")
    suspend fun getVerifiedCountInBatch(batchName: String): Int

    @Query("SELECT COUNT(DISTINCT card_id) FROM verified_cards WHERE batch_name = :batchName")
    suspend fun getUniqueVerifiedCountInBatch(batchName: String): Int


    @Query("DELETE FROM batch_cards WHERE batch_name = :batchName")
    suspend fun deleteCardsByBatch(batchName: String)

    @Query("SELECT * FROM batch_cards WHERE batch_name = :batchName")
    suspend fun getCardsByBatchSync(batchName: String): List<BatchCard>
}