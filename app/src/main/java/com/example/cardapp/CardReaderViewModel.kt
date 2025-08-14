package com.example.cardapp

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CardReaderViewModel : ViewModel() {

    // Using StateFlow for better Compose integration
    private val _cards = MutableStateFlow<List<CardInfo>>(emptyList())
    val cards: StateFlow<List<CardInfo>> = _cards.asStateFlow()

    // Keep the mutable list for backward compatibility
    val cardList = mutableStateListOf<CardInfo>()

    // Repository for enquiry operations
    private var repository: CardRepository? = null

    fun setRepository(cardRepository: CardRepository) {
        this.repository = cardRepository
    }


    companion object {
        val instance = CardReaderViewModel()
    }

    fun addCard(cardInfo: CardInfo) {
        // Add to mutable list (for backward compatibility)
        cardList.add(0, cardInfo)  //Add to beginning for newest first

        //Update StateFlow
        _cards.value = cardList.toList()
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
}