package com.example.cardapp

import android.annotation.SuppressLint
import android.nfc.tech.IsoDep
import android.util.Log
import java.io.IOException
import java.nio.charset.StandardCharsets

class CardDataReader {
    companion object {
        private const val TAG = "CardDataReader"

        // Common AID for various card applications
        private val CARD_AIDS = arrayOf(
            "A000000077AB01", // Your specific AID
            "315041592E5359532E4444463031" // Generic card AID
        )

        // APDU commands for reading card data
        private fun selectApplicationCommand(aid: String): ByteArray {
            val aidBytes = hexStringToByteArray(aid)
            val command = ByteArray(5 + aidBytes.size)
            command[0] = 0x00.toByte() // CLA
            command[1] = 0xA4.toByte() // INS (SELECT)
            command[2] = 0x04.toByte() // P1
            command[3] = 0x00.toByte() // P2
            command[4] = aidBytes.size.toByte() // LC
            System.arraycopy(aidBytes, 0, command, 5, aidBytes.size)
            return command
        }

        // Read record command
        private fun readRecordCommand(recordNumber: Int, sfi: Int): ByteArray {
            return byteArrayOf(
                0x00.toByte(), // CLA
                0xB2.toByte(), // INS (READ RECORD)
                recordNumber.toByte(), // P1
                ((sfi shl 3) or 0x04).toByte(), // P2
                0x00.toByte()  // LE
            )
        }

        // Get processing options command
        private fun getProcessingOptionsCommand(): ByteArray {
            return byteArrayOf(
                0x80.toByte(), // CLA
                0xA8.toByte(), // INS (GET PROCESSING OPTIONS)
                0x00.toByte(), // P1
                0x00.toByte(), // P2
                0x02.toByte(), // LC
                0x83.toByte(), // PDOL
                0x00.toByte(), // PDOL length
                0x00.toByte()  // LE
            )
        }

        private fun hexStringToByteArray(s: String): ByteArray {
            val len = s.length
            val data = ByteArray(len / 2)
            for (i in 0 until len step 2) {
                data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            }
            return data
        }

        private fun byteArrayToHex(bytes: ByteArray): String {
            return bytes.joinToString("") { "%02X".format(it) }
        }

        private fun isPrintableAscii(byte: Byte): Boolean {
            val b = byte.toInt() and 0xFF
            return b in 32..126
        }
    }

    data class CardData(
        val cardId: String?,
        val holderName: String?,
        val expirationDate: String?,
        val applicationLabel: String?,
        val pan: String?, // Primary Account Number
        val additionalInfo: Map<String, String> = emptyMap(),
        val rawData: List<String> = emptyList()
    )

