package com.adloop.app

import android.content.Context
import androidx.core.content.edit
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class Campaign(
    val id: String,
    val source: String,
    val sourceLabel: String,
    val brand: String,
    val headline: String,
    val description: String,
    val artLine: String,
    val mark: String,
    val durationSeconds: Int,
    val feeCredits: Int,
    val rewardCredits: Int,
    val currency: String,
    val top: Color,
    val bottom: Color,
    val accent: Color,
) {
    companion object {
        fun from(json: JSONObject) = Campaign(
            id = json.getString("id"),
            source = json.getString("source"),
            sourceLabel = json.getString("sourceLabel"),
            brand = json.getString("brand"),
            headline = json.getString("headline"),
            description = json.getString("description"),
            artLine = json.getString("artLine"),
            mark = json.getString("mark"),
            durationSeconds = json.getInt("durationSeconds"),
            feeCredits = json.getInt("feeCredits"),
            rewardCredits = json.getInt("rewardCredits"),
            currency = json.getString("currency"),
            top = Color(AndroidColor.parseColor(json.getString("topColor"))),
            bottom = Color(AndroidColor.parseColor(json.getString("bottomColor"))),
            accent = Color(AndroidColor.parseColor(json.getString("accentColor"))),
        )
    }
}

data class Earning(val name: String, val source: String, val amount: Int)
data class Wallet(val balance: Int, val history: List<Earning>)

class AdLoopApi(context: Context) {
    private val preferences = context.getSharedPreferences("adloop_demo", Context.MODE_PRIVATE)
    private val userId = preferences.getString("user_id", null) ?: UUID.randomUUID().toString().replace("-", "").also {
        preferences.edit { putString("user_id", it) }
    }
    private val baseUrl = "http://10.0.2.2:8787"

    suspend fun register(): JSONObject = request("POST", "/api/users", JSONObject().put("userId", userId))

    suspend fun feed(): List<Campaign> = request("GET", "/api/feed?userId=$userId")
        .getJSONArray("campaigns")
        .let { rows -> (0 until rows.length()).map { Campaign.from(rows.getJSONObject(it)) } }

    suspend fun wallet(): Wallet {
        val result = request("GET", "/api/wallet?userId=$userId")
        val history = result.getJSONArray("history")
        return Wallet(result.getInt("balance"), (0 until history.length()).map { index ->
            val row = history.getJSONObject(index)
            Earning(
                name = row.getString("sponsor"),
                source = if (row.getString("source_type") == "provider") "Provider · ${row.getString("provider_name")}" else "Direct sponsor",
                amount = row.getInt("amount_credits"),
            )
        })
    }

    suspend fun startView(campaignId: String): String = request(
        "POST", "/api/views/start", JSONObject().put("userId", userId).put("campaignId", campaignId),
    ).getString("sessionId")

    suspend fun completeView(sessionId: String): Int = request(
        "POST", "/api/views/complete", JSONObject().put("userId", userId).put("sessionId", sessionId),
    ).getInt("credited")

    suspend fun cancelView(sessionId: String) {
        request("POST", "/api/views/cancel", JSONObject().put("userId", userId).put("sessionId", sessionId))
    }

    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val result = stream?.bufferedReader()?.use { JSONObject(it.readText()) } ?: JSONObject()
            if (status !in 200..299) throw IOException(result.optString("error", "Request failed ($status)"))
            result
        } finally {
            connection.disconnect()
        }
    }
}
