package com.example.cardapp

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaPlayer
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

    // Navigation States
    private enum class Screen {
        SPLASH,
        MAIN_MENU,
        CARD_READER,
        ENQUIRY
    }

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null
    private val cardDataReader = OptimizedCardDataReader()
    private var isEnquiryMode by mutableStateOf(false)
//    private var isTestMode by mutableStateOf(true)

    // Navigation state
    private var currentScreen by mutableStateOf(Screen.SPLASH)

//    Database and repository
    private lateinit var cardRepository: CardRepository

    // Track active reading job to prevent overlapping reads
    private var readingJob: Job? = null

    private var toneGenerator: ToneGenerator? = null
    private var showEnquiryScreen by mutableStateOf(false)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Could not initialize tone generator: ${e.message}")
        }

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
                                }
                            )
                        }

                        Screen.MAIN_MENU -> {
                            MainMenuScreen(
                                onBatchScanningClick = {
                                    isEnquiryMode = false
                                    currentScreen = Screen.CARD_READER
                                },
                                onEnquiryClick = {
                                    isEnquiryMode = true
                                    currentScreen = Screen.ENQUIRY
                                }
                            )
                        }

                        Screen.CARD_READER -> {
                            CardReaderScreen(
                                onBackClick = {
                                    currentScreen = Screen.MAIN_MENU
                                }
                            )
                        }

                        Screen.ENQUIRY -> {
                            EnquiryScreen(
                                onBackClick = {
                                    isEnquiryMode = false
                                    currentScreen = Screen.MAIN_MENU
                                }
                            )
                        }
                    }

