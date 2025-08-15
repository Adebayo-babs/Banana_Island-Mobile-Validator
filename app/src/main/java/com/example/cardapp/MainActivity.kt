package com.example.cardapp

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

        //PendingIntent object so the Android system can populate it with the details of the tag when it is scanned
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

        CardReaderViewModel.instance.setRepository(cardRepository)

        lifecycleScope.launch {
            cardRepository.initializeAllBatches()
            cardRepository.updateBatch001WithNewCards()
            cardRepository.initializeCache()

        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
        //Cancel any active reading operation
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

        // Use coroutines with proper error handling and timeouts
        readingJob = lifecycleScope.launch {
            try {
                Log.d(TAG, "=== STARTING FAST CARD READ ===")
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
                Log.d(TAG, "=== CARD READ COMPLETED in ${totalTime}ms ===")
                Log.d(TAG, "Card ID: ${cardData.cardId}")
                Log.d(TAG, "Holder Name: ${cardData.holderName}")

                // Process the card data on main thread
                withContext(Dispatchers.Main) {
                    if (cardData.cardId != null) {
                        // Perform verification against database
                        performCardVerification(cardData, tag, totalTime)

//                        Toast.makeText(
//                            this@MainActivity,
//                            "${cardData.cardId}",
//                            Toast.LENGTH_LONG
//                        ).show()
                    } else {
                        val message = "Could not read card ID (read time: ${totalTime}ms)"
                        Log.w(TAG, message)
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()

                        // Still add to UI for debugging
                        addErrorCardToUI(tag, "NO_ID_FOUND", totalTime)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Card read timeout after 2 seconds")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Card read timeout (>2s)", Toast.LENGTH_LONG).show()
                    addErrorCardToUI(tag, "TIMEOUT", 2000)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing card: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error reading card: ${e.message}", Toast.LENGTH_LONG).show()
                    addErrorCardToUI(tag, "ERROR: ${e.message}", 0)
                }
            }
        }
    }


    private suspend fun performCardVerification(
        cardData: OptimizedCardDataReader.CardData,
        tag: Tag,
        readTimeMs: Long
    ) {
        try {
            // Verify card against database (background thread)
            val verificationResult = withContext(Dispatchers.IO) {
                cardRepository.verifyScannedCard(
                    scannedCardId = cardData.cardId!!,
                    holderName = cardData.holderName,
                    additionalData = cardData.additionalInfo
                )
            }

            Log.d(TAG, "Verification result: ${verificationResult.message}")

            // Create CardInfo for UI display
            val cardInfo = CardInfo(
                id = cardData.cardId!!,
                timestamp = System.currentTimeMillis(),
                additionalInfo = buildCardInfoString(cardData, verificationResult, readTimeMs),
                techList = tag.techList.toList(),
                verificationStatus = if (verificationResult.isSuccess) "VERIFIED" else "NOT_FOUND",
                batchName = verificationResult.batchCard?.batchName,
                isVerified = verificationResult.isSuccess
            )

            // Add to UI
            CardReaderViewModel.instance.addCard(cardInfo)

            // Show verification result
            showVerificationResult(verificationResult)

            // Show statistics
            showVerificationStats()

        } catch (e: Exception) {
            Log.e(TAG, "Error during verification: ${e.message}")
            Toast.makeText(this@MainActivity, "Verification error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addErrorCardToUI(tag: Tag, error: String, readTimeMs: Long) {
        val cardInfo = CardInfo(
            id = "ERROR_${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            additionalInfo = "❌ $error\nRead time: ${readTimeMs}ms\nTech: ${tag.techList.joinToString(", ")}",
            techList = tag.techList.toList(),
            verificationStatus = "ERROR",
            isVerified = false
        )
        CardReaderViewModel.instance.addCard(cardInfo)
    }

    private fun buildCardInfoString(
        cardData: OptimizedCardDataReader.CardData,
        verificationResult: CardRepository.VerificationResult,
        readTimeMs: Long
    ): String {
        return buildString {
//            // Performance info first
//            appendLine("⚡ Read time: ${readTimeMs}ms")

            // Verification status
            appendLine("Status: ${if (verificationResult.isSuccess) "✅ VERIFIED" else "❌ NOT FOUND"}")

            if (verificationResult.batchCard != null) {
                appendLine("Batch: ${verificationResult.batchCard.batchName}")
                verificationResult.batchCard.cardOwner?.let {
                    appendLine(" Found")
                }
            } else {
                appendLine("Batch: Not found in Batch 001")
            }

            // Card data
//            cardData.holderName?.let { appendLine("👤 Name: $it") }
//            cardData.applicationLabel?.let { appendLine("🏷️ App: $it") }
//            cardData.expirationDate?.let { appendLine("📅 Expires: $it") }

            // Performance data
//            if (cardData.additionalInfo.isNotEmpty()) {
//                val importantInfo = cardData.additionalInfo.entries
//                    .filter { !it.value.contains("tag_") } // Skip technical tags
//                    .take(2) // Show only 2 most important
//
//                if (importantInfo.isNotEmpty()) {
//                    appendLine("📋 ${importantInfo.joinToString(", ") { "${it.key}: ${it.value}" }}")
//                }
//            }

        }.trim()
    }



    private fun showVerificationResult(result: CardRepository.VerificationResult) {
        val message = if (result.isSuccess) {
            if (result.batchCard != null) {
                "${result.message}\n" +
                        "Batch: ${result.batchCard.batchName}"
            } else {
                result.message
            }
        } else {
            result.message
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.d(TAG, "Verification: $message")
    }



    @SuppressLint("DefaultLocale")
    private fun showVerificationStats() {
        lifecycleScope.launch {
            try {
                val stats = cardRepository.getVerificationStats(CardRepository.BATCH_01_NAME)
                val statsMessage = "📊 ${CardRepository.BATCH_01_NAME} Progress: " +
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

