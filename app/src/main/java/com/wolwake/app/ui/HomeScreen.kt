package com.wolwake.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wolwake.app.data.Device
import com.wolwake.app.data.DeviceStore
import com.wolwake.app.net.NetProbe
import com.wolwake.app.net.RemoteCmd
import com.wolwake.app.net.Wol
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { DeviceStore(context) }

    var devices by remember { mutableStateOf<List<Device>>(store.load()) }
    var groupFilter by remember { mutableStateOf("全部") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var statuses by remember { mutableStateOf<Map<String, Boolean?>>(emptyMap()) }
    var logs by remember { mutableStateOf(listOf<String>()) }
    var logExpanded by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editorFor by remember { mutableStateOf<Device?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var remoteFor by remember { mutableStateOf<Device?>(null) }
    var pendingDelete by remember { mutableStateOf<Device?>(null) }

    fun addLog(msg: String) {
        logs = (listOf("${nowText()}  $msg") + logs).take(80)
    }

    fun persist(list: List<Device>) {
        devices = list
        store.save(list)
    }

    fun wakeAll(list: List<Device>) {
        val targets = list.filter { Wol.parseMac(it.mac) != null }
        if (targets.isEmpty()) {
            addLog("没有可唤醒的设备")
            return
        }
        scope.launch {
            addLog("开始唤醒 ${targets.size} 台设备")
            for (d in targets) {
                runCatching { Wol.wake(context, d) }
                    .onSuccess { r ->
                        if (r.success) {
                            addLog("${d.name}：已发送 ${r.bytes}B → ${r.ok.joinToString(" ")}")
                            if (r.fail.isNotEmpty()) addLog("${d.name}：部分地址失败 ${r.fail.joinToString(" ")}")
                        } else {
                            addLog("${d.name}：发送失败 ${r.fail.joinToString(" ")}")
                        }
                    }
                    .onFailure { addLog("${d.name}：异常 ${it.message ?: it.javaClass.simpleName}") }
            }
            val verify = targets.filter { it.checkHost.isNotBlank() }
            if (verify.isNotEmpty()) {
                delay(6000)
                verify.forEach { d ->
                    scope.launch {
                        val online = NetProbe.check(d.checkHost, d.checkPort)
                        statuses = statuses + (d.id to online)
                        addLog("${d.name}：${if (online) "已上线" else "暂时仍离线"}（${d.checkHost}）")
                    }
                }
            }
        }
    }

    fun probeAll(list: List<Device>) {
        val targets = list.filter { it.checkHost.isNotBlank() }
        if (targets.isEmpty()) {
            addLog("没有设备配置「状态检测地址」，无法探测")
            return
        }
        targets.forEach { d -> statuses = statuses + (d.id to null) }
        scope.launch {
            targets.forEach { d ->
                val online = NetProbe.check(d.checkHost, d.checkPort)
                statuses = statuses + (d.id to online)
            }
            addLog("状态检测完成：${targets.count { statuses[it.id] == true }}/${targets.size} 在线")
        }
    }

    fun saveDevice(d: Device) {
        val exists = devices.any { it.id == d.id }
        persist(if (exists) devices.map { if (it.id == d.id) d else it } else devices + d)
        addLog(if (exists) "已更新 ${d.name}" else "已新增 ${d.name}")
        editorOpen = false
    }

    fun removeDevice(d: Device) {
        persist(devices.filterNot { it.id == d.id })
        selected = selected - d.id
        addLog("已删除 ${d.name}")
    }

    fun doImport(text: String, group: String) {
        val (list, bad) = parseMacLines(text, group, store.defaultPort, store.defaultBroadcast)
        if (list.isEmpty()) {
            addLog("导入失败：未识别到 MAC（$bad 行无效）")
        } else {
            persist(devices + list)
            addLog("导入 ${list.size} 台设备" + if (bad > 0) "，跳过 $bad 行" else "")
        }
        importing = false
    }

    val groups = listOf("全部") + devices.map { it.group }.distinct().sorted()
    val shown = if (groupFilter == "全部") devices else devices.filter { it.group == groupFilter }
    val selectedDevices = devices.filter { it.id in selected }

    // 分组被删空时自动回到「全部」，避免停留在空列表
    LaunchedEffect(groups) {
        if (groupFilter != "全部" && groupFilter !in groups) groupFilter = "全部"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("网络唤醒", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Wake-on-LAN · 局域网广播", fontSize = 11.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { probeAll(shown) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "检测状态")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.Share, contentDescription = "导入导出")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("从剪贴板 / 文本导入") },
                                onClick = { menuOpen = false; importing = true }
                            )
                            DropdownMenuItem(
                                text = { Text("导出为 macs.txt") },
                                onClick = { menuOpen = false; exporting = true }
                            )
                            DropdownMenuItem(
                                text = { Text("全部检测状态") },
                                onClick = { menuOpen = false; probeAll(shown) }
                            )
                            DropdownMenuItem(
                                text = { Text("清空全部设备") },
                                onClick = {
                                    menuOpen = false
                                    persist(emptyList())
                                    selected = emptySet()
                                    addLog("已清空全部设备")
                                }
                            )
                        }
                    }
                    IconButton(onClick = { editorFor = null; editorOpen = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新增设备")
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
                Column(Modifier.navigationBarsPadding()) {
                    if (logExpanded) {
                        LogPanel(logs) {
                            logs = emptyList()
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { wakeAll(selectedDevices) },
                            enabled = selectedDevices.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("唤醒选中 (${selectedDevices.size})")
                        }
                        OutlinedButton(
                            onClick = { wakeAll(shown) },
                            enabled = shown.isNotEmpty()
                        ) { Text("全部") }
                        IconButton(onClick = { logExpanded = !logExpanded }) {
                            Icon(Icons.Default.List, contentDescription = "日志")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            GroupChips(groups, groupFilter) { g ->
                groupFilter = g
                if (g != "全部") {
                    selected = selected.filter { id -> devices.any { it.id == id && it.group == g } }.toSet()
                }
            }
            Text(
                "共 ${shown.size} 台 · 已选 ${selected.size}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有设备", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "点击右上角 + 新增，或从 macs.txt 批量导入",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(shown, key = { it.id }) { d ->
                        DeviceCard(
                            device = d,
                            checked = d.id in selected,
                            status = statuses[d.id],
                            onCheckedChange = { on ->
                                selected = if (on) selected + d.id else selected - d.id
                            },
                            onWake = { wakeAll(listOf(d)) },
                            onProbe = { probeAll(listOf(d)) },
                            onEdit = { editorFor = d; editorOpen = true },
                            onRemote = { remoteFor = d },
                            onDelete = { pendingDelete = d }
                        )
                    }
                }
            }
        }
    }

    if (editorOpen) {
        EditorDialog(
            device = editorFor,
            defaultBroadcast = store.defaultBroadcast,
            defaultPort = store.defaultPort,
            groups = groups.filter { it != "全部" },
            onDismiss = { editorOpen = false },
            onSave = { saveDevice(it) }
        )
    }

    if (importing) {
        TextDialog(
            title = "导入设备",
            hint = "每行一条：名称,MAC[,广播地址,端口,分组]。也可直接粘贴现有 macs.txt；已自动读取当前剪贴板。",
            initial = clipboardText(context),
            confirmText = "导入",
            onDismiss = { importing = false },
            onConfirm = { text -> doImport(text, if (groupFilter == "全部") "默认" else groupFilter) }
        )
    }

    if (exporting) {
        TextDialog(
            title = "导出设备",
            hint = "点「复制」后保存为 macs.txt，其他 WOL 工具可直接继续使用。",
            initial = devicesToText(devices),
            confirmText = "复制",
            onDismiss = { exporting = false },
            onConfirm = { text ->
                setClipboard(context, text)
                addLog("已复制 ${devices.size} 台设备到剪贴板")
                exporting = false
            }
        )
    }

    remoteFor?.let { d ->
        RemoteDialog(
            device = d,
            onDismiss = { remoteFor = null },
            onSend = { url, cmd ->
                persist(devices.map { if (it.id == d.id) it.copy(remoteUrl = url) else it })
                remoteFor = null
                scope.launch {
                    RemoteCmd.send(url, cmd)
                        .onSuccess { addLog("${d.name}：$it") }
                        .onFailure { addLog("${d.name}：远程指令失败 ${it.message ?: it.javaClass.simpleName}") }
                }
            }
        )
    }

    pendingDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除设备") },
            text = { Text("确定删除「${d.name}」？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    removeDevice(d)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun GroupChips(groups: List<String>, current: String, onSelect: (String) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(groups) { g ->
            FilterChip(
                selected = g == current,
                onClick = { onSelect(g) },
                label = { Text(g, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
private fun DeviceCard(
    device: Device,
    checked: Boolean,
    status: Boolean?,
    onCheckedChange: (Boolean) -> Unit,
    onWake: () -> Unit,
    onProbe: () -> Unit,
    onEdit: () -> Unit,
    onRemote: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.padding(top = 2.dp)
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        device.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(status)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    Wol.formatMac(device.mac),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${device.group} · 广播 ${device.broadcast} · UDP ${device.port}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (device.checkHost.isNotBlank()) {
                    Text(
                        "检测 ${device.checkHost}" + if (device.checkPort > 0) ":${device.checkPort}" else "",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = onWake,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) { Text("唤醒", fontSize = 13.sp) }
                    TextButton(
                        onClick = onProbe,
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) { Text("检测", fontSize = 13.sp) }
                    Box {
                        TextButton(
                            onClick = { menu = true },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("更多", fontSize = 13.sp) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = false; onEdit() })
                            DropdownMenuItem(text = { Text("远程关机 / 重启") }, onClick = { menu = false; onRemote() })
                            DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onDelete() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: Boolean?) {
    val (label, color) = when (status) {
        true -> "在线" to Color(0xFF15803D)
        false -> "离线" to Color(0xFFB91C1C)
        null -> "未检测" to Color(0xFF6B7280)
    }
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = color)
    }
}

@Composable
private fun LogPanel(logs: List<String>, onClear: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .padding(horizontal = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("运行日志", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("清空", fontSize = 12.sp)
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(logs) { line ->
                Text(
                    line,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}

private fun nowText(): String =
    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
