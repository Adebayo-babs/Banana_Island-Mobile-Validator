package com.example.cardapp.model

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardapp.viewmodel.CardReaderViewModel
import kotlinx.coroutines.launch

@Composable
fun SessionSubmissionSection(
    viewModel: CardReaderViewModel,
    modifier: Modifier = Modifier,
    onSubmissionSuccess: () -> Unit = {}
) {

    val sessionStatsData = viewModel.getCurrentSessionStats()
    val coroutineScope = rememberCoroutineScope()

    var showSubmissionDialog by remember { mutableStateOf(false) }
    var submissionNotes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var submissionResult by remember { mutableStateOf<SubmitScannedCardsResponse?>(null) }

    if (sessionStatsData.sessionActive && sessionStatsData.totalScanned > 0) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Current Session",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = { showSubmissionDialog = true },
                    enabled = sessionStatsData.totalScanned > 0 && !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Submit Batch")
                }
            }
        }
    }

    // 🔹 Submission Dialog
    if (showSubmissionDialog) {
        AlertDialog(
            onDismissRequest = { showSubmissionDialog = false },
            title = {
                Text(
                    if (submissionResult == null) "Submit Session to API"
                    else "Submission Result"
                )
            },
            text = {
                if (submissionResult == null) {
                    // Normal submit form
                    Column {
                        Text(
                            text = "You are about to submit ${sessionStatsData.totalScanned} verified cards to the API.",
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        OutlinedTextField(
                            value = submissionNotes,
                            onValueChange = { submissionNotes = it },
                            label = { Text("Notes (optional)") },
                            placeholder = { Text("Add any notes about this batch...") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                } else {
                    // ✅ Success message
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "✅ Submission Successful!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = submissionResult?.message ?: "",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                if (submissionResult == null) {
                    TextButton(
                        onClick = {
                            isSubmitting = true
                            coroutineScope.launch {
                                try {
                                    val result = viewModel.submitCurrentSession(
                                        notes = submissionNotes.takeIf { it.isNotBlank() }
                                    )
                                    submissionResult = result
                                    if (result?.status == "success") {
                                        viewModel.clearSessionAfterSubmission()
                                        onSubmissionSuccess()
                                    }
                                } catch (e: Exception) {
                                    submissionResult = SubmitScannedCardsResponse(
                                        status = "error",
                                        statusCode = 500,
                                        message = "Failed to submit: ${e.message}"
                                    )
                                } finally {
                                    isSubmitting = false
                                    submissionNotes = ""
                                }
                            }
                        },
                        enabled = !isSubmitting
                    ) {
                        Text(if (isSubmitting) "Submitting..." else "Submit")
                    }
                } else {
                    // 🔹 Continue button after success
                    TextButton(
                        onClick = {
                            submissionResult = null
                            showSubmissionDialog = false
                        }
                    ) {
                        Text("Continue")
                    }
                }
            },
            dismissButton = {
                if (submissionResult == null) {
                    TextButton(onClick = { showSubmissionDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

//    val sessionStatsData = viewModel.getCurrentSessionStats()
//    val coroutineScope = rememberCoroutineScope()
//
//    var showSubmissionDialog by remember { mutableStateOf(false) }
//    var submissionNotes by remember { mutableStateOf("") }
//    var isSubmitting by remember { mutableStateOf(false) }
//    var submissionResult by remember { mutableStateOf<SubmitScannedCardsResponse?>(null) }
//
//    if (sessionStatsData.sessionActive && sessionStatsData.totalScanned > 0) {
//        Card(
//            modifier = modifier.fillMaxWidth(),
//            colors = CardDefaults.cardColors(
//                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
//            )
//        ) {
//            Column(
//                modifier = Modifier.padding(16.dp)
//            ) {
//                Text(
//                    text = "Current Session",
//                    fontSize = 12.sp,
//                    fontWeight = FontWeight.SemiBold,
//                    color = MaterialTheme.colorScheme.secondary,
//                    modifier = Modifier.padding(bottom = 2.dp)
//                )
//
////                Row(
////                    modifier = Modifier.fillMaxWidth(),
////                    horizontalArrangement = Arrangement.SpaceBetween
////                ) {
////                    Column {
////                        Text(
////                            text = "Session ID",
////                            fontSize = 12.sp,
////                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
////                        )
////                        Text(
//////                            .take(5) + "..."
////                            text = sessionStatsData.sessionId,
////                            fontSize = 18.sp,
////                            fontWeight = FontWeight.Medium
////                        )
////                    }
//
////                    Column(horizontalAlignment = Alignment.End) {
////                        Text(
////                            text = "Verified Cards: ${sessionStatsData.totalScanned}",
////                            fontSize = 12.sp,
////                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
////                        )
////                        Text(
////                            text = "${sessionStatsData.totalScanned}",
////                            fontSize = 14.sp,
////                            fontWeight = FontWeight.Medium,
////                            color = MaterialTheme.colorScheme.secondary
////                        )
////                    }
////                }
//
//                Spacer(modifier = Modifier.height(4.dp))
//
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.spacedBy(8.dp)
//                ) {
////                    OutlinedButton(
////                        onClick = {
////                            // End session without submitting
////                            coroutineScope.launch {
////                                try {
////                                    // Clear UI state immediately
////                                    showSubmissionDialog = false
////                                    submissionNotes = ""
////                                    isSubmitting = false
////                                    submissionResult = null
////
////                                    // End the session in ViewModel
////                                    viewModel.endCurrentSession()
////                                    // Clear scanned cards too
////                                    viewModel.clearCards()
////                                }catch (e: Exception) {
////                                    Log.e("SessionSubmission", "Error ending session: ${e.message}")
////                                }
////                            }
////                        },
////                        modifier = Modifier.weight(1f)
////                    ) {
////                        Text("End Session")
////                    }
//
//                    Button(
//                        onClick = { showSubmissionDialog = true },
//                        enabled = sessionStatsData.totalScanned > 0 && !isSubmitting,
//                        modifier = Modifier.weight(1f)
//                    ) {
//                        if (isSubmitting) {
//                            Text("Submitting...")
//                        } else {
//                            Text("Submit Batch")
//                        }
//                    }
//                }
//
//                // Show last submission result if available
//                submissionResult?.let { result ->
//                    Spacer(modifier = Modifier.height(8.dp))
//                    Card(
//                        colors = CardDefaults.cardColors(
//                            containerColor = if (result.status == "success")
//                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
//                            else
//                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
//                        )
//                    ) {
//                        Column(modifier = Modifier.padding(12.dp)) {
//                            Text(
//                                text = if (result.status == "success") "✅ Submission Successful" else "❌ Submission Failed",
//                                fontSize = 14.sp,
//                                fontWeight = FontWeight.SemiBold,
//                                color = if (result.status == "success")
//                                    MaterialTheme.colorScheme.primary
//                                else
//                                    MaterialTheme.colorScheme.error
//                            )
//                            Text(
//                                text = result.message,
//                                fontSize = 12.sp,
//                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
//                            )
////                            result.data?.let { data ->
////                                Text(
////                                    text = "Submitted: ${data.submittedCount}, Duplicates: ${data.duplicateCount}, Errors: ${data.errorCount}",
////                                    fontSize = 11.sp,
////                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
////                                )
////                            }
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//    // Submission Dialog
//    if (showSubmissionDialog) {
//        AlertDialog(
//            onDismissRequest = {
//                showSubmissionDialog = false
//                               },
//            title = { Text("Submit Session to API") },
//            text = {
//                Column {
//                    Text(
//                        text = "You are about to submit ${sessionStatsData.totalScanned} verified cards to the API.",
//                        modifier = Modifier.padding(bottom = 16.dp)
//                    )
//
//                    OutlinedTextField(
//                        value = submissionNotes,
//                        onValueChange = { submissionNotes = it },
//                        label = { Text("Notes (optional)") },
//                        placeholder = { Text("Add any notes about this batch...") },
//                        modifier = Modifier.fillMaxWidth(),
//                        maxLines = 3
//                    )
//                }
//            },
//            confirmButton = {
//                TextButton(
//                    onClick = {
//
//                        showSubmissionDialog = false
//                        isSubmitting = true
//                        viewModel.clearSessionAfterSubmission()
//                        onSubmissionSuccess()
//
//                        coroutineScope.launch {
//                            try {
//                                val result = viewModel.submitCurrentSession(
//                                    notes = submissionNotes.takeIf { it.isNotBlank() }
//                                )
//                                submissionResult = result
//
//                                // Show toast message
//                                if (result?.status == "success") {
//                                    // Success feedback
//                                } else {
//                                    // Error feedback
//                                }
//
//                            } catch (e: Exception) {
//                                submissionResult = SubmitScannedCardsResponse(
//                                    status = "error",
//                                    statusCode = 500,
//                                    message = "Failed to submit: ${e.message}"
//                                )
//                            } finally {
//                                isSubmitting = false
//                                submissionNotes = ""
//                            }
//                        }
//                    },
//                    enabled = !isSubmitting
//                ) {
//                    Text("Submit")
//                }
//            },
//            dismissButton = {
//                TextButton(
//                    onClick = { showSubmissionDialog = false }
//                ) {
//                    Text("Cancel")
//                }
//            }
//        )
//    }
}