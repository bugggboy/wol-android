package com.wolwake.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wolwake.app.data.Device
import com.wolwake.app.net.RemoteCmd
import com.wolwake.app.net.Wol
import java.util.UUID

/* ---------- 剪贴板 ---------- */

fun clipboardText(context: Context): String {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
}

fun setClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("macs", text))
}

/* ---------- 文本导入 / 导出 ---------- */

/**
 * 兼容两种 macs.txt 写法：
 *   名称,MAC               （简写，地址/端口/分组用默认值）
 *   名称,MAC,广播,端口,分组 （本 App 导出的完整格式）
 * 也支持空格/制表符分隔、以 # 开头的注释行。
 */
fun parseMacLines(text: String, group: String, port: Int, broadcast: String): Pair<List<Device>, Int> {
    val macRegex = Regex("(?i)([0-9a-f]{2}[:-]?){5}[0-9a-f]{2}")
    val out = mutableListOf<Device>()
    var bad = 0

    text.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEach

        val parts = line.split(',', ';', '|', '\t').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size >= 2 && Wol.parseMac(parts[1]) != null) {
            out += Device(
                id = UUID.randomUUID().toString(),
                name = parts[0].ifBlank { Wol.formatMac(parts[1]) },
                mac = Wol.formatMac(parts[1]),
                broadcast = parts.getOrNull(2)?.ifBlank { broadcast } ?: broadcast,
                port = parts.getOrNull(3)?.toIntOrNull() ?: port,
                group = parts.getOrNull(4)?.ifBlank { group } ?: group
            )
            return@forEach
        }

        val mac = macRegex.find(line)?.value
        if (mac == null) {
            bad++
            return@forEach
        }
        val name = line.replace(mac, " ").trim().trim(',', ';', '|', '-', '\t', ' ').ifBlank { Wol.formatMac(mac) }
        out += Device(
            id = UUID.randomUUID().toString(),
            name = name,
            mac = Wol.formatMac(mac),
            broadcast = broadcast,
            port = port,
            group = group
        )
    }
    return out to bad
}

fun devicesToText(devices: List<Device>): String {
    val head = "# 名称,MAC,广播地址,端口,分组（可直接编辑后重新导入）\n"
    return head + devices.joinToString("\n") {
        "${it.name},${it.mac},${it.broadcast},${it.port},${it.group}"
    }
}

/* ---------- 对话框 ---------- */

@Composable
fun EditorDialog(
    device: Device?,
    defaultBroadcast: String,
    defaultPort: Int,
    groups: List<String>,
    onDismiss: () -> Unit,
    onSave: (Device) -> Unit
) {
    var name by remember(device) { mutableStateOf(device?.name ?: "") }
    var mac by remember(device) { mutableStateOf(device?.mac ?: "") }
    var broadcast by remember(device) { mutableStateOf(device?.broadcast ?: defaultBroadcast) }
    var portText by remember(device) { mutableStateOf((device?.port ?: defaultPort).toString()) }
    var group by remember(device) { mutableStateOf(device?.group ?: groups.firstOrNull() ?: "默认") }
    var checkHost by remember(device) { mutableStateOf(device?.checkHost ?: "") }
    var checkPortText by remember(device) {
        mutableStateOf(((device?.checkPort ?: 0)).takeIf { it > 0 }?.toString() ?: "")
    }
    var remoteUrl by remember(device) { mutableStateOf(device?.remoteUrl ?: "") }
    var secureOn by remember(device) { mutableStateOf(device?.secureOn ?: "") }
    var error by remember(device) { mutableStateOf("") }
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (device == null) "新增设备" else "编辑设备") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DialogField("名称", name) { name = it }
                DialogField("MAC 地址", mac) { mac = it }
                DialogField("广播地址（auto = 自动子网广播）", broadcast) { broadcast = it }
                DialogField("端口（默认 9）", portText) { portText = it }
                DialogField("分组（如 办公区 / 机房 / 测试）", group) { group = it }
                DialogField("状态检测地址（可选，IP）", checkHost) { checkHost = it }
                DialogField("状态检测端口（可选）", checkPortText) { checkPortText = it }
                DialogField("远程指令接口（可选，含 {cmd}）", remoteUrl) { remoteUrl = it }
                DialogField("SecureOn 口令（可选）", secureOn) { secureOn = it }
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (Wol.parseMac(mac) == null) {
                    error = "MAC 需为 12 位十六进制，例如 00:1A:2B:3C:4D:5E"
                    return@TextButton
                }
                if (name.isBlank()) {
                    error = "请填写设备名称"
                    return@TextButton
                }
                onSave(
                    Device(
                        id = device?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        mac = Wol.formatMac(mac),
                        broadcast = broadcast.trim().ifBlank { defaultBroadcast },
                        port = (portText.trim().toIntOrNull() ?: defaultPort).coerceIn(1, 65535),
                        group = group.trim().ifBlank { "默认" },
                        checkHost = checkHost.trim(),
                        checkPort = (checkPortText.trim().toIntOrNull() ?: 0).coerceIn(0, 65535),
                        secureOn = secureOn.trim(),
                        remoteUrl = remoteUrl.trim()
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun TextDialog(
    title: String,
    hint: String,
    initial: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 300.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun RemoteDialog(
    device: Device,
    onDismiss: () -> Unit,
    onSend: (url: String, cmd: String) -> Unit
) {
    var url by remember(device) { mutableStateOf(device.remoteUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("远程控制 · ${device.name}") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("接口模板（{cmd} 是命令占位符）") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "示例：http://192.168.1.10:8080/cmd?do={cmd}\n目标机需运行 README 中的 PowerShell 监听脚本。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onSend(url, RemoteCmd.SHUTDOWN) }) { Text("关机") }
                TextButton(onClick = { onSend(url, RemoteCmd.RESTART) }) { Text("重启") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun DialogField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}