//                    if (showEnquiryScreen) {
//                        // Set enquiry mode when showing enquiry screen
//                        isEnquiryMode = true
//                        EnquiryScreen(
//                            onBackClick = {
//                                showEnquiryScreen = false
//                                isEnquiryMode = false
//                            }
//                        )
//                    } else {
//                        isEnquiryMode = false
//                        CardReaderScreen(
//                            onEnquiryClick = {
//                                showEnquiryScreen = true
//                            }
//                        )
//                    }
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

        // Handle based on mode
        if (isEnquiryMode) {
            processEnquiryNFCTag(tag, isoDep)
        }else {
            // Batch verification logic
            val selectedBatch = CardReaderViewModel.instance.getCurrentBatch()
            if (selectedBatch.isEmpty()) {
                Toast.makeText(this, "Please select a batch first", Toast.LENGTH_SHORT).show()
                return
            }

            processVerificationNFCTag(tag, isoDep, selectedBatch)
        }
    }

    private fun processEnquiryNFCTag(tag: Tag, isoDep: IsoDep) {
        readingJob = lifecycleScope.launch {
            try {
                Log.d(TAG, "Starting enquiry card read")
                val startTime = System.currentTimeMillis()

                // Show immediate feedback to user
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Reading card for enquiry...", Toast.LENGTH_SHORT).show()
                }

                // Read card data
                val cardData = withContext(Dispatchers.IO) {
                    withTimeout(5000L) {
                        cardDataReader.readCardDataAsync(isoDep)
                    }
                }

                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "ENQUIRY CARD READ COMPLETED in ${totalTime}ms")
                Log.d(TAG, "Card ID: ${cardData.cardId}")

                // Process the card data on main thread
                withContext(Dispatchers.Main) {
                    if (cardData.cardId != null) {
                        // Create a CardInfo object for enquiry (won't be added to verification list)
                        performDirectEnquiry(cardData.cardId, totalTime)

                        Toast.makeText(this@MainActivity, "Card read successfully", Toast.LENGTH_SHORT).show()

                    } else {
                        val message = "Could not read card ID (read time: ${totalTime}ms)"
                        Log.w(TAG, message)
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()

                        // Trigger error result
                        val errorResult = CardRepository.CardEnquiryResult(
                            cardExists = false,
                            batchName = null,
                            isVerified = false,
                            message = "Could not read card ID",
                            batchCard = null,
                            verifiedCard = null
                        )
                        CardReaderViewModel.instance.triggerEnquiryResult(errorResult, "UNKNOWN_ID", totalTime)


                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Card read timeout after 5 seconds")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Card read timeout (>5s)", Toast.LENGTH_LONG).show()

                    // Trigger timeout error result
                    val errorResult = CardRepository.CardEnquiryResult(
                        cardExists = false,
                        batchName = null,
                        isVerified = false,
                        message = "Card read timeout after 5 seconds",
                        batchCard = null,
                        verifiedCard = null
                    )
                    CardReaderViewModel.instance.triggerEnquiryResult(errorResult, "TIMEOUT", 5000)

                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing card: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error reading card: ${e.message}", Toast.LENGTH_LONG).show()

                    // Trigger general error result
                    val errorResult = CardRepository.CardEnquiryResult(
                        cardExists = false,
                        batchName = null,
                        isVerified = false,
                        message = "Error reading card: ${e.message}",
                        batchCard = null,
                        verifiedCard = null
                    )
                    CardReaderViewModel.instance.triggerEnquiryResult(errorResult, "ERROR", 0)

                }
            }
        }
    }

    private fun performDirectEnquiry(cardId: String, readTimeMs: Long) {
        lifecycleScope.launch {
            try {

                // Start searching
                CardReaderViewModel.instance.setEnquirySearching(true)

                Log.d(TAG, "Starting direct enquiry for card: $cardId")

                // Show that we're searching
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(this@MainActivity, "Searching across batches...", Toast.LENGTH_SHORT).show()
//                }

                // Perform the global enquiry
                val enquiryResult = withContext(Dispatchers.IO) {
                    performDirectApiEnquiry(cardId)
                }

                Log.d(TAG, "Direct enquiry completed. Result: ${enquiryResult.message}")

                // Trigger the result in the ViewModel - this will show the dialog
                withContext(Dispatchers.Main) {
                    //Stop searching
                    CardReaderViewModel.instance.setEnquirySearching(false)
                    //Trigger the result in the ViewModel - this will show the dialog
                    CardReaderViewModel.instance.triggerEnquiryResult(enquiryResult,cardId, readTimeMs)

                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during direct enquiry: ${e.message}")

                withContext(Dispatchers.Main) {
                    // Stop searching on error
                    CardReaderViewModel.instance.setEnquirySearching(false)
                    val errorResult = CardRepository.CardEnquiryResult(
                        cardExists = false,
                        batchName = null,
                        isVerified = false,
                        message = "Error during enquiry: ${e.message}",
                        batchCard = null,
                        verifiedCard = null
                    )
                    CardReaderViewModel.instance.triggerEnquiryResult(errorResult, cardId, readTimeMs)
                }
            }
        }
    }

    private suspend fun performDirectApiEnquiry(cardId: String): CardRepository.CardEnquiryResult {
        return try {
            Log.d(TAG, "Making direct API call for card: $cardId")

            // Use the direct API endpoint for enquiry
            val enquiryRequest = EnquireCardRequest(cardId = cardId)
            val apiResponse = ApiService.api.enquireCard(enquiryRequest)

            Log.d(TAG, "API Response - found: ${apiResponse.data?.found}")
            Log.d(TAG, "API Response - batchNumber: ${apiResponse.data?.batchNumber}")
            Log.d(TAG, "API Response - batchName: ${apiResponse.data?.batchName}")
            Log.d(TAG, "API Response - message: ${apiResponse.message}")

            // Extract data from the nested structure
            val cardData = apiResponse.data
            val cardExists = cardData?.found == true
            val batchNumber = cardData?.batchNumber
            val batchName = cardData?.batchName

            Log.d(TAG, "Extracted - cardExists: $cardExists, batchName: '$batchName', batchNumber: $batchNumber")

            // Convert API response to CardEnquiryResult
            CardRepository.CardEnquiryResult(
                cardExists = cardExists,
                batchName = batchName, // Use the full batch name from API: "Batch 10 - Port Stuart Cards"
                isVerified = cardData?.status == "ACTIVE", // Assuming ACTIVE status means verified
                message = apiResponse.message,
                batchCard = null, // API doesn't return full batch card details in enquiry
                verifiedCard = null // API doesn't return full verified card details in enquiry
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error during direct API enquiry: ${e.message}")
            CardRepository.CardEnquiryResult(
                cardExists = false,
                batchName = null,
                isVerified = false,
                message = "Failed to query card database: ${e.message}",
                batchCard = null,
                verifiedCard = null
            )
        }
    }

    // ADDED: Comprehensive enquiry method
    private suspend fun performComprehensiveEnquiry(cardId: String): CardRepository.CardEnquiryResult {
        return try {
            Log.d(TAG, "Starting comprehensive enquiry for card: $cardId")

            // Get all available batches
            val batches = CardReaderViewModel.instance.availableBatches.value

            if (batches.isEmpty()) {
                Log.w(TAG, "No batches available for search")
                return CardRepository.CardEnquiryResult(
                    cardExists = false,
                    batchName = null,
                    isVerified = false,
                    message = "No batches available for search",
                    batchCard = null,
                    verifiedCard = null
                )
            }

            Log.d(TAG, "Searching across ${batches.size} batches: ${batches.joinToString(", ")}")

            // Search through each batch
            for (batchName in batches) {
                try {
                    Log.d(TAG, "Checking batch: $batchName")

                    // Load the specific batch
                    val batchLoaded = cardRepository.loadSpecificBatch(batchName)

                    if (!batchLoaded) {
                        Log.w(TAG, "Failed to load batch: $batchName")
                        continue
                    }

                    // Search for the card in this batch
                    val verificationResult = cardRepository.verifyScannedCardAgainstBatch(
                        scannedCardId = cardId,
                        targetBatchName = batchName,
                        holderName = null,
                        additionalData = emptyMap()
                    )

                    if (verificationResult.isSuccess) {
                        Log.d(TAG, "✅ Card found in batch: $batchName")


                        return CardRepository.CardEnquiryResult(
                            cardExists = true,
                            batchName = batchName,
                            isVerified = true,
                            message = "Card found and verified in $batchName",
                            batchCard = verificationResult.batchCard,
                            verifiedCard = null
                        )
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "Error searching batch $batchName: ${e.message}")
                    // Continue searching other batches
                    continue
                }
            }

            // Card not found in any batch
            Log.d(TAG, "❌ Card not found in any of the ${batches.size} available batches")

            return CardRepository.CardEnquiryResult(
                cardExists = false,
                batchName = null,
                isVerified = false,
                message = "Card not found in any of the ${batches.size} available batches",
                batchCard = null,
                verifiedCard = null
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error during comprehensive enquiry: ${e.message}")
            return CardRepository.CardEnquiryResult(
                cardExists = false,
                batchName = null,
                isVerified = false,
                message = "Error during search: ${e.message}",
                batchCard = null,
                verifiedCard = null
            )
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


//    private fun processVerificationNFCTag(tag: Tag, isoDep: IsoDep, selectedBatch: String) {
//        readingJob = lifecycleScope.launch {
//            try {
//                Log.d(TAG, "=== STARTING TEST MODE CARD READ ===")
//                val startTime = System.currentTimeMillis()
//
//                // Show immediate feedback to user
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(this@MainActivity, "Reading card...", Toast.LENGTH_SHORT).show()
//                }
//
//                if (isTestMode) {
//                    // TEST MODE: Just read card and add to list without verification
//                    val cardData = withContext(Dispatchers.IO) {
//                        withTimeout(2000L) {
//                            cardDataReader.readCardDataAsync(isoDep)
//                        }
//                    }
//
//                    val totalTime = System.currentTimeMillis() - startTime
//                    Log.d(TAG, "TEST MODE - Card read completed in ${totalTime}ms")
//
//                    withContext(Dispatchers.Main) {
//                        val cardInfo = CardInfo(
//                            id = cardData.cardId ?: "UNKNOWN_${System.currentTimeMillis()}",
//                            timestamp = System.currentTimeMillis(),
//                            additionalInfo = buildString {
////                                appendLine("TEST MODE - No verification")
//                                cardData.holderName?.let {
//                                    if (it.isNotBlank()) appendLine("Name: $it")
//                                }
//                                appendLine("Read time: ${totalTime}ms")
//                            }.trim(),
//                            techList = tag.techList.toList(),
//                            verificationStatus = "TEST MODE",
//                            batchName = "TEST",
//                            isVerified = true // Mark as verified for UI purposes
//                        )
//
//                        // Add to UI
//                        CardReaderViewModel.instance.addCard(cardInfo)
//
//                        Toast.makeText(
//                            this@MainActivity,
//                            "Card added (Test Mode)",
//                            Toast.LENGTH_SHORT
//                        ).show()
//                    }
//                } else {
//                    // ORIGINAL VERIFICATION CODE (keep existing logic)
//                    val cardData = withContext(Dispatchers.IO) {
//                        withTimeout(2000L) {
//                            cardDataReader.readCardDataAsync(isoDep)
//                        }
//                    }
//
//                    val totalTime = System.currentTimeMillis() - startTime
//                    Log.d(TAG, "CARD READ COMPLETED in ${totalTime}ms")
//
//                    withContext(Dispatchers.Main) {
//                        if (cardData.cardId != null) {
//                            performFastCardVerification(cardData, tag, totalTime, selectedBatch)
//                        } else {
//                            val message = "Could not read card ID (read time: ${totalTime}ms)"
//                            Log.w(TAG, message)
//                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
//                            addErrorCardToUI(tag, "NO_ID_FOUND", totalTime, selectedBatch)
//                        }
//                    }
//                }
//            } catch (e: TimeoutCancellationException) {
//                Log.e(TAG, "Card read timeout after 2 seconds")
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(this@MainActivity, "Card read timeout (>2s)", Toast.LENGTH_LONG).show()
//                    if (!isTestMode) {
//                        addErrorCardToUI(tag, "TIMEOUT", 2000, selectedBatch)
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "Error processing card: ${e.message}")
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(this@MainActivity, "Error reading card: ${e.message}", Toast.LENGTH_LONG).show()
//                    if (!isTestMode) {
//                        addErrorCardToUI(tag, "ERROR: ${e.message}", 0, selectedBatch)
//                    }
//                }
//            }
//        }
//    }

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


                // Play success sound
//                playSuccessSound(this)

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

                // Update statistics
                updateVerificationStats(targetBatch)
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

    private fun playSuccessSound(context: Context) {
//        val mediaPlayer = MediaPlayer.create(context, R.raw.success)
//        mediaPlayer.start()
//        mediaPlayer.setOnCompletionListener { mp ->
//            mp.release()
//        }
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_CONFIRM, 2000)
        } catch (e: Exception) {
            Log.e(TAG, "Could not play success sound: ${e.message}")
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

