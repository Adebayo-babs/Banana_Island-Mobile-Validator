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
import com.example.cardapp.ui.theme.CardAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null
    private val cardDataReader = CardDataReader()

//    Database and repository
    private lateinit var cardRepository: CardRepository

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

        // Run card reading in background thread
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {

                Log.d(TAG, "Processing NFC tag")

                // Read card data using enhanced reader
                val cardData = cardDataReader.readCardData(isoDep)

                Log.d(TAG, "SCANNED CARD DATA")
                Log.d(TAG, "Card ID: ${cardData.cardId}")
                Log.d(TAG, "Holder Name: ${cardData.holderName}")

                // Switch back to main thread for UI updates
                CoroutineScope(Dispatchers.Main).launch {
                    if (cardData.cardId != null) {

                        // Perform verification against database
                        performCardVerification(cardData, tag)

                        Toast.makeText(this@MainActivity, "Card ID: ${cardData.cardId}", Toast.LENGTH_LONG).show()
                    } else {
                        val message = "Could not read card ID from scanned card"
                        Log.w(TAG, message)
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing card: ${e.message}")
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(this@MainActivity, "Error reading card: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }



    private fun performCardVerification(cardData: CardDataReader.CardData, tag: Tag) {
        lifecycleScope.launch {
            try {
                //   Verify card against database
                val verificationResult = cardRepository.verifyScannedCard(
                    scannedCardId = cardData.cardId!!,
                    holderName = cardData.holderName,
                    additionalData = cardData.additionalInfo
                )

                Log.d(TAG, "Verification result: ${verificationResult.message}")

                // Create CardInfo for UI display
                val cardInfo = CardInfo(
                    id = cardData.cardId,
                    timestamp = System.currentTimeMillis(),
                    additionalInfo = buildCardInfoString(cardData, verificationResult),
                    techList = tag.techList.toList(),
                    verificationStatus = if (verificationResult.isSuccess) "VERIFIED" else "NOT_FOUND",
                    batchName = verificationResult.batchCard?.batchName
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
    }



    private fun buildCardInfoString(
        cardData: CardDataReader.CardData,
        verificationResult: CardRepository.VerificationResult
    ): String {
        return buildString {
            // Verification status
            appendLine("🔍 Status: ${if (verificationResult.isSuccess) "✅ VERIFIED" else "❌ NOT FOUND"}")

            if (verificationResult.batchCard != null) {
                appendLine("📦 Batch: ${verificationResult.batchCard.batchName}")
                verificationResult.batchCard.cardOwner?.let {
                    appendLine("👤 Expected: $it")
                }
            } else {
                appendLine("Batch: Not found in any batch")
            }

            // Card data
//            cardData.holderName?.let { appendLine("👤 Scanned: $it") }
//            cardData.expirationDate?.let { appendLine("📅 Expires: $it") }
//            cardData.applicationLabel?.let { appendLine("🏷️ App: $it") }

        }.trim()
    }



    private fun showVerificationResult(result: CardRepository.VerificationResult) {
        val message = if (result.isSuccess) {
            if (result.batchCard != null) {
                "${result.message}\n" +
                        "👤 Expected: ${result.batchCard.cardOwner}\n" +
                        "📦 Batch: ${result.batchCard.batchName}"
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

