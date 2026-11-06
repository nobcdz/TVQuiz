package com.nobcdz.tvquiz

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

class GateOverlay(
    private val context: Context,
    private val windowType: Int,
    private val goHome: (() -> Boolean)? = null
) {
    private val main = Handler(Looper.getMainLooper())
    private val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val shell = WebShell(context)
    @Volatile private var session: Session? = null
    val isShowing: Boolean get() = session?.attached == true

    fun show(reason: String): Boolean {
        val active = session ?: Session(shell.create("about:blank")).also { session = it }
        if (active.attached) return true
        val params = WindowManager.LayoutParams(
            -1, -1, windowType, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "TVQuiz viewing gate"
        }
        return try {
            windows.addView(active.view, params)
            active.attached = true
            active.load(reason)
            active.view.requestFocus()
            true
        } catch (_: RuntimeException) {
            hide()
            false
        }
    }

    fun suspend() {
        val active = session ?: return
        if (!active.attached) return
        active.attached = false
        active.upPresses = 0
        runCatching { windows.removeViewImmediate(active.view) }
    }

    fun hide() {
        val active = session ?: return
        suspend()
        session = null
        active.view.removeJavascriptInterface(BRIDGE_NAME)
        active.view.stopLoading()
        active.view.destroy()
    }

    private inner class Session(val view: WebView) {
        @Volatile var attached = false
        var upPresses = 0
        private var lastUpAt = 0L
        private var page: String? = null

        init {
            view.addJavascriptInterface(Bridge(this), BRIDGE_NAME)
            view.setOnKeyListener { _, key, event -> handleKey(key, event) }
        }

        fun load(reason: String) {
            val route = when (reason) {
                "mode" -> "mode"
                "timeup" -> "quiz"
                else -> "lock?reason=" + android.net.Uri.encode(reason)
            }
            if (page == route) return
            page = route
            view.loadUrl(Prefs.localBase(context) + "#/" + route)
        }

        private fun handleKey(key: Int, event: KeyEvent): Boolean {
            if (key == KeyEvent.KEYCODE_BACK) return true
            if (event.action != KeyEvent.ACTION_DOWN) return false
            if (event.repeatCount == 0) {
                if (key != KeyEvent.KEYCODE_DPAD_UP) {
                    upPresses = 0
                } else {
                    val now = SystemClock.elapsedRealtime()
                    upPresses = if (now - lastUpAt < 900) upPresses + 1 else 1
                    lastUpAt = now
                    if (upPresses == 5) {
                        upPresses = 0
                        post { openParent(false) }
                        return true
                    }
                }
            }
            return shell.dispatchKey(view, key, event)
        }

        fun isCurrent(): Boolean = session === this && attached

        fun post(action: () -> Unit) {
            main.post { if (isCurrent()) action() }
        }
    }

    private fun openParent(selectingMode: Boolean) {
        if (selectingMode && ViewingMode.current(context) != "choose") return
        GateService.hideGates()
        if (!selectingMode) hide()
        val intent = Intent(context, ParentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (selectingMode) intent.putExtra("enter_parent_mode", true)
        try {
            context.startActivity(intent)
        } catch (error: RuntimeException) {
            android.util.Log.w("TVQuizGate", "Cannot open parent controls", error)
        }
    }

    private inner class Bridge(private val owner: Session) {
        @JavascriptInterface fun viewingMode(): String = ViewingMode.current(context)
        @JavascriptInterface fun childProfiles(): String =
            if (owner.isCurrent()) DeviceRuntime.household.profiles().toString() else "[]"
        @JavascriptInterface fun pairingAddress(): String = DeviceRuntime.pairingAddress(context).toString()
        @JavascriptInterface fun apiToken(): String = if (owner.isCurrent()) AdminAuth.deviceToken else ""
        @JavascriptInterface fun deviceInfo(): String =
            JSONObject().put("platform", if (DeviceBridge.isTv) "android-tv" else "android").toString()

        @JavascriptInterface fun chooseTeen(childId: Int): Boolean {
            if (!owner.isCurrent() || !ViewingMode.chooseTeen(context, childId)) return false
            val blocked = DeviceRuntime.household.status().getString("reason").isNotEmpty()
            if (!blocked) owner.post {
                hide()
                if (goHome?.invoke() != true) ViewingMode.openHome(context)
            }
            return blocked
        }

        @JavascriptInterface fun chooseParent() = owner.post { openParent(true) }
        @JavascriptInterface fun openParent() = owner.post { openParent(false) }


        @JavascriptInterface fun exitToLauncher() = owner.post {
            val status = DeviceRuntime.household.status()
            if (status.getString("reason").isEmpty() && status.optLong("remainingSeconds") > 0) hide()
        }
    }

    companion object {
        private const val BRIDGE_NAME = "SOTVNative"

        @Suppress("DEPRECATION")
        fun applicationWindowType(): Int = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

        fun canDraw(context: Context): Boolean = try {
            if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(context)
            else context.checkCallingOrSelfPermission(android.Manifest.permission.SYSTEM_ALERT_WINDOW) == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }
    }
}
