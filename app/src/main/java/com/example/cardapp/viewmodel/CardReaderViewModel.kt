package com.example.cardapp.viewmodel

import android.content.ContentValues.TAG
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardapp.model.CardInfo
import com.example.cardapp.model.ScanningSession
import com.example.cardapp.model.SubmitScannedCardsResponse
import com.example.cardapp.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class CardReaderViewModel : ViewModel() {

    // Dialog state data class
    data class DialogState(
        val showDialog: Boolean = false,
        val title: String = "",
        val message: String = ""
    )


    data class BatchStats(
        val totalCards: Int = 0,
        val verifiedCards: Int = 0,
        val completionPercentage: Double = if (totalCards > 0) (verifiedCards.toDouble()/totalCards) * 100 else 0.0
    )

    data class EnquiryTrigger(
        val result: CardRepository.CardEnquiryResult,
        val cardId: String,
        val readTimeMs: Long,
        val timestamp: Long = System.currentTimeMillis()
    )


    companion object {
        val instance = CardReaderViewModel()
    }

    // Using StateFlow for better Compose integration
    private val _cards = MutableStateFlow<List<CardInfo>>(emptyList())
    val cards: StateFlow<List<CardInfo>> = _cards.asStateFlow()

    // Available Batches
    private val _availableBatches = MutableStateFlow<List<String>>(emptyList())
    val availableBatches: StateFlow<List<String>> = _availableBatches

    // Batch Selection
    private val _selectedBatch = MutableStateFlow("") // Default batch
    val selectedBatch: StateFlow<String> = _selectedBatch.asStateFlow()

    // Batch Statistics
    private val _batchStats = MutableStateFlow(BatchStats(0,0))
    val batchStats: StateFlow<BatchStats> = _batchStats.asStateFlow()

    // State for card IDs in a batch
    private val _batchCardIds = MutableStateFlow<List<String>>(emptyList())
    val batchCardIds: StateFlow<List<String>> = _batchCardIds

    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    private val _enquiryResult = MutableStateFlow<EnquiryTrigger?>(null)
    val enquiryResult: StateFlow<EnquiryTrigger?> = _enquiryResult.asStateFlow()


    private val _currentSession = MutableStateFlow<ScanningSession?>(null)
    val currentSession = _currentSession.asStateFlow()

    // Add to CardReaderViewModel
    private val _isEnquirySearching = MutableStateFlow(false)
    val isEnquirySearching: StateFlow<Boolean> = _isEnquirySearching.asStateFlow()

    fun setEnquirySearching(searching: Boolean) {
        _isEnquirySearching.value = searching
    }

    init {
        // Load available batches at startup
        viewModelScope.launch {
            _availableBatches.value = repository?.getAvailableBatches() ?: emptyList()
        }
    }
    // Mutable list for backward compatibility
    val cardList = mutableStateListOf<CardInfo>()

    // Repository for enquiry operations
    private var repository: CardRepository? = null

    fun setRepository(cardRepository: CardRepository) {
        this.repository = cardRepository

        // Load initial batch stats
//        updateBatchStats()
    }

    // Trigger enquiry result without adding to cards list
    fun triggerEnquiryResult(result: CardRepository.CardEnquiryResult, cardId: String, readTimeMs: Long){
        _enquiryResult.value = EnquiryTrigger(result, cardId, readTimeMs)
    }

    //Clear Enquiry Result
    fun clearEnquiryResult() {
        _enquiryResult.value = null
    }


    fun getRepository(): CardRepository? = repository

    // Load specific batch when search button is clicked
    suspend fun loadSpecificBatch(batchName: String): Boolean {
        return try {
            repository?.loadSpecificBatch(batchName) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error loading batch: ${e.message}")
            false
        }
    }

    fun endCurrentSession() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "========== ENDING CURRENT SESSION ==========")
                Log.d(TAG, "Current session ID: ${_currentSession.value?.sessionId}")
                Log.d(TAG, "Total cards in session: ${_currentSession.value?.scannedCards?.size ?: 0}")

                // Clear the current session
                _currentSession.value = null

                // Clear scanned cards list
                _cards.value = emptyList()

                Log.d(TAG, "✅ Session ended successfully - ready for new session")

            } catch (e: Exception) {
                Log.e(TAG, "Error ending session: ${e.message}")
            }
        }
    }

    // Set selected batch
    fun setSelectedBatch(batch: String) {
        _selectedBatch.value = batch
//        viewModelScope.launch {
//            updateBatchStats()
//        }
    }

    fun showNotFoundDialog(title: String, message: String) {
        _dialogState.value = DialogState(
            showDialog = true,
            title = title,
            message = message
        )
    }

    fun dismissDialog() {
        _dialogState.value = DialogState(
            showDialog = false,
            title = "",
            message = ""
        )
    }

    // Get current selected batch
    fun getCurrentBatch(): String = _selectedBatch.value

    // Update batch statistics
