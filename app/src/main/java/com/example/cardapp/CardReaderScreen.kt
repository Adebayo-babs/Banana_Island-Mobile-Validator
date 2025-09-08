package com.example.cardapp


import android.annotation.SuppressLint
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardapp.model.CardInfo
import com.example.cardapp.model.CardInfoItem
import com.example.cardapp.model.EmptyStateCard
import com.example.cardapp.model.SessionSubmissionSection
import com.example.cardapp.repository.CardRepository
import com.example.cardapp.viewmodel.CardReaderViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat

@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("DefaultLocale")
@Composable
fun CardReaderScreen(
    onEnquiryClick: () -> Unit = {},
    viewModel: CardReaderViewModel = viewModel { CardReaderViewModel.instance }
) {

    val cards by viewModel.cards.collectAsState()
    val selectedBatch by viewModel.selectedBatch.collectAsState()
    val context = LocalContext.current

    // Dialog state from ViewModel
    val dialogState by viewModel.dialogState.collectAsState()

    val dateFormatter =
        remember { SimpleDateFormat("HH:mm:ss dd/MM/yyyy", java.util.Locale.getDefault()) }
    val coroutineScope = rememberCoroutineScope()

    var enquiryDialogCard by remember { mutableStateOf<CardInfo?>(null) }
    var enquiryResult by remember { mutableStateOf<CardRepository.CardEnquiryResult?>(null) }
    var batchNumberInput by remember { mutableStateOf("") }
    var isValidBatchNumber by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }

    // Validate batch number input
    val validateBatchInput: (String) -> Unit = { input ->
        batchNumberInput = input

        if (input.isNotEmpty()) {
            // Check if input is a valid number and within reasonable range (1-50)
            val batchNum = input.toIntOrNull()
            isValidBatchNumber = batchNum != null && batchNum in 1..50
        } else {
            isValidBatchNumber = true
        }
    }

    // Button Search Click
    val searchBatch: () -> Unit = {
        if (batchNumberInput.isNotEmpty() && isValidBatchNumber) {
            coroutineScope.launch {
                isSearching = true
                try {
                    val batchNum = batchNumberInput.toInt()
                    val formattedBatch = "Batch ${batchNum.toString().padStart(3, '0')}"

                    //Load the batch from API
                    val success = viewModel.loadSpecificBatch(formattedBatch)
                    if (success) {
                        viewModel.setSelectedBatch(formattedBatch)
                        // Show success toast
                        Toast.makeText(
                            context,
                            "Batch $batchNum fetched successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // Show error if batch not found
                        viewModel.showNotFoundDialog(
                            title = "Batch not found",
                            message = "Batch $batchNum is not available or active"
                        )
                    }
                } catch (e: Exception) {
                    viewModel.showNotFoundDialog(
                        title = "Error",
                        message = "Failed to load batch: ${e.message}"
                    )
                } finally {
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
        // Header with Enquiry Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Card Batch Verification",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            // New Enquiry button
            OutlinedButton(
                onClick = onEnquiryClick
            ) {
                Text("Enquiry")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        //Updated Batch Selected with Search Button
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Enter Batch Number",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Batch Number Input with Search Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutlinedTextField(
                        value = batchNumberInput,
                        onValueChange = validateBatchInput,
                        label = { Text("Batch Number") },
                        placeholder = { Text("Enter batch number...") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = !isValidBatchNumber,
                        supportingText = {
                            if (!isValidBatchNumber) {
                                Text(
                                    text = "Please enter a valid batch number (1-50)",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            } else if (selectedBatch.isNotEmpty()) {
                                Text(
//                                    Selected: $selectedBatch
                                    text = "",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Search Button
                    OutlinedButton(
                        onClick = searchBatch,
                        enabled = batchNumberInput.isNotEmpty() && isValidBatchNumber && !isSearching,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        if (isSearching) {
                            Text("Searching...")
                        } else {
                            Text("Search")
                        }
                    }
                }

                // Show loading indicator when searching
                if (isSearching) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Batch Info
//                if (selectedBatch.isNotEmpty() && isValidBatchNumber) {
//                    Spacer(modifier = Modifier.height(8.dp))
//
//                    val batchStats by viewModel.batchStats.collectAsState()
//
//                    if (batchStats.totalCards > 0) {
//                        LinearProgressIndicator(
//                            progress = { (batchStats.verifiedCards.toFloat() / batchStats.totalCards) },
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(top = 4.dp),
//                        )
//                        Text(
//                            text = "${
//                                String.format(
//                                    "%.1f",
//                                    batchStats.completionPercentage
//                                )
//                            }% Complete",
//                            fontSize = 12.sp,
//                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
//                            modifier = Modifier.padding(top = 2.dp)
//                        )
//
//                        Row(
//                            modifier = Modifier.fillMaxWidth(),
//                            horizontalArrangement = Arrangement.End
//                        ) {
//                            OutlinedButton(
//                                onClick = { viewModel.resetCurrentBatch() },
//                                modifier = Modifier.padding(top = 8.dp)
//                            ) {
//                                Text("Reset Batch")
//                            }
//                        }
//                    }
//                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Header row with count and clear button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Scanned Cards (${cards.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (cards.isNotEmpty()) {
                        val verifiedCount = cards.count { it.isVerified }
//                        val notFoundCount = cards.count { it.verificationStatus == "NOT_FOUND" }
//                        ❌ $notFoundCount not found

                        Text(
//                            $verifiedCount verified
                                text = "✅ ",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                if (cards.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { viewModel.clearCards() }
                    ) {
                        Text("Clear All")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SessionSubmissionSection(
                viewModel = viewModel,
                modifier = Modifier.padding(bottom = 8.dp),
                onSubmissionSuccess = {
                    // Clear all after successful submission
                    viewModel.clearCards()
                    viewModel.setSelectedBatch("")
                    batchNumberInput = ""
                }
            )


//            Spacer(modifier = Modifier.height(8.dp))

            // Cards list
            if (cards.isEmpty()) {
                EmptyStateCard(selectedBatch = selectedBatch)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = cards.reversed(), // Show most recent first
                        key = { cardInfo -> "${cardInfo.id}_${cardInfo.timestamp}" }
                    ) { cardInfo ->
                        CardInfoItem(
                            cardInfo = cardInfo,
                            dateFormatter = dateFormatter,
                            onRemove = { viewModel.removeCard(cardInfo) },
                            onEnquiry = { card ->
                                // Perform enquiry when button is clicked
                                coroutineScope.launch {
                                    try {
                                        enquiryResult = viewModel.performEnquiry(card.id)
                                        enquiryDialogCard = card
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Enquiry failed: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        NotFoundDialog(
            showDialog = dialogState.showDialog,
            title = dialogState.title,
            message = dialogState.message,
            onDismiss = { viewModel.dismissDialog()}
        )

        // Enquiry Result Dialog
        enquiryDialogCard?.let { card ->
            enquiryResult?.let { result ->
                EnquiryResultDialog(
                    cardInfo = card,
                    enquiryResult = result,
                    onDismiss = {
                        enquiryDialogCard = null
                        enquiryResult = null
                    }
                )

            }

        }
    }
}

@Composable
fun NotFoundDialog(
    showDialog: Boolean,
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            },
            icon = {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null
                )
            }
        )
    }
}