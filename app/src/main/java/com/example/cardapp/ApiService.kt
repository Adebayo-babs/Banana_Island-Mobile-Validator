package com.example.cardapp

import com.example.cardapp.model.SubmitScannedCardsRequest
import com.example.cardapp.model.SubmitScannedCardsResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

data class BatchInfo(
    val batchName: String? = null,
    val batchNumber: String? = null,
    val totalCards: Int = 0,
    val cards: List<String>? = null,
    val description: String? = null
)

data class BatchResponse(
    val status: String,
    val statusCode: Int,
    val message: String,
    val data: Batch,
    val metadata: Any? = null
)

data class Batch(
    val batchNumber: Int,
    val batchName: String,
    val cardIds: List<String>,
    val totalCards: Int,
    val personalizationVendor: String,
    val expiryDate: String,
    val contactLga: String,
    val createdAt: String,
    val jobNumber: String,
    val description: String,
    val bankJobNumber: String,
    val reorderFlag: Boolean,
    val expectedRecords: Int,
    val actualRecords: Int,
    val integrityCheck: Boolean
)

data class BatchSummaryStats(
    val totalBatches: Int = 0,
    val totalCards: Int = 0,
    val totalVerified: Int = 0,
    val completionPercentage: Double = 0.0
)

data class BatchCompletionStats(
    val batchNumber: String,
    val totalCards: Int,
    val scannedCards: Int,
    val verifiedCards: Int,
    val completionPercentage: Double,
    val discrepancies: Int = 0
)

data class VerifyCardRequest(
    val cardId: String,
    val batchNumber: String? = null,
    val holderName: String? = null,
    val additionalData: Map<String, String>? = null
)

data class VerifyCardResponse(
    val success: Boolean,
    val message: String,
    val cardId: String? = null,
    val batchNumber: String? = null,
    val isVerified: Boolean = false,
    val alreadyScanned: Boolean = false
)

data class EnquireCardRequest(
    val cardId: String
)

data class EnquireCardResponse(
    val status: String,
    val statusCode: Int,
    val message: String,
    val data: CardData?
)

data class CardData(
    val cardId: String,
    val found: Boolean,
    val batchNumber: Int?,
    val batchName: String?,
    val cardHolder: CardHolder?,
    val status: String?,
    val deliveryStatus: String?
)

data class CardHolder(
    val surname: String,
    val firstname: String,
    val middlename: String,
    val contactLga: String,
    val stateOfResidence: String
)


data class ScanRecord(
    val timestamp: String,
    val batchNumber: String,
    val verified: Boolean
)

data class SubmitCorrectionRequest(
    val cardId: String,
    val correctBatch: String,
    val reason: String? = null,
    val notes: String? = null
)

data class SubmitCorrectionResponse(
    val success: Boolean,
    val message: String,
    val correctionId: String? = null
)

//data class SubmitScannedCardsRequest(
//    val sessionId: String? = null,
//    val batchNumber: String,
//    val scannedCards: List<ScannedCardData>,
//    val scannerInfo: ScannerInfo? = null
//)

data class ScannedCardData(
    val cardId: String,
    val timestamp: String,
    val holderName: String? = null,
    val verified: Boolean,
    val additionalData: Map<String, String>? = null
)

data class ScannerInfo(
    val deviceId: String? = null,
    val appVersion: String? = null,
    val scannerName: String? = null
)

data class BatchListResponse(
    val status: String,
    val statusCode: Int,
    val message: String,
    val data: List<BatchSummary>
)


data class FetchBatchResponse(
    val status: String,
    val statusCode: Int,
    val message: String,
    val data: Batch?
)


data class BatchSummary(
    val batchId: Int,
    val batchNo: Int? = null,
    val name: String,
    val description: String? = null,
    val personalizationVendor: String? = null,
    val totalRecords: Int,
    val status: String, // "ACTIVE", "CANCELLED", etc.
    val expiryMonth: Int? = null,
    val expiryYear: Int? = null,
    val contactLga: String? = null,
    val createdAt: String? = null,
    val lastUpdated: String? = null
)

data class FetchBatchRequest(
    val batchNumber: Int
)

data class ApiResponse(
    val status: String,
    val message: String,
    val data: Any? = null
)




