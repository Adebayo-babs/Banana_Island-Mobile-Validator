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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.cardapp.repository.CardRepository
import com.example.cardapp.viewmodel.CardReaderViewModel
import kotlinx.coroutines.launch

@Composable
fun EnquiryScreen(
    onBackClick: () -> Unit,
    viewModel: CardReaderViewModel = viewModel { CardReaderViewModel.instance }
) {
    var enquiryResult by remember { mutableStateOf<CardRepository.CardEnquiryResult?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var lastScannedCard by remember { mutableStateOf<CardInfo?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }
    var enquiryHistory by remember { mutableStateOf<List<EnquiryHistoryItem>>(emptyList()) }


    val cards by viewModel.cards.collectAsState()
    val availableBatches by viewModel.availableBatches.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Listen for new cards being scanned
    LaunchedEffect(cards.size) {
        if (cards.isNotEmpty()) {
            val latestCard = cards.first()
            // Only process enquiry cards and avoid duplicates
            if (latestCard != lastScannedCard && latestCard.verificationStatus == "ENQUIRY") {
                lastScannedCard = latestCard

                // Automatically search when a new card is scanned
                isSearching = true
                coroutineScope.launch {
                    try {
                        enquiryResult = viewModel.performGlobalEnquiry(latestCard.id)

                        // Add to history
                        val historyItem = EnquiryHistoryItem(
                            cardId = latestCard.id,
                            timestamp = System.currentTimeMillis(),
                            found = enquiryResult?.cardExists == true,
                            batchName = enquiryResult?.batchName,
                            isVerified = enquiryResult?.isVerified == true,
                            message = enquiryResult?.message ?: "Unknown error"
                        )
                        enquiryHistory = listOf(historyItem) + enquiryHistory.take(9) // Keep last 10

                        showResultDialog = true
                    } catch (e: Exception) {
                        enquiryResult = CardRepository.CardEnquiryResult(
                            cardExists = false,
                            batchName = null,
                            isVerified = false,
                            message = "Error during enquiry: ${e.message}",
                            batchCard = null,
                            verifiedCard = null
                        )

                        // Add error to history
                        val historyItem = EnquiryHistoryItem(
                            cardId = latestCard.id,
                            timestamp = System.currentTimeMillis(),
                            found = false,
                            batchName = null,
                            isVerified = false,
                            message = "Error: ${e.message}"
                        )
                        enquiryHistory = listOf(historyItem) + enquiryHistory.take(9)

                        showResultDialog = true
                    } finally {
                        isSearching = false
                    }
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    text = "This will automatically search all ${availableBatches.size} available batches to find which batch contains the scanned card.",
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
                    Text("Searching across all ${availableBatches.size} batches...")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Enquiry History
        if (enquiryHistory.isNotEmpty()) {
            Text(
                text = "Recent Enquiries",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(enquiryHistory) { historyItem ->
                    EnquiryHistoryCard(historyItem = historyItem)
                }
            }
        }

        // Search Results
//        if (!isSearching && cards.isNotEmpty()) {
//            Text(
//                text = "Recent Scans",
//                fontSize = 18.sp,
//                fontWeight = FontWeight.SemiBold,
//                modifier = Modifier.padding(bottom = 8.dp)
//            )
//
//            LazyColumn(
//                verticalArrangement = Arrangement.spacedBy(8.dp)
//            ) {
//                items(cards.take(5)) { card -> // Show last 5 scans
//                    Card(
//                        modifier = Modifier.fillMaxWidth(),
//                        colors = CardDefaults.cardColors(
//                            containerColor = MaterialTheme.colorScheme.surfaceVariant
//                        )
//
//                    ) {
//                        Column(
//                            modifier = Modifier.padding(12.dp)
//                        ) {
//                            Text(
//                                text = "Card ID: ${card.id}",
//                                fontSize = 14.sp,
//                                fontWeight = FontWeight.Medium
//                            )
//                            Text(
//                                text = "Scanned at: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(card.timestamp)}",
//                                fontSize = 12.sp,
//                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
//                            )
//                        }
//                    }
//                }
//            }
//        }
    }

    // Result Dialog
    if (showResultDialog && enquiryResult != null) {
        EnquiryResultDialogDone(
            result = enquiryResult!!,
            onDismiss = {
                showResultDialog = false
                enquiryResult = null
            }
        )
    }
}


@Composable
fun EnquiryResultDialogDone(
    result: CardRepository.CardEnquiryResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        result.cardExists && result.isVerified -> Icons.Filled.CheckCircle
                        result.cardExists && !result.isVerified -> Icons.Filled.Warning
                        else -> Icons.Filled.Clear
                    },
                    contentDescription = null,
                    tint = when {
                        result.cardExists && result.isVerified -> MaterialTheme.colorScheme.primary
                        result.cardExists && !result.isVerified -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        result.cardExists && result.isVerified -> "Card Found & Verified!"
                        result.cardExists && !result.isVerified -> "Card Found (Unverified)"
                        else -> "Card Not Found"
                    },
                    color = when {
                        result.cardExists && result.isVerified -> MaterialTheme.colorScheme.primary
                        result.cardExists && !result.isVerified -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }
        },
        text = {
            Column {
                if (result.cardExists && result.batchName != null) {
                    Text(
                        text = "Batch: ${result.batchName}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (result.isVerified) {
                        Text(
                            text = "✅ This card has been verified in ${result.batchName}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "⚠️ This card exists in ${result.batchName} but has not been verified yet",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = result.message,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

data class EnquiryHistoryItem(
    val cardId: String,
    val timestamp: Long,
    val found: Boolean,
    val batchName: String?,
    val isVerified: Boolean,
    val message: String
)

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