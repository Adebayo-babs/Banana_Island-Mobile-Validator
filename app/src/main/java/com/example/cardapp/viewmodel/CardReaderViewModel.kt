package com.example.cardapp.viewmodel

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardapp.model.CardInfo
import com.example.cardapp.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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


    fun addCard(cardInfo: CardInfo) {
        // Add to mutable list (for backward compatibility)
        cardList.add(0, cardInfo)  //Add to beginning for newest first

        //Update StateFlow
        _cards.value = cardList.toList()

        // Update batch stats if this card was verified
        if (cardInfo.isVerified) {
            updateBatchStats()
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
}