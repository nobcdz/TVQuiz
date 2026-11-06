package com.nobcdz.tvquiz

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class WatchAccessibilityService : AccessibilityService() {
    private var window: GateOverlay? = null

    override fun onServiceConnected() {
        window?.hide()
        window = GateOverlay(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        service = this
        ensureGuard()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val owner = event.packageName?.toString() ?: return
        if (owner == packageName || owner in transientWindows || ViewingGuardPolicy.isInputSurface(owner)) return

        Foreground.update(owner, event.className?.toString())
        ensureGuard()
        GateService.onForegroundChanged()
    }

    private fun ensureGuard() {
        if (!GateService.isRunning() && Prefs.onboarded(this)) GateService.start(this)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        window?.hide()
        window = null
        if (service === this) {
            service = null
            Foreground.clearExternal()
        }
        super.onDestroy()
    }

    companion object {
        private val transientWindows = setOf("android", "com.android.systemui", "com.xiaomi.mitv.systemui")
        @Volatile private var service: WatchAccessibilityService? = null
        val connected: Boolean get() = service != null

        fun hasGate(): Boolean = service?.window?.isShowing == true
        fun showGate(reason: String): Boolean = service?.window?.show(reason) == true
        fun hideGate() { service?.window?.hide() }
    }
}
