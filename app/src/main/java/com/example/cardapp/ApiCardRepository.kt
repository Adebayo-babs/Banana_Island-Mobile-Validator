package com.example.cardapp

import android.annotation.SuppressLint
import android.util.Log
import com.example.cardapp.model.SubmitScannedCardsRequest
import com.example.cardapp.model.SubmitScannedCardsResponse

class ApiCardRepository {

    companion object {
        private const val TAG = "ApiCardRepository"
    }

    private val api = ApiService.api

    // Cache for batch data to avoid repeated API calls
    private val batchCache = mutableMapOf<Int, BatchData>()

    data class BatchData(
        val batchNumber: Int,
        val batchName: String,
        val cardIds: List<String>,
        val totalCards: Int,
        val fetchTime: Long = System.currentTimeMillis()
    )

    // Fetch all available batches
    @SuppressLint("DefaultLocale")
    suspend fun getAvailableBatches(): List<String> {
        return try {
            Log.d(TAG, "Fetching available batches from API...")
            val response = api.getAllBatches()

            Log.d(TAG, "API Response - Status: ${response.status}, Message: ${response.message}")
            Log.d(TAG, "Total batches received: ${response.data.size}")

            // Filter only ACTIVE batches and extract batch numbers
            val activeBatches = response.data
                .filter { batch ->
                    batch.status == "ACTIVE"
                }
                .sortedBy { it.batchNo } // Sort by batch number
                .mapNotNull { batch ->
                    batch.batchNo?.toString()?.padStart(3, '0')
                }
                .distinct()

            Log.d(TAG, "Active batch numbers found: $activeBatches")
            Log.d(TAG, "Total active batches: ${activeBatches.size}")

            activeBatches

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching batches from API: ${e.message}")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            e.printStackTrace()
            throw e
        }
    }

