package com.nobcdz.tvquiz

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ProtectionActivity : Activity() {
    private val manager by lazy { getSystemService(DEVICE_POLICY_SERVICE) as? DevicePolicyManager }
    private val admin by lazy { ComponentName(this, ProtectionAdminReceiver::class.java) }
    private val isOwner get() = manager?.isDeviceOwnerApp(packageName) == true

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        render()
    }

    private fun render(message: String? = null) {
        val policy = manager
        val supported = policy != null && packageManager.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)
        val blocked = isOwner && policy?.isUninstallBlocked(admin, packageName) == true
        val pad = (28 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFF101A32.toInt())
        }
        fun label(value: String, size: Float) {
            content.addView(TextView(this).apply {
                text = value
                textSize = size
                setTextColor(0xFFEAF4FF.toInt())
                setPadding(0, 0, 0, pad / 2)
            })
        }
        fun button(title: String, primary: Boolean = false, action: () -> Unit) {
            content.addView(Button(this).apply {
                text = title
                learningStyle(primary)
                setOnClickListener { action() }
            })
        }
        label("防卸载保护", 28f)
        label(message ?: when {
            blocked -> "已启用 · 系统会阻止卸载${getString(R.string.app_name)}"
            isOwner -> "设备管理已就绪 · 可以启用防卸载"
            supported -> "未启用 · 需要先由家长配置设备所有者"
            else -> "未启用 · 此电视系统未声明设备管理支持"
        }, 18f)
        label("保护仅针对本应用。观看守护仍依赖电视的无障碍或悬浮窗权限。", 16f)
        if (isOwner && policy != null) {
            if (!blocked) button("启用防卸载", true) {
                try {
                    policy.setUninstallBlocked(admin, packageName, true)
                    render()
                } catch (_: RuntimeException) {
                    render("系统未能启用防卸载，请核对设备管理状态。")
                }
            }
            button("解除设备管理") { confirmRelease(policy, blocked) }
        }
        button("返回") { finish() }
        setContentView(ScrollView(this).apply { addView(content) })
    }

    @Suppress("DEPRECATION")
    private fun confirmRelease(policy: DevicePolicyManager, wasBlocked: Boolean) {
        AlertDialog.Builder(this)
            .setTitle("解除设备管理？")
            .setMessage("解除后，系统将允许卸载本应用。")
            .setNegativeButton("取消", null)
            .setPositiveButton("解除") { _, _ ->
                try {
                    policy.setUninstallBlocked(admin, packageName, false)
                    policy.clearDeviceOwnerApp(packageName)
                    if (policy.isAdminActive(admin)) policy.removeActiveAdmin(admin)
                    render()
                } catch (_: RuntimeException) {
                    if (wasBlocked && isOwner) {
                        try { policy.setUninstallBlocked(admin, packageName, true) }
                        catch (_: RuntimeException) { }
                    }
                    render("解除操作未完成，请检查当前保护状态。")
                }
            }
            .show()
    }

    override fun onStop() {
        super.onStop()
        finish()
    }
}