// Retrofit API interface
interface CardBatchApi {

    // Get all available batches
    @GET("/api/v1/card-batch-scanning/batches")
    suspend fun getAllBatches(): BatchListResponse

    // Fetch specific batch information
    @POST("/api/v1/card-batch-scanning/fetch-batch")
    suspend fun fetchBatch(@Body request: FetchBatchRequest): FetchBatchResponse

    // Get batch summary statistics
    @GET("/api/v1/card-batch-scanning/batches/summary-stats")
    suspend fun getBatchSummaryStats(): BatchSummaryStats

    // Get batch completion statistics
    @GET("/api/v1/card-batch-scanning/batches/{batchNumber}/completion")
    suspend fun getBatchCompletion(@Path("batchNumber") batchNumber: Int): BatchCompletionStats

    // Get batch scanning dashboard
    @GET("/api/v1/card-batch-scanning/batches/{batchNumber}/dashboard")
    suspend fun getBatchDashboard(@Path("batchNumber") batchNumber: String): Map<String, Any>

    // Verify card against batch
    @POST("/api/v1/card-batch-scanning/verify-card")
    suspend fun verifyCard(@Body request: VerifyCardRequest): VerifyCardResponse

    // Enquire about card
    @POST("/api/v1/card-batch-scanning/enquire-card")
    suspend fun enquireCard(@Body request: EnquireCardRequest): EnquireCardResponse

    // Submit correction
    @POST("/api/v1/card-batch-scanning/submit-correction")
    suspend fun submitCorrection(@Body request: SubmitCorrectionRequest): SubmitCorrectionResponse

    // Submit scanned cards batch
    @POST("/api/v1/card-batch-scanning/submit-scanned-cards")
    suspend fun submitScannedCards(@Body request: SubmitScannedCardsRequest): Response<SubmitScannedCardsResponse>

    // Get scan session details
    @GET("/api/v1/card-batch-scanning/sessions/{sessionId}")
    suspend fun getSessionDetails(@Path("sessionId") sessionId: String): Map<String, Any>

    // Get scan history for batch
    @GET("/api/v1/card-batch-scanning/batches/{batchNumber}/scan-history")
    suspend fun getBatchScanHistory(@Path("batchNumber") batchNumber: String): List<ScanRecord>

    // Get batch discrepancies
    @GET("/api/v1/card-batch-scanning/batches/{batchNumber}/discrepancies")
    suspend fun getBatchDiscrepancies(@Path("batchNumber") batchNumber: String): Map<String, Any>

    // Validate batch integrity
    @GET("/api/v1/card-batch-scanning/batches/{batchNumber}/integrity")
    suspend fun validateBatchIntegrity(@Path("batchNumber") batchNumber: String): Map<String, Any>

    // Generate batch report
    @GET("/api/v1/card-batch-scanning/batches/{batchNumber}/report")
    suspend fun generateBatchReport(@Path("batchNumber") batchNumber: String): Map<String, Any>

    // Update batch scan statistics
    @PUT("/api/v1/card-batch-scanning/batches/{batchNumber}/statistics")
    suspend fun updateBatchStatistics(
        @Path("batchNumber") batchNumber: String,
        @Body stats: Map<String, Any>
    ): Map<String, Any>

    // Get pending corrections
    @GET("/api/v1/card-batch-scanning/corrections/pending")
    suspend fun getPendingCorrections(): List<Map<String, Any>>

    // Apply correction
    @PUT("/api/v1/card-batch-scanning/corrections/{correctionId}/apply")
    suspend fun applyCorrection(@Path("correctionId") correctionId: String): Map<String, Any>

    // Bulk apply corrections for batch
    @PUT("/api/v1/card-batch-scanning/batches/{batchNumber}/corrections/bulk-apply")
    suspend fun bulkApplyCorrections(@Path("batchNumber") batchNumber: String): Map<String, Any>

    // Cleanup old scan sessions
    @DELETE("/api/v1/card-batch-scanning/sessions/cleanup")
    suspend fun cleanupOldSessions(): Map<String, Any>
}

// API Service singleton
object ApiService {
    private const val BASE_URL = "https://lasrra-internal-card-tracking-api.onrender.com/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: CardBatchApi = retrofit.create(CardBatchApi::class.java)
}