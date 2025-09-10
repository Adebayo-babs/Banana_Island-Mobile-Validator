package com.example.cardapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EnquiryHistoryCard(historyItem: EnquiryHistoryItem) {

    // Extract batch number from batch name if available
    val batchNumber = historyItem.batchName?.let { batchName ->
        val numberRegex = Regex("""(\d+)""")
        numberRegex.find(batchName)?.value?.toIntOrNull()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                historyItem.found && historyItem.isVerified -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                historyItem.found && !historyItem.isVerified -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header row with status icon and card ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            historyItem.found && historyItem.isVerified -> Icons.Filled.CheckCircle
                            historyItem.found && !historyItem.isVerified -> Icons.Filled.CheckCircle
                            else -> Icons.Filled.Clear
                        },
                        contentDescription = null,
                        tint = when {
                            historyItem.found && historyItem.isVerified -> MaterialTheme.colorScheme.primary
                            historyItem.found && !historyItem.isVerified -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = historyItem.cardId,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(historyItem.timestamp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Batch information (if found)
            if (historyItem.found && historyItem.batchName != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
//                    Icon(
//                        Icons.Filled.Search,
//                        contentDescription = null,
//                        tint = MaterialTheme.colorScheme.primary,
//                        modifier = Modifier.size(14.dp)
//                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = historyItem.batchName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        // Show batch number if available
                        batchNumber?.let { number ->
                            Text(
                                text = "Batch Number: $number",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // Status message
//            Text(
//                text = when {
//                    historyItem.found && historyItem.isVerified -> "✅ Verified"
//                    historyItem.found && !historyItem.isVerified -> "⚠️ Found but not verified"
//                    else -> "❌ Not found"
//                },
//                fontSize = 12.sp,
//                color = when {
//                    historyItem.found && historyItem.isVerified -> MaterialTheme.colorScheme.primary
//                    historyItem.found && !historyItem.isVerified -> MaterialTheme.colorScheme.tertiary
//                    else -> MaterialTheme.colorScheme.error
//                }
//            )

            // Additional message if different from default
            if (historyItem.message.isNotBlank() &&
                !historyItem.message.contains("successfully", ignoreCase = true) &&
                !historyItem.message.contains("not found", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = historyItem.message,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2
                )
            }
        }
    }
}