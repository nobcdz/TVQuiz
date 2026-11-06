package com.nobcdz.tvquiz

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

object Foreground {
    private data class Window(val packageName: String, val className: String?)

    @Volatile private var externalWindow: Window? = null
    val lastActivityClass: String? get() = externalWindow?.className
    private var resumed: java.lang.ref.WeakReference<android.app.Activity>? = null

    fun resumeOwn(activity: android.app.Activity) {
        resumed = java.lang.ref.WeakReference(activity)
    }

    fun pauseOwn(activity: android.app.Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    fun update(pkg: String, activityClass: String?) {
        externalWindow = Window(pkg, activityClass)
    }

    fun clearExternal() {
        externalWindow = null
    }

    fun current(context: Context): String? {
        val activity = resumed?.get()
        if (activity != null && (Build.VERSION.SDK_INT < 24 || !activity.isInMultiWindowMode)) {
            return activity.packageName
        }
        val observed = if (WatchAccessibilityService.connected) externalWindow else null
        return observed?.packageName ?: if (hasUsageAccess(context)) usageForeground(context) else null
    }

    @Suppress("DEPRECATION")
    private fun usageForeground(context: Context): String? = runCatching {
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 24 * 60 * 60 * 1000L
        val history = usage.queryEvents(start, end)
        val entry = UsageEvents.Event()
        var resumedPackage: String? = null
        while (history.getNextEvent(entry)) {
            if (entry.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                resumedPackage = entry.packageName
            }
        }
        resumedPackage ?: usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            ?.maxByOrNull { it.lastTimeUsed }
            ?.takeIf { it.lastTimeUsed > 0 }
            ?.packageName
    }.getOrNull()

    @Suppress("DEPRECATION")
    fun hasUsageAccess(context: Context): Boolean {
        val service = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager ?: return false
        val operation = android.app.AppOpsManager.OPSTR_GET_USAGE_STATS
        val uid = context.applicationInfo.uid
        return runCatching {
            val access = if (Build.VERSION.SDK_INT < 29) {
                service.checkOpNoThrow(operation, uid, context.packageName)
            } else service.unsafeCheckOpNoThrow(operation, uid, context.packageName)
            access == android.app.AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }
}
