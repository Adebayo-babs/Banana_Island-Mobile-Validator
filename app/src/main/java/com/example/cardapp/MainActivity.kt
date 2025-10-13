package com.example.cardapp

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.media.ToneGenerator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.cardapp.model.CardInfo
import com.example.cardapp.data.local.database.CardDatabase
import com.example.cardapp.data.repository.CardRepository
import com.example.cardapp.data.reader.OptimizedCardDataReader
import com.example.cardapp.data.remote.ApiService
import com.example.cardapp.data.remote.CardVerificationResponse
import com.example.cardapp.data.remote.VerifyQRResponse
import com.example.cardapp.ui.CardVerificationDialog
import com.example.cardapp.ui.MainMenuScreen
import com.example.cardapp.ui.QRScannerScreen
import com.example.cardapp.ui.QRVerificationDialog
import com.example.cardapp.ui.SplashScreen
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

    // Navigation States
    private enum class Screen {
        SPLASH,
        MAIN_MENU,
        QR_SCANNER
    }

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null
    private val cardDataReader = OptimizedCardDataReader()

    // State variables for Card Verification
    private var showVerificationDialog by mutableStateOf(false)
    private var verificationResponse by mutableStateOf<CardVerificationResponse?>(null)
    private var verificationCardId by mutableStateOf("")

    // State variables for QR Verification
    private var showQRVerificationDialog by mutableStateOf(false)
    private var qrVerificationResponse by mutableStateOf<VerifyQRResponse?>(null)
    private var qrVerificationData by mutableStateOf("")

    // Track which screen is active for NFC
    private var isCardReaderActive by mutableStateOf(false)
    private var isMainMenuActive by mutableStateOf(false)

    // Navigation state
    private var currentScreen by mutableStateOf(Screen.SPLASH)

    // Store last scanned card ID for main menu display
    private var lastScannedCardId by mutableStateOf<String?>(null)

    // Store last scanned QR code for main menu display
    private var lastScannedQRCode by mutableStateOf<String?>(null)

    // Database and repository
    private lateinit var cardRepository: CardRepository

    // Track active reading job to prevent overlapping reads
    private var readingJob: Job? = null

    private var toneGenerator: ToneGenerator? = null

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
                    when (currentScreen) {
                        Screen.SPLASH -> {
                            SplashScreen (
                                onNavigateToMain = {
                                    currentScreen = Screen.MAIN_MENU
                                    isMainMenuActive = true
                                    isCardReaderActive = false
                                }
                            )
                        }

                        Screen.MAIN_MENU -> {
                            MainMenuScreen(
                                lastScannedCardId = lastScannedCardId,
                                onQRScannerClick = {
                                    isCardReaderActive = false
                                    isMainMenuActive = false
                                    currentScreen = Screen.QR_SCANNER
                                }
                            )
                        }

//                        Screen.CARD_READER -> {
//                            CardReaderScreen(
//                                onBackClick = {
//                                    isCardReaderActive = false
//                                    isMainMenuActive = true
//                                    currentScreen = Screen.MAIN_MENU
//
//                                }
//                            )
//                        }

                        Screen.QR_SCANNER -> {
                            QRScannerScreen(
                                onBackClick = {
                                    isCardReaderActive = false
                                    isMainMenuActive = true
                                    currentScreen = Screen.MAIN_MENU
                                },
                                onQRCodeScanned = { qrCode ->
                                    processQRCode(qrCode)
                                   // Toast.makeText(this, "QR Code: $qrCode", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }

                    // Show card verification dialog when needed
                    if (showVerificationDialog && verificationResponse != null) {
                        CardVerificationDialog(
                            cardId = verificationCardId,
                            success = verificationResponse!!.success,
                            message = verificationResponse!!.message,
                            additionalData = verificationResponse!!.data,
                            onDismiss = {
                                showVerificationDialog = false
                                verificationResponse = null
                                verificationCardId = ""
                            }
                        )
                    }

                    // Show QR verification dialog when needed
                    if (showQRVerificationDialog && qrVerificationResponse != null) {
                        QRVerificationDialog(
                            qrData = qrVerificationData,
                            success = qrVerificationResponse!!.success,
                            message = qrVerificationResponse!!.message,
                            additionalData = qrVerificationResponse!!.data,
                            onDismiss = {
                                showQRVerificationDialog = false
                                qrVerificationResponse = null
                                qrVerificationData = ""
                            }
                        )
                    }
                }
            }
        }
        // Handle NFC intent if app was launched by NFC
        handleIntent(intent)
    }

    private fun processQRCode(qrCode: String) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Processing QR Code: $qrCode")

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Verifying QR code...", Toast.LENGTH_SHORT).show()
                }

                // Call API to verify QR code
                val apiResponse = ApiService.verifyQRCode(qrCode)

                withContext(Dispatchers.Main) {
                    // Store response and show dialog
                    qrVerificationData = qrCode
                    val isIllegal = apiResponse.data?.get("isIllegal") as? Boolean ?: true
                    qrVerificationResponse = apiResponse.copy(
                        success = !isIllegal
                    )
                    showQRVerificationDialog = true
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing QR code: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
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

        // Handle based on mode
        when {
            isMainMenuActive -> {
                // Main menu: Read and display card ID
                processMainMenuNFCTAG(isoDep)
            }
            isCardReaderActive -> {
                // Card reader screen: full verification
                val selectedBatch = CardReaderViewModel.instance.getCurrentBatch()
                if (selectedBatch.isEmpty()) {
                    Toast.makeText(this, "Please select a batch first", Toast.LENGTH_SHORT).show()
                    return
                }
                processVerificationNFCTag(tag, isoDep, selectedBatch)
            }
        }
    }

    private fun processMainMenuNFCTAG(isoDep: IsoDep) {
        readingJob = lifecycleScope.launch {
            try {
                Log.d(TAG, "Reading card on main menu")

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Reading card...", Toast.LENGTH_SHORT).show()
                }

                val cardData = withContext(Dispatchers.IO) {
                    withTimeout(3000L) {
                        cardDataReader.readCardDataAsync(isoDep)
                    }
                }


                if (cardData.cardId != null) {
                    lastScannedCardId = cardData.cardId

                    // Call API to verify card
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Verifying card",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    val apiResponse = ApiService.verifyCard(cardData.cardId)

                    withContext(Dispatchers.Main) {
                        // Store response and show dialog
                        verificationCardId = cardData.cardId
                        val isIllegal = apiResponse.data?.get("isIllegal") as? Boolean ?: true
                        verificationResponse = apiResponse.copy(
                            success = !isIllegal
                        )
                        showVerificationDialog = true
                    }
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Could not read card ID",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: TimeoutCancellationException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Card read timeout",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun processVerificationNFCTag(tag: Tag, isoDep: IsoDep, selectedBatch: String) {
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
            Log.d(TAG, "LOCAL VERIFICATION STARTING")
            Log.d(TAG, "Card ID: ${cardData.cardId}")
            Log.d(TAG, "Target Batch: $targetBatch")

            val verificationStartTime = System.currentTimeMillis()

            // Local verification
            val verificationResult = withContext(Dispatchers.IO) {
                cardRepository.verifyScannedCardAgainstBatch(
                    scannedCardId = cardData.cardId!!,
                    targetBatchName = targetBatch,
                    holderName = cardData.holderName,
                    additionalData = cardData.additionalInfo
                )
            }

            val verificationTime = System.currentTimeMillis() - verificationStartTime
            Log.d(TAG, "LOCAL VERIFICATION COMPLETED in ${verificationTime}ms")
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
                        "✅ Card found in $targetBatch",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                // FAILED - Card not found in target batch
                Log.d(TAG, "❌ VERIFICATION FAILED!")
                Log.d(TAG, "Reason: ${verificationResult.message}")

                // Play error sound
                playErrorSound()

                withContext(Dispatchers.Main) {
                    showNotFoundDialog(
                        cardId = cardData.cardId!!,
                        message = verificationResult.message
                    )
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during fast verification: ${e.message}")

            // Play error sound
            playErrorSound()

            withContext(Dispatchers.Main) {
                showNotFoundDialog(
                    cardId = cardData.cardId ?: "UNKNOWN",
                    message = "Verification error: ${e.message}"
                )
            }
        }
    }

    private fun playErrorSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 2000)
        } catch (e: Exception) {
            Log.e(TAG, "Could not play error sound: ${e.message}")
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
//            appendLine("Status: ✅ VERIFIED")
            appendLine("Batch: ${verificationResult.batchCard?.batchName ?: "Current"}")

            // Card data if available
            cardData.holderName?.let {
                if (it.isNotBlank()) {
                    appendLine("Name: $it")
                }
            }
        }.trim()
    }

}

