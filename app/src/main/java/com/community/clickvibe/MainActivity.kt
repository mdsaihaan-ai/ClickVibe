package com.community.clickvibe

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessaging
import android.net.Uri
import android.os.Bundle
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.FullScreenContentCallback
import androidx.activity.OnBackPressedCallback
import android.graphics.Color
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
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
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false
    private var lastInterstitialShownAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                android.util.Log.d("ClickVibeFCM", "FCM token retrieved successfully")
            } else {
                android.util.Log.e("ClickVibeFCM", "Failed to retrieve FCM token", task.exception)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
        Thread {
            MobileAds.initialize(this@MainActivity) {
                runOnUiThread {
                    loadInterstitialAd()
                }
            }
        }.start()
        window.decorView.setBackgroundColor(Color.rgb(9, 11, 15))

        handleCallbackIntent(intent)

        setContent {
            ClickVibeTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier
                            .weight(1f)
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
                                        this@MainActivity.fileUploadCallback
                                            ?.onReceiveValue(null)

                                        this@MainActivity.fileUploadCallback =
                                            filePathCallback

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

                                loadUrl("file:///android_asset/index.html")
                            }
                        }
                    )

                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        factory = { context ->
                            AdView(context).apply {
                                setAdSize(AdSize.BANNER)
                                adUnitId = "ca-app-pub-5803009368928238/9554102488"
                                loadAd(AdRequest.Builder().build())
                            }
                        }
                    )
                }
            }
        }
        setupBackButton()
    }

    private fun loadInterstitialAd() {
        if (isInterstitialLoading || interstitialAd != null) return


        isInterstitialLoading = true

        InterstitialAd.load(
            this,
            "ca-app-pub-5803009368928238/5509869516",
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                }
            }
        )


    }

    private fun maybeShowInterstitial() {
        val now = System.currentTimeMillis()

        if (interstitialAd == null) return
        if (now - lastInterstitialShownAt < 3 * 60 * 1000L) return

        val ad = interstitialAd ?: return
        interstitialAd = null
        lastInterstitialShownAt = now

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadInterstitialAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                loadInterstitialAd()
            }
        }

        ad.show(this)

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
        fun requestInterstitial() {
            runOnUiThread {
                maybeShowInterstitial()
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

