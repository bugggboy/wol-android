# 网络唤醒 · Android 版（WolWake）

局域网 Wake-on-LAN 工具，Kotlin + Jetpack Compose 单 Activity 应用。

界面沿用"设备列表 + 一键唤醒"这套经典 WOL 工具的交互思路，按手机屏幕重新排布：分组标签、卡片列表、底部唤醒栏。核心协议层不依赖任何第三方库，魔术包构造与发送只有几十行。

## 功能

- **魔术包唤醒**：6×0xFF + 目标 MAC×16（共 102 字节），可选 SecureOn 6 字节口令；UDP 默认端口 9，可按设备修改
- **多目标广播**：一台设备可配多个广播地址；填 `auto` 时自动推算本机所在子网的定向广播（如 `192.168.1.255`），应对会丢弃全局广播的 AP
- **设备分组**：一套列表按分组切换，适合把办公区、机房、测试机分开管理
- **批量唤醒**：多选唤醒 / 单台唤醒 / 全部唤醒，带时间戳的运行日志
- **状态探测**：ICMP + TCP 双路（445 / 3389 / 22 / 80 / 135，或指定端口），唤醒后 6 秒自动复核一次
- **远程关机 / 重启**：向自建 HTTP 接口下发 `shutdown` / `restart`（仓库内含目标机监听脚本）
- **列表互导**：`macs.txt` 双向导入导出，兼容常见的"名称,MAC"纯文本格式，可整份粘贴

## 已构建好的 APK

| 文件 | 说明 |
| --- | --- |
| [`wol-android-1.0.0-debug.apk`](https://github.com/bugggboy/wol-android/releases/latest) | 8.4 MB，debug 签名，可直接侧载 |

- 包名 `com.wolwake.app`，版本 1.0（versionCode 1），Android 7.0+（minSdk 24 / targetSdk 34）
- 安装：手机允许「安装未知来源应用」→ 拷入 APK 点击安装
- 正式分发请自行配置 `signingConfigs` 后 `assembleRelease`

## 目录结构

```
app/src/main/java/com/wolwake/app/
├── MainActivity.kt
├── data/
│   ├── Device.kt          设备模型（MAC / 广播地址 / 端口 / 分组 / 探测地址）
│   └── DeviceStore.kt     JSON 持久化（SharedPreferences，无 Room 依赖）
├── net/
│   ├── Wol.kt             魔术包构造与发送、子网广播推算
│   ├── NetProbe.kt        ICMP + TCP 在线探测
│   └── RemoteCmd.kt       HTTP 远程指令
└── ui/
    ├── HomeScreen.kt      主界面：分组、设备卡片、批量唤醒、日志
    ├── Dialogs.kt         编辑 / 导入导出 / 远程控制对话框
    └── Theme.kt           配色
pc-agent/
└── wol-agent.ps1          目标机上的指令监听脚本（可选）
```

## 编译

前置：JDK 17、Android SDK Platform 34、Build-Tools 34.0.0。

```bash
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
```

输出 `app/build/outputs/apk/debug/app-debug.apk`。也可直接用 Android Studio 打开本目录，Sync 后 Run。

两个环境相关的注意点：

- Android Gradle Plugin 不允许工程路径含中文，遇到会直接报错；本仓库已在 `gradle.properties` 里加 `android.overridePathCheck=true` 跳过该检查。
- `services.gradle.org` 在国内常连接超时，可把 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 换成 `https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip`。

## 远程关机 / 重启（可选）

安卓无法直接写 SMB 共享，因此这里用 HTTP 接口替代共享目录写命令文件的做法。在目标 Windows 机上以管理员身份运行 `pc-agent/wol-agent.ps1`：

```powershell
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://+:8080/")
$listener.Start()

while ($true) {
    $ctx = $listener.GetContext()
    $cmd = $ctx.Request.QueryString["do"]
    $bytes = [Text.Encoding]::UTF8.GetBytes("ok:$cmd")
    $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
    $ctx.Response.Close()

    switch ($cmd) {
        "shutdown" { shutdown.exe /s /t 5 /f }
        "restart"  { shutdown.exe /r /t 5 /f }
    }
}
```

App 里把该设备的「远程指令接口」填成 `http://192.168.1.100:8080/cmd?do={cmd}` 即可，`{cmd}` 会被替换为 `shutdown` 或 `restart`。

Windows 防火墙需放行 8080/TCP。**不建议把该端口暴露到公网**：脚本收到指令即执行系统关机，没有任何鉴权。

## 常见问题

| 现象 | 原因 / 处理 |
| --- | --- |
| 提示「已发送」但机器没起来 | 目标机关机后网卡掉电：进 BIOS 开 `Wake on LAN` / `PCI-E Power On`，Windows 网卡属性勾选「允许此设备唤醒计算机」+「魔术包」，并关闭「快速启动」 |
| 只有连同一个 Wi-Fi 才成功 | WOL 是二层广播，手机在别的网段时路由器不转发；改连同一 LAN，或在路由器上把 `UDP 9` 定向广播到目标网段 |
| 只有 255.255.255.255 成功、定向地址失败 | 部分 AP 开启了客户端隔离；把广播地址填成 `auto`（自动算 `192.168.x.255`）或手填子网广播地址 |
| 状态一直显示离线 | 设备未填「状态检测地址」；或目标机防火墙屏蔽 ICMP/TCP。可直接填一个确定开放的端口，如 `3389` |
| 唤醒后要等多久 | 冷启动一般 20–60 秒；App 在 6 秒时先复核一次，之后可手动点「检测」 |

## 已知限制

- 需要前台操作，不做后台常驻/定时唤醒（安卓侧若要实现需前台服务，目前未做）
- 不支持跨路由器的自动唤醒，跨网段需在路由器上配定向广播转发
- 仅 IPv4
