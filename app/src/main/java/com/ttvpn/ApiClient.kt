package com.ttvpn

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

sealed class RegisterResult {
    data class Success(
        val config: String,
        val assignedIp: String,
        val subscriptionExpiresAt: String?
    ) : RegisterResult()
    data class Failure(val reason: String) : RegisterResult()
    object NetworkError : RegisterResult()
}

object ApiClient {

    fun redeemVoucher(token: String, code: String): RegisterResult {
        return try {
            val url = URL(Config.BASE_URL + "/api/voucher/redeem")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = Config.HTTP_CONNECT_TIMEOUT_MS
                readTimeout = Config.HTTP_READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
            }
            val body = JSONObject().put("code", code).toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val respCode = conn.responseCode
            val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            conn.disconnect()

            if (respCode in 200..299) {
                val json = JSONObject(response)
                if (json.optBoolean("ok", false)) {
                    RegisterResult.Success(
                        config = "",
                        assignedIp = "",
                        subscriptionExpiresAt = json.optString("expiry_date", null)
                    )
                } else {
                    RegisterResult.Failure(json.optString("error", "unknown"))
                }
            } else {
                val json = JSONObject(response)
                RegisterResult.Failure(json.optString("error", "http_$respCode"))
            }
        } catch (e: Exception) {
            RegisterResult.NetworkError
        }
    }

    fun provisionConfig(token: String): RegisterResult {
        return try {
            val url = URL(Config.ENDPOINT_PROVISION)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = Config.HTTP_CONNECT_TIMEOUT_MS
                readTimeout = Config.HTTP_READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $token")
            }
            conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            conn.disconnect()

            if (code in 200..299) {
                val json = JSONObject(response)
                if (json.optBoolean("ok", false)) {
                    RegisterResult.Success(
                        config = json.getString("config"),
                        assignedIp = json.optString("assignedIp", ""),
                        subscriptionExpiresAt = null
                    )
                } else {
                    RegisterResult.Failure(json.optString("error", "unknown backend error"))
                }
            } else {
                RegisterResult.Failure("HTTP $code: $response")
            }
        } catch (e: Exception) {
            RegisterResult.Failure(e.message ?: "Unknown network exception")
        }
    }

    fun initiatePayment(token: String, plan: String): String? {
        return try {
            val url = URL(Config.ENDPOINT_PAY_INITIATE)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = Config.HTTP_CONNECT_TIMEOUT_MS
                readTimeout = Config.HTTP_READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $token")
            }
            val body = JSONObject().put("plan", plan).toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            conn.disconnect()

            if (code in 200..299) {
                val json = JSONObject(response)
                if (json.optBoolean("ok", false)) {
                    json.getString("checkout_url")
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
