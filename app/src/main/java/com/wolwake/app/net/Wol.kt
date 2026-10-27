package com.wolwake.app.net

import android.content.Context
import android.net.ConnectivityManager
import com.wolwake.app.data.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Wake-on-LAN 发送端。
 *
 * 报文格式（WOL 标准）：6 字节 0xFF + 目标 MAC 重复 16 次（共 102 字节），
 * 可选追加 6 字节 SecureOn 口令。通过 UDP 广播到端口 9（可改）。
 */
object Wol {

    data class Result(
        val ok: List<String>,
        val fail: List<String>,
        val bytes: Int
    ) {
        val success: Boolean get() = ok.isNotEmpty()
    }

    /** 宽松解析 MAC：接受 AA:BB:CC:DD:EE:FF / AA-BB-... / AABBCCDDEEFF */
    fun parseMac(input: String): ByteArray? {
        val hex = input.filter { it.isLetterOrDigit() }
        if (hex.length != 12) return null
        if (hex.any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(6) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    /** 统一展示为 AA:BB:CC:DD:EE:FF */
    fun formatMac(input: String): String {
        val b = parseMac(input) ?: return input.trim().uppercase()
        return b.joinToString(":") { String.format("%02X", it.toInt() and 0xFF) }
    }

    fun magicPacket(mac: ByteArray, secureOn: ByteArray? = null): ByteArray {
        val pwd = secureOn ?: ByteArray(0)
        val buf = ByteArray(6 + 16 * mac.size + pwd.size)
        for (i in 0 until 6) buf[i] = 0xFF.toByte()
        var idx = 6
        repeat(16) { for (b in mac) buf[idx++] = b }
        if (pwd.isNotEmpty()) System.arraycopy(pwd, 0, buf, idx, pwd.size)
        return buf
    }

    /** 把 "255.255.255.255, auto, 192.168.1.255" 拆成地址列表，剔除 auto 关键字 */
    fun targetsOf(broadcast: String): List<String> =
        broadcast.split(',', ';', ' ', '\n', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("auto", true) }
            .distinct()

    fun usesAuto(broadcast: String): Boolean =
        broadcast.split(',', ';', ' ', '\n', '\t').any { it.trim().equals("auto", true) }

    /** 根据当前联网方式推算子网定向广播地址，例如 192.168.1.23/24 → 192.168.1.255 */
    fun subnetBroadcasts(context: Context): List<String> {
        val out = mutableListOf<String>()
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val props = cm.getLinkProperties(cm.activeNetwork) ?: return@runCatching
            for (la in props.linkAddresses) {
                val addr = la.address
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val ip = addr.address
                    val ipInt = ((ip[0].toInt() and 0xFF) shl 24) or
                        ((ip[1].toInt() and 0xFF) shl 16) or
                        ((ip[2].toInt() and 0xFF) shl 8) or
                        (ip[3].toInt() and 0xFF)
                    val prefix = la.prefixLength.coerceIn(0, 32)
                    val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
                    val bc = ipInt or mask.inv()
                    out += "${(bc ushr 24) and 0xFF}.${(bc ushr 16) and 0xFF}.${(bc ushr 8) and 0xFF}.${bc and 0xFF}"
                }
            }
        }
        return out.distinct()
    }

    /** 发送唤醒包（IO 线程执行），返回实际发出的目标与失败目标 */
    suspend fun wake(context: Context, device: Device): Result = withContext(Dispatchers.IO) {
        val mac = parseMac(device.mac)
            ?: return@withContext Result(emptyList(), listOf("MAC 格式错误"), 0)

        val packet = magicPacket(mac, parseMac(device.secureOn))
        val targets = LinkedHashSet<String>()
        targets += "255.255.255.255"
        targets += targetsOf(device.broadcast)
        if (usesAuto(device.broadcast)) targets += subnetBroadcasts(context)

        val port = if (device.port in 1..65535) device.port else 9
        val ok = mutableListOf<String>()
        val fail = mutableListOf<String>()

        DatagramSocket().use { socket ->
            socket.broadcast = true
            for (t in targets) {
                try {
                    val addr = InetAddress.getByName(t)
                    socket.send(DatagramPacket(packet, packet.size, addr, port))
                    ok += "$t:$port"
                } catch (e: Exception) {
                    fail += "$t(${e.javaClass.simpleName})"
                }
            }
        }
        Result(ok, fail, packet.size)
    }
}
