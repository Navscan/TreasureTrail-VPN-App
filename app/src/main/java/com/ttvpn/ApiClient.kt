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

    fun register(voucher: String): RegisterResult {
        val body = JSONObject().put("voucher", voucher).toString()
        val response = postJson(Config.ENDPOINT_REGISTER, body)
            ?: return RegisterResult.NetworkError

        return try {
            val json = JSONObject(response)
            if (json.optBoolean("ok", false)) {
                RegisterResult.Success(
                    config = json.getString("config"),
                    assignedIp = json.optString("assignedIp", ""),
                    subscriptionExpiresAt = json.optString("subscriptionExpiresAt", "")
                        .takeIf { it.isNotEmpty() && it != "null" }
                )
            } else {
                RegisterResult.Failure(json.optString("reason", "unknown"))
            }
        } catch (e: Exception) {
            RegisterResult.NetworkError
        }
    }

    fun health(): Boolean {
        return try {
            val url = URL(Config.ENDPOINT_HEALTH)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = Config.HTTP_CONNECT_TIMEOUT_MS
                readTimeout = Config.HTTP_READ_TIMEOUT_MS
            }
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (e: Exception) {
            false
        }
    }

    private fun postJson(endpoint: String, body: String): String? {
        return try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = Config.HTTP_CONNECT_TIMEOUT_MS
                readTimeout = Config.HTTP_READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            conn.disconnect()

            if (code in 200..299) response else null
        } catch (e: Exception) {
            null
        }
    }
}
