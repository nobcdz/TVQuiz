package com.nobcdz.tvquiz

internal object ViewingGuardPolicy {
    data class Decision(val count: Boolean, val protect: Boolean)

    fun evaluate(
        awake: Boolean,
        foreground: String?,
        monitoring: Boolean,
        overlay: Boolean,
        accessibility: Boolean,
        music: Boolean,
        activityClass: String?,
        sdk: Int,
        systemExempt: Boolean,
        launcher: Boolean
    ): Decision {
        if (!awake) return Decision(false, false)
        val count = when (foreground) {
            null -> overlay || music
            "com.mitv.tvhome" -> music || isMiTvPlayback(activityClass)
            else -> monitoring && !systemExempt && !launcher
        }
        val protect = !systemExempt && when {
            sdk < 29 -> true
            foreground == null -> overlay || accessibility
            else -> monitoring
        }
        return Decision(count, protect)
    }

    fun isMiTvPlayback(activityClass: String?): Boolean =
        activityClass?.startsWith("com.mitv.tvhome.") == true &&
            (activityClass.endsWith("DetailsActivity") || activityClass.endsWith("PlayerActivity"))

    private val systemPackages = setOf(
        "android", "com.android.systemui", "com.android.settings", "com.android.tv.settings",
        "com.google.android.tv.settings", "com.google.android.dialer", "com.android.dialer",
        "com.android.phone", "com.android.server.telecom", "com.android.emergency"
    )

    fun isInputSurface(pkg: String): Boolean = "inputmethod" in pkg || "keyboard" in pkg

    fun isSystemPackage(pkg: String): Boolean = pkg in systemPackages || isInputSurface(pkg) ||
        "packageinstaller" in pkg || "permissioncontroller" in pkg
}
