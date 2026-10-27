# wol-agent.ps1 —— 目标机上的极简远程指令监听（可选组件，按需启用）
# 用法：右键「使用 PowerShell 运行」，或在管理员 PowerShell 中执行
#   Set-ExecutionPolicy -Scope Process Bypass -Force; .\wol-agent.ps1

$port = 8080
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://+:$port/")
$listener.Start()
Write-Host "WOL agent listening on :$port  (GET /cmd?do=shutdown|restart)"

while ($true) {
    $ctx = $listener.GetContext()
    $cmd = $ctx.Request.QueryString["do"]
    $bytes = [Text.Encoding]::UTF8.GetBytes("ok:$cmd")
    $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
    $ctx.Response.Close()

    Write-Host "$(Get-Date -Format 'HH:mm:ss') 收到指令: $cmd"
    switch ($cmd) {
        "shutdown" { shutdown.exe /s /t 5 /f }
        "restart"  { shutdown.exe /r /t 5 /f }
    }
}
