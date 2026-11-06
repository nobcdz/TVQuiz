package com.nobcdz.tvquiz

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Updater {
    private const val CACHE_TTL = 21_600_000L

    data class Info(val latest: String, val current: String, val force: Boolean, val notes: String, val url: String)

    fun api(ctx: Context) = Prefs.updateApi(ctx).ifBlank {
        "https://api.github.com/repos/nobcdz/TVQuiz/releases/latest"
    }

    fun currentVersion(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    }.getOrNull() ?: "0.0.0"

    fun check(ctx: Context, forceRefresh: Boolean = false, onResult: (Info?) -> Unit) {
        val address = api(ctx)
        if (address.isBlank()) {
            onResult(null)
            return
        }
        val cached = cachedInfo(ctx)
        val age = System.currentTimeMillis() - Prefs.updateCheckedAt(ctx)
        if (!forceRefresh && cached != null && age in 0 until CACHE_TTL) {
            onResult(cached)
            return
        }
        Thread({
            val update = runCatching {
                decodeRelease(fetchRelease(address), currentVersion(ctx))
            }.getOrNull()
            if (update != null) {
                val data = JSONObject()
                data.put("latest", update.latest)
                data.put("current", update.current)
                data.put("notes", update.notes)
                data.put("force", update.force)
                data.put("url", update.url)
                Prefs.setUpdateInfo(ctx, data.toString())
                Prefs.setUpdateCheckedAt(ctx, System.currentTimeMillis())
            }
            onResult(update)
        }, "tvquiz-update-check").apply { isDaemon = true }.start()
    }

    fun cachedInfo(ctx: Context): Info? = runCatching {
        val data = JSONObject(Prefs.updateInfo(ctx))
        Info(data.optString("latest"), data.optString("current"), data.optBoolean("force"),
            data.optString("notes"), data.optString("url"))
    }.getOrNull()

    private fun fetchRelease(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 6000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "TVQuiz")
        try {
            require(connection.responseCode in 200..299)
            return connection.inputStream.use { JSONObject(it.readBoundedText(1_048_576)) }
        } finally {
            connection.disconnect()
        }
    }

    internal fun decodeRelease(data: JSONObject, current: String): Info? {
        val version = data.optString("tag_name").removePrefix("v").trim()
        if (!Regex("""\d+\.\d+\.\d+""").matches(version) ||
            data.optBoolean("draft") || data.optBoolean("prerelease")) return null
        val assets = data.optJSONArray("assets") ?: return null
        var preferred: JSONObject? = null
        for (index in 0 until assets.length()) {
            val item = assets.optJSONObject(index) ?: continue
            val name = item.optString("name")
            if (name.equals("TVQuiz-$version.apk", ignoreCase = true) ||
                name.equals("TVQuiz-TV-$version.apk", ignoreCase = true)) {
                preferred = item
                break
            }
        }
        val address = preferred?.optString("browser_download_url").orEmpty()
        if (!address.startsWith("https://")) return null
        val description = data.optString("body")
        val mandatory = description.contains("[force]", true) || description.contains("force update", true)
        return Info(version, current, mandatory && isNewer(version, current), description.trim(), address)
    }

    fun isNewer(latest: String, current: String): Boolean {
        val left = latest.split('.', '-')
        val right = current.split('.', '-')
        for (index in 0 until maxOf(left.size, right.size)) {
            val compared = (left.getOrNull(index)?.toIntOrNull() ?: 0)
                .compareTo(right.getOrNull(index)?.toIntOrNull() ?: 0)
            if (compared != 0) return compared > 0
        }
        return false
    }

    fun newerThanCurrent(ctx: Context, info: Info?): Info? =
        info?.takeIf { isNewer(it.latest, currentVersion(ctx)) }
}
