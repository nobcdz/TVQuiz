package com.nobcdz.tvquiz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, broadcast: Intent?) {
        if (broadcast?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        ViewingMode.resetForBoot(app)
        if (!Prefs.onboarded(app)) return
        GateService.start(app)
    }
}
