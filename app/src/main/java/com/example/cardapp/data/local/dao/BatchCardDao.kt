package com.example.cardapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cardapp.data.local.entity.BatchCard
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchCardDao {
    @Query("SELECT * FROM batch_cards WHERE batch_name = :batchName ORDER BY created_at ASC")
    suspend fun getCardsByBatch(batchName: String): List<BatchCard>

    @Query("SELECT * FROM batch_cards WHERE card_id = :cardId LIMIT 1")
    suspend fun getBatchCardById(cardId: String): BatchCard?

    // Add to BatchCardDao.kt
    @Query("SELECT * FROM batch_cards WHERE card_id = :cardId LIMIT 1")
    suspend fun getCardById(cardId: String): BatchCard?

    @Query("SELECT * FROM batch_cards WHERE card_id = :cardId AND batch_name = :batchName")
    suspend fun getCardByIdAndBatch(cardId: String, batchName: String): BatchCard?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: BatchCard)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<BatchCard>)

    @Delete
    suspend fun deleteCard(card: BatchCard)

    @Query("SELECT * FROM batch_cards WHERE batch_name = :batchName")
    suspend fun getCardsByBatchSync(batchName: String): List<BatchCard>

    @Query("DELETE FROM batch_cards WHERE batch_name = :batchName")
    suspend fun deleteCardsByBatch(batchName: String)

    @Query("SELECT DISTINCT batch_name FROM batch_cards ORDER BY batch_name")
    fun getAllBatches(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM batch_cards WHERE batch_name = :batchName")
    suspend fun getCardCountInBatch(batchName: String): Int


}