package com.pubgdnspro.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.StringReader
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var backend: Backend
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequestCode = 4107
    private val vpnRequestCode = 4108
    private val executor = Executors.newSingleThreadExecutor()
    private var pendingConfig: Config? = null
    private val tunnel = object : Tunnel {
        override fun getName(): String = "PUBG-DNS-PRO"
        override fun onStateChange(newState: Tunnel.State) {
            runOnUiThread { notifyJs("onWireGuardState", "${newState.name}") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backend = GoBackend(this)
        webView = WebView(this)
        @Suppress("SetJavaScriptEnabled")
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = true
        webView.addJavascriptInterface(NativeBridge(), "AndroidVPN")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/octet-stream", "application/x-wireguard-profile"))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                return try { startActivityForResult(intent, fileChooserRequestCode); true }
                catch (_: Exception) { fileChooserCallback?.onReceiveValue(null); fileChooserCallback = null; false }
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return if (uri.scheme == "https" && uri.host != "appassets.androidplatform.net") {
                    startActivity(Intent(Intent.ACTION_VIEW, uri)); true
                } else false
            }
        }
        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    private inner class NativeBridge {
        @JavascriptInterface fun connectWireGuard(rawConfig: String) {
            runOnUiThread {
                try {
                    // Parse in memory only. No private key is logged or persisted by this app.
                    pendingConfig = Config.parse(StringReader(rawConfig))
                    val consent = GoBackend.VpnService.prepare(this@MainActivity)
                    if (consent != null) startActivityForResult(consent, vpnRequestCode) else startTunnel()
                } catch (e: Exception) {
                    notifyJs("onWireGuardError", e.message ?: "کانفیگ WireGuard معتبر نیست")
                }
            }
        }
        @JavascriptInterface fun disconnectWireGuard() {
            executor.execute {
                try { backend.setState(tunnel, Tunnel.State.DOWN, null); runOnUiThread { notifyJs("onWireGuardState", "DOWN") } }
                catch (e: Exception) { runOnUiThread { notifyJs("onWireGuardError", e.message ?: "قطع اتصال ناموفق بود") } }
            }
        }
        @JavascriptInterface fun getWireGuardState() {
            executor.execute {
                val state = try { backend.getState(tunnel).name } catch (_: Exception) { "DOWN" }
                runOnUiThread { notifyJs("onWireGuardState", state) }
            }
        }
    }

    private fun startTunnel() {
        val config = pendingConfig ?: return
        pendingConfig = null
        notifyJs("onWireGuardState", "CONNECTING")
        executor.execute {
            try {
                backend.setState(tunnel, Tunnel.State.UP, config)
                runOnUiThread { notifyJs("onWireGuardState", "UP") }
            } catch (e: Exception) {
                runOnUiThread { notifyJs("onWireGuardError", e.message ?: "اتصال برقرار نشد؛ سرور و کانفیگ را بررسی کن") }
            }
        }
    }

    private fun notifyJs(function: String, value: String) {
        if (!::webView.isInitialized) return
        val safe = org.json.JSONObject.quote(value)
        webView.post { webView.evaluateJavascript("window.$function && window.$function($safe);", null) }
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            fileChooserRequestCode -> {
                val result = if (resultCode == RESULT_OK) data?.data?.let { arrayOf(it) } else null
                fileChooserCallback?.onReceiveValue(result); fileChooserCallback = null
            }
            vpnRequestCode -> if (resultCode == RESULT_OK) startTunnel() else {
                pendingConfig = null
                notifyJs("onWireGuardError", "مجوز VPN داده نشد")
            }
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null); fileChooserCallback = null
        executor.shutdownNow()
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}
