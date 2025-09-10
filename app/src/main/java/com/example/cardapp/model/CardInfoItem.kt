package com.example.cardapp.model


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat

@Composable
fun CardInfoItem(
    cardInfo: CardInfo,
    dateFormatter: SimpleDateFormat,
    onRemove: () -> Unit,
    onEnquiry: (CardInfo) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (cardInfo.verificationStatus) {
                "VERIFIED" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                "NOT_FOUND" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                "ERROR" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header row with card ID and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cardInfo.id,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = when (cardInfo.verificationStatus) {
                        "VERIFIED" -> MaterialTheme.colorScheme.primary
                        "NOT_FOUND", "ERROR" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                Row {
                    // Individual remove button (X)
                    IconButton(
                        onClick = onRemove, // Now removes only this card
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove this card",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Enquiry button
//                    if (cardInfo.verificationStatus != "ERROR") {
//                        IconButton(
//                            onClick = { onEnquiry(cardInfo) },
//                            modifier = Modifier.size(24.dp)
//                        ) {
//                            Icon(
//                                Icons.Filled.Search,
//                                contentDescription = "Enquire about this card",
//                                tint = MaterialTheme.colorScheme.secondary,
//                                modifier = Modifier.size(16.dp)
//                            )
//                        }
//                    }
                }
            }

            // Status and timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                when (cardInfo.verificationStatus) {
                    "VERIFIED" -> "✅ Found"
                    "NOT_FOUND" -> "❌ Not Found"
                    "ERROR" -> "⚠️ Error"
                    else -> cardInfo.verificationStatus
                }?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Text(
                    text = dateFormatter.format(cardInfo.timestamp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Additional info
            if (cardInfo.additionalInfo.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = cardInfo.additionalInfo,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(selectedBatch: String = "") {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📱",
                    fontSize = 48.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

            }
        }
    }
}