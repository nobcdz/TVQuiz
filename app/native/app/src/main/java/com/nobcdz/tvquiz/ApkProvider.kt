package com.nobcdz.tvquiz

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/** Exposes only the verified update filename through the installer's one-time URI grant. */
class ApkProvider : ContentProvider() {
    override fun onCreate() = true

    private fun resolve(uri: Uri): File? {
        val application = context ?: return null
        val update = ApkInstaller.apkFile(application)
        return update.takeIf { uri == uriFor(application, it) }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val update = resolve(uri)
        if (mode != "r" || update == null || !update.isFile) throw FileNotFoundException()
        return ParcelFileDescriptor.open(update, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? =
        if (resolve(uri) == null) null else "application/vnd.android.package-archive"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        fun uriFor(context: Context, file: File): Uri =
            Uri.Builder().scheme("content").authority(context.packageName + ".apk")
                .appendPath(file.name).build()
    }
}
