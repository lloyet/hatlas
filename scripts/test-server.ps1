<#
.SYNOPSIS
  Controls the local Paper 1.21.11 test server used for in-game bot tests (VitaminMCP).

.DESCRIPTION
  Usage: powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-server.ps1 <start|stop|restart|deploy|status|console "<cmd>">

  - start   : rotates logs/latest.log, launches the server in its own minimized console window,
              waits for "Done (" in the log.
  - stop    : sends "stop" over RCON (clean shutdown, so Atlas onDisable saves its data files),
              waits for the java process to exit. Never kills the process.
  - restart : stop then start.
  - deploy  : gradlew build, replaces plugins/atlas-*.jar, then restart.
  - status  : running / stopped.
  - console : sends one console command over RCON and prints the reply.

  Never use /reload: always restart.

  Environment (all optional):
    TEST_SERVER_DIR   server folder   (default C:\Users\lloyet\Documents\Projects\servers\minecraft-paper1.21.11)
    PLUGIN_NAME       jar prefix      (default atlas)
    TEST_SERVER_XMX   heap size       (default 2G)
    MC_RCON_PASSWORD  RCON password   (required for stop/console; must match rcon.password in server.properties)
    MC_RCON_PORT      RCON port       (default 25575)
#>
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('start', 'stop', 'restart', 'deploy', 'status', 'console')]
    [string]$Action,
    [Parameter(Position = 1)]
    [string]$Command
)

$ErrorActionPreference = 'Stop'

$ServerDir  = if ($env:TEST_SERVER_DIR) { $env:TEST_SERVER_DIR } else { 'C:\Users\lloyet\Documents\Projects\servers\minecraft-paper1.21.11' }
$PluginName = if ($env:PLUGIN_NAME) { $env:PLUGIN_NAME } else { 'atlas' }
$Xmx        = if ($env:TEST_SERVER_XMX) { $env:TEST_SERVER_XMX } else { '2G' }
$RconPort   = if ($env:MC_RCON_PORT) { [int]$env:MC_RCON_PORT } else { 25575 }
$ProjectDir = Split-Path -Parent $PSScriptRoot
$PidFile    = Join-Path $ServerDir 'test-server.pid'
$LatestLog  = Join-Path $ServerDir 'logs\latest.log'

function Get-ServerProcess {
    if (-not (Test-Path $PidFile)) { return $null }
    $serverPid = [int](Get-Content $PidFile -Raw)
    $proc = Get-Process -Id $serverPid -ErrorAction SilentlyContinue
    if ($null -eq $proc -or $proc.ProcessName -notmatch 'java') { return $null }
    return $proc
}

# ── Minimal RCON client (Source RCON protocol) ──────────────────────────────
function Send-RconPacket([System.IO.Stream]$stream, [int]$id, [int]$type, [string]$body) {
    $payload = [System.Text.Encoding]::UTF8.GetBytes($body)
    $length  = 4 + 4 + $payload.Length + 2
    $buf = New-Object byte[] (4 + $length)
    [BitConverter]::GetBytes([int]$length).CopyTo($buf, 0)
    [BitConverter]::GetBytes($id).CopyTo($buf, 4)
    [BitConverter]::GetBytes($type).CopyTo($buf, 8)
    $payload.CopyTo($buf, 12)
    $stream.Write($buf, 0, $buf.Length)
    $stream.Flush()
}

function Read-RconPacket([System.IO.Stream]$stream) {
    $header = New-Object byte[] 4
    $read = 0
    while ($read -lt 4) { $read += $stream.Read($header, $read, 4 - $read) }
    $length = [BitConverter]::ToInt32($header, 0)
    $data = New-Object byte[] $length
    $read = 0
    while ($read -lt $length) { $read += $stream.Read($data, $read, $length - $read) }
    return @{
        Id   = [BitConverter]::ToInt32($data, 0)
        Body = [System.Text.Encoding]::UTF8.GetString($data, 8, [Math]::Max(0, $length - 10))
    }
}

