package com.nobcdz.tvquiz

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

class GateWatchdog : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.onboarded(context)) return
        schedule(context)
        if (!GateService.isRunning()) {
            android.util.Log.i("TVQuizGate", "Restarting screen-time guard after service exit")
            GateService.start(context)
        }
    }

    companion object {
        fun schedule(context: Context) {
            if (Build.VERSION.SDK_INT >= 26 || !Prefs.onboarded(context)) return
            val intent = PendingIntent.getBroadcast(context, 1, Intent(context, GateWatchdog::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.setExact(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 15_000, intent)
        }
    }
}
