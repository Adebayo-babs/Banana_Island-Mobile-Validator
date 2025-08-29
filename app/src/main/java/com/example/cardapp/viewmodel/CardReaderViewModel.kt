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
        val verifiedCards: Int = 0
    ) {
        val completionPercentage: Float
            get() = if (totalCards == 0) 0f else (verifiedCards.toFloat() / totalCards * 100f)
    }


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
    private val _batchStats = MutableStateFlow(CardRepository.VerificationStats(0, 0, 0))
    val batchStats: StateFlow<CardRepository.VerificationStats> = _batchStats.asStateFlow()

    // State for card IDs in a batch
    private val _batchCardIds = MutableStateFlow<List<String>>(emptyList())
    val batchCardIds: StateFlow<List<String>> = _batchCardIds

    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()


    private val _currentSession = MutableStateFlow<ScanningSession?>(null)
    val currentSession = _currentSession.asStateFlow()

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
        updateBatchStats()
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
        viewModelScope.launch {
            updateBatchStats()
        }
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

    private fun updateBatchStats() {
        viewModelScope.launch {
            try {
                val stats = repository?.getVerificationStats(_selectedBatch.value)
                if (stats != null) {
                    _batchStats.value = stats
                }
            } catch (e: Exception) {
                Log.e("CardReaderVM", "Failed to update stats: ${e.message}")
            }
        }
    }


    fun resetCurrentBatch() {
        viewModelScope.launch {
            try {
                repository?.resetBatchVerification(_selectedBatch.value)
                //Refresh stats after reset
                updateBatchStats()
                // Clear local UI cards
                _cards.value = emptyList()
            }catch (e: Exception) {
                Log.e("CardReaderViewModel", "Error resetting batch: ${e.message}")
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
        cardList.remove(cardInfo)
        _cards.value = cardList.toList()
    }

    // Perform enquiry for specific card
    suspend fun performEnquiry(cardId: String): CardRepository.CardEnquiryResult? {
        return try {
            repository?.enquireScannedCard(cardId)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCurrentBatchStats(): CardRepository.VerificationStats? {
        return repository?.getVerificationStats(_selectedBatch.value)
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

    fun addCard(cardInfo: CardInfo) {
        val currentCards = _cards.value.toMutableList()
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