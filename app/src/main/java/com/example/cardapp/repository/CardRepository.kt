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


    // Track currently loaded batch to avoid unnecessary API calls
    private var currentlyLoadedBatch: String? = null
    private var availableBatches: List<String> = emptyList()

    // Cache for current batch card IDs (Local Verification)
    private var currentBatchCardIds: List<String> = emptyList()

    data class CardEnquiryResult(
        val cardExists: Boolean,
        val batchName: String?,
        val batchNumber: Int? = null,
        val isVerified: Boolean,
        val message: String,
        val batchCard: BatchCard? = null,
        val verifiedCard: VerifiedCard? = null
    )


    companion object {
        private const val TAG = "CardRepository"
    }


    // Get available batches (convert API batch numbers to display names)
    fun getAvailableBatches(): List<String> {
        return availableBatches.map { batchNumber ->
            "Batch $batchNumber"
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


    // Clear current batch data
    suspend fun clearCurrentBatch() {
        withContext(Dispatchers.IO) {
            currentlyLoadedBatch = null
            currentBatchCardIds = emptyList()
            Log.d(TAG, "Current batch data cleared")
        }
    }

    suspend fun insertVerifiedCard(verifiedCard: VerifiedCard): Long = verifiedCardDao.insertVerifiedCard(verifiedCard)



    data class VerificationResult(
        val isSuccess: Boolean,
        val message: String,
        val batchCard: BatchCard? = null,
        val verifiedCardId: Long? = null
    )


}