package com.example.cad.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.parser.DxfModel

class CadJsInterface(
    private val contentProvider: () -> String,
    private val onRendered: (Int, Int) -> Unit = { _, _ -> },
    private val onError: (String) -> Unit = {}
) {
    @JavascriptInterface
    fun getDxfContent(): String {
        return contentProvider()
    }

    @JavascriptInterface
    fun onDrawingRendered(entityCount: Int, layerCount: Int) {
        onRendered(entityCount, layerCount)
    }

    @JavascriptInterface
    fun onRenderError(errorMsg: String) {
        onError(errorMsg)
    }
}

/**
 * High-performance Android WebView component embedding Three.js & dxf-parser.
 * Delivers 100% distortion-free WebGL CAD rendering directly inside the app.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CadWebView(
    model: DxfModel,
    modifier: Modifier = Modifier,
    onModelRendered: ((entityCount: Int, layerCount: Int) -> Unit)? = null
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPageLoaded by remember { mutableStateOf(false) }

    val rawContent = remember(model) {
        model.getRawOrGeneratedDxf()
    }

    val jsInterface = remember(rawContent) {
        CadJsInterface(
            contentProvider = { rawContent },
            onRendered = { entities, layers ->
                onModelRendered?.invoke(entities, layers)
            }
        )
    }

    fun passDxfToWebView(webView: WebView, dxfContent: String) {
        if (dxfContent.isBlank()) return
        val data = dxfContent.toByteArray(Charsets.UTF_8)
        val base64Dxf = Base64.encodeToString(data, Base64.NO_WRAP)
        webView.post {
            webView.evaluateJavascript("javascript:loadDxfFromBase64('$base64Dxf');", null)
        }
    }

    // Whenever model changes and page is loaded, evaluate JavaScript with Base64 encoding
    LaunchedEffect(model, isPageLoaded) {
        val webView = webViewRef
        if (webView != null && isPageLoaded) {
            passDxfToWebView(webView, rawContent)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0B1120))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(Color.parseColor("#0B1120"))
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        @Suppress("DEPRECATION")
                        allowFileAccessFromFileURLs = true
                        @Suppress("DEPRECATION")
                        allowUniversalAccessFromFileURLs = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = false
                        displayZoomControls = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    addJavascriptInterface(jsInterface, "AndroidCadBridge")

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isPageLoaded = true
                            if (view != null && rawContent.isNotBlank()) {
                                passDxfToWebView(view, rawContent)
                            }
                        }
                    }

                    // Load local HTML from assets folder
                    loadUrl("file:///android_asset/index.html")
                    webViewRef = this
                }
            },
            update = { webView ->
                webViewRef = webView
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }
}
