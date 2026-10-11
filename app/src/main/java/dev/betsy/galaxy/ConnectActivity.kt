package dev.betsy.galaxy

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import dev.betsy.galaxy.databinding.ActivityConnectBinding
import dev.betsy.galaxy.logic.ApiAddress
import dev.betsy.galaxy.logic.RemoteFailure
import dev.betsy.galaxy.logic.friendlyApiError
import java.io.IOException

class ConnectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConnectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        keepContentClearOfSystemBars(binding.root)
        val app = application as GalaxyApp
        binding.serverUrl.setText(app.sessions.current.baseUrl)
        binding.appToken.setText(app.sessions.current.token)
        binding.testConnection.setOnClickListener { test() }
        binding.saveConnection.setOnClickListener { saveAndOpen() }
    }

    private fun test() {
        val session = readSession() ?: return
        setBusy(true)
        val app = application as GalaxyApp
        Thread {
            val previous = app.sessions.current
            app.sessions.save(session.first, session.second)
            val message = try {
                val payload = app.api.get("/sources")
                val channels = payload.optJSONObject("showcase")?.optJSONArray("channels")?.length() ?: 0
                val wlm = payload.optJSONObject("wlm")?.optBoolean("available") == true
                "Connection works. Showcase channels: $channels. We Love Music: ${if (wlm) "yes" else "no"}."
            } catch (error: RemoteFailure) {
                app.sessions.save(previous.baseUrl, previous.token)
                friendlyApiError(error.code)
            } catch (error: IllegalStateException) {
                app.sessions.save(previous.baseUrl, previous.token)
                error.message ?: "Betsy is unreachable."
            } catch (error: IOException) {
                app.sessions.save(previous.baseUrl, previous.token)
                "Betsy is unreachable."
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                binding.connectionStatus.text = message
                setBusy(false)
            }
        }.start()
    }

    private fun saveAndOpen() {
        val session = readSession() ?: return
        val app = application as GalaxyApp
        app.sessions.save(session.first, session.second)
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    private fun readSession(): Pair<String, String>? {
        val token = binding.appToken.text?.toString()?.trim().orEmpty()
        if (token.isEmpty()) {
            binding.connectionStatus.text = "Paste the app token from Galaxy admin."
            return null
        }
        val url = try {
            ApiAddress.normalize(binding.serverUrl.text?.toString().orEmpty())
        } catch (error: IllegalArgumentException) {
            binding.connectionStatus.text = error.message
            return null
        }
        binding.serverUrl.setText(url)
        return url to token
    }

    private fun setBusy(busy: Boolean) {
        binding.testConnection.isEnabled = !busy
        binding.saveConnection.isEnabled = !busy
        binding.connectionProgress.visibility = if (busy) View.VISIBLE else View.GONE
    }
}
