package com.nobcdz.tvquiz

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

internal class DeviceBridge(
    private val host: Activity,
    private val browser: WebView,
    private val parentPanel: ParentActivity? = null
) {
    @Volatile private var attached = true

    fun detach() {
        attached = false
        browser.removeJavascriptInterface(NAME)
    }

    private fun ui(action: () -> Unit) {
        if (!attached) return
        host.runOnUiThread {
            if (attached && !host.isFinishing && !host.isDestroyed) action()
        }
    }

    @JavascriptInterface
    fun viewingMode(): String = if (attached) ViewingMode.current(host) else ""

    @JavascriptInterface
    fun childProfiles(): String = if (attached) DeviceRuntime.household.profiles().toString() else "[]"

    @JavascriptInterface
    fun pairingAddress(): String = if (attached) address() else "{}"

    @JavascriptInterface
    fun lanInfo(): String = if (attached && parentPanel != null) address() else ""

    private fun address(): String = DeviceRuntime.pairingAddress(host).toString()

    @JavascriptInterface
    fun chooseTeen(childId: Int): Boolean {
        if (!attached || !ViewingMode.chooseTeen(host, childId)) return false
        val mustStudy = !canReturnHome()
        if (!mustStudy) exitToLauncher()
        return mustStudy
    }

    @JavascriptInterface
    fun chooseParent() = ui {
        if (ViewingMode.current(host) == "choose") openParentPage(selectMode = true)
    }

    @JavascriptInterface
    fun openParent() = ui { openParentPage(selectMode = false) }

    private fun openParentPage(selectMode: Boolean) {
        val intent = Intent(host, ParentActivity::class.java)
        if (selectMode) intent.putExtra("enter_parent_mode", true)
        host.startActivity(intent)
    }

    @JavascriptInterface
    fun apiToken(): String = if (attached && parentPanel == null) AdminAuth.deviceToken else ""

    @JavascriptInterface
    fun deviceInfo(): String = JSONObject()
        .put("platform", if (isTv) "android-tv" else "android")
        .put("model", Build.MODEL)
        .put("version", BuildConfig.VERSION_NAME)
        .toString()

    @JavascriptInterface
    fun parentPanelAction(action: String) = ui { parentPanel?.runPanelAction(action) }

    @JavascriptInterface
    fun exitToLauncher() = ui {
        if (!canReturnHome()) return@ui
        GateService.hideGates()
        val modeEntry = host.intent.getStringExtra("reason") == "mode"
        if (host !is GateActivity || modeEntry) ViewingMode.openHome(host)
        host.finish()
    }

    private fun canReturnHome(): Boolean = ViewingMode.current(host) == "teen" &&
        DeviceRuntime.household.status().optString("reason").isEmpty()

    @JavascriptInterface
    fun backToQuiz() = ui {
        if (parentPanel == null) return@ui
        if (ViewingMode.current(host) == "parent") ViewingMode.openHome(host)
        else host.startActivity(Intent(host, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        host.finish()
    }

    @JavascriptInterface
    fun updateInfo(): String =
        if (attached) Updater.newerThanCurrent(host, Updater.cachedInfo(host))?.let(::releasePayload).orEmpty()
        else ""

    @Suppress("UNUSED_PARAMETER")
    @JavascriptInterface
    fun installUpdate(url: String, latest: String, notes: String) = ui {
        val release = Updater.newerThanCurrent(host, Updater.cachedInfo(host)) ?: return@ui
        if (release.url == url && release.latest == latest) {
            host.startActivity(Intent(host, UpdateActivity::class.java).putExtra("payload", releasePayload(release)))
        }
    }

    companion object {
        const val NAME = "SOTVNative"
        val isTv: Boolean get() = BuildConfig.FLAVOR == "tv"

        fun releasePayload(release: Updater.Info): String = JSONObject()
            .put("latest", release.latest)
            .put("current", release.current)
            .put("notes", release.notes)
            .put("url", release.url)
            .put("force", release.force)
            .toString()
    }
}
