package com.example.cardapp.repository

import android.util.Log
import com.example.cardapp.ApiCardRepository
import com.example.cardapp.EnquireCardResponse
import com.example.cardapp.model.BatchCard
import com.example.cardapp.model.SubmitScannedCardsRequest
import com.example.cardapp.model.SubmitScannedCardsResponse
import com.example.cardapp.model.VerifiedCard
import com.example.cardapp.model.database.BatchCardDao
import com.example.cardapp.model.database.VerifiedCardDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CardRepository(
    private val batchCardDao: BatchCardDao,
    private val verifiedCardDao: VerifiedCardDao
) {

    // API repository for network calls
    private val apiRepository = ApiCardRepository()

    // Track currently loaded batch to avoid unnecessary API calls
    private var currentlyLoadedBatch: String? = null
    private var availableBatches: List<String> = emptyList()

    // Cache for current batch card IDs (Local Verification)
    private var currentBatchCardIds: List<String> = emptyList()

    // Load specific batch (Called when search button is clicked)
    suspend fun loadSpecificBatch(batchName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading batch: $batchName")
                val batchNumber = batchNameToBatchNumber(batchName)
                val batchData = apiRepository.fetchBatchWithCache(batchNumber)

                if (batchData == null) {
                    Log.w(TAG, "Failed to load batch data for $batchName")
                    return@withContext false
                }

                //Cache the card IDs for fast local verification
                currentBatchCardIds = batchData.cardIds
                currentlyLoadedBatch = batchName

                Log.d(TAG, "Batch loaded successfully with ${currentBatchCardIds.size} cards")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Error loading batch $batchName: ${e.message}")
                return@withContext false
            }
        }
    }

    data class CardEnquiryResult(
        val cardExists: Boolean,
        val batchName: String?,
        val batchNumber: Int? = null,
        val isVerified: Boolean,
        val message: String,
        val batchCard: BatchCard? = null,
        val verifiedCard: VerifiedCard? = null
    )

    suspend fun performGlobalEnquiry(cardId: String): CardEnquiryResult {
        return try {
            Log.d("CardRepository", "Starting global enquiry for card: $cardId")

            // Get all available batches
            val availableBatches = getAvailableBatches()
            Log.d("CardRepository", "Searching through ${availableBatches.size} batches")

            if (availableBatches.isEmpty()) {
                return CardEnquiryResult(
                    cardExists = false,
                    batchName = null,
                    isVerified = false,
                    message = "No batches available to search",
                    batchCard = null,
                    verifiedCard = null
                )
            }

            // Search through each batch
            for (batchName in availableBatches) {
                Log.d("CardRepository", "Searching in batch: $batchName")

                try {
                    // Load batch data from API if not already loaded
                    loadSpecificBatch(batchName)

                    // Check if card exists in this batch's database
                    val batchCard = batchCardDao.getCardById(cardId)

                    if (batchCard != null && batchCard.batchName == batchName) {
                        Log.d("CardRepository", "Card found in batch: $batchName")

                        // Check if it's also been verified (scanned)
                        val verifiedCard = verifiedCardDao.getVerifiedCardById(cardId)
                        val isVerified = verifiedCard != null

                        return CardEnquiryResult(
                            cardExists = true,
                            batchName = batchName,
                            isVerified = isVerified,
                            message = if (isVerified) {
                                "Card found in $batchName and has been verified ✓"
                            } else {
                                "Card found in $batchName but not yet verified"
                            },
                            batchCard = batchCard,
                            verifiedCard = verifiedCard
                        )
                    }

                } catch (e: Exception) {
                    Log.e("CardRepository", "Error searching batch $batchName: ${e.message}")
                    // Continue searching other batches even if one fails
                    continue
                }
            }

            // Card not found in any batch
            Log.d("CardRepository", "Card not found in any of the ${availableBatches.size} batches")
            CardEnquiryResult(
                cardExists = false,
                batchName = null,
                isVerified = false,
                message = "Card ID '$cardId' was not found in any of the ${availableBatches.size} available batches",
                batchCard = null,
                verifiedCard = null
            )

        } catch (e: Exception) {
            Log.e("CardRepository", "Error during global enquiry: ${e.message}")
            CardEnquiryResult(
                cardExists = false,
                batchName = null,
                isVerified = false,
                message = "Error during enquiry: ${e.message}",
                batchCard = null,
                verifiedCard = null
            )
        }
    }

    companion object {
        private const val TAG = "CardRepository"
    }

    // Initialize repository with API data
    suspend fun initializeWithApi() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing repository with API data...")

                // Get available batches from API
                availableBatches = apiRepository.getAvailableBatches()
                Log.d(TAG, "Available batches from API: $availableBatches")

                Log.d(TAG, "Repository initialization complete - loaded ${availableBatches.size} available batches")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing repository: ${e.message}")
                throw e
            }
        }
    }

    // Get available batches (convert API batch numbers to display names)
    fun getAvailableBatches(): List<String> {
        return availableBatches.map { batchNumber ->
            "Batch $batchNumber"
        }
    }

    // Convert batch name to batch number for API calls
    private fun batchNameToBatchNumber(batchName: String): Int {
        return batchName.replace("Batch", "").trim().toInt()
    }

    // OPTIMIZED: Load specific batch on demand
    suspend fun loadBatchIfNeeded(batchName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading batch if needed: $batchName")

                val batchNumber = batchNameToBatchNumber(batchName)

                // If this batch is already loaded, skip
                if (currentlyLoadedBatch == batchName) {
                    Log.d(TAG, "Batch $batchName already loaded, skipping")
                    return@withContext true
                }

                // Get batch data from API
                val batchData = apiRepository.fetchBatchWithCache(batchNumber)

                if (batchData == null) {
                    Log.w(TAG, "Failed to load batch data for $batchName")
                    return@withContext false
                }

                Log.d(TAG, "Loaded batch: ${batchData.batchName}")
                Log.d(TAG, "Total cards: ${batchData.totalCards}")
                Log.d(TAG, "Card IDs: ${batchData.cardIds.size}")

                Log.d(TAG, "Fetched Card IDs: ${batchData.cardIds.joinToString()}")


                // Clear any previous batch data from local database
                if (currentlyLoadedBatch != null) {
                    batchCardDao.deleteCardsByBatch(currentlyLoadedBatch!!)
                    Log.d(TAG, "Cleared previous batch data: $currentlyLoadedBatch")
                }

                // Store batch cards in local database for fast lookups
                val batchCards = batchData.cardIds.map { cardId ->
                    BatchCard(
                        cardId = cardId,
                        batchName = batchName,
                        description = "Card from ${batchData.batchName}",
                        cardOwner = null
                    )
                }

                if (batchCards.isNotEmpty()) {
                    insertCards(batchCards)
                    Log.d(TAG, "Inserted ${batchCards.size} cards into local database")
                }

                // Update currently loaded batch
                currentlyLoadedBatch = batchName

                true
            } catch (e: Exception) {
                Log.e(TAG, "Error loading batch $batchName: ${e.message}")
                false
            }
        }
    }

    // Local verification (No API calls to other batches)
    suspend fun verifyScannedCardAgainstBatch(
        scannedCardId: String,
        targetBatchName: String,
        holderName: String?,
        additionalData: Map<String, String> = emptyMap()
    ): VerificationResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "LOCAL BATCH VERIFICATION ")
                Log.d(TAG, "Verifying card ID: '$scannedCardId'")
                Log.d(TAG, "Against loaded batch: '$targetBatchName'")

                // Check if the batch has been loaded
                if (currentlyLoadedBatch != targetBatchName || currentBatchCardIds.isEmpty()) {
                    return@withContext VerificationResult(
                        isSuccess = false,
                        message = "Batch not loaded. Enter the batch number and click the search button"
                    )
                }

                // Check if card exists in current batch (Local check only)
                val isFound = currentBatchCardIds.any { it.equals(scannedCardId, ignoreCase = true)}

                Log.d(TAG, "Local verification result: $isFound")

                if (isFound) {
                    Log.d(TAG, "Card found in current batch")
                    val batchCard = BatchCard(
                        cardId = scannedCardId,
                        batchName = targetBatchName,
                        description = "Verified card from $targetBatchName",
                        cardOwner = holderName
                    )

                    // Check if already verified to avoid duplicates
                    val existingVerification = verifiedCardDao.getVerifiedCardById(scannedCardId)
                    var verifiedId = existingVerification?.id

                    if (existingVerification == null) {
                        val verifiedCard = VerifiedCard(
                            cardId = scannedCardId,
                            batchName = targetBatchName,
                            holderName = holderName,
                            additionalData = additionalData.takeIf { it.isNotEmpty() }?.let { data ->
                                data.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                            }
                        )
                        verifiedId = insertVerifiedCard(verifiedCard)
                        Log.d(TAG, "Added new verification record (ID: $verifiedId)")
                    }

                    return@withContext VerificationResult(
                        isSuccess = true,
                        message = "Card found in $targetBatchName",
                        batchCard = batchCard,
                        verifiedCardId = verifiedId
                    )
                } else {
                    Log.d(TAG, "CARD NOT FOUND in current batch")

                    // NO API CALL TO OTHER BATCHES - just return not found
                    return@withContext VerificationResult(
                        isSuccess = false,
                        message = "Card not found in $targetBatchName"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during fast verification: ${e.message}")
                return@withContext VerificationResult(
                    isSuccess = false,
                    message = "Verification error: ${e.message}"
                )
            }
        }
    }

    suspend fun getCurrentBatchStats(batchName: String): VerificationStats {
        return withContext(Dispatchers.IO) {
            try {
                val totalCards = currentBatchCardIds.size
                val verifiedCards = getUniqueVerifiedCountInBatch(batchName)
                val totalScans = getVerifiedCountInBatch(batchName)

                VerificationStats(
                    totalCards = totalCards,
                    verifiedCards = verifiedCards,
                    totalScans = totalScans
                )
            }catch (e: Exception) {
                Log.e(TAG, "Error getting current batch stats: ${e.message}")
                VerificationStats(0, 0, 0)
            }
        }
    }

    // Clear current batch data
    suspend fun clearCurrentBatch() {
        withContext(Dispatchers.IO) {
            currentlyLoadedBatch = null
            currentBatchCardIds = emptyList()
            Log.d(TAG, "Current batch data cleared")
        }
    }

    // NEW: Remove specific verified card
    suspend fun removeVerifiedCard(cardId: String) {
        withContext(Dispatchers.IO) {
            try {
                verifiedCardDao.deleteVerifiedCard(cardId)
                Log.d(TAG, "Removed verified card: $cardId")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing verified card: ${e.message}")
            }
        }
    }



    // Find card in any batch using API
    suspend fun findCardInAnyBatch(cardId: String): BatchCard? {
        return withContext(Dispatchers.IO) {
            try {
                val cardLocation = apiRepository.findCardInAnyBatch(cardId)

                if (cardLocation != null) {
                    BatchCard(
                        cardId = cardId,
                        batchName = "Batch ${cardLocation.batchNumber}",
                        description = "Found in ${cardLocation.batchName}",
                        cardOwner = null
                    )
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "Error finding card in any batch: ${e.message}")
                null
            }
        }
    }

    // Enquire about a scanned card using API
    suspend fun enquireScannedCard(scannedCardId: String): CardEnquiryResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Enquiring about card ID via API: $scannedCardId")

                val apiResponse = apiRepository.enquireCard(scannedCardId)
                val cardData = apiResponse.data

                CardEnquiryResult(
                    cardExists = cardData?.found == true,
                    batchName = cardData?.batchName,
                    batchNumber = cardData?.batchNumber,
                    isVerified = false,
                    message = apiResponse.message,
                    batchCard = if (cardData?.found == true && cardData.batchName != null) {
                        BatchCard(
                            cardId = scannedCardId,
                            batchName = cardData.batchName,
                            description = "Found via enquiry",
                            cardOwner = cardData.cardHolder?.firstname
                        )
                    } else null,
                    verifiedCard = verifiedCardDao.getVerifiedCardById(scannedCardId)

                )
            } catch (e: Exception) {
                Log.e(TAG, "Error during API card enquiry: ${e.message}")
                CardEnquiryResult(
                    cardExists = false,
                    batchName = null,
                    batchNumber = null,
                    isVerified = false,
                    message = "❌ ERROR: API enquiry failed - ${e.message}"
                )
            }
        }
    }

    // Get verification statistics
    suspend fun getVerificationStats(batchName: String): VerificationStats {
        return withContext(Dispatchers.IO) {
            try {
                val batchNumber = batchNameToBatchNumber(batchName)

                // Try to get stats from API first
                val apiStats = apiRepository.getBatchCompletion(batchNumber)

                if (apiStats != null) {
                    VerificationStats(
                        totalCards = apiStats.totalCards,
                        verifiedCards = apiStats.verifiedCards,
                        totalScans = apiStats.scannedCards
                    )
                } else {
                    // Fallback: use batch data from API and local verification count
                    val batchData = apiRepository.fetchBatchWithCache(batchNumber)
                    VerificationStats(
                        totalCards = batchData?.totalCards ?: 0,
                        verifiedCards = getUniqueVerifiedCountInBatch(batchName),
                        totalScans = getVerifiedCountInBatch(batchName)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting verification stats: ${e.message}")
                VerificationStats(
                    totalCards = 0,
                    verifiedCards = getUniqueVerifiedCountInBatch(batchName),
                    totalScans = getVerifiedCountInBatch(batchName)
                )
            }
        }
    }

    suspend fun resetBatchVerification(batchName: String) {
        withContext(Dispatchers.IO) {
            try {
                verifiedCardDao.deleteVerifiedCardsByBatch(batchName)
                Log.d(TAG, "Verification data cleared for batch: $batchName")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing batch verification: ${e.message}")
            }
        }
    }

    // Database operations
    suspend fun getCardById(cardId: String): BatchCard? = batchCardDao.getCardById(cardId)

    suspend fun insertCards(cards: List<BatchCard>) = batchCardDao.insertCards(cards)

    suspend fun getCardCountInBatch(batchName: String): Int = batchCardDao.getCardCountInBatch(batchName)

    suspend fun insertVerifiedCard(verifiedCard: VerifiedCard): Long = verifiedCardDao.insertVerifiedCard(verifiedCard)

    suspend fun getVerifiedCountInBatch(batchName: String): Int = verifiedCardDao.getVerifiedCountInBatch(batchName)

    suspend fun getUniqueVerifiedCountInBatch(batchName: String): Int = verifiedCardDao.getUniqueVerifiedCountInBatch(batchName)

    suspend fun clearAllVerifiedCards() {
        withContext(Dispatchers.IO) {
            try {
                verifiedCardDao.deleteAllVerifiedCards()
                Log.d(TAG, "All verified cards cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing verified cards: ${e.message}")
            }
        }
    }

    data class VerificationResult(
        val isSuccess: Boolean,
        val message: String,
        val batchCard: BatchCard? = null,
        val verifiedCardId: Long? = null
    )

    data class VerificationStats(
        val totalCards: Int,
        val verifiedCards: Int,
        val totalScans: Int
    ) {
        val remainingCards: Int get() = totalCards - verifiedCards
        val completionPercentage: Double get() = if (totalCards > 0) (verifiedCards.toDouble() / totalCards) * 100 else 0.0
    }













    suspend fun submitScannedCards(request: SubmitScannedCardsRequest): SubmitScannedCardsResponse {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Submitting ${request.scannedCards.size} cards to API...")
                apiRepository.submitScannedCards(request)
            } catch (e: Exception) {
                Log.e(TAG, "Error submitting cards: ${e.message}")
                SubmitScannedCardsResponse(
                    status = "error",
                    statusCode = 500,
                    message = "Submission failed: ${e.message}"
                )
            }
        }
    }

}