function Invoke-Rcon([string]$cmd) {
    if (-not $env:MC_RCON_PASSWORD) { throw 'MC_RCON_PASSWORD is not set (must match rcon.password in server.properties).' }
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $client.Connect('127.0.0.1', $RconPort)
        $client.ReceiveTimeout = 10000
        $stream = $client.GetStream()
        Send-RconPacket $stream 1 3 $env:MC_RCON_PASSWORD
        $auth = Read-RconPacket $stream
        if ($auth.Id -eq -1) { throw 'RCON authentication failed (wrong MC_RCON_PASSWORD).' }
        Send-RconPacket $stream 2 2 $cmd
        try { return (Read-RconPacket $stream).Body } catch { return '' }  # "stop" may close the socket first
    } finally {
        $client.Close()
    }
}

# ── Actions ─────────────────────────────────────────────────────────────────
function Start-TestServer {
    if (Get-ServerProcess) { Write-Output 'already running'; return }
    if (Test-Path $LatestLog) { Move-Item $LatestLog (Join-Path $ServerDir 'logs\previous.log') -Force }
    $jar = Join-Path $ServerDir 'server.jar'
    $javaArgs = @("-Xms$Xmx", "-Xmx$Xmx", '-Dlog4j2.formatMsgNoLookups=true', '-XX:+UseG1GC',
                  '-jar', "`"$jar`"", '--nojline', 'nogui')
    $proc = Start-Process -FilePath 'java' -ArgumentList $javaArgs -WorkingDirectory $ServerDir `
                          -WindowStyle Minimized -PassThru
    Set-Content -Path $PidFile -Value $proc.Id
    for ($i = 0; $i -lt 180; $i++) {
        if ($proc.HasExited) { throw "server exited during startup (code $($proc.ExitCode)), see $LatestLog" }
        if ((Test-Path $LatestLog) -and (Select-String -Path $LatestLog -Pattern 'Done \(' -Quiet)) {
            Write-Output "server ready (pid $($proc.Id))"; return
        }
        Start-Sleep -Seconds 1
    }
    throw 'startup timeout (180 s)'
}

function Stop-TestServer {
    $proc = Get-ServerProcess
    if (-not $proc) { Write-Output 'already stopped'; Remove-Item $PidFile -ErrorAction SilentlyContinue; return }
    Invoke-Rcon 'stop' | Out-Null
    if (-not $proc.WaitForExit(120000)) {
        throw "server did not stop within 120 s (pid $($proc.Id)); not killing it to avoid data loss"
    }
    Remove-Item $PidFile -ErrorAction SilentlyContinue
    Write-Output 'server stopped'
}

function Deploy-Plugin {
    Push-Location $ProjectDir
    try {
        & .\gradlew.bat build -q
        if ($LASTEXITCODE -ne 0) { throw 'gradle build failed' }
    } finally { Pop-Location }
    $jar = Get-ChildItem (Join-Path $ProjectDir 'build\libs') -Filter "$PluginName-*.jar" |
           Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
           Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jar) { throw "no $PluginName-*.jar in build\libs" }
    Stop-TestServer
    Get-ChildItem (Join-Path $ServerDir 'plugins') -Filter "$PluginName-*.jar" | Remove-Item
    Copy-Item $jar.FullName (Join-Path $ServerDir 'plugins')
    Write-Output "deployed: $($jar.Name)"
    Start-TestServer
}

switch ($Action) {
    'start'   { Start-TestServer }
    'stop'    { Stop-TestServer }
    'restart' { Stop-TestServer; Start-TestServer }
    'deploy'  { Deploy-Plugin }
    'status'  { if (Get-ServerProcess) { 'running' } else { 'stopped' } }
    'console' { if (-not $Command) { throw 'usage: console "<command>"' }; Invoke-Rcon $Command }
}
