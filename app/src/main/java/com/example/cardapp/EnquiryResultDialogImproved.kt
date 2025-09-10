package com.example.cardapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardapp.repository.CardRepository

@Composable
fun EnquiryResultDialogImproved(
    result: CardRepository.CardEnquiryResult,
    cardId: String,
    onDismiss: () -> Unit
) {
    val isFoundInSystem = result.cardExists
    val isVerified = result.isVerified

    // Extract batch number from batch name
    // For your API: "Batch 10 - Port Stuart Cards" -> extract "10"
    val batchNumber = result.batchNumber

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        isFoundInSystem && isVerified -> Icons.Filled.CheckCircle
                        isFoundInSystem && !isVerified -> Icons.Filled.Warning
                        else -> Icons.Filled.Clear
                    },
                    contentDescription = null,
                    tint = when {
                        isFoundInSystem && isVerified -> MaterialTheme.colorScheme.primary
                        isFoundInSystem && !isVerified -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isFoundInSystem && isVerified -> "Card Found"
                        isFoundInSystem && !isVerified -> "Card Found"
                        else -> "Card Not Found"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isFoundInSystem && isVerified -> MaterialTheme.colorScheme.primary
                        isFoundInSystem && !isVerified -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }
        },
        text = {
            Column {
                // Card ID section
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Card ID",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = cardId,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Result-specific content
                if (isFoundInSystem) {
                    // FOUND CARD UI
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isVerified -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            if (result.batchName != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
//                                    Icon(
//                                        Icons.Filled.Search,
//                                        contentDescription = null,
//                                        tint = MaterialTheme.colorScheme.primary,
//                                        modifier = Modifier.size(20.dp)
//                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Found in Batch",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )

                                        // Display the full batch name from API
                                        // e.g., "Batch 10 - Port Stuart Cards"
                                        Text(
                                            text = result.batchName,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        // Display just the batch number separately
                                        // e.g., "Batch Number: 10"
                                        batchNumber?.let { number ->
                                            Text(
                                                text = "Batch Number: $number",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // Verification status
//                            Row(
//                                verticalAlignment = Alignment.CenterVertically
//                            ) {
//                                Icon(
//                                    imageVector = if (isVerified) Icons.Filled.CheckCircle else Icons.Filled.Warning,
//                                    contentDescription = null,
//                                    tint = if (isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
//                                    modifier = Modifier.size(18.dp)
//                                )
//                                Spacer(modifier = Modifier.width(8.dp))
//                                Text(
//                                    text = if (isVerified) {
//                                        "This card has been verified"
//                                    } else {
//                                        "This card exists but hasn't been verified yet"
//                                    },
//                                    fontSize = 14.sp,
//                                    fontWeight = FontWeight.Medium,
//                                    color = if (isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
//                                )
//                            }
                        }
                    }
                } else {
                    // NOT FOUND CARD UI
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Card not found in any available batch",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Additional message if available
                if (result.message.isNotBlank() &&
                    result.message != "Card information retrieved successfully" &&
                    result.message != "Card not found in system") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result.message,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = when {
                        isFoundInSystem && isVerified -> MaterialTheme.colorScheme.primary
                        isFoundInSystem && !isVerified -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            ) {
                Text("OK", fontWeight = FontWeight.Medium)
            }
        }
    )
}