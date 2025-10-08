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
}