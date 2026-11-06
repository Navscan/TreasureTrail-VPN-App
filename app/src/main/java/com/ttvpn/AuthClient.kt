package com.ttvpn

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

sealed class AuthResult {
    data class Success(val token: String, val tier: String) : AuthResult()
    data class Failure(val error: String) : AuthResult()
    object NetworkError : AuthResult()
}

data class LicenseResult(val isActive: Boolean, val tier: String)

object AuthClient {

    fun login(email: String, password: String): AuthResult {
        val body = JSONObject().put("email", email).put("password", password).toString()
        return postAuth(Config.ENDPOINT_AUTH_LOGIN, body)
    }

    fun register(email: String, password: String): AuthResult {
        val body = JSONObject().put("email", email).put("password", password).toString()
        return postAuth(Config.ENDPOINT_AUTH_REGISTER, body)
    }

    fun guestLogin(deviceId: String): AuthResult {
        val body = JSONObject().put("device_id", deviceId).toString()
        return postAuth(Config.ENDPOINT_AUTH_GUEST, body)
    }

    fun updateEmail(token: String, newEmail: String): AuthResult {
        return try {
            val url = URL(Config.BASE_URL + "/api/auth/update-email")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                connectTimeout = Config.HTTP_CONNECT_TIMEOUT_MS
                readTimeout = Config.HTTP_READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
            }
            val body = JSONObject().put("new_email", newEmail).toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            conn.disconnect()

            val json = JSONObject(response)
            if (code in 200..299 && json.optBoolean("ok", false)) {
                AuthResult.Success(json.getString("token"), "free")
            } else {
                AuthResult.Failure(json.optString("error", "unknown_error"))
            }
        } catch (e: Exception) {
            AuthResult.NetworkError
        }
    }

    fun checkLicense(token: String): LicenseResult {
        return try {
            val url = URL(Config.ENDPOINT_AUTH_ME)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                connectTimeout = Config.HTTP_CONNECT_TIMEOUT_MS
                readTimeout = Config.HTTP_READ_TIMEOUT_MS
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            conn.disconnect()

            if (code == 200) {
                val json = JSONObject(response)
                LicenseResult(
                    isActive = json.optBoolean("ok", false) && json.optBoolean("is_active", false),
                    tier = json.optString("tier", "free")
                )
            } else {
                LicenseResult(false, "free")
            }
        } catch (e: Exception) {
            LicenseResult(false, "free")
        }
    }

    private fun postAuth(endpoint: String, body: String): AuthResult {
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

            val json = JSONObject(response)
            if (code in 200..299 && json.optBoolean("ok", false)) {
                AuthResult.Success(json.getString("token"), json.optString("tier", "free"))
            } else {
                AuthResult.Failure(json.optString("error", "unknown_error"))
            }
        } catch (e: Exception) {
            AuthResult.NetworkError
        }
    }
}
