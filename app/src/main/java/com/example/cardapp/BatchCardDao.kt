package com.example.cardapp

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchCardDao {
    @Query("SELECT * FROM batch_cards WHERE batch_name = :batchName ORDER BY created_at ASC")
    fun getCardsByBatch(batchName: String): Flow<List<BatchCard>>

    @Query("SELECT * FROM batch_cards WHERE card_id = :cardId")
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