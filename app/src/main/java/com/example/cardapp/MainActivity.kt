package com.example.cardapp

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.cardapp.model.CardInfo
import com.example.cardapp.model.database.CardDatabase
import com.example.cardapp.repository.CardRepository
import com.example.cardapp.repository.OptimizedCardDataReader
import com.example.cardapp.ui.theme.CardAppTheme
import com.example.cardapp.viewmodel.CardReaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null
    private val cardDataReader = OptimizedCardDataReader()

//    Database and repository
    private lateinit var cardRepository: CardRepository

    // Track active reading job to prevent overlapping reads
    private var readingJob: Job? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupNFC()
        setupDatabase()

        enableEdgeToEdge()
        setContent {
            CardAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CardReaderScreen()
                }
            }
        }
        // Handle NFC intent if app was launched by NFC
        handleIntent(intent)
    }

    private fun setupNFC() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // PendingIntent object so the Android system can populate it with the details of the tag when it is scanned
        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        // Setup intent filters for NFC
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        val tech = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        val tag = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        intentFiltersArray = arrayOf(ndef, tech, tag)

        // Setup technology filters
        techListsArray = arrayOf(
            arrayOf(IsoDep::class.java.name)
        )
    }

    private fun setupDatabase() {

        // Initialize database and repository
        val database = CardDatabase.getDatabase(this)
        cardRepository = CardRepository(
            database.batchCardDao(),
            database.verifiedCardDao()
        )

        // Get available batches from API
        val batches = cardRepository.getAvailableBatches()
        CardReaderViewModel.instance.updateAvailableBatches(batches)

        CardReaderViewModel.instance.setRepository(cardRepository)

        lifecycleScope.launch {
            try {
                // Show loading message
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Connecting to API...", Toast.LENGTH_SHORT).show()
                }

                // Initialize with API data
                cardRepository.initializeWithApi()

                // Get available batches from API
                val batches = cardRepository.getAvailableBatches()

                if (batches.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No active batches found in API", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // Update ViewModel with API batches
                CardReaderViewModel.instance.updateAvailableBatches(batches)

                // Show success message
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Connected! ${batches.size} batches available", Toast.LENGTH_SHORT).show()
                }

                Log.d(TAG, "Available batches loaded: $batches")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up database: ${e.message}")
                // Show error to user
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to connect to API: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            lifecycleScope.launch {
                CardReaderViewModel.instance.selectedBatch.collect { batchName ->
                    if (batchName.isNotEmpty()) {
                        val batchNumber = batchName.replace("Batch ", "").trim().toInt()
                        CardReaderViewModel.instance.startSession(batchNumber)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
        // Cancel any active reading operation
        readingJob?.cancel()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        if (NfcAdapter.ACTION_TAG_DISCOVERED == action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == action
        ) {
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            tag?.let { processNFCTag(it) }
        }
    }

    private fun processNFCTag(tag: Tag) {
        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            Toast.makeText(this, "Card not supported (no IsoDep)", Toast.LENGTH_SHORT).show()
            return
        }

        // Cancel any previous reading operation
        readingJob?.cancel()

        // Get the currently selected batch from viewModel
        val selectedBatch = CardReaderViewModel.instance.getCurrentBatch()

        if (selectedBatch.isEmpty()) {
            Toast.makeText(this, "Please select a batch first", Toast.LENGTH_SHORT).show()
            return
        }

        // Use coroutines with proper error handling and timeouts
        readingJob = lifecycleScope.launch {
            try {
                Log.d(TAG, "=== STARTING OPTIMIZED CARD READ ===")
                val startTime = System.currentTimeMillis()

                // Show immediate feedback to user
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Reading card...", Toast.LENGTH_SHORT).show()
                }

                // OPTIMIZED: Read card data with aggressive timeout
                val cardData = withContext(Dispatchers.IO) {
                    withTimeout(2000L) { // 2 second max timeout
                        cardDataReader.readCardDataAsync(isoDep)
                    }
                }

                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "CARD READ COMPLETED in ${totalTime}ms")
                Log.d(TAG, "Card ID: ${cardData.cardId}")
                Log.d(TAG, "Holder Name: ${cardData.holderName}")

                // Process the card data on main thread
                withContext(Dispatchers.Main) {
                    if (cardData.cardId != null) {
                        // Perform FAST verification using API directly
                        performFastCardVerification(cardData, tag, totalTime, selectedBatch)
                    } else {
                        val message = "Could not read card ID (read time: ${totalTime}ms)"
                        Log.w(TAG, message)
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()

                        // Still add to UI for debugging
                        addErrorCardToUI(tag, "NO_ID_FOUND", totalTime, selectedBatch)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Card read timeout after 2 seconds")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Card read timeout (>2s)", Toast.LENGTH_LONG).show()
                    addErrorCardToUI(tag, "TIMEOUT", 2000, selectedBatch)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing card: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error reading card: ${e.message}", Toast.LENGTH_LONG).show()
                    addErrorCardToUI(tag, "ERROR: ${e.message}", 0, selectedBatch)
                }
            }
        }
    }

    private suspend fun performFastCardVerification(
        cardData: OptimizedCardDataReader.CardData,
        tag: Tag,
        readTimeMs: Long,
        targetBatch: String
    ) {
        try {
            Log.d(TAG, "VERIFICATION STARTING")
            Log.d(TAG, "Card ID: ${cardData.cardId}")
            Log.d(TAG, "Target Batch: $targetBatch")

            val verificationStartTime = System.currentTimeMillis()

            // Use the optimized verification that calls API directly
            val verificationResult = withContext(Dispatchers.IO) {
                cardRepository.verifyScannedCardAgainstBatch(
                    scannedCardId = cardData.cardId!!,
                    targetBatchName = targetBatch,
                    holderName = cardData.holderName,
                    additionalData = cardData.additionalInfo
                )
            }

            val verificationTime = System.currentTimeMillis() - verificationStartTime
            Log.d(TAG, "VERIFICATION COMPLETED in ${verificationTime}ms")
            Log.d(TAG, "Verification result: ${verificationResult.message}")

            if (verificationResult.isSuccess) {
                // SUCCESS - Card found in the correct batch
                Log.d(TAG, "✅ VERIFICATION SUCCESS!")

                val cardInfo = CardInfo(
                    id = cardData.cardId!!,
                    timestamp = System.currentTimeMillis(),
                    additionalInfo = buildSuccessCardInfoString(cardData, verificationResult, readTimeMs, verificationTime),
                    techList = tag.techList.toList(),
                    verificationStatus = "VERIFIED",
                    batchName = targetBatch,
                    isVerified = true
                )

                // Add to UI
                CardReaderViewModel.instance.addCard(cardInfo)

                // Show success message
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "✅ Card verified in $targetBatch",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Update statistics
                updateVerificationStats(targetBatch)
            } else {
                // FAILED - Card not found in target batch
                Log.d(TAG, "❌ VERIFICATION FAILED!")
                Log.d(TAG, "Reason: ${verificationResult.message}")

                withContext(Dispatchers.Main) {
                    showNotFoundDialog(
                        cardId = cardData.cardId!!,
                        message = verificationResult.message
                    )
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during fast verification: ${e.message}")
            withContext(Dispatchers.Main) {
                showNotFoundDialog(
                    cardId = cardData.cardId ?: "UNKNOWN",
                    message = "Verification error: ${e.message}"
                )
            }
        }
    }

    private fun showNotFoundDialog(cardId: String, message: String) {
        CardReaderViewModel.instance.showNotFoundDialog(
            title = "Card Not Found in Selected Batch",
            message = "Card ID: $cardId\n\n$message"
        )
    }

    private fun addErrorCardToUI(tag: Tag, error: String, readTimeMs: Long, targetBatch: String) {
        // Only add to UI for technical errors like timeouts
        if (error.contains("TIMEOUT") || error.contains("ERROR")) {
            val cardInfo = CardInfo(
                id = "ERROR_${System.currentTimeMillis()}",
                timestamp = System.currentTimeMillis(),
                additionalInfo = "$error\nRead time: ${readTimeMs}ms\nTech: ${tag.techList.joinToString(", ")}",
                techList = tag.techList.toList(),
                verificationStatus = "ERROR",
                batchName = targetBatch,
                isVerified = false
            )
            CardReaderViewModel.instance.addCard(cardInfo)
        }
    }

    private fun buildSuccessCardInfoString(
        cardData: OptimizedCardDataReader.CardData,
        verificationResult: CardRepository.VerificationResult,
        readTimeMs: Long,
        verificationTimeMs: Long
    ): String {
        return buildString {
            // Verification status
            appendLine("Status: ✅ VERIFIED")
            appendLine("Batch: ${verificationResult.batchCard?.batchName ?: "Current"}")

            // Card data if available
            cardData.holderName?.let {
                if (it.isNotBlank()) {
                    appendLine("Name: $it")
                }
            }

            // Performance info
//            appendLine("Read: ${readTimeMs}ms | Verify: ${verificationTimeMs}ms")
//
//            // Additional info if available
//            if (cardData.additionalInfo.isNotEmpty()) {
//                cardData.additionalInfo.forEach { (key, value) ->
//                    appendLine("$key: $value")
//                }
//            }
        }.trim()
    }



    @SuppressLint("DefaultLocale")
    private fun updateVerificationStats(batchName: String) {
        lifecycleScope.launch {
            try {
                val stats = cardRepository.getVerificationStats(batchName)
                val statsMessage = "$batchName Progress: " +
                        "${stats.verifiedCards}/${stats.totalCards} " +
                        "(${String.format("%.1f", stats.completionPercentage)}%)"

                Log.d(TAG, "Updated Stats: $statsMessage")
                Log.d(TAG, "Total scans: ${stats.totalScans}, Remaining: ${stats.remainingCards}")

                // Update ViewModel with latest stats
//                CardReaderViewModel.instance.updateBatchStats(
//                    totalCards = stats.totalCards,
//                    verifiedCards = stats.verifiedCards
//                )

            } catch (e: Exception) {
                Log.e(TAG, "Error updating stats: ${e.message}")
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun showVerificationStats(batchName: String) {
        lifecycleScope.launch {
            try {
                val stats = cardRepository.getVerificationStats(batchName)
                val statsMessage = "$batchName Progress: " +
                        "${stats.verifiedCards}/${stats.totalCards} " +
                        "(${String.format("%.1f", stats.completionPercentage)}%)"

                Log.d(TAG, "Stats: $statsMessage")
                Log.d(TAG, "Total scans: ${stats.totalScans}, Remaining: ${stats.remainingCards}")

            } catch (e: Exception) {
                Log.e(TAG, "Error getting stats: ${e.message}")
            }
        }
    }

}

