package com.example.cardapp.model

data class CardInfo(
    val id: String,
    val timestamp: Long,
    val additionalInfo: String,
    val techList: List<String>,
    val verificationStatus: String? = null,
    val batchName: String? = null,
    val isVerified: Boolean = false
)