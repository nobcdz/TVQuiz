package com.nobcdz.tvquiz

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/** These names are part of the on-device format and remain stable across upgrades. */
object Prefs {
    private const val UPDATE_WINDOW = 600_000L

    private fun read(context: Context): SharedPreferences =
        context.getSharedPreferences("sotv", Context.MODE_PRIVATE)

    private inline fun write(context: Context, change: SharedPreferences.Editor.() -> Unit) {
        val edit = read(context).edit()
        edit.change()
        edit.apply()
    }

    fun localPort(c: Context) = read(c).getInt("local_port", 8099)
    fun localBase(c: Context) = "http://127.0.0.1:${localPort(c)}/"
    fun setLocalPort(c: Context, port: Int) = write(c) { putInt("local_port", port) }

    fun onboarded(c: Context) = read(c).getBoolean("onboarded", false)
    fun setOnboarded(c: Context, v: Boolean) = write(c) { putBoolean("onboarded", v) }

    fun deviceName(c: Context) = read(c).getString("device_name", null) ?: Build.MODEL ?: "客厅电视"
    fun setDeviceName(c: Context, v: String) = write(c) { putString("device_name", v) }

    fun updateApi(c: Context) = read(c).getString("update_api", null).orEmpty()
    fun setUpdateApi(c: Context, v: String) = write(c) { putString("update_api", v) }
    fun updateInfo(c: Context) = read(c).getString("update_info", null).orEmpty()
    fun setUpdateInfo(c: Context, v: String) = write(c) { putString("update_info", v) }
    fun updateCheckedAt(c: Context) = read(c).getLong("update_checked_at", 0L)
    fun setUpdateCheckedAt(c: Context, v: Long) = write(c) { putLong("update_checked_at", v) }

    fun updateInProgress(c: Context): Boolean {
        val started = read(c).getLong("update_started_at", 0L)
        return started > 0L && System.currentTimeMillis() - started < UPDATE_WINDOW
    }

    fun setUpdateInProgress(c: Context, v: Boolean) = write(c) {
        putLong("update_started_at", if (v) System.currentTimeMillis() else 0L)
    }

    fun serviceRunning(c: Context) = read(c).getBoolean("service_running", false)
    fun setServiceRunning(c: Context, v: Boolean) = write(c) { putBoolean("service_running", v) }
}
