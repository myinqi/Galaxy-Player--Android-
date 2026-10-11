package dev.betsy.galaxy.net

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SunoClip {
    private val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun load(songId: String): JSONObject {
        val id = songId.lowercase()
        if (!uuid.matches(id)) throw IllegalStateException("The song video could not be loaded.")
        val request = Request.Builder()
            .url("https://studio-api.prod.suno.com/api/clip/$id")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept", "application/json")
            .header("Origin", "https://suno.com")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("The song video could not be loaded.")
            val text = response.body?.string().orEmpty()
            if (!text.trimStart().startsWith("{")) throw IllegalStateException("The song video could not be loaded.")
            return JSONObject(text)
        }
    }
}
