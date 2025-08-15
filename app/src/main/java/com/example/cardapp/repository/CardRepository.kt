package com.example.cardapp.repository

import android.util.Log
import com.example.cardapp.model.BatchCard
import com.example.cardapp.model.VerifiedCard
import com.example.cardapp.model.database.BatchCardDao
import com.example.cardapp.model.database.VerifiedCardDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CardRepository(
    private val batchCardDao: BatchCardDao,
    private val verifiedCardDao: VerifiedCardDao
) {

    // In-memory cache for faster lookups
    private var cardToBufferCache: MutableMap<String, String>? = null
    private var lastCacheUpdate = 0L

    data class CardEnquiryResult(
        val cardExists: Boolean,
        val batchName: String?,
        val isVerified: Boolean,
        val message: String,
        val batchCard: BatchCard? = null,
        val verifiedCard: VerifiedCard? = null
    )


    companion object {
        private const val TAG = "CardRepository"
        const val BATCH_01_NAME = "Batch 001"
        const val BATCH_02_NAME = "Batch 002"
        const val BATCH_03_NAME = "Batch 003"

        // Hardcoded card IDs
        private val BATCH_01_CARDS = listOf(
            "LAG1696317781", "LAG1696317782", "LAG1696317783", "LAG1696317784",
            "LAG1696317785", "LAG1696317786", "LAG1696317787", "LAG1696317788",
            "LAG1696317789", "LAG1696317790", "LAG1696317795", "LAG1252171582"
        )

        private val BATCH_02_CARDS = listOf(
            "LAG1696317781", "LAG1696317801", "LAG1252171582",
            "LAG1696317803", "LAG1696317804", "LAG1696317805"
        )

        private val BATCH_03_CARDS = listOf(
            "LAG1696317900", "LAG1696317901", "LAG1696317902", "LAG1696317903"
        )

        // Map to easily look up which batch a card belongs to
        private val CARD_TO_BATCH_MAP = mutableMapOf<String, String>().apply {
            BATCH_01_CARDS.forEach { put(it, BATCH_01_NAME) }
            BATCH_02_CARDS.forEach { put(it, BATCH_02_NAME) }
            BATCH_03_CARDS.forEach { put(it, BATCH_03_NAME) }
        }
    }

    suspend fun initializeCache() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing performance cache")
                refreshCache()
                Log.d(TAG, "Cache initialized with ${cardToBufferCache?.size} cards")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing cache: ${e.message}")
            }
        }
    }

    private suspend fun refreshCache() {
        val startTime = System.currentTimeMillis()

        // Build card-to-batch cache from database
        cardToBufferCache = mutableMapOf<String, String>().apply {
            putAll(CARD_TO_BATCH_MAP) // Start with hardcoded map
        }

        lastCacheUpdate = System.currentTimeMillis()
        Log.d(TAG, "Cache refreshed in ${System.currentTimeMillis() - startTime}ms")
    }



    // Batch Card operations
    suspend fun getCardById(cardId: String): BatchCard? =
        batchCardDao.getCardById(cardId)

    suspend fun insertCards(cards: List<BatchCard>) =
        batchCardDao.insertCards(cards)

    suspend fun getCardCountInBatch(batchName: String): Int =
        batchCardDao.getCardCountInBatch(batchName)

    suspend fun insertVerifiedCard(verifiedCard: VerifiedCard): Long =
        verifiedCardDao.insertVerifiedCard(verifiedCard)

    suspend fun getVerifiedCountInBatch(batchName: String): Int =
        verifiedCardDao.getVerifiedCountInBatch(batchName)

    suspend fun getUniqueVerifiedCountInBatch(batchName: String): Int =
        verifiedCardDao.getUniqueVerifiedCountInBatch(batchName)

    // Enquire about a scanned card - check which batch it belongs to and verification status
    suspend fun enquireScannedCard(scannedCardId: String): CardEnquiryResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Enquiring about card ID: $scannedCardId")

                // First check in hardcoded map for quick lookup
                val expectedBatch = CARD_TO_BATCH_MAP[scannedCardId]

                if (expectedBatch != null) {
                    Log.d(TAG, "Card found in batch: $expectedBatch")

                    // Get the batch card details
                    val batchCard = getCardById(scannedCardId)

                    // Check if it's verified
                    val verifiedCard = verifiedCardDao.getVerifiedCardById(scannedCardId)
                    val isVerified = verifiedCard != null

                    val message = if (isVerified) {
                        "Card found in $expectedBatch and is verified"
                    } else {
                        "Card found in $expectedBatch but not yet verified"
                    }

                    CardEnquiryResult(
                        cardExists = true,
                        batchName = expectedBatch,
                        isVerified = isVerified,
                        message = message,
                        batchCard = batchCard,
                        verifiedCard = verifiedCard
                    )
                } else {
                    // Fallback: Check in database (in case card exists but not in hardcoded list)
                    val batchCard = getCardById(scannedCardId)

                    if (batchCard != null) {
                        val verifiedCard = verifiedCardDao.getVerifiedCardById(scannedCardId)
                        val isVerified = verifiedCard != null

                        val message = if (isVerified) {
                            "✅ Card found in ${batchCard.batchName} and is VERIFIED"
                        } else {
                            "📋 Card found in ${batchCard.batchName} but NOT YET VERIFIED"
                        }

                        CardEnquiryResult(
                            cardExists = true,
                            batchName = batchCard.batchName,
                            isVerified = isVerified,
                            message = message,
                            batchCard = batchCard,
                            verifiedCard = verifiedCard
                        )
                    } else {
                        Log.d(TAG, "Card not found in any batch")
                        CardEnquiryResult(
                            cardExists = false,
                            batchName = null,
                            isVerified = false,
                            message = "❌ Card ID '$scannedCardId' not found in any batch"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during card enquiry: ${e.message}")
                CardEnquiryResult(
                    cardExists = false,
                    batchName = null,
                    isVerified = false,
                     message = "❌ ERROR: Enquiry failed - \${e.message}"
                )
            }
        }
    }

    // Initialize all batches with hardcoded data
    suspend fun initializeAllBatches() {
        withContext(Dispatchers.IO) {
            try {
                // Initialize Batch 001
                initializeBatchWithCards(BATCH_01_NAME, BATCH_01_CARDS)

                // Initialize Batch 002
                initializeBatchWithCards(BATCH_02_NAME, BATCH_02_CARDS)

                // Initialize Batch 003
                initializeBatchWithCards(BATCH_03_NAME, BATCH_03_CARDS)

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing batches: ${e.message}")
            }
        }
    }

    private suspend fun initializeBatchWithCards(batchName: String, cardIds: List<String>) {
        try {
            val existingCount = getCardCountInBatch(batchName)
            if (existingCount == 0) {
                Log.d(TAG, "Initializing $batchName with ${cardIds.size} cards")

                val batchCards = cardIds.map { cardId ->
                    BatchCard(
                        cardId = cardId,
                        batchName = batchName,
                        description = "Pre-loaded card for $batchName"
                    )
                }

                insertCards(batchCards)
                Log.d(TAG, "Successfully initialized $batchName")
            } else {
                Log.d(TAG, "$batchName already exists with $existingCount cards")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing $batchName: ${e.message}")
        }
    }

    // Add this to your CardRepository class
    suspend fun updateBatch001WithNewCards() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== CHECKING FOR BATCH UPDATES ===")

                // Calculate hash of current hardcoded list
                val currentListHash = BATCH_01_CARDS.sorted().joinToString(",").hashCode()

                // Get stored hash from preferences or calculate from database
                val storedHash = getStoredBatchHash()

                Log.d(TAG, "Current list hash: $currentListHash")
                Log.d(TAG, "Stored hash: $storedHash")

                if (currentListHash != storedHash) {
                    Log.d(TAG, "Batch list has changed. Updating database...")

                    // Clear and reinitialize
                    batchCardDao.deleteCardsByBatch(BATCH_01_NAME)

                    val batchCards = BATCH_01_CARDS.map { cardId ->
                        BatchCard(
                            cardId = cardId,
                            batchName = BATCH_01_NAME,
                            description = "Pre-loaded card for initial batch"
                        )
                    }

                    insertCards(batchCards)

                    // Store new hash
                    storeBatchHash(currentListHash)

                    val newCount = getCardCountInBatch(BATCH_01_NAME)
                    Log.d(TAG, "Update complete. New count: $newCount")

                    // Log all card IDs to verify
                    BATCH_01_CARDS.forEach { cardId ->
                        val found = getCardById(cardId)
                        Log.d(TAG, "Card $cardId: ${if (found != null) "✓" else "✗"}")
                    }

                } else {
                    Log.d(TAG, "Batch list unchanged - no update needed")
                }

                Log.d(TAG, "=== UPDATE CHECK COMPLETE ===")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating batch: ${e.message}", e)
            }
        }
    }

    private suspend fun getStoredBatchHash(): Int {
        // Calculate hash from existing database content
        val existingCards = batchCardDao.getCardsByBatchSync(BATCH_01_NAME)
        return existingCards.map { it.cardId }.sorted().joinToString(",").hashCode()
    }

    private suspend fun storeBatchHash(hash: Int) {
        Log.d(TAG, "New batch hash stored: $hash")
        // Store in SharedPreferences
    }

    // Card verification logic
    data class VerificationResult(
        val isSuccess: Boolean,
        val message: String,
        val batchCard: BatchCard? = null,
        val verifiedCardId: Long? = null
    )

    suspend fun verifyScannedCard(
        scannedCardId: String,
        holderName: String?,
        additionalData: Map<String, String> = emptyMap()
    ): VerificationResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Verifying card ID: $scannedCardId")


                // Check if card exists in any batch (starting with Batch 001)
                val batchCard = batchCardDao.getCardByIdAndBatch(scannedCardId.trim(), BATCH_01_NAME)
                    ?: batchCardDao.getCardById(scannedCardId.trim())

                if (batchCard != null) {
                    Log.d(TAG, "Card found in batch: ${batchCard.batchName}")

                    // Check if already verified
                    val existingVerification = verifiedCardDao.getVerifiedCardById(scannedCardId)
                    if (existingVerification != null) {
                        Log.d(TAG, "Card already verified at: ${existingVerification.verifiedAt}")
                        return@withContext VerificationResult(
                            isSuccess = true,
                            message = "SUCCESS: Card already verified in ${batchCard.batchName}",
                            batchCard = batchCard,
                            verifiedCardId = existingVerification.id
                        )
                    }

                    // Create verified card entry
                    val verifiedCard = VerifiedCard(
                        cardId = scannedCardId,
                        batchName = batchCard.batchName,
                        holderName = holderName,
                        additionalData = if (additionalData.isNotEmpty()) {
                            additionalData.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                        } else null
                    )

                    val verifiedId = insertVerifiedCard(verifiedCard)
                    Log.d(TAG, "Card verified successfully with ID: $verifiedId")

                    VerificationResult(
                        isSuccess = true,
                        message = "SUCCESS: Card verified and added to ${batchCard.batchName}",
                        batchCard = batchCard,
                        verifiedCardId = verifiedId
                    )
                } else {
                    Log.d(TAG, "Card not found in any batch")
                    VerificationResult(
                        isSuccess = false,
                        message = "❌ FAILURE: Card ID '$scannedCardId' not found in $BATCH_01_NAME"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during card verification: ${e.message}")
                VerificationResult(
                    isSuccess = false,
                    message = "❌ ERROR: Verification failed - ${e.message}"
                )
            }
        }
    }

    // Get verification statistics
    suspend fun getVerificationStats(batchName: String): VerificationStats {
        return withContext(Dispatchers.IO) {
            VerificationStats(
                totalCards = getCardCountInBatch(batchName),
                verifiedCards = getUniqueVerifiedCountInBatch(batchName),
                totalScans = getVerifiedCountInBatch(batchName)
            )
        }
    }

    data class VerificationStats(
        val totalCards: Int,
        val verifiedCards: Int,
        val totalScans: Int
    ) {
        val remainingCards: Int get() = totalCards - verifiedCards
        val completionPercentage: Double get() = if (totalCards > 0) (verifiedCards.toDouble() / totalCards) * 100 else 0.0
    }
}