    // Optimized batch fetching with caching
    suspend fun fetchBatchWithCache(batchNumber: Int): BatchData? {
        return try {
            // Check cache first (cache for 5 minutes)
            val cached = batchCache[batchNumber]
            if (cached != null && (System.currentTimeMillis() - cached.fetchTime) < 300000) {
                Log.d(TAG, "Using cached data for batch $batchNumber")
                return cached
            }

            Log.d(TAG, "Fetching fresh batch data for batch number: $batchNumber")

            val response = api.fetchBatch(FetchBatchRequest(batchNumber))
            Log.d(TAG, "Batch fetch response: ${response.message}")

            val batchData = response.data?.let { data ->
                BatchData(
                    batchNumber = data.batchNumber,
                    batchName = data.batchName,
                    cardIds = data.cardIds,
                    totalCards = data.totalCards
                )
            }

            // Cache the result
            if (batchData != null) {
                batchCache[batchNumber] = batchData
                Log.d(TAG, "Cached batch data for $batchNumber with ${batchData.cardIds.size} cards")
            }

            batchData
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching batch $batchNumber: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // Optimized card verification
    suspend fun verifyCardInBatch(cardId: String, batchNumber: Int): CardVerificationResult {
        return try {
            Log.d(TAG, "========== VERIFYING CARD $cardId IN BATCH $batchNumber ==========")

            val batchData = fetchBatchWithCache(batchNumber)

            if (batchData == null) {
                Log.w(TAG, "Could not fetch batch data for $batchNumber")
                return CardVerificationResult(
                    isFound = false,
                    batchNumber = batchNumber,
                    batchName = "Batch $batchNumber",
                    message = "Failed to load batch $batchNumber from API",
                    totalCardsInBatch = 0
                )
            }

            // Check if card exists in batch
            val isFound = batchData.cardIds.any { it.equals(cardId, ignoreCase = true) }

            Log.d(TAG, "Card verification result:")
            Log.d(TAG, "- Card ID: $cardId")
            Log.d(TAG, "- Batch: ${batchData.batchName}")
            Log.d(TAG, "- Found: $isFound")
            Log.d(TAG, "- Total cards in batch: ${batchData.totalCards}")

            if (isFound) {
                Log.d(TAG, "✅ VERIFICATION SUCCESS - Card found in batch")
            } else {
                Log.d(TAG, "❌ VERIFICATION FAILED - Card not found in batch")
                Log.d(TAG, "Available cards in batch:")
                batchData.cardIds.forEachIndexed { index, id ->
                    Log.d(TAG, "   ${index + 1}. $id")
                }
            }

            CardVerificationResult(
                isFound = isFound,
                batchNumber = batchNumber,
                batchName = batchData.batchName,
                message = if (isFound) "Card found in ${batchData.batchName}"
                else "Card not found in ${batchData.batchName}",
                totalCardsInBatch = batchData.totalCards,
                cardIds = batchData.cardIds
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error verifying card $cardId in batch $batchNumber: ${e.message}")
            e.printStackTrace()
            CardVerificationResult(
                isFound = false,
                batchNumber = batchNumber,
                batchName = "Batch $batchNumber",
                message = "Verification error: ${e.message}",
                totalCardsInBatch = 0
            )
        }
    }

    // Find card in any available batch
    suspend fun findCardInAnyBatch(cardId: String): CardLocationResult? {
        return try {
            Log.d(TAG, "Searching for card $cardId in all available batches...")

            val availableBatches = getAvailableBatches()

            for (batchNumberString in availableBatches) {
                val batchNumber = batchNumberString.toInt()
                val batchData = fetchBatchWithCache(batchNumber)
                if (batchData != null) {
                    val isFound = batchData.cardIds.any { it.equals(cardId, ignoreCase = true) }
                    if (isFound) {
                        Log.d(TAG, "Card $cardId found in batch $batchNumber")
                        return CardLocationResult(
                            cardId = cardId,
                            batchNumber = batchNumber,
                            batchName = batchData.batchName,
                            isFound = true
                        )
                    }
                }
            }

            Log.d(TAG, "Card $cardId not found in any available batch")
            null

        } catch (e: Exception) {
            Log.e(TAG, "Error searching for card $cardId: ${e.message}")
            null
        }
    }

    // Clear cache
    fun clearCache() {
        batchCache.clear()
        Log.d(TAG, "Batch cache cleared")
    }

    data class CardVerificationResult(
        val isFound: Boolean,
        val batchNumber: Int,
        val batchName: String,
        val message: String,
        val totalCardsInBatch: Int,
        val cardIds: List<String> = emptyList()
    )

    data class CardLocationResult(
        val cardId: String,
        val batchNumber: Int,
        val batchName: String,
        val isFound: Boolean
    )

    // Keep other existing methods...
//    suspend fun fetchBatchInfo(batchNumber: String): Batch? {
//        return try {
//            Log.d(TAG, "Fetching batch info for batch number: $batchNumber")
//            val response = api.fetchBatch(mapOf("batchNumber" to batchNumber))
//            Log.d(TAG, "Batch info response for $batchNumber: ${response.message}")
//            Log.d(TAG, "Batch has ${response.data?.cardIds?.size ?: 0} card IDs")
//
//            response.data
//        } catch (e: Exception) {
//            Log.e(TAG, "Error fetching batch info for $batchNumber: ${e.message}")
//            e.printStackTrace()
//            null
//        }
//    }

//    suspend fun getBatchCardIds(batchNumber: String): List<String> {
//        return try {
//            val batchData = fetchBatchWithCache(batchNumber)
//            batchData?.cardIds ?: emptyList()
//        } catch (e: Exception) {
//            Log.e(TAG, "Error getting card IDs for batch $batchNumber: ${e.message}")
//            emptyList()
//        }
//    }

    suspend fun getBatchCompletion(batchNumber: Int): BatchCompletionStats? {
        return try {
            Log.d(TAG, "Getting batch completion for: $batchNumber")
            api.getBatchCompletion(batchNumber)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting batch completion for $batchNumber: ${e.message}")
            null
        }
    }

    suspend fun verifyCard(
        cardId: String,
        batchNumber: String,
        holderName: String? = null,
        additionalData: Map<String, String>? = null
    ): VerifyCardResponse {
        return try {
            Log.d(TAG, "Verifying card $cardId against batch $batchNumber")
            val request = VerifyCardRequest(
                cardId = cardId,
                batchNumber = batchNumber,
                holderName = holderName,
                additionalData = additionalData
            )

            val response = api.verifyCard(request)
            Log.d(TAG, "Verification response: Success=${response.success}, Message=${response.message}")

            response
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying card $cardId: ${e.message}")
            e.printStackTrace()
            VerifyCardResponse(
                success = false,
                message = "API Error: ${e.message}",
                cardId = cardId
            )
        }
    }

    suspend fun enquireCard(cardId: String): EnquireCardResponse {
        return try {
            Log.d(TAG, "Enquiring about card: $cardId")
            val request = EnquireCardRequest(cardId = cardId)

            val response = api.enquireCard(request)
            Log.d(TAG, "Enquiry response: Exists=${response.cardExists}, Message=${response.message}")

            response
        } catch (e: Exception) {
            Log.e(TAG, "Error enquiring card $cardId: ${e.message}")
            e.printStackTrace()
            EnquireCardResponse(
                cardExists = false,
                message = "API Error: ${e.message}"
            )
        }
    }







    suspend fun submitScannedCards(request: SubmitScannedCardsRequest): SubmitScannedCardsResponse {
        return try {
            // SIMPLE LOGGING - What we're sending
            Log.d(TAG, "Batch Number: ${request.batchNumber}")
            Log.d(TAG, "Session Start: ${request.sessionStartTime}")
            Log.d(TAG, "Session End: ${request.sessionEndTime}")
            Log.d(TAG, "Device ID: ${request.deviceId}")
            Log.d(TAG, "Location: ${request.location}")
            Log.d(TAG, "Operator ID: ${request.operatorId}")
            Log.d(TAG, "Number of cards: ${request.scannedCards.size}")
            Log.d(TAG, "Notes: ${request.notes}")

            // Log each card
            request.scannedCards.forEachIndexed { i, card ->
                Log.d(TAG, "Card $i: ID=${card.cardId}")
            }

            // Convert to JSON and log it
            val gson = com.google.gson.Gson()
            val jsonString = gson.toJson(request)
            Log.d(TAG, "JSON being sent: $jsonString")

            val response = api.submitScannedCards(request)

            Log.d(TAG, "API submission successful: $response")

            if (response.isSuccessful) {
                response.body() ?: throw Exception("Empty response body")
            } else {
                throw Exception("API error: ${response.code()} - ${response.errorBody()?.string()}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error submitting cards to API: ${e.message}")
            throw e
        }
    }

}