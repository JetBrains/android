package com.example.targetsdkupgradesample32_33.webview

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
//import androidx.webkit.WebSettingsCompat
//import androidx.webkit.WebViewFeature
import com.example.targetsdkupgradesample32_33.R

class WebviewContentActivity : AppCompatActivity() {

    /*
    lateinit var webView : WebView
    lateinit var default : Button
    lateinit var dark : Button
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview_content)
        webView  = findViewById(R.id.webView)
        default  = findViewById(R.id.button)
        dark  = findViewById(R.id.button2)

        webView.loadUrl("https://developer.android.com/");

        webView.settings.javaScriptEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }
        }

        default.setOnClickListener({
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(webView.settings, WebSettingsCompat.FORCE_DARK_OFF)
            }
        })

        dark.setOnClickListener({
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(webView.settings, WebSettingsCompat.FORCE_DARK_ON)
            }
        })
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

     */
}