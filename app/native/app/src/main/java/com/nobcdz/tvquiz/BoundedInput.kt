package com.nobcdz.tvquiz

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

internal fun InputStream.readBoundedText(maxBytes: Int): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (count > maxBytes - output.size()) throw IOException("Response exceeds limit")
        output.write(buffer, 0, count)
    }
    return output.toString("UTF-8")
}
