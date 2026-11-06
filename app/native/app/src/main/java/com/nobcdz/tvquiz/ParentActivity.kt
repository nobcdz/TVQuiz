package com.nobcdz.tvquiz

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.inputmethod.EditorInfo
import android.view.View
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.webkit.WebView

class ParentActivity : Activity() {
    private var verified = false
    private var checkingUpdate = false
    private var formRevision = 0
    private var web: WebView? = null
    private var bridge: DeviceBridge? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceRuntime.ensure(this)
        if (Prefs.onboarded(this)) GateService.start(this)
        form(!AdminAuth.configured(this))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun EditText.maskPassword() {
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        transformationMethod = object : PasswordTransformationMethod() {
            override fun getTransformation(source: CharSequence, view: View): CharSequence =
                object : CharSequence {
                    override val length get() = source.length
                    override fun get(index: Int): Char {
                        source[index]
                        return '•'
                    }
                    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
                        "•".repeat(endIndex - startIndex)
                    override fun toString() = "•".repeat(length)
                }
        }
    }

    private fun form(setup: Boolean, changing: Boolean = false) {
        releasePanel()
        val revision = ++formRevision
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(34), dp(28), dp(34), dp(28))
            setBackgroundColor(0xFF111428.toInt())
        }
        val heading = if (changing) "修改管理密码" else if (setup) "设置管理密码" else "家长验证"
        content.addView(label("TVQuiz · 魔改电视答题系统", 13f))
        content.addView(label(heading, 28f))
        content.addView(label(if (setup) "请输入 6–64 位管理密码，两次输入须一致。" else "输入密码以验证家长身份。", 16f))
        val password = passwordField(if (changing) "新密码" else "管理密码")
        val confirmation = if (setup) passwordField("确认密码") else null
        content.addView(password)
        confirmation?.let { content.addView(it) }
        val message = label("", 14f)
        content.addView(message)
        val submit = Button(this).apply {
            text = when {
                setup -> "保存密码"
                intent.getBooleanExtra("enter_parent_mode", false) -> "验证并返回电视桌面"
                else -> "验证并打开连接页"
            }
            learningStyle(primary = true)
        }
        content.addView(submit, LinearLayout.LayoutParams(-1, dp(52)))
        submit.setOnClickListener {
            val value = password.text.toString()
            if (setup) {
                if (value.length !in 6..64 || value != confirmation?.text.toString()) {
                    message.text = "请确认密码长度和两次输入。"
                    return@setOnClickListener
                }
                AdminAuth.setPassword(this, value)
                password.text.clear()
                confirmation?.text?.clear()
                if (Prefs.onboarded(this)) unlockPanel()
                else {
                    startActivity(Intent(this, OnboardingActivity::class.java))
                    finish()
                }
            } else {
                submit.isEnabled = false
                Thread({
                    val accepted = runCatching { AdminAuth.verify(this, value) }.getOrDefault(false)
                    runOnUiThread {
                        if (revision != formRevision || isFinishing || isDestroyed) return@runOnUiThread
                        submit.isEnabled = true
                        password.text.clear()
                        if (accepted) unlockPanel()
                        else message.text = "密码错误，连续错误 5 次后需等待一分钟"
                    }
                }, "tvquiz-parent-verify").start()
            }
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        })
    }

    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(0xFFE2EBFF.toInt())
        setPadding(0, dp(6), 0, dp(8))
    }

    private fun passwordField(placeholder: String) = EditText(this).apply {
        hint = placeholder
        learningField()
        maskPassword()
        isSaveEnabled = false
    }

    private fun unlockPanel() {
        verified = true
        connection()
    }

    private fun connection() {
        if (intent.getBooleanExtra("enter_parent_mode", false)) {
            ViewingMode.chooseParent(this)
            GateService.hideGates()
            ViewingMode.openHome(this)
            finish()
            return
        }
        web = WebShell(this).create("about:blank").also {
            val session = DeviceBridge(this, it, this)
            bridge = session
            it.addJavascriptInterface(session, DeviceBridge.NAME)
            setContentView(it)
            it.requestFocus()
            it.loadUrl(Prefs.localBase(this) + "#/pair")
        }
    }

    internal fun runPanelAction(action: String) {
        if (!verified || web == null || isFinishing) return
        when (action) {
            "curriculum" -> startActivity(Intent(this, FamilyActivity::class.java))
            "permissions" -> startActivity(Intent(this, OnboardingActivity::class.java))
            "protection" -> startActivity(Intent(this, ProtectionActivity::class.java))
            "password" -> form(true, true)
            "update" -> checkUpdate()
        }
    }

    private fun checkUpdate() {
        if (checkingUpdate) return
        checkingUpdate = true
        val panel = web
        val progress = AlertDialog.Builder(this).setMessage("正在检查更新…").create()
        progress.show()
        Updater.check(this, forceRefresh = true) { info ->
            runOnUiThread {
                checkingUpdate = false
                if (isDestroyed || isFinishing) return@runOnUiThread
                val showResult = progress.isShowing && verified && web === panel
                progress.dismiss()
                if (!showResult) return@runOnUiThread
                val update = Updater.newerThanCurrent(this, info)
                if (update != null) {
                    startActivity(Intent(this, UpdateActivity::class.java)
                        .putExtra("payload", DeviceBridge.releasePayload(update)))
                } else {
                    val message = if (info == null) "无法获取正式版本，请检查网络后重试。"
                        else "当前已是最新版本 v" + Updater.currentVersion(this)
                    AlertDialog.Builder(this).setMessage(message).setPositiveButton("确定", null).show()
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val panel = web ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && WebShell(this).dispatchKey(panel, event.keyCode, event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun releasePanel() {
        val panel = web ?: return
        web = null
        bridge?.detach()
        bridge = null
        (panel.parent as? ViewGroup)?.removeView(panel)
        panel.destroy()
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) return
        val wasVerified = verified
        verified = false
        formRevision++
        if (wasVerified || AdminAuth.configured(this)) form(false)
    }

    override fun onDestroy() {
        releasePanel()
        super.onDestroy()
    }
}


