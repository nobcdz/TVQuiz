package com.nobcdz.tvquiz

import android.os.SystemClock
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

internal data class LanRequest(
    val method: String,
    val path: String,
    private val query: String,
    val headers: Map<String, String>,
    val body: String,
    val loopback: Boolean
) {
    fun param(name: String): String? = query.split('&').asReversed().firstOrNull {
        it.substringBefore('=') == name
    }?.let { URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }

    fun isSameOrigin(): Boolean {
        val origin = headers["origin"] ?: return true
        val host = headers["host"] ?: return false
        val supplied = runCatching { URI(origin) }.getOrNull() ?: return false
        return supplied.scheme == "http" && supplied.rawAuthority.equals(host, ignoreCase = true) &&
            supplied.rawPath.isNullOrEmpty() && supplied.rawQuery == null &&
            supplied.rawFragment == null && supplied.rawUserInfo == null
    }
}

internal data class LanReply(val status: Int, val mime: String?, val bytes: ByteArray) {
    companion object {
        fun json(value: JSONObject, status: Int = 200) =
            LanReply(status, "application/json; charset=utf-8", value.toString().toByteArray(Charsets.UTF_8))

        fun error(status: Int, reason: String) = json(JSONObject().put("error", reason), status)
    }
}

internal object LanHttp {
    private const val MAX_HEADERS = 16_384
    private const val MAX_LINE = 8192
    private const val MAX_BODY = 4_000_000
    private const val REQUEST_TIMEOUT_MS = 8000L

    class BadRequest(val status: Int = 400) : IOException()

    fun read(socket: Socket): LanRequest? {
        socket.soTimeout = REQUEST_TIMEOUT_MS.toInt()
        val started = SystemClock.elapsedRealtime()
        val source = BufferedInputStream(socket.getInputStream())
        val line = line(source, started) ?: return null
        val pieces = line.split(' ', limit = 3)
        if (pieces.size != 3 || !pieces[1].startsWith('/') ||
            pieces[2] !in listOf("HTTP/1.0", "HTTP/1.1")) throw BadRequest()
        val headers = linkedMapOf<String, String>()
        var received = 0
        while (true) {
            val field = line(source, started) ?: throw BadRequest()
            if (field.isEmpty()) break
            received += field.length + 2
            if (received > MAX_HEADERS) throw BadRequest(431)
            val colon = field.indexOf(':')
            if (colon < 1) throw BadRequest()
            val name = field.substring(0, colon).lowercase(Locale.ROOT)
            if (!name.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }) throw BadRequest()
            if (name in headers && name in setOf("content-length", "host", "origin", "authorization",
                    "transfer-encoding", "x-device-token", "x-admin-key", "x-admin-token", "x-child-profile")) throw BadRequest()
            headers[name] = field.substring(colon + 1).trim()
        }
        if (headers.containsKey("transfer-encoding")) throw BadRequest()
        val length = headers["content-length"]?.let { it.toIntOrNull() ?: throw BadRequest() } ?: 0
        if (length !in 0..MAX_BODY) throw BadRequest(413)
        val data = ByteArray(length)
        var offset = 0
        while (offset < data.size) {
            checkTime(started)
            val count = source.read(data, offset, data.size - offset)
            if (count < 0) throw BadRequest()
            offset += count
        }
        checkTime(started)
        return LanRequest(pieces[0].uppercase(Locale.ROOT), pieces[1].substringBefore('?'),
            pieces[1].substringAfter('?', ""), headers, String(data, Charsets.UTF_8),
            socket.inetAddress.isLoopbackAddress)
    }

    private fun line(source: BufferedInputStream, started: Long): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            checkTime(started)
            val next = source.read()
            if (next < 0) return null
            if (next == 10) return bytes.toString("UTF-8").removeSuffix("\r")
            if (bytes.size() == MAX_LINE) throw BadRequest(431)
            bytes.write(next)
        }
    }

    private fun checkTime(started: Long) {
        if (SystemClock.elapsedRealtime() - started >= REQUEST_TIMEOUT_MS) throw BadRequest(408)
    }

    fun write(socket: Socket, response: LanReply) {
        val label = when (response.status) {
            204 -> "No Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            408 -> "Request Timeout"
            413 -> "Payload Too Large"
            431 -> "Request Header Fields Too Large"
            else -> "OK"
        }
        val headers = listOfNotNull(
            "HTTP/1.1 ${response.status} $label",
            response.mime?.let { "Content-Type: $it" },
            "Content-Length: ${response.bytes.size}",
            "Connection: close",
            "Cache-Control: no-store",
            "X-Content-Type-Options: nosniff",
            "Content-Security-Policy: frame-src 'none'; object-src 'none'; base-uri 'none'"
        )
        socket.getOutputStream().apply {
            write((headers.joinToString("\r\n") + "\r\n\r\n").toByteArray(Charsets.UTF_8))
            write(response.bytes)
            flush()
        }
    }
}
