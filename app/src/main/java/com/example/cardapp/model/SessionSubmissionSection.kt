package com.example.cardapp.model


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.cardapp.viewmodel.CardReaderViewModel
import kotlinx.coroutines.launch

@Composable
fun SessionSubmissionSection(
    viewModel: CardReaderViewModel,
    modifier: Modifier = Modifier,
    onSubmissionSuccess: () -> Unit = {},
    currentCardCount: Int = 0
) {

    val sessionStatsData = viewModel.getCurrentSessionStats()
    val coroutineScope = rememberCoroutineScope()

    var showSubmissionDialog by remember { mutableStateOf(false) }
    var submissionNotes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var submissionResult by remember { mutableStateOf<SubmitScannedCardsResponse?>(null) }

    if (sessionStatsData.sessionActive && currentCardCount > 0) {
        Button(
            onClick = { showSubmissionDialog = true },
            enabled = currentCardCount > 0 && !isSubmitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isSubmitting) "Submitting..." else "Submit Batch",
                fontSize = 14.sp
            )
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
                            text = "You are about to submit $currentCardCount cards to the API.",
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
}