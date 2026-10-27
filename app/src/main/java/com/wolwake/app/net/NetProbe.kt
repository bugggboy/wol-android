package com.wolwake.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 开机状态探测：先 ICMP，再尝试常见 TCP 端口。
 * 关机状态下网关通常不会回应任何探测，因此探测失败可视为"仍处于关机"。
 */
object NetProbe {

    private val DEFAULT_PORTS = listOf(445, 3389, 22, 80, 135)

    suspend fun check(host: String, port: Int = 0, timeoutMs: Int = 1200): Boolean =
        withContext(Dispatchers.IO) {
            val target = host.trim()
            if (target.isEmpty()) return@withContext false

            runCatching {
                if (InetAddress.getByName(target).isReachable(timeoutMs)) return@withContext true
            }

            val ports = if (port in 1..65535) listOf(port) else DEFAULT_PORTS
            for (p in ports) {
                runCatching {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(target, p), timeoutMs)
                        return@withContext true
                    }
                }
            }
            false
        }
}
