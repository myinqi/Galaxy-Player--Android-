package dev.betsy.galaxy.web

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import dev.betsy.galaxy.GalaxyApp
import org.json.JSONObject

class GalaxyJsBridge(
    private val app: GalaxyApp,
    private val openSettings: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun request(method: String, path: String, body: String): String {
        return try {
            if (!app.sessions.ready()) {
                JSONObject()
                    .put("__status", 401)
                    .put("message", "Save the Betsy app token first.")
                    .put("description", "Save the Betsy app token first.")
                    .toString()
            } else {
                app.backend.request(method, path, body)
            }
        } catch (error: Exception) {
            JSONObject()
                .put("__status", 502)
                .put("message", error.message ?: "Betsy is unreachable.")
                .put("description", error.message ?: "Betsy is unreachable.")
                .toString()
        }
    }

    @JavascriptInterface
    fun command(json: String): String = app.director.command(json)

    @JavascriptInterface
    fun snapshot(): String = app.director.snapshot()

    @JavascriptInterface
    fun openSettings() {
        main.post { openSettings() }
    }
}
