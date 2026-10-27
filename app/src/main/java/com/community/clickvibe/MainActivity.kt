package com.community.clickvibe

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import android.graphics.Color
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.community.clickvibe.ui.theme.ClickVibeTheme
import android.webkit.WebChromeClient
import android.webkit.ValueCallback
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private val filePickerLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val selectedFile =
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { arrayOf(it) }
                } else {
                    null
                }

            fileUploadCallback?.onReceiveValue(selectedFile)
            fileUploadCallback = null
        }

    private var webView: WebView? = null
    private var pendingOAuthCallback: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(Color.rgb(9, 11, 15))

        handleCallbackIntent(intent)

        setContent {
            ClickVibeTheme {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = WindowInsets.statusBars
                                .asPaddingValues()
                                .calculateTopPadding()
                        ),
                    factory = { context ->
                        WebView(context).apply {
                            setBackgroundColor(Color.rgb(9, 11, 15))
                            visibility = View.INVISIBLE
                            webView = this

                            webChromeClient = object : WebChromeClient() {
                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    this@MainActivity.fileUploadCallback?.onReceiveValue(null)
                                    this@MainActivity.fileUploadCallback = filePathCallback


                                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        type = "image/*"
                                    }

                                    filePickerLauncher.launch(intent)
                                    return true
                                }


                            }


                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true

                            addJavascriptInterface(
                                ClickVibeBridge(),
                                "ClickVibeAndroid"
                            )

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(
                                    view: WebView,
                                    url: String
                                ) {
                                    super.onPageFinished(view, url)
                                    deliverPendingCallback()
                                }
                            }

                            loadUrl(
                                "file:///android_asset/index.html"
                            )
                        }
                    }
                )
            }
        }
        setupBackButton()
    }

    private fun setupBackButton() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val view = webView

                    if (view == null) {
                        finish()
                        return
                    }

                    view.evaluateJavascript(
                        "typeof window.handleAndroidBack === 'function' ? window.handleAndroidBack() : false"
                    ) { handled ->
                        if (handled != "true") {
                            finish()
                        }
                    }
                }
            }
        )
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallbackIntent(intent)
    }

    private fun handleCallbackIntent(intent: Intent?) {
        val uri = intent?.data ?: return

        if (
            uri.scheme == "clickvibe" &&
            uri.host == "login-callback"
        ) {
            pendingOAuthCallback = uri.toString()
            deliverPendingCallback()
        }
    }

    private fun deliverPendingCallback() {
        val callback = pendingOAuthCallback ?: return
        val view = webView ?: return

        pendingOAuthCallback = null

        val safeCallback = org.json.JSONObject.quote(callback)

        view.post {
            view.evaluateJavascript(
                """
                if (typeof window.handleOAuthCallback === "function") {
                    window.handleOAuthCallback($safeCallback);
                } else {
                    console.error(
                        "ClickVibe: OAuth callback handler is unavailable."
                    );
                }
                """.trimIndent(),
                null
            )
        }
    }

    inner class ClickVibeBridge {
        @JavascriptInterface
        fun showWebView() {
            runOnUiThread {
                webView?.visibility = View.VISIBLE
            }
        }

        @JavascriptInterface
        fun openOAuth(url: String) {
            runOnUiThread {
                try {
                    val uri = Uri.parse(url)

                    if (
                        uri.scheme != "https" ||
                        uri.host !=
                        "xhbvwmmteyrueoewifqi.supabase.co"
                    ) {
                        return@runOnUiThread
                    }

                    startActivity(
                        Intent(Intent.ACTION_VIEW, uri)
                    )
                } catch (error: Exception) {
                    android.util.Log.e(
                        "ClickVibe",
                        "Unable to open Google sign-in",
                        error
                    )
                }
            }
        }
    }
}

