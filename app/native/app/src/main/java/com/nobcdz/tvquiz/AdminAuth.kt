package com.nobcdz.tvquiz

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Retains the credential format used by installed versions. Sessions live only in memory. */
object AdminAuth {
    private const val MAX_FAILURES = 5
    private const val RETRY_DELAY = 60_000L
    private const val SESSION_LIFETIME = 43_200_000L
    private val entropy = SecureRandom()
    private val expirationByToken = HashMap<String, Long>()
    val deviceToken: String = newToken()

    fun configured(context: Context): Boolean = credentials(context).contains("hash")

    @Synchronized
    fun setPassword(context: Context, value: String) {
        require(acceptable(value)) { "管理密码需要 6–64 位" }
        val salt = bytes(16)
        val encodedHash = encode(derive(value, salt))
        check(credentials(context).edit()
            .putString("salt", encode(salt))
            .putString("hash", encodedHash)
            .remove("failures")
            .remove("blocked_until")
            .commit()) { "无法保存管理密码" }
        expirationByToken.clear()
    }

    @Synchronized
    fun verify(context: Context, value: String): Boolean {
        if (!acceptable(value)) return false
        val settings = credentials(context)
        val now = System.currentTimeMillis()
        if (settings.getLong("blocked_until", 0L) > now) return false
        val saved = settings.getString("hash", null) ?: return false
        val salt = settings.getString("salt", null) ?: return false
        val matches = try {
            MessageDigest.isEqual(Base64.decode(saved, Base64.NO_WRAP),
                derive(value, Base64.decode(salt, Base64.NO_WRAP)))
        } catch (_: IllegalArgumentException) {
            false
        }
        val editor = settings.edit()
        if (matches) {
            editor.remove("failures").remove("blocked_until").apply()
        } else {
            val failures = settings.getInt("failures", 0) + 1
            if (failures >= MAX_FAILURES) editor.remove("failures").putLong("blocked_until", now + RETRY_DELAY)
            else editor.putInt("failures", failures)
            editor.commit()
        }
        return matches
    }

    @Synchronized
    fun login(context: Context, value: String): String? {
        if (!verify(context, value)) return null
        val now = SystemClock.elapsedRealtime()
        val sessions = expirationByToken.entries.iterator()
        while (sessions.hasNext()) {
            if (sessions.next().value <= now) sessions.remove()
        }
        val token = newToken()
        expirationByToken[token] = now + SESSION_LIFETIME
        return token
    }

    @Synchronized
    fun valid(token: String?): Boolean {
        val expiry = expirationByToken[token] ?: return false
        return SystemClock.elapsedRealtime() < expiry
    }

    @Synchronized
    fun logout(token: String?) {
        expirationByToken.remove(token)
    }

    private fun acceptable(value: String): Boolean = value.length in 6..64

    private fun credentials(context: Context): SharedPreferences =
        context.getSharedPreferences("parent_access", Context.MODE_PRIVATE)

    private fun derive(value: String, salt: ByteArray): ByteArray {
        val key = PBEKeySpec(value.toCharArray(), salt, 60_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(key).encoded
        } finally {
            key.clearPassword()
        }
    }

    private fun bytes(count: Int): ByteArray = ByteArray(count).also(entropy::nextBytes)
    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun newToken(): String = bytes(32).joinToString("") { byte ->
        (byte.toInt() and 255).toString(16).padStart(2, '0')
    }
}
