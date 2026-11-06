package com.nobcdz.tvquiz

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class OnboardingActivity : Activity() {
    private enum class Access(val label: String, val purpose: String, val action: String, val appSpecific: Boolean) {
        OVERLAY("悬浮窗", "在观看时间结束时显示学习任务", Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true),
        USAGE("使用情况访问", "识别正在使用的应用并统计观看时间", Settings.ACTION_USAGE_ACCESS_SETTINGS, false),
        ACCESSIBILITY("无障碍服务", "及时识别应用切换并显示学习任务", Settings.ACTION_ACCESSIBILITY_SETTINGS, false),
        BACKGROUND("后台运行", "按电视系统提供的选项保持守护运行", Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true)
    }

    private val labels = linkedMapOf<Access, TextView>()
    private lateinit var summary: TextView
    private val ui = Handler(Looper.getMainLooper())
    private val statusTick = object : Runnable {
        override fun run() {
            updateLabels()
            ui.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (!AdminAuth.configured(this)) {
            navigate(ParentActivity::class.java)
            return
        }
        DeviceRuntime.ensure(this)
        if (DeviceBridge.isTv) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(28), px(18), px(28), px(18))
        }
        content.addView(caption("首次设置  /  观看管理", 13f, MINT))
        content.addView(caption("设置使用时间管理", 28f, TEXT, bold = true))
        summary = caption("", 15f, MUTED)
        content.addView(summary, vertical(8))
        for (access in Access.values()) content.addView(accessRow(access), vertical(6))
        val actions = LinearLayout(this).apply { gravity = Gravity.END }
        val start = control("开始学习") { complete(MainActivity::class.java) }
        val manage = control("进入家长设置", primary = true) { complete(ParentActivity::class.java) }
        actions.addView(start, LinearLayout.LayoutParams(0, px(44), 1f))
        actions.addView(manage, LinearLayout.LayoutParams(0, px(44), 1f).apply { marginStart = px(10) })
        content.addView(actions, vertical(12))
        setContentView(ScrollView(this).apply {
            setBackgroundColor(BACKDROP)
            isFillViewport = true
            addView(content)
        })
        if (DeviceBridge.isTv) manage.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        if (::summary.isInitialized) statusTick.run()
    }

    override fun onPause() {
        ui.removeCallbacks(statusTick)
        super.onPause()
    }

    private fun accessRow(access: Access): LinearLayout {
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(caption(access.label, 16f, TEXT, bold = true))
            addView(caption(access.purpose, 12f, MUTED))
        }
        val status = caption("", 12f, MUTED)
        labels[access] = status
        val settings = control("设置") { openSettings(access) }.apply {
            contentDescription = "设置" + access.label
        }
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = panel(CARD, LINE)
            setPadding(px(14), px(6), px(10), px(6))
            addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            addView(status, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(12) })
            addView(settings, LinearLayout.LayoutParams(px(66), px(40)))
        }
    }

    private fun updateLabels() {
        val overlay = GateOverlay.canDraw(this)
        val usage = Foreground.hasUsageAccess(this)
        val accessibility = WatchAccessibilityService.connected
        val active = setOfNotNull(
            Access.OVERLAY.takeIf { overlay },
            Access.USAGE.takeIf { usage },
            Access.ACCESSIBILITY.takeIf { accessibility }
        )
        for ((access, label) in labels) {
            val enabled = access in active
            label.text = when {
                access == Access.BACKGROUND -> "按设备设置"
                enabled -> "已开启"
                else -> "未开启"
            }
            label.setTextColor(if (enabled) MINT else MUTED)
        }
        summary.text = when {
            accessibility || overlay && usage -> "观看管理已就绪。"
            overlay -> "已启用简易管理，可继续开启使用情况访问。"
            usage -> "已可记录使用时间，开启悬浮窗后可显示学习任务。"
            else -> "可按需开启这些权限，也可以稍后回来设置。"
        }
    }

    private fun openSettings(access: Access) {
        GateService.openingSystemPage()
        val app = Uri.fromParts("package", packageName, null)
        val candidates = listOfNotNull(
            if (access.appSpecific) Intent(access.action, app) else null,
            Intent(access.action),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (candidate in candidates) {
            try {
                startActivity(candidate)
                return
            } catch (_: ActivityNotFoundException) {
                // Some TV builds only expose the general settings screen.
            } catch (_: SecurityException) {
                // Try the next system settings entry when this one is restricted.
            }
        }
        Toast.makeText(this, "请在系统设置中调整权限", Toast.LENGTH_SHORT).show()
    }

    private fun complete(destination: Class<out Activity>) {
        Prefs.setOnboarded(this, true)
        try {
            GateService.start(this)
        } catch (_: RuntimeException) {
            Toast.makeText(this, "后台管理暂未启动，您仍可继续学习", Toast.LENGTH_SHORT).show()
        }
        navigate(destination)
    }

    private fun navigate(destination: Class<out Activity>) {
        startActivity(Intent(this, destination))
        finish()
    }

    private fun control(title: String, primary: Boolean = false, click: () -> Unit) = Button(this).apply {
        text = title
        isAllCaps = false
        textSize = 14f
        stateListAnimator = null
        backgroundTintList = null
        val normal = if (primary) MINT else BUTTON
        setTextColor(if (primary) BACKDROP else TEXT)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), panel(normal, WHITE, 3))
            addState(intArrayOf(android.R.attr.state_pressed), panel(normal, MINT, 2))
            addState(intArrayOf(), panel(normal, LINE))
        }
        setOnClickListener { click() }
    }

    private fun panel(fill: Int, border: Int, width: Int = 1) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = px(10).toFloat()
        setStroke(px(width).coerceAtLeast(1), border)
    }

    private fun caption(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun px(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun vertical(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(top) }

    private companion object {
        const val BACKDROP = 0xFF111428.toInt()
        const val CARD = 0xFF1D223C.toInt()
        const val BUTTON = 0xFF343A5A.toInt()
        const val LINE = 0xFF565C7C.toInt()
        const val TEXT = 0xFFF4F4FE.toInt()
        const val MUTED = 0xFFC1C6DB.toInt()
        const val MINT = 0xFFC9F693.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
