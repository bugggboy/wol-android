package com.wolwake.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 远程关机 / 重启。
 *
 * 不少桌面端 WOL 工具是通过往共享目录写命令文件来触发关机的，安卓端无法直接写 SMB 共享，
 * 因此改为调用目标机上的一个极简 HTTP 接口（URL 模板中的 {cmd} 会被替换）：
 *
 *   http://192.168.1.100:8080/cmd?do={cmd}
 *
 * 目标机可用仓库内 pc-agent/wol-agent.ps1 起一个监听脚本，语义等价于写命令文件。
 */
object RemoteCmd {

    const val SHUTDOWN = "shutdown"
    const val RESTART = "restart"

    suspend fun send(urlTemplate: String, cmd: String, timeoutMs: Int = 4000): Result<String> =
        withContext(Dispatchers.IO) {
            if (urlTemplate.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("未配置远程指令接口"))
            }
            runCatching {
                val url = URL(urlTemplate.replace("{cmd}", cmd))
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.requestMethod = "GET"
                try {
                    val code = conn.responseCode
                    if (code in 200..299) "已下发 $cmd（HTTP $code）" else "下发 $cmd 失败（HTTP $code）"
                } finally {
                    conn.disconnect()
                }
            }
        }
}
