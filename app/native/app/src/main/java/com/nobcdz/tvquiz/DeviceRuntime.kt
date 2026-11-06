package com.nobcdz.tvquiz

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import org.json.JSONObject

object DeviceRuntime {
    private class Running(val bank: LocalStore, val family: Household, val network: LanServer)
    @Volatile private var running: Running? = null
    val store: LocalStore get() = requireNotNull(running).bank
    val household: Household get() = requireNotNull(running).family

    @Synchronized
    fun ensure(context: Context) {
        if (running != null) return
        val application = context.applicationContext as Application
        val bank = LocalStore(application)
        SeedAssets.ensureQuestions(application, bank)
        val family = Household(application, bank)
        val network = LanServer(application, bank, application.assets, household = family)
        network.start()
        Prefs.setLocalPort(application, network.boundPort)
        WebShell.PrefsHolder.baseUrl = Prefs.localBase(application)
        application.registerActivityLifecycleCallbacks(ActivityWindows)
        running = Running(bank, family, network)
    }

    fun pairingAddress(context: Context): JSONObject {
        ensure(context)
        val network = requireNotNull(running).network
        return JSONObject().put("port", network.boundPort).put("ip", network.localIp().orEmpty())
    }

    private object ActivityWindows : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            Foreground.resumeOwn(activity)
            GateService.onOwnActivityResumed()
        }
        override fun onActivityPaused(activity: Activity) = Foreground.pauseOwn(activity)
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) = Foreground.pauseOwn(activity)
    }
}
