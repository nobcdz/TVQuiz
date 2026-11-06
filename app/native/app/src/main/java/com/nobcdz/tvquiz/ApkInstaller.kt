package com.nobcdz.tvquiz

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ApkInstaller {
    private const val MAX_APK_BYTES = 128L * 1024 * 1024

    fun apkFile(ctx: Context): File = File(File(ctx.cacheDir, "updates"), "update.apk")

    @Suppress("DEPRECATION")
    private fun isUpgrade(ctx: Context, candidate: File, version: String): Boolean {
        if (!candidate.isFile || candidate.length() > MAX_APK_BYTES) return false
        val manager = ctx.packageManager
        val flags = PackageManager.GET_SIGNATURES
        val incoming = manager.getPackageArchiveInfo(candidate.path, flags) ?: return false
        if (incoming.packageName != ctx.packageName || incoming.versionName != version) return false
        val installed = manager.getPackageInfo(ctx.packageName, flags)
        val signers = incoming.signatures.orEmpty().toSet()
        return incoming.versionCode > installed.versionCode &&
            signers.isNotEmpty() && signers == installed.signatures.orEmpty().toSet()
    }

    fun alreadyDownloaded(ctx: Context, version: String): Boolean = isUpgrade(ctx, apkFile(ctx), version)

    @Synchronized
    fun download(ctx: Context, url: String, version: String, onProgress: (Int) -> Unit): File? {
        val destination = apkFile(ctx)
        val folder = requireNotNull(destination.parentFile)
        val staged = File(folder, "update.pending.apk")
        return runCatching {
            folder.mkdirs()
            require(url.startsWith("https://"))
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("User-Agent", "TVQuiz")
                require(connection.responseCode in 200..299)
                val expected = connection.getHeaderField("Content-Length")?.toLongOrNull()
                require(expected == null || expected in 1..MAX_APK_BYTES)
                var transferred = 0L
                connection.inputStream.use { source ->
                    staged.outputStream().buffered().use { target ->
                        val buffer = ByteArray(32768)
                        while (true) {
                            val size = source.read(buffer)
                            if (size < 0) break
                            transferred += size
                            require(transferred <= MAX_APK_BYTES)
                            target.write(buffer, 0, size)
                            if (expected != null) onProgress((transferred * 100 / expected).toInt().coerceIn(0, 99))
                        }
                    }
                }
                require(expected == null || expected == transferred)
                require(isUpgrade(ctx, staged, version))
                check(staged.renameTo(destination))
                onProgress(100)
                destination
            } finally {
                connection.disconnect()
                staged.delete()
            }
        }.getOrNull()
    }

    fun install(ctx: Context, file: File, version: String): Boolean = runCatching {
        require(file == apkFile(ctx) && isUpgrade(ctx, file, version))
        val content = if (Build.VERSION.SDK_INT >= 24) ApkProvider.uriFor(ctx, file) else Uri.fromFile(file)
        val request = Intent(Intent.ACTION_VIEW).setDataAndType(content, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (ctx !is Activity) request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        GateService.openingSystemPage()
        ctx.startActivity(request)
    }.isSuccess
}
