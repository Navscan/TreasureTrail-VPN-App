package com.ttvpn

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

data class BannedIp(val ip: String, val jail: String)

data class CerberusStatus(
    val uptimeSeconds: Long,
    val memPercent: Int,
    val memUsedBytes: Long,
    val memTotalBytes: Long,
    val diskPercent: Int,
    val diskUsedBytes: Long,
    val diskTotalBytes: Long,
    val jails: List<String>,
    val bannedTotal: Int,
    val recentBanned: List<BannedIp>,
    val failedServices: List<String>
)

sealed class CerberusResult {
    data class Success(val status: CerberusStatus) : CerberusResult()
    data class Failure(val reason: String) : CerberusResult()
    object NetworkError : CerberusResult()
}

object CerberusClient {

    fun fetch(): CerberusResult {
        return try {
            val url = URL(Config.ENDPOINT_CERBERUS)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = Config.HTTP_CONNECT_TIMEOUT_MS
                readTimeout = Config.HTTP_READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            conn.disconnect()

            if (code !in 200..299) return CerberusResult.Failure("HTTP $code")

            val json = JSONObject(body)
            if (!json.optBoolean("ok", false)) {
                return CerberusResult.Failure(json.optString("error", "unknown"))
            }

            val sys = json.getJSONObject("system")
            val mem = sys.getJSONObject("mem")
            val disk = sys.getJSONObject("disk")
            val f2b = json.getJSONObject("fail2ban")

            val jailsArr = f2b.optJSONArray("jails")
            val jails = mutableListOf<String>()
            if (jailsArr != null) {
                for (i in 0 until jailsArr.length()) jails.add(jailsArr.getString(i))
            }

            val bannedArr = f2b.optJSONArray("recentBanned")
            val banned = mutableListOf<BannedIp>()
            if (bannedArr != null) {
                for (i in 0 until bannedArr.length()) {
                    val o = bannedArr.getJSONObject(i)
                    banned.add(BannedIp(o.optString("ip", ""), o.optString("jail", "")))
                }
            }

            val failedArr = json.optJSONArray("failedServices")
            val failed = mutableListOf<String>()
            if (failedArr != null) {
                for (i in 0 until failedArr.length()) failed.add(failedArr.getString(i))
            }

            CerberusResult.Success(
                CerberusStatus(
                    uptimeSeconds = sys.optLong("uptimeSeconds", 0L),
                    memPercent = mem.optInt("percent", 0),
                    memUsedBytes = mem.optLong("used", 0L),
                    memTotalBytes = mem.optLong("total", 0L),
                    diskPercent = disk.optInt("percent", 0),
                    diskUsedBytes = disk.optLong("used", 0L),
                    diskTotalBytes = disk.optLong("total", 0L),
                    jails = jails,
                    bannedTotal = f2b.optInt("bannedTotal", 0),
                    recentBanned = banned,
                    failedServices = failed
                )
            )
        } catch (e: Exception) {
            CerberusResult.NetworkError
        }
    }
}
