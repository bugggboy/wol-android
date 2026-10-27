package com.wolwake.app.data

/**
 * 一台待唤醒设备。
 *
 * @param broadcast 目标地址，支持多个，用逗号/分号/空格分隔；填 auto 时自动追加本机所在子网的定向广播地址。
 *                  发送时始终额外广播一次 255.255.255.255，兼容只认全局广播的网卡 / AP。
 * @param checkHost 唤醒后用于判断是否开机的地址（IP 或主机名），留空则不做状态探测。
 * @param checkPort 探测端口，0 表示自动尝试常见端口（445/3389/22/80/135）。
 * @param secureOn  SecureOn 口令（6 字节，12 位十六进制），普通网卡留空。
 * @param remoteUrl 远程关机/重启接口模板，{cmd} 会被替换为 shutdown / restart。
 */
data class Device(
    val id: String,
    val name: String,
    val mac: String,
    val broadcast: String = "255.255.255.255",
    val port: Int = 9,
    val group: String = "默认",
    val checkHost: String = "",
    val checkPort: Int = 0,
    val secureOn: String = "",
    val remoteUrl: String = ""
)
