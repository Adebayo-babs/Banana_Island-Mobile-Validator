package com.example.cardapp

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "verified_cards")
data class VerifiedCard(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "card_id")
    val cardId: String,

    @ColumnInfo(name = "batch_name")
    val batchName: String,

    @ColumnInfo(name = "holder_name")
    val holderName: String?,

    @ColumnInfo(name = "verified_at")
    val verifiedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "additional_data")
    val additionalData: String? = null
)
