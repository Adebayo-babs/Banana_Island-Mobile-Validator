package com.example.cardapp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

data class VerifyCardRequest(
    val cardId: String,
    val batchNumber: String? = null,
    val holderName: String? = null,
    val additionalData: Map<String, String>? = null
)

data class VerifyCardResponse(
    val success: Boolean,
    val message: String,
    val cardId: String? = null,
    val batchNumber: String? = null,
    val isVerified: Boolean = false,
    val alreadyScanned: Boolean = false
)


data class CardData(
    val cardId: String,
    val found: Boolean,
    val batchNumber: Int?,
    val batchName: String?,
    val cardHolder: CardHolder?,
    val status: String?,
    val deliveryStatus: String?
)

data class CardHolder(
    val surname: String,
    val firstname: String,
    val middlename: String,
    val contactLga: String,
    val stateOfResidence: String
)




data class VerifyQRRequest(
    val qrData: String
)

data class VerifyQRResponse(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any>? = null
)

data class CardVerificationRequest(
    val cardId: String
)

data class CardVerificationResponse(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any>? = null
)


// API Service singleton
object ApiService {
    private const val TAG = "ApiService"
    private const val BASE_URL = "http://10.65.10.127:3000"
    private const val VERIFY_CARD_ENDPOINT = "/api/token/verify/card"
    private const val VERIFY_QR_ENDPOINT = "/api/token/verify/qr"
    private const val TIMEOUT = 5000

    suspend fun verifyCard(cardId: String): CardVerificationResponse = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null

        try {
            Log.d(TAG, "=== VERIFY CARD REQUEST ===")
            Log.d(TAG, "Card ID: $cardId")
            Log.d(TAG, "URL: $BASE_URL$VERIFY_CARD_ENDPOINT")

            val url = URL("$BASE_URL$VERIFY_CARD_ENDPOINT")
            connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                doOutput = true
                doInput = true
            }

            // Create request body
            val jsonRequest = JSONObject().apply {
                put("lagId", cardId)
            }

            Log.d(TAG, "Request body: ${jsonRequest.toString()}")

            // Send Request
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonRequest.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")

            // Read response
            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val response = BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }

            Log.d(TAG, "Response gotten: $response")

            // Parse response
            val jsonResponse = JSONObject(response)

            val dataMap = mutableMapOf<String, Any>()
            jsonResponse.keys().forEach { key ->
                when (val value = jsonResponse.get(key)) {
                    is String, is Boolean, is Number -> dataMap[key] = value
                    is org.json.JSONArray -> dataMap[key] = value
                    else -> dataMap[key] = value.toString()
                }
            }

            CardVerificationResponse(
                success = responseCode in 200..299,
                message = jsonResponse.optString("message", "Unknown response"),
                data = dataMap
            )
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Connection timeout - Server not responding", e)
            CardVerificationResponse(
                success = false,
                message = "Connection timeout. Please check:\n1. Server is running at $BASE_URL\n2. Device is on same network\n3. Firewall allows connection"
            )
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Unknown host - Cannot reach server", e)
            CardVerificationResponse(
                success = false,
                message = "Cannot reach server at $BASE_URL\nPlease verify:\n1. Server IP address is correct\n2. Device has network connectivity"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying card: ${e.message}", e)
            CardVerificationResponse(
                success = false,
                message = "Network error: ${e.javaClass.simpleName}\n${e.message ?: "Unknown error"}"
            )
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun verifyQRCode(qrData: String): VerifyQRResponse = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null

        try {
            Log.d(TAG, "=== VERIFY QR REQUEST ===")
            Log.d(TAG, "QR Data: $qrData")
            Log.d(TAG, "URL: $BASE_URL$VERIFY_QR_ENDPOINT")

            val url = URL("$BASE_URL$VERIFY_QR_ENDPOINT")
            connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                doOutput = true
                doInput = true
            }

            // Create request body
            val jsonRequest = JSONObject().apply {
                put("token", qrData)
            }

            Log.d(TAG, "Request body: $jsonRequest")

            // Send Request
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonRequest.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "QR Response code: $responseCode")

            // Read response
            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val response = BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }

            Log.d(TAG, "QR Response: $response")

            // Parse response
            val jsonResponse = JSONObject(response)

            val dataMap = mutableMapOf<String, Any>()
            jsonResponse.keys().forEach { key ->
                when (val value = jsonResponse.get(key)) {
                    is String, is Boolean, is Number -> dataMap[key] = value
                    is org.json.JSONArray -> dataMap[key] = value
                    else -> dataMap[key] = value.toString()
                }
            }

            VerifyQRResponse(
                success = responseCode in 200..299 ,
                message = jsonResponse.optString("message", "Unknown response"),
                data = dataMap
            )
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "QR Connection timeout - Server not responding", e)
            VerifyQRResponse(
                success = false,
                message = "Connection timeout. Please check:\n1. Server is running at $BASE_URL\n2. Device is on same network\n3. Firewall allows connection"
            )
        } catch (e: UnknownHostException) {
            Log.e(TAG, "QR Unknown host - Cannot reach server", e)
            VerifyQRResponse(
                success = false,
                message = "Cannot reach server at $BASE_URL\nPlease verify:\n1. Server IP address is correct\n2. Device has network connectivity"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying QR code: ${e.message}", e)
            VerifyQRResponse(
                success = false,
                message = "Network error: ${e.javaClass.simpleName}\n${e.message ?: "Unknown error"}"
            )
        } finally {
            connection?.disconnect()
        }
    }
}