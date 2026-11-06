package com.nobcdz.tvquiz

import android.content.Context
import android.content.Intent

object ViewingMode {
    private fun prefs(context: Context) =
        context.getSharedPreferences("viewing_mode", Context.MODE_PRIVATE)

    private val bootId by lazy {
        try { java.io.File("/proc/sys/kernel/random/boot_id").readText().trim() }
        catch (_: java.io.IOException) { "" }
        catch (_: SecurityException) { "" }
    }

    @Synchronized
    fun current(context: Context): String {
        val prefs = prefs(context)
        if (bootId.isNotEmpty() && prefs.getString("boot_id", "") != bootId) resetForBoot(context)
        return prefs.getString("boot_mode", "choose") ?: "choose"
    }

    @Synchronized fun chooseTeen(context: Context, childId: Int): Boolean {
        if (current(context) != "choose" || !DeviceRuntime.household.chooseChild(childId)) return false
        return select(context, "teen")
    }
    fun chooseParent(context: Context): Boolean = select(context, "parent")

    @Synchronized
    private fun select(context: Context, mode: String): Boolean {
        if (current(context) != "choose") return false
        return prefs(context).edit().putString("boot_mode", mode).commit()
    }

    @Synchronized
    fun resetForBoot(context: Context) {
        prefs(context).edit().putString("boot_id", bootId).remove("boot_mode").commit()
    }

    fun openHome(context: Context) {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(home) }
        catch (_: android.content.ActivityNotFoundException) { revealPreviousTask(context) }
        catch (_: SecurityException) { revealPreviousTask(context) }
    }

    private fun revealPreviousTask(context: Context) {
        (context as? android.app.Activity)?.moveTaskToBack(true)
    }
}
