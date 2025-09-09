package com.example.cardapp

import android.util.Log
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
    var enquiryHistory by remember { mutableStateOf<List<EnquiryHistoryItem>>(emptyList()) }
    val isSearching by viewModel.isEnquirySearching.collectAsState()
    var showResultDialog by remember { mutableStateOf(false) }
    var currentResult by remember { mutableStateOf<CardRepository.CardEnquiryResult?>(null) }
    var currentCardId by remember { mutableStateOf<String?>(null) }


    val availableBatches by viewModel.availableBatches.collectAsState()
    val enquiryResult by viewModel.enquiryResult.collectAsState()
    val coroutineScope = rememberCoroutineScope()


    // Listen for enquiry results from the viewModel
    LaunchedEffect(enquiryResult) {

        enquiryResult?.let { trigger ->
            Log.d("EnquiryScreen", "Received enquiry result: ${trigger.result.message}")

            // Stop the searching indicator
//            isSearching = false

            //Set the current result for dialog
            currentResult = trigger.result
            currentCardId = trigger.cardId
            showResultDialog = true

            // Add to history
            val historyItem = EnquiryHistoryItem(
                cardId = trigger.cardId,
                timestamp = trigger.timestamp,
                found = trigger.result.cardExists,
                batchName = trigger.result.batchName,
                isVerified = trigger.result.isVerified,
                message = trigger.result.message
            )
            enquiryHistory = listOf(historyItem) + enquiryHistory.take(9) // Keep last 10

            // Clear the enquiry result from viewModel
            viewModel.clearEnquiryResult()

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
    if (showResultDialog && currentResult != null && currentCardId != null) {
        Log.d("EnquiryScreen", "Showing dialog for result: ${currentResult?.message}")
        EnquiryResultDialogDone(
            result = currentResult!!,
            cardId = currentCardId!!,
            onDismiss = {
                Log.d("EnquiryScreen", "Dialog dismissed")
                showResultDialog = false
                currentResult = null
                // Reset processed card ID to allow same card to be scanned again
                currentCardId = null
            }
        )
    }

}


@Composable
fun EnquiryResultDialogDone(
    result: CardRepository.CardEnquiryResult,
    cardId: String,
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
                Text(
                    text = "Card ID: $cardId",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (result.cardExists && result.batchName != null) {
                    Text(
                        text = "Found in: ${result.batchName}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (result.isVerified) {
                            "✅ This card has been verified in ${result.batchName}"
                        } else {
                            "⚠️ This card exists in ${result.batchName} but has not been verified yet"
                        },
                        fontSize = 14.sp,
                        color = if (result.isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = result.message,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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