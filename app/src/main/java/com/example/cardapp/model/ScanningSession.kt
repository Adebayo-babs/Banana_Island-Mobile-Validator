package com.example.cardapp.model

import android.util.Log

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

//    fun removeScannedCard(cardId: String) {
//        scannedCards.find { it.cardId.equals(cardId, ignoreCase = true)}
//    }

    fun removeScannedCard(cardId: String) {
        Log.d("ScanningSession", "=== REMOVE FROM SESSION DEBUG ===")
        Log.d("ScanningSession", "Attempting to remove card: $cardId")
        Log.d("ScanningSession", "Session size before: ${scannedCards.size}")
        Log.d("ScanningSession", "All cards in session: ${scannedCards.map { it.cardId }}")

        val initialSize = scannedCards.size

        // Find the specific card to remove
        val cardToRemove = scannedCards.find { it.cardId.equals(cardId, ignoreCase = true) }

        if (cardToRemove != null) {
            Log.d("ScanningSession", "Found matching card: ${cardToRemove.cardId}")
            val removed = scannedCards.remove(cardToRemove)
            Log.d("ScanningSession", "Removal successful: $removed")
            Log.d("ScanningSession", "Session size after: ${scannedCards.size}")
            Log.d("ScanningSession", "Cards remaining: ${scannedCards.map { it.cardId }}")
        } else {
            Log.w("ScanningSession", "Card $cardId not found in session")
            Log.d("ScanningSession", "Available cards for comparison:")
            scannedCards.forEachIndexed { index, card ->
                Log.d("ScanningSession", "  [$index] '${card.cardId}' (equals check: ${card.cardId.equals(cardId, ignoreCase = true)})")
            }
        }

        Log.d("ScanningSession", "=== END REMOVE FROM SESSION DEBUG ===")
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
