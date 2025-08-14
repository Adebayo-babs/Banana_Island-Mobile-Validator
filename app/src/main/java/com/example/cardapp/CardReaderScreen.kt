package com.example.cardapp


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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat


@Composable
fun CardReaderScreen(
    viewModel: CardReaderViewModel = viewModel { CardReaderViewModel.instance }
) {

    val cards by viewModel.cards.collectAsState()
    val dateFormatter = remember { SimpleDateFormat("HH:mm:ss dd/MM/yyyy", java.util.Locale.getDefault()) }
    val coroutineScope = rememberCoroutineScope()
    var enquiryDialogCard by remember { mutableStateOf<CardInfo?>(null) }
    var enquiryResult by remember { mutableStateOf<CardRepository.CardEnquiryResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        // Header
        Text(
            text = "Card Batch Verification",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

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
                    val notFoundCount = cards.count {it.verificationStatus == "NOT_FOUND" }


                    Text(
                        text = "✅ $verifiedCount verified  ❌ $notFoundCount not found",
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

        // Cards list
        if (cards.isEmpty()) {
            EmptyStateCard()
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
//                                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

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




