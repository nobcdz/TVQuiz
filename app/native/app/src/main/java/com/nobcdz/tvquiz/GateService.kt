package com.nobcdz.tvquiz

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class GateService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var stopped = false
    private var lastTick = SystemClock.elapsedRealtime()
    private var wasUsing = false
    private var previousViewers = "1"
    private var lastGate = 0L
    private var wasInteractive = true
    @Volatile private var settingsUntil = 0L
    private val systemSettingsPackage by lazy {
        packageManager.resolveActivity(Intent(android.provider.Settings.ACTION_SETTINGS), 0)?.activityInfo?.packageName
    }
    private var anchor: View? = null
    private lateinit var overlay: GateOverlay
    private lateinit var gateAudio: GateAudio
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val sample = Runnable { sampleForeground() }

    private fun requestSample(delay: Long = 0L) {
        if (stopped) return
        handler.removeCallbacks(sample)
        handler.postDelayed(sample, delay)
    }

    private fun sampleForeground() {
        if (stopped) return
        try {
            checkForeground()
        } catch (error: Exception) {
            android.util.Log.e("TVQuizGate", "Screen time check failed", error)
        } finally {
            requestSample(1_000L)
        }
    }

    private fun checkForeground() {
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastTick
        lastTick = now
        val interactive = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
        if (interactive && !wasInteractive) {
            ViewingMode.resetForBoot(this)
            lastGate = 0L
            wasUsing = false
        }
        wasInteractive = interactive
        val mode = ViewingMode.current(this)
        if (mode == "parent") {
            wasUsing = false
            hideAll()
            gateAudio.update(false)
            return
        }
        if (wasUsing && mode == "teen") DeviceRuntime.household.consume(elapsed, previousViewers)
        previousViewers = DeviceRuntime.household.selection()
        val canOverlay = GateOverlay.canDraw(this)
        updateAnchor(canOverlay)
        if (!canOverlay) overlay.hide()
        if (now < settingsUntil) { wasUsing = false; hideAll(); gateAudio.update(false); return }
        val locked = (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
        val awake = interactive && !locked
        val monitoring = canOverlay || WatchAccessibilityService.connected || Foreground.hasUsageAccess(this)
        val pkg = Foreground.current(this)
        val policy = ViewingGuardPolicy.evaluate(
            awake, pkg, monitoring, canOverlay, WatchAccessibilityService.connected,
            awake && audioManager.isMusicActive, Foreground.lastActivityClass, Build.VERSION.SDK_INT,
            systemExempt = pkg != null && systemExempt(pkg),
            launcher = awake && pkg != null && monitoring && isLauncher(pkg)
        )
        val reason = if (mode == "choose") "mode" else DeviceRuntime.household.status().getString("reason")
        val visible = hasGate()
        val feedback = visible && DeviceRuntime.household.hasFeedback()
        val needsGate = reason.isNotEmpty()
        wasUsing = policy.count && !needsGate && !feedback
        if (!policy.protect || (!needsGate && !feedback)) hideAll()
        if (needsGate && policy.protect) presentGate(reason, now, visible, canOverlay)
        val mute = mode == "teen" && awake && (needsGate || feedback)
        gateAudio.update(mute && (policy.protect || GateActivity.visible))
    }

    private fun presentGate(reason: String, now: Long, visible: Boolean, overlayAllowed: Boolean) {
        val activityAvailable = Build.VERSION.SDK_INT < 29
        val quotaActivity = reason == "quota" && activityAvailable
        if (GateActivity.visible || now - lastGate <= 1_800L || (visible && !quotaActivity)) return
        lastGate = now

        // The legacy quota activity lets the system power menu stay above the gate.
        if (quotaActivity) launchGate(reason)
        if (WatchAccessibilityService.showGate(reason)) return
        if (overlayAllowed && overlay.show(reason)) return
        if (activityAvailable && !quotaActivity) launchGate(reason)
    }

    private fun launchGate(reason: String) {
        val page = Intent(this, GateActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("reason", reason)
        }
        runCatching { startActivity(page) }.onFailure {
            android.util.Log.w("TVQuizGate", "Cannot open gate activity", it)
        }
    }

    private fun updateAnchor(allowed: Boolean) {
        if (allowed == (anchor != null)) return
        val windows = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
        val existing = anchor
        if (!allowed && existing != null) {
            anchor = null
            runCatching { windows.removeViewImmediate(existing) }
            return
        }
        val marker = View(this)
        marker.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        marker.setBackgroundColor(Color.argb(1, 0, 0, 0))
        val noInput = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val frame = WindowManager.LayoutParams(
            1, 1, GateOverlay.applicationWindowType(),
            noInput or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "TVQuiz guard anchor"
            alpha = 0.01f
        }
        runCatching { windows.addView(marker, frame) }.onSuccess { anchor = marker }
    }

    private fun hideAll() {
        overlay.hide()
        WatchAccessibilityService.hideGate()
    }

    private fun systemExempt(pkg: String): Boolean =
        pkg == packageName || pkg == systemSettingsPackage || ViewingGuardPolicy.isSystemPackage(pkg)

    private fun isLauncher(pkg: String): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, 0)?.activityInfo?.packageName == pkg
    }

    override fun onCreate() {
        super.onCreate()
        publishForegroundNotification()
        DeviceRuntime.ensure(this)
        overlay = GateOverlay(this, GateOverlay.applicationWindowType())
        gateAudio = GateAudio(this)
        instance = this
        Prefs.setServiceRunning(this, true)
        requestSample()
    }

    @Suppress("DEPRECATION")
    private fun publishForegroundNotification() {
        val channel = "tvquiz_viewing_guard"
        val builder = if (Build.VERSION.SDK_INT < 26) {
            Notification.Builder(this)
        } else {
            val notifications = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val category = NotificationChannel(channel, "使用时间管理", NotificationManager.IMPORTANCE_LOW)
            notifications.createNotificationChannel(category)
            Notification.Builder(this, channel)
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val card = builder.apply {
            setContentTitle(getString(R.string.app_name) + " · 使用时间管理中")
            setContentText("点击打开学习任务")
            setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            setOngoing(true)
            setContentIntent(open)
        }.build()
        if (Build.VERSION.SDK_INT < 34) startForeground(1001, card)
        else startForeground(1001, card, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        if (instance === this) {
            instance = null
            Prefs.setServiceRunning(this, false)
        }
        release("application gate") { if (::overlay.isInitialized) overlay.hide() }
        release("accessibility gate") { WatchAccessibilityService.hideGate() }
        release("window anchor") { updateAnchor(false) }
        release("media audio") { if (::gateAudio.isInitialized) gateAudio.update(false) }
        super.onDestroy()
    }

    private fun release(resource: String, action: () -> Unit) {
        try {
            action()
        } catch (error: RuntimeException) {
            android.util.Log.w("TVQuizGate", "Cannot release $resource", error)
        }
    }

    private fun postWhileRunning(action: GateService.() -> Unit) {
        handler.post { if (!stopped && instance === this) action(this) }
    }

    companion object {
        @Volatile private var instance: GateService? = null
        internal fun isRunning(): Boolean = instance != null
        internal fun isMiTvPlayback(activityClass: String?): Boolean =
            ViewingGuardPolicy.isMiTvPlayback(activityClass)
        fun start(context: Context) {
            GateWatchdog.schedule(context)
            val intent = Intent(context, GateService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (error: IllegalStateException) {
                if (context is android.app.Activity) throw error
                android.util.Log.w("TVQuizGate", "System deferred background guard startup", error)
            }
        }

        fun hasGate(): Boolean = WatchAccessibilityService.hasGate() || instance?.overlay?.isShowing == true

        fun hideGates() {
            val running = instance
            if (running != null) running.postWhileRunning { hideAll() }
            else Handler(Looper.getMainLooper()).post {
                if (instance == null) WatchAccessibilityService.hideGate()
            }
        }

        fun onOwnActivityResumed() {
            val running = instance
            if (running == null) {
                Handler(Looper.getMainLooper()).post {
                    if (instance != null) return@post
                    WatchAccessibilityService.hideGate()
                }
                return
            }
            running.settingsUntil = 0L
            running.postWhileRunning { hideAll() }
        }

        fun openingSystemPage() {
            val running = instance
            if (running == null) {
                hideGates()
                return
            }
            running.settingsUntil = SystemClock.elapsedRealtime() + 120_000L
            if (Looper.myLooper() == Looper.getMainLooper()) running.wasUsing = false
            running.postWhileRunning {
                wasUsing = false
                hideAll()
            }
        }

        fun onForegroundChanged() {
            instance?.let { running ->
                if (Looper.myLooper() == Looper.getMainLooper()) running.requestSample()
                else running.postWhileRunning { requestSample() }
            }
        }
    }
}