//    private fun updateBatchStats() {
//        viewModelScope.launch {
//            repository?.let { repo ->
//                try {
//                    val stats = repo.getVerificationStats(_selectedBatch.value)
//                    _batchStats.value = stats
//                } catch (e: Exception) {
////                  Toast.makeText(this, message, Toast.LENGTH_LONG).show()
//                }
//
//            }
//        }
//    }

//    private fun updateBatchStats() {
//        viewModelScope.launch {
//            try {
//                val stats = repository?.getVerificationStats(_selectedBatch.value)
//                if (stats != null) {
//                    _batchStats.value = stats
//                }
//            } catch (e: Exception) {
//                Log.e("CardReaderVM", "Failed to update stats: ${e.message}")
//            }
//        }
//    }


    fun resetCurrentBatch() {
        viewModelScope.launch {
            try {
                // Clear local state
                _cards.value = emptyList()
                _batchStats.value = BatchStats(0, 0)

                // Clear repository cache
                repository?.clearCurrentBatch()

                Log.d(TAG, "Current batch reset")
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting batch: ${e.message}")
            }
        }
    }

    fun updateAvailableBatches(batches: List<String>) {
        _availableBatches.value = batches
        // Set first batch as default if none is selected
        if (_selectedBatch.value.isEmpty() && batches.isNotEmpty()) {
            setSelectedBatch(batches.first())
        }
    }




    fun clearCards() {
        cardList.clear()
        _cards.value = emptyList()
    }

    fun removeCard(cardInfo: CardInfo) {
        viewModelScope.launch {
            try {
                val currentCards = _cards.value.toMutableList()
                currentCards.remove(cardInfo)
                _cards.value = currentCards

                Log.d(TAG, "Removed card: ${cardInfo.id}")

                updateBatchStatsIfNeeded()
            } catch (e: Exception) {
                Log.e(TAG, "Error removing card: ${e.message}")
            }
        }
    }

    // NEW: Method to clear everything after successful submission
    fun clearSessionAfterSubmission() {
        viewModelScope.launch {
            try {
                // Clear cards list
                _cards.value = emptyList()

                // Clear selected batch
                _selectedBatch.value = ""

                // Reset batch stats
                _batchStats.value = BatchStats(0, 0)

                // Clear any cached batch data
                repository?.clearCurrentBatch()

                Log.d(TAG, "Session cleared after successful submission")

            } catch (e: Exception) {
                Log.e(TAG, "Error clearing session: ${e.message}")
            }
        }
    }

    // UPDATED: Get stats from current loaded batch
    private suspend fun updateBatchStatsIfNeeded() {
        val currentBatch = _selectedBatch.value
        if (currentBatch.isNotEmpty()) {
            repository?.let { repo ->
                try {
                    val stats = repo.getCurrentBatchStats(currentBatch)
                    _batchStats.value = BatchStats(
                        totalCards = stats.totalCards,
                        verifiedCards = stats.verifiedCards,
                        completionPercentage = stats.completionPercentage
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating batch stats: ${e.message}")
                }
            }
        }
    }

    // Perform enquiry for specific card
    suspend fun performEnquiry(cardId: String): CardRepository.CardEnquiryResult? {
        return try {
            repository?.enquireScannedCard(cardId)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing enquiry: ${e.message}")
            null
        }
    }






//    suspend fun performGlobalEnquiry(cardId: String): CardRepository.CardEnquiryResult {
//        return try {
//            Log.d(TAG, "Starting global enquiry for card: $cardId")
//
//            // Get all available batches
//            val batches = _availableBatches.value
//
//            if (batches.isEmpty()) {
//                return CardRepository.CardEnquiryResult(
//                    cardExists = false,
//                    batchName = null,
//                    isVerified = false,
//                    message = "No batches available for search",
//                    batchCard = null,
//                    verifiedCard = null,
//
//                )
//            }
//
//            Log.d(TAG, "Searching across ${batches.size} batches...")
//
//            // Search through each batch
//            for (batchName in batches) {
//                try {
//                    Log.d(TAG, "Checking batch: $batchName")
//
//                    // Load the batch and search for the card
//                    repository?.loadSpecificBatch(batchName)
//
//                    val result = repository?.verifyScannedCardAgainstBatch(
//                        scannedCardId = cardId,
//                        targetBatchName = batchName,
//                        holderName = null,
//                        additionalData = emptyMap()
//                    )
//
//                    if (result?.isSuccess == true) {
//                        Log.d(TAG, "✅ Card found in batch: $batchName")
//                        return CardRepository.CardEnquiryResult(
//                            cardExists = true,
//                            batchName = batchName,
//                            isVerified = true,
//                            message = "Card successfully found in $batchName",
//                            batchCard = result.batchCard,
//                            verifiedCard = null
//                        )
//                    }
//
//                } catch (e: Exception) {
//                    Log.w(TAG, "Error searching batch $batchName: ${e.message}")
//                    // Continue searching other batches
//                    continue
//                }
//            }
//
//            // Card not found in any batch
//            Log.d(TAG, "❌ Card not found in any of the ${batches.size} available batches")
//
//            return CardRepository.CardEnquiryResult(
//                cardExists = false,
//                batchName = null,
//                isVerified = false,
//                message = "Card not found in any of the ${batches.size} available batches",
//                batchCard = null,
//                verifiedCard = null
//            )
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Error during global enquiry: ${e.message}")
//            return CardRepository.CardEnquiryResult(
//                cardExists = false,
//                batchName = null,
//                isVerified = false,
//                message = "Error during search: ${e.message}",
//                batchCard = null,
//                verifiedCard = null
//            )
//        }
//    }

    suspend fun performGlobalEnquiry(cardId: String): CardRepository.CardEnquiryResult {
        return try {
            Log.d(TAG, "Starting global enquiry for card: $cardId")

            // Check if repository exists
            if (repository == null) {
                return CardRepository.CardEnquiryResult(
                    cardExists = false,
                    batchName = null,
                    isVerified = false,
                    message = "Repository not initialized",
                    batchCard = null,
                    verifiedCard = null
                )
            }

            // Call the repository's global enquiry method
            repository!!.performGlobalEnquiry(cardId)

        } catch (e: Exception) {
            Log.e(TAG, "Error during global enquiry: ${e.message}")
            CardRepository.CardEnquiryResult(
                cardExists = false,
                batchName = null,
                isVerified = false,
                message = "Error during search: ${e.message}",
                batchCard = null,
                verifiedCard = null
            )
        }
    }








    fun startSession(batchNumber: Int) {
        val currentTime = Instant.now().atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_INSTANT)

        val session = ScanningSession(
            batchNumber = batchNumber,
            startTime = currentTime
        )
        _currentSession.value = session
        Log.d(TAG, "Started new scanning session: ${session.sessionId}")
    }

