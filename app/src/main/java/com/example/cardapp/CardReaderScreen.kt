package com.example.cardapp


import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardapp.model.CardInfo
import com.example.cardapp.model.SessionSubmissionSection
import com.example.cardapp.repository.CardRepository
import com.example.cardapp.viewmodel.CardReaderViewModel
import java.text.SimpleDateFormat

@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("DefaultLocale")
@Composable
fun CardReaderScreen(
    onBackClick: () -> Unit = {},
    viewModel: CardReaderViewModel = viewModel { CardReaderViewModel.instance }
) {

    BackHandler {
        onBackClick()
    }

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

    // State to track batch fetch success
    var batchFetched by remember { mutableStateOf(false) }
    var fetchedBatchInfo by remember { mutableStateOf("") }

    // State to control search visibility
    var showSearchSection by remember { mutableStateOf(true) }

    // Validate batch number input
    val validateBatchInput: (String) -> Unit = { input ->
        batchNumberInput = input
        // Reset batch fetched state when input changes
        batchFetched = false
        fetchedBatchInfo = ""


        if (input.isNotEmpty()) {
            // Check if input is a valid number and within reasonable range (1-50)
            val batchNum = input.toIntOrNull()
            isValidBatchNumber = batchNum != null && batchNum in 1..50
        } else {
            isValidBatchNumber = true
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Card Batch Verification",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Toggle search section button
            if (!showSearchSection && selectedBatch.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showSearchSection = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Change Batch", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Header row with count and clear button - made more compact
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Scanned Cards (${cards.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (cards.isNotEmpty()) {
                    val verifiedCount = cards.count { it.isVerified }
                    Text(
                        text = "✅ $verifiedCount verified",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            if (cards.isNotEmpty()) {
                OutlinedButton(
                    onClick = { viewModel.clearCards() },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Clear All", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Spacer(modifier = Modifier.height(8.dp))

        SessionSubmissionSection(
            viewModel = viewModel,
            modifier = Modifier.padding(bottom = 8.dp),
            currentCardCount = cards.size,
            onSubmissionSuccess = {
                // Clear all after successful submission
                viewModel.clearCards()
                viewModel.setSelectedBatch("")
                batchNumberInput = ""
                batchFetched = false
                fetchedBatchInfo = ""
                showSearchSection = true // Show search section again after submission
            }
        )
    }

    NotFoundDialog(
        showDialog = dialogState.showDialog,
        title = dialogState.title,
        message = dialogState.message,
        onDismiss = { viewModel.dismissDialog()}
    )

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