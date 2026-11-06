package com.nobcdz.tvquiz

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.KeyEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

class WebShell(private val context: Context) {
    @SuppressLint("SetJavaScriptEnabled")
    fun create(url: String): WebView {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        return WebView(context).apply {
            setBackgroundColor(0xFF08080A.toInt())
            isFocusableInTouchMode = true
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                cacheMode = WebSettings.LOAD_NO_CACHE
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                setSupportZoom(false)
            }
            webViewClient = LocalContent()
            loadUrl(url)
        }
    }

    fun dispatchKey(web: WebView, keyCode: Int, event: KeyEvent?): Boolean {
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> "left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            KeyEvent.KEYCODE_DPAD_UP -> "up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "down"
            else -> null
        }
        val command = if (direction != null) "move('$direction')" else when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "select()"
            KeyEvent.KEYCODE_BACK -> if ((event?.repeatCount ?: 0) <= 2) "back()" else return false
            else -> return false
        }
        web.evaluateJavascript("if(window.TV) TV.$command", null)
        return true
    }

    private class LocalContent : WebViewClient() {
        @Deprecated("Required on old WebView versions")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = !isAllowed(url)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !isAllowed(request.url.toString())

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val target = request.url
            if (target.scheme == "data" || isAllowed(target.toString())) return null
            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
        }
    }

    companion object {
        fun isAllowed(url: String?): Boolean {
            if (url == "about:blank") return true
            val target = url?.let(Uri::parse) ?: return false
            val origin = PrefsHolder.baseUrl?.let(Uri::parse) ?: return false
            return target.scheme == origin.scheme && target.host == origin.host &&
                target.port == origin.port && target.userInfo == null
        }
    }

    object PrefsHolder {
        @Volatile var baseUrl: String? = null
    }
}
