<#
.SYNOPSIS
  Controls the local Paper 1.21.11 test server used for in-game bot tests (VitaminMCP).

.DESCRIPTION
  Usage: powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-server.ps1 <start|stop|restart|deploy|status|console "<cmd>"|db-up|baseline|reset|install-structures>

  - start   : rotates logs/latest.log, launches the server in its own minimized console window,
              waits for "Done (" in the log.
  - stop    : sends "stop" over RCON (clean shutdown, so Atlas onDisable saves its data files),
              waits for the java process to exit. Never kills the process.
  - restart : stop then start.
  - deploy  : gradlew build, replaces plugins/atlas-*.jar, then restart.
  - status  : running / stopped.
  - console : sends one console command over RCON and prints the reply.
  - db-up   : starts the local PostgreSQL (docker/postgres) and creates the test database if missing.
  - baseline: (server stopped by the script) saves the current worlds + plugins/atlas + a dump of the
              test database as the baseline in <TEST_SERVER_DIR>\baseline. Run once after the setup.
  - run-console <file>: runs the "console" steps of a scenario JSON over one RCON connection
              ("wait_for" ticks steps become a pause; other steps are skipped). Used for the
              baseline setup (_setup/build-baseline.json) once its bot is connected.
  - rebuild-from-map: stops the server, replaces the worlds with the raw test map (TEST_MAP_DIR,
              default <repo>\run\world), empties the test database and plugins/atlas (config.yml from scripts/test-world/atlas-config.yml),
              installs the structures and starts. Then run the setup (_setup/build-baseline.json)
              and 'baseline'. Only needed when the map or the setup changes.
  - install-structures: copies src/main/resources/structures/*.nbt into world/generated/hatlas/
              (so /place template hatlas:<name> works). Part of building the baseline.
  - reset   : stops the server, restores the baseline (worlds, plugins/atlas, database), starts it.
              Use before a test session or after a run left a mess. Nothing else to undo.

  The test server is disposable (test world only, no real data): scenarios do not need to clean up.

  Never use /reload: always restart.

  Environment (all optional):
    TEST_SERVER_DIR   server folder   (default C:\Users\lloyet\Documents\Projects\servers\minecraft-paper1.21.11)
    PLUGIN_NAME       jar prefix      (default atlas)
    TEST_SERVER_XMX   heap size       (default 2G)
    MC_RCON_PASSWORD  RCON password   (required for stop/console; must match rcon.password in server.properties)
    MC_RCON_PORT      RCON port       (default 25575)
    ATLAS_DB_PASSWORD PostgreSQL password (passed to the server; the plugin reads it)
    ATLAS_PG_PORT     PostgreSQL port (default 5432)
    TEST_DB_NAME      test database   (default atlas_test)
    TEST_MAP_DIR      raw test map    (default <repo>\run\world)
  Missing values are read from <repo>\.env and <repo>\docker\postgres\.env (KEY=VALUE lines).
#>
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('start', 'stop', 'restart', 'deploy', 'status', 'console', 'db-up', 'baseline', 'reset', 'install-structures', 'run-console', 'rebuild-from-map')]
    [string]$Action,
    [Parameter(Position = 1)]
    [string]$Command
)

$ErrorActionPreference = 'Stop'

$ProjectDirEarly = Split-Path -Parent $PSScriptRoot
foreach ($envFile in @((Join-Path $ProjectDirEarly '.env'), (Join-Path $ProjectDirEarly 'docker\postgres\.env'))) {
    if (-not (Test-Path $envFile)) { continue }
    foreach ($line in Get-Content $envFile) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$' -and -not [Environment]::GetEnvironmentVariable($Matches[1])) {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim('"', "'"))
        }
    }
}

$ServerDir  = if ($env:TEST_SERVER_DIR) { $env:TEST_SERVER_DIR } else { 'C:\Users\lloyet\Documents\Projects\servers\minecraft-paper1.21.11' }
$PluginName = if ($env:PLUGIN_NAME) { $env:PLUGIN_NAME } else { 'atlas' }
$Xmx        = if ($env:TEST_SERVER_XMX) { $env:TEST_SERVER_XMX } else { '2G' }
$RconPort   = if ($env:MC_RCON_PORT) { [int]$env:MC_RCON_PORT } else { 25575 }
$ProjectDir = Split-Path -Parent $PSScriptRoot
$PidFile    = Join-Path $ServerDir 'test-server.pid'
$LatestLog  = Join-Path $ServerDir 'logs\latest.log'
$DbName     = if ($env:TEST_DB_NAME) { $env:TEST_DB_NAME } else { 'atlas_test' }
$Compose    = Join-Path $ProjectDir 'docker\postgres\docker-compose.yml'
$BaselineDir = Join-Path $ServerDir 'baseline'
# Folders that make up the test world state (worlds + Atlas config/data files).
$StateDirs  = @('world', 'world_nether', 'world_the_end', 'plugins\atlas')

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

function Invoke-RconBatch([string[]]$cmds) {
    if (-not $env:MC_RCON_PASSWORD) { throw 'MC_RCON_PASSWORD is not set (must match rcon.password in server.properties).' }
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $client.Connect('127.0.0.1', $RconPort)
        $client.ReceiveTimeout = 30000
        $stream = $client.GetStream()
        Send-RconPacket $stream 1 3 $env:MC_RCON_PASSWORD
        $auth = Read-RconPacket $stream
        if ($auth.Id -eq -1) { throw 'RCON authentication failed (wrong MC_RCON_PASSWORD).' }
        $i = 10
        foreach ($c in $cmds) {
            if ($c -match '^#sleep (\d+)$') { Start-Sleep -Milliseconds ([int]$Matches[1]); continue }
            $i++
            Send-RconPacket $stream $i 2 $c
            $reply = (Read-RconPacket $stream).Body
            if ($reply -match 'Unknown|Incorrect|error|Error|not found|Only players|Failed|failed') { Write-Output "[$c] -> $reply" }
        }
    } finally {
        $client.Close()
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
    Start-Database | Out-Null
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

# ── Test database (PostgreSQL in docker) ────────────────────────────────────
function Invoke-Psql([string]$db, [string]$sql) {
    $out = & docker compose -f $Compose exec -T postgres psql -U atlas -d $db -v ON_ERROR_STOP=1 -tAc $sql
    if ($LASTEXITCODE -ne 0) { throw "psql failed: $sql" }
    return $out
}

function Start-Database {
    & docker compose -f $Compose up -d --wait | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'docker compose up failed (is Docker Desktop running?)' }
    $exists = Invoke-Psql 'atlas' "SELECT 1 FROM pg_database WHERE datname = '$DbName'"
    if (-not $exists) { Invoke-Psql 'atlas' "CREATE DATABASE $DbName OWNER atlas" | Out-Null; Write-Output "created database $DbName" }
    Write-Output "database ready ($DbName)"
}

function Copy-Tree([string]$from, [string]$to) {
    if (-not (Test-Path $from)) { return }
    & robocopy $from $to /MIR /NFL /NDL /NJH /NJS /NP /R:2 /W:1 | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy $from -> $to failed ($LASTEXITCODE)" }
    $global:LASTEXITCODE = 0
}

function Save-Baseline {
    Stop-TestServer
    Start-Database
    New-Item -ItemType Directory -Force $BaselineDir | Out-Null
    foreach ($d in $StateDirs) { Copy-Tree (Join-Path $ServerDir $d) (Join-Path $BaselineDir $d) }
    & docker compose -f $Compose exec -T postgres pg_dump -U atlas -Fc -f /tmp/baseline.dump $DbName
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed' }
    $cid = (& docker compose -f $Compose ps -q postgres).Trim()
    & docker cp "${cid}:/tmp/baseline.dump" (Join-Path $BaselineDir 'database.dump') | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'docker cp of the dump failed' }
    $sha = (& git -C $ProjectDir rev-parse --short HEAD)
    Set-Content (Join-Path $BaselineDir 'baseline.txt') "saved $(Get-Date -Format s) from repo $sha, database $DbName"
    Write-Output "baseline saved to $BaselineDir"
}

function Restore-Baseline {
    if (-not (Test-Path (Join-Path $BaselineDir 'database.dump'))) { throw "no baseline in $BaselineDir (run 'baseline' once)" }
    Stop-TestServer
    Start-Database
    foreach ($d in $StateDirs) {
        $target = Join-Path $ServerDir $d
        if (Test-Path (Join-Path $BaselineDir $d)) { Copy-Tree (Join-Path $BaselineDir $d) $target }
        elseif (Test-Path $target) { Remove-Item $target -Recurse -Force }
    }
    Invoke-Psql 'atlas' "DROP DATABASE IF EXISTS $DbName WITH (FORCE)" | Out-Null
    Invoke-Psql 'atlas' "CREATE DATABASE $DbName OWNER atlas" | Out-Null
    $cid = (& docker compose -f $Compose ps -q postgres).Trim()
    & docker cp (Join-Path $BaselineDir 'database.dump') "${cid}:/tmp/baseline.dump" | Out-Null
    & docker compose -f $Compose exec -T postgres pg_restore -U atlas -d $DbName --no-owner /tmp/baseline.dump
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore failed' }
    Write-Output 'baseline restored'
    Start-TestServer
}

function Invoke-ConsoleSteps([string]$file) {
    $steps = Get-Content $file -Raw | ConvertFrom-Json
    $cmds = New-Object System.Collections.Generic.List[string]
    foreach ($st in $steps) {
        if ($st.action -eq 'console') { $cmds.Add($st.command) }
        elseif ($st.action -eq 'wait_for' -and $st.condition -eq 'ticks') { $cmds.Add("#sleep $([int]$st.count * 50)") }
    }
    Invoke-RconBatch $cmds.ToArray()
    Write-Output "ran $($cmds.Count) console steps from $file"
}

function Rebuild-FromMap {
    $map = if ($env:TEST_MAP_DIR) { $env:TEST_MAP_DIR } else { Join-Path $ProjectDir 'run\world' }
    if (-not (Test-Path (Join-Path $map 'level.dat'))) { throw "no map at $map (set TEST_MAP_DIR)" }
    Stop-TestServer
    Start-Database | Out-Null
    foreach ($d in 'world', 'world_nether', 'world_the_end') {
        $t = Join-Path $ServerDir $d
        if (Test-Path $t) { Remove-Item $t -Recurse -Force }
    }
    & robocopy $map (Join-Path $ServerDir 'world') /E /NFL /NDL /NJH /NJS /NP /XF session.lock | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy of the map failed ($LASTEXITCODE)" }
    $global:LASTEXITCODE = 0
    # Vanilla layout -> Paper layout (nether / end in their own world folders).
    foreach ($pair in @(@('DIM-1', 'world_nether'), @('DIM1', 'world_the_end'))) {
        $from = Join-Path $ServerDir "world\$($pair[0])"
        if (Test-Path $from) {
            New-Item -ItemType Directory -Force (Join-Path $ServerDir $pair[1]) | Out-Null
            Move-Item $from (Join-Path $ServerDir "$($pair[1])\$($pair[0])")
        }
    }
    $atlas = Join-Path $ServerDir 'plugins\atlas'
    if (Test-Path $atlas) { Remove-Item $atlas -Recurse -Force }
    New-Item -ItemType Directory -Force $atlas | Out-Null
    Copy-Item (Join-Path $ProjectDir 'scripts\test-world\atlas-config.yml') (Join-Path $atlas 'config.yml')
    Invoke-Psql 'atlas' "DROP DATABASE IF EXISTS $DbName WITH (FORCE)" | Out-Null
    Invoke-Psql 'atlas' "CREATE DATABASE $DbName OWNER atlas" | Out-Null
    Install-Structures
    Write-Output "rebuilt from $map"
    Start-TestServer
}

function Install-Structures {
    $src = Join-Path $ProjectDir 'src\main\resources\structures'
    # 1.21+ reads generated/<ns>/structure/; older versions structures/. Install in both.
    foreach ($sub in @('structure', 'structures')) {
        $dst = Join-Path $ServerDir "world\generated\hatlas\$sub"
        New-Item -ItemType Directory -Force $dst | Out-Null
        Copy-Item (Join-Path $src '*.nbt') $dst -Force
    }
    Write-Output "structures installed: $((Get-ChildItem $src -Filter *.nbt).BaseName -join ', ')"
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
    'db-up'   { Start-Database }
    'baseline'{ Save-Baseline }
    'reset'   { Restore-Baseline }
    'install-structures' { Install-Structures }
    'rebuild-from-map' { Rebuild-FromMap }
    'run-console' { if (-not $Command) { throw 'usage: run-console <scenario.json>' }; Invoke-ConsoleSteps $Command }
}