//    fun addCard(cardInfo: CardInfo) {
//        val currentCards = _cards.value.toMutableList()
//        currentCards.add(cardInfo)
//        _cards.value = currentCards
//
//        // Add to current session if it exists and card is verified
//        _currentSession.value?.let { session ->
//            if (cardInfo.isVerified) {
//                val scanTime = Instant.ofEpochMilli(cardInfo.timestamp)
//                    .atOffset(ZoneOffset.UTC)
//                    .format(DateTimeFormatter.ISO_INSTANT)
//
//                session.addScannedCard(cardInfo.id, scanTime)
//                Log.d(TAG, "Added card to session: ${cardInfo.id}")
//            }
//        }
//    }

    // Add this to CardReaderViewModel class:

    // New StateFlow specifically for enquiry cards
    private val _enquiryCards = MutableStateFlow<List<CardInfo>>(emptyList())
    val enquiryCards: StateFlow<List<CardInfo>> = _enquiryCards.asStateFlow()

    // Modified addCard method:
//    fun addCard(cardInfo: CardInfo) {
//        if (cardInfo.verificationStatus == "ENQUIRY") {
//            Log.d(TAG, "Adding enquiry card to enquiry list: ${cardInfo.id}")
//            // Add to separate enquiry cards list
//            val currentEnquiryCards = _enquiryCards.value.toMutableList()
//            currentEnquiryCards.add(cardInfo)
//            _enquiryCards.value = currentEnquiryCards
//            return
//        }
//
//        // Regular cards processing
//        val currentCards = _cards.value.toMutableList()
//        currentCards.add(cardInfo)
//        _cards.value = currentCards
//
//        // Add to current session if it exists and card is verified
//        _currentSession.value?.let { session ->
//            if (cardInfo.isVerified) {
//                val scanTime = Instant.ofEpochMilli(cardInfo.timestamp)
//                    .atOffset(ZoneOffset.UTC)
//                    .format(DateTimeFormatter.ISO_INSTANT)
//
//                session.addScannedCard(cardInfo.id, scanTime)
//                Log.d(TAG, "Added card to session: ${cardInfo.id}")
//            }
//        }
//    }

    // In CardReaderViewModel, replace the addCard method with this:
    fun addCard(cardInfo: CardInfo) {
        val currentCards = _cards.value.toMutableList()

        // For enquiry cards, add temporarily for detection but don't add to session
        if (cardInfo.verificationStatus == "ENQUIRY") {
            currentCards.add(cardInfo)
            _cards.value = currentCards
            Log.d(TAG, "Added enquiry card (temporary): ${cardInfo.id}")
            return
        }

        // For verification cards, add to both list and session
        currentCards.add(cardInfo)
        _cards.value = currentCards

        // Add to current session if it exists and card is verified
        _currentSession.value?.let { session ->
            if (cardInfo.isVerified) {
                val scanTime = Instant.ofEpochMilli(cardInfo.timestamp)
                    .atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_INSTANT)

                session.addScannedCard(cardInfo.id, scanTime)
                Log.d(TAG, "Added card to session: ${cardInfo.id}")
            }
        }
    }

    // Clear only enquiry cards (not verification cards)
    fun clearEnquiryCards() {
        viewModelScope.launch {
            val currentCards = _cards.value.toMutableList()
            currentCards.removeAll { it.verificationStatus == "ENQUIRY" }
            _cards.value = currentCards
            Log.d(TAG, "Cleared enquiry cards")
        }
    }

    // Method to check if we have any enquiry cards
    fun hasEnquiryCards(): Boolean {
        return _cards.value.any { it.verificationStatus == "ENQUIRY" }
    }

    suspend fun getCurrentBatchStats(): CardRepository.VerificationStats? {
        return repository?.getVerificationStats(_selectedBatch.value)
    }

    suspend fun submitCurrentSession(notes: String? = null): SubmitScannedCardsResponse? {
        val session = _currentSession.value ?: return null

        return try {
            val endTime = Instant.now().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT)

            val request = session.createSubmissionRequest(endTime, notes)

            Log.d(TAG, "Submitting session with ${request.scannedCards.size} cards")

            val response = repository?.submitScannedCards(request)

            if (response?.status == "success") {
                // Clear session after successful submission
                _currentSession.value = null
                Log.d(TAG, "Session submitted successfully")
            }

            response
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting session: ${e.message}")
            null
        }
    }


    // Add method to get current session stats
    fun getCurrentSessionStats(): SessionStats {
        val session = _currentSession.value
        val verifiedCards = _cards.value.count { it.isVerified }

        return SessionStats(
            sessionId = session?.sessionId ?: "",
            totalScanned = verifiedCards,
            sessionActive = session != null,
            startTime = session?.startTime ?: ""
        )
    }

    data class SessionStats(
        val sessionId: String,
        val totalScanned: Int,
        val sessionActive: Boolean,
        val startTime: String
    )


}