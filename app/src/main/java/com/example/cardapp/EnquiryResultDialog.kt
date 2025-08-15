package com.example.cardapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardapp.model.CardInfo
import com.example.cardapp.repository.CardRepository
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun EnquiryResultDialog(
    cardInfo: CardInfo,
    enquiryResult: CardRepository.CardEnquiryResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🔍 Enquiry Result",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                // Card ID
                Text(
                    text = "Card ID: ${cardInfo.id}",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (enquiryResult.cardExists) {
                    // Found in batch
                    Text(
                        text = "Found in Batch",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    enquiryResult.batchName?.let { batchName ->
                        Text(
                            text = "Batch: $batchName",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (enquiryResult.isVerified) {
                        Text(
                            text = "Status: Already Verified",
                            color = MaterialTheme.colorScheme.primary
                        )
                        enquiryResult.verifiedCard?.let { verified ->
                            Text(
                                text = "Verified: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(verified.verifiedAt)}",
                                fontSize = 14.sp
                            )
//                            verified.holderName?.let { name ->
//                                Text(
//                                    text = "👤 Holder: $name",
//                                    fontSize = 14.sp
//                                )
//                            }
                        }
                    } else {
                        Text(
                            text = "⏳ Status: Not Yet Verified",
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "This card exists in the batch but hasn't been verified yet.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    enquiryResult.batchCard?.cardOwner?.let { owner ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "👤 Expected Owner: $owner",
                            fontSize = 14.sp
                        )
                    }

                } else {
                    // Not found anywhere
                    Text(
                        text = "❌ Not Found",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "This card was not found in any of the available batches.",
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Checked Batches:",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "• Batch 001\n• Batch 002\n• Batch 003",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}