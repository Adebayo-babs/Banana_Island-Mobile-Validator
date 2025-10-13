package com.example.cardapp.viewmodel

import android.content.ContentValues.TAG
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardapp.model.CardInfo
import com.example.cardapp.data.local.entity.ScanningSession
import com.example.cardapp.data.repository.CardRepository
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

    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    private val _enquiryResult = MutableStateFlow<EnquiryTrigger?>(null)
    val enquiryResult: StateFlow<EnquiryTrigger?> = _enquiryResult.asStateFlow()


    private val _currentSession = MutableStateFlow<ScanningSession?>(null)

    // Add to CardReaderViewModel
    private val _isEnquirySearching = MutableStateFlow(false)
    val isEnquirySearching: StateFlow<Boolean> = _isEnquirySearching.asStateFlow()

    init {
        // Load available batches at startup
        viewModelScope.launch {
            _availableBatches.value = repository?.getAvailableBatches() ?: emptyList()
        }
    }

    // Repository for enquiry operations
    private var repository: CardRepository? = null

    fun setRepository(cardRepository: CardRepository) {
        this.repository = cardRepository
    }


    // Set selected batch
    fun setSelectedBatch(batch: String) {
        _selectedBatch.value = batch
    }

    fun showNotFoundDialog(title: String, message: String) {
        _dialogState.value = DialogState(
            showDialog = true,
            title = title,
            message = message
        )
    }

    // Get current selected batch
    fun getCurrentBatch(): String = _selectedBatch.value

    fun updateAvailableBatches(batches: List<String>) {
        _availableBatches.value = batches
        // Set first batch as default if none is selected
        if (_selectedBatch.value.isEmpty() && batches.isNotEmpty()) {
            setSelectedBatch(batches.first())
        }
    }

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

}