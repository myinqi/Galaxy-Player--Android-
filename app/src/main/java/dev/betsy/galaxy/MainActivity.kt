package dev.betsy.galaxy

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.betsy.galaxy.databinding.ActivityPlayerBinding
import dev.betsy.galaxy.web.GalaxyJsBridge

class MainActivity : AppCompatActivity() {
    private var web: WebView? = null
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as GalaxyApp
        if (!app.sessions.ready()) {
            startActivity(Intent(this, ConnectActivity::class.java))
            finish()
            return
        }
        val binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        keepContentClearOfSystemBars(binding.root)
        startService(Intent(this, dev.betsy.galaxy.player.GalaxyPlaybackService::class.java))
        val view = binding.playerWeb
        web = view
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        WebView.setWebContentsDebuggingEnabled(debuggable)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.mediaPlaybackRequiresUserGesture = false
        view.addJavascriptInterface(
            GalaxyJsBridge(app) {
                startActivity(Intent(this, ConnectActivity::class.java))
            },
            "GalaxyNative",
        )
        val assets = WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        view.webChromeClient = object : WebChromeClient() {
            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                showJsConfirm(message, result)
                return true
            }

            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                showJsConfirm(message, result, cancel = false)
                return true
            }
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                assets.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host?.lowercase().orEmpty()
                val outside = host == "suno.com" || host.endsWith(".suno.com") ||
                    host == "welovemusic.ai" || host.endsWith(".welovemusic.ai")
                if (!outside) return false
                startActivity(Intent(Intent.ACTION_VIEW, request.url))
                return true
            }
        }
        app.director.attach(view)
        view.loadUrl(PLAYER_URL)
        askToNotify()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        web?.loadUrl(PLAYER_URL)
    }

    override fun onDestroy() {
        web?.let { (application as GalaxyApp).director.detach(it) }
        super.onDestroy()
    }

    private fun showJsConfirm(message: String, result: JsResult, cancel: Boolean = true) {
        if (isFinishing) {
            result.cancel()
            return
        }
        var answered = false
        fun finish(ok: Boolean) {
            if (answered) return
            answered = true
            if (ok) result.confirm() else result.cancel()
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> finish(true) }
            .setOnCancelListener { finish(false) }
        if (cancel) {
            dialog.setNegativeButton(android.R.string.cancel) { _, _ -> finish(false) }
        }
        dialog.show()
    }

    private fun askToNotify() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val PLAYER_URL = "https://appassets.androidplatform.net/index.html"
    }
}
