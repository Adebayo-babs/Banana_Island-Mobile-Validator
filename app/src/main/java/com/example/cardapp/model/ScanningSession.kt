package com.example.cardapp.model

// Updated API data classes to match the actual endpoint
data class SubmitScannedCardsRequest(
    val batchNumber: Int,
    val sessionStartTime: String,
    val sessionEndTime: String,
    val deviceId: String,
    val location: String = "LAGOS_PERSO_CENTER", // Default location
    val operatorId: String,
    val scannedCards: List<SubmittedCardData>,
    val notes: String? = null
)

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

    fun createSubmissionRequest(endTime: String, notes: String? = null): SubmitScannedCardsRequest {
        return SubmitScannedCardsRequest(
            batchNumber = batchNumber,
            sessionStartTime = startTime,
            sessionEndTime = endTime,
            deviceId = deviceId,
            operatorId = operatorId,
            scannedCards = scannedCards.toList(),
            notes = notes
        )
    }
}