    fun readCardData(isoDep: IsoDep): CardData {
        var cardId: String? = null
        var holderName: String? = null
        var expirationDate: String? = null
        var applicationLabel: String? = null
        var pan: String? = null
        val additionalInfo = mutableMapOf<String, String>()
        val rawDataList = mutableListOf<String>()

        try {
            isoDep.connect()
            Log.d(TAG, "Connected to card")

            // Try different AIDs to find the right application
            var applicationSelected = false
            for (aid in CARD_AIDS) {
                try {
                    val selectCommand = selectApplicationCommand(aid.toString())
                    val response = isoDep.transceive(selectCommand)
                    val responseHex = byteArrayToHex(response)
                    rawDataList.add("SELECT $aid: $responseHex")

                    if (response.size >= 2) {
                        val sw1 = response[response.size - 2].toInt() and 0xFF
                        val sw2 = response[response.size - 1].toInt() and 0xFF

                        if (sw1 == 0x90 && sw2 == 0x00) {
                            Log.d(TAG, "Successfully selected application: $aid")
                            applicationSelected = true

                            // Parse application selection response for application label
                            applicationLabel = parseApplicationLabel(response)
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to select AID $aid: ${e.message}")
                }
            }

            if (!applicationSelected) {
                Log.w(TAG, "No application could be selected")
                return CardData(null, null, null, null, null, additionalInfo, rawDataList)
            }

            // Get Processing Options
            try {
                val gpoCommand = getProcessingOptionsCommand()
                val gpoResponse = isoDep.transceive(gpoCommand)
                val gpoHex = byteArrayToHex(gpoResponse)
                rawDataList.add("GPO: $gpoHex")
                Log.d(TAG, "GPO Response: $gpoHex")
            } catch (e: Exception) {
                Log.w(TAG, "GPO failed: ${e.message}")
            }

            // Try to read various records that might contain card data
            for (sfi in 1..10) {
                for (record in 1..10) {
                    try {
                        val readCommand = readRecordCommand(record, sfi)
                        val recordResponse = isoDep.transceive(readCommand)

                        if (recordResponse.size >= 2) {
                            val sw1 = recordResponse[recordResponse.size - 2].toInt() and 0xFF
                            val sw2 = recordResponse[recordResponse.size - 1].toInt() and 0xFF

                            if (sw1 == 0x90 && sw2 == 0x00) {
                                val recordHex = byteArrayToHex(recordResponse)
                                rawDataList.add("Record SFI:$sfi REC:$record: $recordHex")

                                // Enhanced parsing for this specific card
                                val parsedData = parseCardRecord(recordResponse, sfi, record)

                                // Merge parsed data
                                if (parsedData.cardId != null && cardId == null) {
                                    cardId = parsedData.cardId
                                }
                                if (parsedData.holderName != null && holderName == null) {
                                    holderName = parsedData.holderName
                                    Log.d(TAG, "Found cardholder name: $holderName")
                                }
                                if (parsedData.expirationDate != null && expirationDate == null) {
                                    expirationDate = parsedData.expirationDate
                                }
                                if (parsedData.pan != null && pan == null) {
                                    pan = parsedData.pan
                                }

                                // Add additional info
                                additionalInfo.putAll(parsedData.additionalInfo)
                            }
                        }
                    } catch (e: Exception) {
                        // Record doesn't exist or can't be read, continue
                    }
                }
            }

            // Log all extracted information
            Log.d(TAG, "=== EXTRACTED CARD DATA ===")
            Log.d(TAG, "Card ID: $cardId")
            Log.d(TAG, "Application Label: $applicationLabel")


        } catch (e: IOException) {
            Log.e(TAG, "IO Exception reading card: ${e.message}")
        } finally {
            try {
                isoDep.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing connection: ${e.message}")
            }
        }

        return CardData(cardId, holderName, expirationDate, applicationLabel, pan, additionalInfo, rawDataList)
    }

    private fun parseApplicationLabel(response: ByteArray): String? {
        // Look for application label tag (50)
        return findTLVData(response, 0x50)?.let {
            String(it, StandardCharsets.UTF_8).trim()
        }
    }

    @SuppressLint("DefaultLocale")
    private fun parseCardRecord(record: ByteArray, sfi: Int, recordNumber: Int): CardData {
        var cardId: String? = null
        var holderName: String? = null
        var expirationDate: String? = null
        var pan: String? = null
        val additionalInfo = mutableMapOf<String, String>()

        // Special handling for SFI 1 Record 1 based on your log data
        if (sfi == 1 && recordNumber == 1) {
            val parsedData = parseCustomRecord(record)
            holderName = parsedData["holderName"]
            cardId = parsedData["cardId"]

            // Add all parsed data to additional info
            additionalInfo.putAll(parsedData)

            Log.d(TAG, "Custom parsing for SFI:$sfi REC:$recordNumber")
            parsedData.forEach { (key, value) ->
                Log.d(TAG, "Parsed $key: $value")
            }
        }

        // Standard EMV parsing
        // Look for PAN (Primary Account Number) - Tag 5A
        findTLVData(record, 0x5A)?.let { panBytes ->
            pan = byteArrayToHex(panBytes)
            if (cardId == null) cardId = pan
        }

        // Look for cardholder name - Tag 5F20
        findTLVData(record, 0x5F, 0x20)?.let { nameBytes ->
            val name = String(nameBytes, StandardCharsets.UTF_8).trim()
            if (name.isNotEmpty() && holderName == null) {
                holderName = name
            }
        }

        // Look for expiration date - Tag 5F24
        findTLVData(record, 0x5F, 0x24)?.let { expiryBytes ->
            if (expiryBytes.size >= 3) {
                val year = String.format("%02d", expiryBytes[0].toInt() and 0xFF)
                val month = String.format("%02d", expiryBytes[1].toInt() and 0xFF)
                expirationDate = "$month/$year"
            }
        }

        // Look for application specific data that might contain card ID
        findTLVData(record, 0x9F, 0x10)?.let { appData ->
            if (cardId == null) {
                cardId = byteArrayToHex(appData)
            }
        }

        // Additional parsing for other useful data
        parseAdditionalEMVData(record, additionalInfo)

        return CardData(cardId, holderName, expirationDate, null, pan, additionalInfo)
    }

    @SuppressLint("DefaultLocale")
    private fun parseCustomRecord(record: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()

        try {

            // This appears to be custom TLV data
            var i = 0
            while (i < record.size - 1) {
                // Look for DF tags (custom/proprietary tags)
                if (i < record.size - 2 && record[i].toInt() and 0xFF == 0xDF) {
                    val tag = ((record[i].toInt() and 0xFF) shl 8) or (record[i + 1].toInt() and 0xFF)
                    i += 2

                    if (i >= record.size) break

                    val length = record[i].toInt() and 0xFF
                    i++

                    if (i + length <= record.size) {
                        val value = ByteArray(length)
                        System.arraycopy(record, i, value, 0, length)

                        when (tag) {
                            0xDF01 -> {
                                // Might be card type or identifier
                                val stringValue = tryParseAsString(value)
                                if (stringValue != null) {
                                    result["cardType"] = stringValue
                                    Log.d(TAG, "Found card type: $stringValue")
                                }
                            }
                            0xDF02 -> {
                                val stringValue = tryParseAsString(value)
                                if (stringValue != null) {
                                    result["holderName"] = stringValue
                                    Log.d(TAG, "Found holder name: $stringValue")
                                }
                            }
                            0xDF03 -> {
                                result["df03"] = byteArrayToHex(value)
                            }
                            0xDF04 -> {
                                result["df04"] = byteArrayToHex(value)
                            }
                            0xDF05 -> {
                                val stringValue = tryParseAsString(value)
                                if (stringValue != null) {
                                    result["country"] = stringValue
                                }
                            }
                            0xDF06 -> {
                                result["df06"] = byteArrayToHex(value)
                                if (length == 3) {
                                    val dateStr = "${String.format("%02d", value[0].toInt() and 0xFF)}/${String.format("%02d", value[1].toInt() and 0xFF)}/${String.format("%02d", value[2].toInt() and 0xFF)}"
                                    result["date1"] = dateStr
                                }
                            }
                            0xDF07 -> {
                                result["df07"] = byteArrayToHex(value)
                            }
                            0xDF09 -> {
                                // Another potential date field
                                result["df09"] = byteArrayToHex(value)
                                if (length == 3) {
                                    val dateStr = "${String.format("%02d", value[0].toInt() and 0xFF)}/${String.format("%02d", value[1].toInt() and 0xFF)}/${String.format("%02d", value[2].toInt() and 0xFF)}"
                                    result["date2"] = dateStr
                                }
                            }
                            0xDF0A -> {
                                val stringValue = tryParseAsString(value)
                                if (stringValue != null) {
                                    result["cardId"] = stringValue
                                    Log.d(TAG, "Found card ID: $stringValue")
                                }
                            }
                            else -> {
                                result["tag_${tag.toString(16)}"] = byteArrayToHex(value)
                            }
                        }
                    }
                    i += length
                } else {
                    i++
                }
            }

            extractReadableStrings(record, result)

        } catch (e: Exception) {
            Log.w(TAG, "Error parsing custom record: ${e.message}")
        }

        return result
    }

    private fun tryParseAsString(bytes: ByteArray): String? {
        return try {
            // Try UTF-8 first
            val utf8String = String(bytes, StandardCharsets.UTF_8).trim()
            if (utf8String.all { it.isLetterOrDigit() || it.isWhitespace() || it in ".-_" }) {
                utf8String
            } else {
                // Try ASCII
                val asciiString = String(bytes, StandardCharsets.US_ASCII).trim()
                if (asciiString.all { it.code in 32..126 }) {
                    asciiString
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractReadableStrings(data: ByteArray, result: MutableMap<String, String>) {
        var stringBuilder = StringBuilder()
        var stringStart = -1

        for (i in data.indices) {
            val byte = data[i]
            if (isPrintableAscii(byte) && byte.toInt() != 0) {
                if (stringStart == -1) {
                    stringStart = i
                    stringBuilder = StringBuilder()
                }
                stringBuilder.append(byte.toInt().toChar())
            } else {
                if (stringStart != -1 && stringBuilder.length >= 3) {
                    val extractedString = stringBuilder.toString().trim()
                    if (extractedString.length >= 3) {
                        // Check if this looks like a name (contains letters and possibly spaces)
                        if (extractedString.any { it.isLetter() } &&
                            extractedString.all { it.isLetter() || it.isWhitespace() }) {
                            result["extracted_string_$stringStart"] = extractedString
                            Log.d(TAG, "Extracted string at position $stringStart: $extractedString")
                        }
                    }
                }
                stringStart = -1
            }
        }

        // Handle case where string goes to end of data
        if (stringStart != -1 && stringBuilder.length >= 3) {
            val extractedString = stringBuilder.toString().trim()
            if (extractedString.length >= 3) {
                result["extracted_string_$stringStart"] = extractedString
                Log.d(TAG, "Extracted string at position $stringStart: $extractedString")
            }
        }
    }

    private fun parseAdditionalEMVData(record: ByteArray, additionalInfo: MutableMap<String, String>) {
        // Application Identifier (AID) - Tag 4F
        findTLVData(record, 0x4F)?.let { aid ->
            additionalInfo["AID"] = byteArrayToHex(aid)
        }

        // Application Label - Tag 50
        findTLVData(record, 0x50)?.let { label ->
            additionalInfo["ApplicationLabel"] = String(label, StandardCharsets.UTF_8).trim()
        }

        // Track 2 Equivalent Data - Tag 57
        findTLVData(record, 0x57)?.let { track2 ->
            additionalInfo["Track2"] = byteArrayToHex(track2)
        }

        // Application Usage Control - Tag 9F07
        findTLVData(record, 0x9F, 0x07)?.let { auc ->
            additionalInfo["AppUsageControl"] = byteArrayToHex(auc)
        }

        // Application Version Number - Tag 9F08
        findTLVData(record, 0x9F, 0x08)?.let { version ->
            additionalInfo["AppVersion"] = byteArrayToHex(version)
        }
    }

    private fun findTLVData(data: ByteArray, vararg tags: Int): ByteArray? {
        var i = 0
        while (i < data.size - 1) {
            var currentTag = data[i].toInt() and 0xFF
            var tagSize = 1

            // Handle multi-byte tags
            if (tags.size > 1 && i < data.size - 2) {
                val nextByte = data[i + 1].toInt() and 0xFF
                if (currentTag == tags[0] && nextByte == tags[1]) {
                    tagSize = 2
                    i += 2
                } else {
                    i++
                    continue
                }
            } else if (currentTag == tags[0]) {
                i++
            } else {
                i++
                continue
            }

            if (i >= data.size) break

            // Get length
            var length = data[i].toInt() and 0xFF
            i++

            // Handle extended length
            if (length and 0x80 != 0) {
                val lengthBytes = length and 0x7F
                if (lengthBytes > 0 && i + lengthBytes <= data.size) {
                    length = 0
                    for (j in 0 until lengthBytes) {
                        length = (length shl 8) or (data[i + j].toInt() and 0xFF)
                    }
                    i += lengthBytes
                }
            }

            if (i + length <= data.size) {
                val value = ByteArray(length)
                System.arraycopy(data, i, value, 0, length)
                return value
            }

            i += length
        }
        return null
    }
}