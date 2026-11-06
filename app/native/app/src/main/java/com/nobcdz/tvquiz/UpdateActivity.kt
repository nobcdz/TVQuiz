package com.nobcdz.tvquiz

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

class UpdateActivity : Activity() {
    private lateinit var primary: Button
    private var busy = false
    private var address = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemChrome()
        val release = runCatching { JSONObject(intent.getStringExtra("payload") ?: "") }.getOrElse { JSONObject() }
        address = release.optString("url")
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(48), dp(32), dp(48), dp(24))
            setBackgroundColor(0xFF111428.toInt())
        }
        content.addView(line(getString(R.string.app_name), 16f, 0xFFD3FF87.toInt()))
        content.addView(line("发现新版本 v" + release.optString("latest"), 32f, Color.WHITE))
        val installed = release.optString("current", Updater.currentVersion(this))
        content.addView(line("当前 v$installed · 更新后继续使用", 16f, 0xFFC3BFD7.toInt()))
        val notes = release.optString("notes").lineSequence()
            .filterNot { it.isBlank() || it.contains("[force]", ignoreCase = true) }.joinToString("\n")
        if (notes.isNotEmpty()) {
            val details = ScrollView(this).apply {
                addView(line(notes, 16f, Color.WHITE).apply { gravity = Gravity.START })
            }
            content.addView(details, LinearLayout.LayoutParams(-1, dp(144)))
        }
        primary = Button(this).apply {
            isEnabled = address.isNotBlank()
            isFocusable = true
            text = actionText()
            learningStyle(true)
            setOnClickListener { requestUpdate() }
        }
        content.addView(primary, LinearLayout.LayoutParams(dp(280), dp(56)))
        content.addView(line("覆盖安装可保留题库和设置", 14f, 0xFFC3BFD7.toInt()))
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(content) })
        primary.requestFocus()
        if (intent.getBooleanExtra("auto", false) && address.isNotBlank()) primary.post { requestUpdate() }
    }

    private fun actionText() = if (ApkInstaller.alreadyDownloaded(this, 0)) "开始安装" else "立即更新"

    private fun requestUpdate() {
        if (busy || address.isBlank()) return
        Prefs.setUpdateInProgress(this, true)
        if (ApkInstaller.alreadyDownloaded(this, 0)) {
            if (!ApkInstaller.install(this, ApkInstaller.apkFile(this))) {
                Prefs.setUpdateInProgress(this, false)
                primary.text = "请允许安装未知应用"
            }
            return
        }
        busy = true
        primary.isEnabled = false
        primary.text = "下载中 0%"
        Thread({
            val result = ApkInstaller.download(applicationContext, address) { percent ->
                runOnUiThread { if (!isDestroyed) primary.text = "下载中 $percent%" }
            }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                busy = false
                primary.isEnabled = true
                if (result == null) {
                    Prefs.setUpdateInProgress(this, false)
                    primary.text = "下载失败，重试"
                } else if (ApkInstaller.install(this, result)) {
                    primary.text = "开始安装"
                } else {
                    Prefs.setUpdateInProgress(this, false)
                    primary.text = "请允许安装未知应用"
                }
            }
        }, "tvquiz-apk-download").apply { isDaemon = true }.start()
    }

    private fun line(text: String, size: Float, color: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        setPadding(0, dp(6), 0, dp(8))
    }

    @Deprecated("Required update must be completed first")
    override fun onBackPressed() = Unit

    @Suppress("DEPRECATION")
    private fun hideSystemChrome() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
