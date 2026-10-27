package com.wolwake.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 设备列表持久化。用 SharedPreferences + JSON，避免引入 Room 的注解处理器，
 * 这样工程开箱即可编译，也便于用文本方式导入导出（与 macs.txt 纯文本格式互通）。
 */
class DeviceStore(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): MutableList<Device> {
        val raw = sp.getString(KEY_DEVICES, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Device(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    mac = o.optString("mac"),
                    broadcast = o.optString("broadcast", "255.255.255.255"),
                    port = o.optInt("port", 9),
                    group = o.optString("group", "默认"),
                    checkHost = o.optString("checkHost", ""),
                    checkPort = o.optInt("checkPort", 0),
                    secureOn = o.optString("secureOn", ""),
                    remoteUrl = o.optString("remoteUrl", "")
                )
            }
        }.getOrElse { mutableListOf() }
    }

    fun save(devices: List<Device>) {
        val arr = JSONArray()
        devices.forEach { d ->
            arr.put(
                JSONObject().apply {
                    put("id", d.id)
                    put("name", d.name)
                    put("mac", d.mac)
                    put("broadcast", d.broadcast)
                    put("port", d.port)
                    put("group", d.group)
                    put("checkHost", d.checkHost)
                    put("checkPort", d.checkPort)
                    put("secureOn", d.secureOn)
                    put("remoteUrl", d.remoteUrl)
                }
            )
        }
        sp.edit().putString(KEY_DEVICES, arr.toString()).apply()
    }

    /** 新增设备时使用的默认端口 / 默认广播地址，即新增设备时的预填值 */
    var defaultPort: Int
        get() = sp.getInt(KEY_DEFAULT_PORT, 9)
        set(value) = sp.edit().putInt(KEY_DEFAULT_PORT, value).apply()

    var defaultBroadcast: String
        get() = sp.getString(KEY_DEFAULT_BC, "255.255.255.255") ?: "255.255.255.255"
        set(value) = sp.edit().putString(KEY_DEFAULT_BC, value).apply()

    companion object {
        private const val PREFS = "wol_store"
        private const val KEY_DEVICES = "devices"
        private const val KEY_DEFAULT_PORT = "default_port"
        private const val KEY_DEFAULT_BC = "default_broadcast"
    }
}
