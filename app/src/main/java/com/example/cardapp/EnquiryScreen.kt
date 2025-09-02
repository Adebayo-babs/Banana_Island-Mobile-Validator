package com.example.cardapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardapp.model.CardInfo
import com.example.cardapp.viewmodel.CardReaderViewModel

@Composable
fun EnquiryScreen(
    onBackClick: () -> Unit,
    viewModel: CardReaderViewModel = viewModel { CardReaderViewModel.instance }
) {
    var searchResults by remember { mutableStateOf<List<EnquiryResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var lastScannedCard by remember { mutableStateOf<CardInfo?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val cards by viewModel.cards.collectAsState()

    // Listen for new cards being scanned
    LaunchedEffect(cards.size) {
        if (cards.isNotEmpty()) {
            val latestCard = cards.first()
            if (latestCard != lastScannedCard) {
                lastScannedCard = latestCard
                // Automatically search when a new card is scanned
                performCardEnquiry(latestCard.id, viewModel) { results ->
                    searchResults = results
                    isSearching = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Card Enquiry",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Instructions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tap any NFC card to search across all batches",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This will search all available batches to find which batch contains the scanned card.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Show searching indicator
        if (isSearching) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Searching across all batches...")
                }
            }
        }

        // Search Results
        if (searchResults.isNotEmpty()) {
            Text(
                text = "Search Results",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResults) { result ->
                    EnquiryResultCard(result = result)
                }
            }
        }
    }
}

// Function to perform card enquiry across all batches
private suspend fun performCardEnquiry(
    cardId: String,
    viewModel: CardReaderViewModel,
    onResult: (List<EnquiryResult>) -> Unit
) {
    try {
        val repository = viewModel.getRepository() ?: return

        // Search across all available batches
        val enquiryResult = repository.enquireScannedCard(cardId)

        val results = listOf(
            EnquiryResult(
                cardId = cardId,
                found = enquiryResult.cardExists,
                batchName = enquiryResult.batchName,
                message = enquiryResult.message
            )
        )

        onResult(results)

    } catch (e: Exception) {
        val errorResult = EnquiryResult(
            cardId = cardId,
            found = false,
            batchName = null,
            message = "Error during enquiry: ${e.message}"
        )
        onResult(listOf(errorResult))
    }
}