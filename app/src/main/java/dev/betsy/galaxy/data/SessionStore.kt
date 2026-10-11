package dev.betsy.galaxy.data

import org.json.JSONObject
import java.io.File

data class Session(val baseUrl: String, val token: String)

class SessionStore(private val file: File) {
    @Volatile var current: Session = Session("", "")
        private set

    init {
        load()
    }

    fun ready(): Boolean = current.baseUrl.isNotBlank() && current.token.isNotBlank()

    @Synchronized
    fun save(baseUrl: String, token: String) {
        current = Session(baseUrl.trim(), token.trim())
        file.parentFile?.mkdirs()
        file.writeText(JSONObject().put("baseUrl", current.baseUrl).put("token", current.token).toString())
    }

    @Synchronized
    fun clear() {
        current = Session("", "")
        if (file.exists()) file.delete()
    }

    private fun load() {
        if (!file.exists()) return
        val root = try {
            JSONObject(file.readText())
        } catch (error: Exception) {
            return
        }
        current = Session(root.optString("baseUrl"), root.optString("token"))
    }
}
