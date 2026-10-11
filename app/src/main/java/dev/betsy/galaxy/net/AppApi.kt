package dev.betsy.galaxy.net

import dev.betsy.galaxy.data.Session
import dev.betsy.galaxy.logic.RemoteFailure
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AppApi(
    private val session: () -> Session,
) {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val token = session().token
            val base = session().baseUrl.toHttpUrlOrNull()
            val url = request.url
            val sameApi = token.isNotBlank() && (
                (base != null && url.host == base.host && url.encodedPath.startsWith(base.encodedPath)) ||
                    url.encodedPath.contains("/galaxy/api/app/")
                )
            val next = if (sameApi) {
                request.newBuilder().header("Authorization", "Bearer $token").build()
            } else {
                request
            }
            chain.proceed(next)
        }
        .build()

    fun get(pathAndQuery: String): JSONObject {
        val base = session().baseUrl.trimEnd('/')
        if (base.isBlank() || session().token.isBlank()) {
            throw RemoteFailure(401, "unauthorized")
        }
        val request = Request.Builder()
            .url(base + pathAndQuery)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer ${session().token}")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = if (text.trimStart().startsWith("{")) JSONObject(text) else JSONObject()
                if (!response.isSuccessful) {
                    throw RemoteFailure(response.code, json.optString("error").ifBlank { "http_${response.code}" })
                }
                return json
            }
        } catch (error: RemoteFailure) {
            throw error
        } catch (error: IOException) {
            throw IllegalStateException("Betsy is unreachable.")
        }
    }
}
