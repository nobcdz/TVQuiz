package com.nobcdz.tvquiz

import android.content.Context
import android.content.res.AssetManager
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class LanServer(
    context: Context,
    store: LocalStore,
    assets: AssetManager,
    private val port: Int = 8099,
    household: Household = DeviceRuntime.household
) {
    private var listener: ServerSocket? = null
    private val endpoint = LanEndpoints(context, store, household, assets) { localIp().orEmpty() to boundPort }

    @Volatile var boundPort: Int = port
        private set

    @Synchronized
    fun start() {
        if (listener != null) return
        val server = listOf(port, 8090, 0).distinct().firstNotNullOfOrNull { candidate ->
            try { ServerSocket(candidate) } catch (_: IOException) { null }
        } ?: error("无法打开本地答题服务")
        val executor = ThreadPoolExecutor(3, 3, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(24))
        boundPort = server.localPort
        listener = server
        Thread({
            try {
                while (!server.isClosed) {
                    val peer = server.accept()
                    try { executor.execute { serve(peer) } }
                    catch (_: RejectedExecutionException) { peer.close() }
                }
            } catch (e: IOException) {
                if (!server.isClosed) android.util.Log.w("TVQuizLan", "Listener stopped", e)
            } finally {
                executor.shutdown()
                synchronized(this) {
                    if (listener === server) listener = null
                }
                runCatching { server.close() }
            }
        }, "tvquiz-lan").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun stop() {
        val server = listener ?: return
        listener = null
        runCatching { server.close() }
    }

    private fun serve(peer: Socket) {
        peer.use { socket ->
            try {
                val request = LanHttp.read(socket) ?: return
                val response = if (request.isSameOrigin()) endpoint.respond(request)
                    else LanReply.error(403, "origin_not_allowed")
                LanHttp.write(socket, response)
            } catch (e: LanHttp.BadRequest) {
                runCatching { LanHttp.write(socket, LanReply.error(e.status, "bad_request")) }
            } catch (_: IOException) {
                // A timed-out or disconnected client has no response channel.
            }
        }
    }

    fun localIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()
}
