package com.nobcdz.tvquiz

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView

open class MainActivity : Activity() {
    private val shell by lazy { WebShell(this) }
    private var page: Page? = null
    private var upCount = 0
    private var lastUpAt = 0L
    private var ready = false

    private data class Page(val view: WebView, val bridge: DeviceBridge)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val firstScreen = when {
            !AdminAuth.configured(this) -> ParentActivity::class.java
            !Prefs.onboarded(this) -> OnboardingActivity::class.java
            else -> null
        }
        if (firstScreen != null) {
            startActivity(Intent(this, firstScreen))
            finish()
            return
        }
        if (redirectParent()) return
        DeviceRuntime.ensure(this)
        ready = true
        if (DeviceBridge.isTv) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        displayPage()
        GateService.start(this)
        if (this !is GateActivity) checkUpdate()
    }

    private fun checkUpdate() {
        Updater.check(this) { info ->
            val release = Updater.newerThanCurrent(this, info) ?: return@check
            runOnUiThread {
                val current = page ?: return@runOnUiThread
                if (isFinishing || isDestroyed) return@runOnUiThread
                val payload = DeviceBridge.releasePayload(release)
                if (release.force) startActivity(Intent(this, UpdateActivity::class.java).putExtra("payload", payload))
                else current.view.evaluateJavascript("window.SOTV_UPDATE&&SOTV_UPDATE($payload)", null)
            }
        }
    }

    private fun displayPage() {
        val reason = intent?.getStringExtra("reason")
        val quota = this is GateActivity && reason == "quota"
        val wakeFlag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (quota) window.clearFlags(wakeFlag) else window.addFlags(wakeFlag)
        val route = when {
            ViewingMode.current(this) == "choose" -> "mode"
            this is GateActivity && !reason.isNullOrEmpty() && reason != "timeup" -> "lock?reason=" + Uri.encode(reason)
            else -> "quiz"
        }
        val address = Prefs.localBase(this) + "#/" + route
        val current = page
        if (current != null) {
            current.view.loadUrl(address)
            signalResume(current)
            return
        }
        val view = shell.create("about:blank")
        val bridge = DeviceBridge(this, view)
        view.addJavascriptInterface(bridge, DeviceBridge.NAME)
        page = Page(view, bridge)
        setContentView(view)
        view.requestFocus()
        view.loadUrl(address)
    }

    private fun signalResume(current: Page) {
        current.view.evaluateJavascript("window.SOTV_RESUME&&SOTV_RESUME()", null)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (ready && !isFinishing && !redirectParent()) displayPage()
    }

    override fun onResume() {
        super.onResume()
        if (!ready || isFinishing || redirectParent()) return
        val current = page
        if (current == null) displayPage() else signalResume(current)
    }

    private fun redirectParent(): Boolean {
        if (ViewingMode.current(this) != "parent") return false
        if (this !is GateActivity) startActivity(Intent(this, ParentActivity::class.java))
        finish()
        return true
    }

    private fun parentShortcut(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        if (event.keyCode != KeyEvent.KEYCODE_DPAD_UP) {
            upCount = 0
            return false
        }
        val now = SystemClock.elapsedRealtime()
        upCount = if (now - lastUpAt < 900) upCount + 1 else 1
        lastUpAt = now
        if (upCount < 5) return false
        upCount = 0
        startActivity(Intent(this, ParentActivity::class.java))
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (parentShortcut(event)) return true
        val view = page?.view ?: return super.dispatchKeyEvent(event)
        val key = event.keyCode
        if (!DeviceBridge.isTv && key == KeyEvent.KEYCODE_ENTER) return super.dispatchKeyEvent(event)
        val managed = key in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_CENTER ||
            key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER || key == KeyEvent.KEYCODE_BACK
        if (!managed) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) shell.dispatchKey(view, key, event)
        return true
    }

    private fun releasePage() {
        val current = page ?: return
        page = null
        current.bridge.detach()
        (current.view.parent as? ViewGroup)?.removeView(current.view)
        current.view.stopLoading()
        current.view.destroy()
    }

    override fun onStop() {
        if (DeviceBridge.isTv) releasePage()
        super.onStop()
    }

    override fun onDestroy() {
        releasePage()
        super.onDestroy()
    }
}
