package com.example.cardapp

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "batch_cards")
data class BatchCard(
    @PrimaryKey
    @ColumnInfo(name = "card_id")
    val cardId: String,

    @ColumnInfo(name = "batch_name")
    val batchName: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "card_owner")
    val cardOwner: String? = null,

    @ColumnInfo(name = "description")
    val description: String? = null

)