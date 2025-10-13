package com.example.cardapp.data.local.entity

data class SubmittedCardData(
    val cardId: String,
    val scanTime: String // ISO format timestamp
)

data class SubmitScannedCardsResponse(
    val status: String,
    val statusCode: Int,
    val message: String,
    val data: SubmissionResultData? = null
)

data class SubmissionResultData(
    val sessionId: String? = null,
    val submittedCount: Int = 0,
    val duplicateCount: Int = 0,
    val errorCount: Int = 0
)

data class ScanningSession(
    val sessionId: String = java.util.UUID.randomUUID().toString(),
    val batchNumber: Int,
    val startTime: String,
    val deviceId: String = android.os.Build.MODEL ?: "UNKNOWN_DEVICE",
    val operatorId: String = "MOBILE_OPERATOR",
    val scannedCards: MutableList<SubmittedCardData> = mutableListOf()
) {
    fun addScannedCard(cardId: String, scanTime: String) {
        scannedCards.add(SubmittedCardData(cardId, scanTime))
    }

}
