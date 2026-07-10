param(
    [switch]$UseDocker,
    [switch]$SkipDocker,
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [switch]$ModelOnly,
    [switch]$WithModel,
    [switch]$NoRestart,
    [switch]$ForcePorts
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

$pathValue = $env:Path
[Environment]::SetEnvironmentVariable("PATH", $null, "Process")
[Environment]::SetEnvironmentVariable("Path", $pathValue, "Process")

function New-RandomSecret {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($bytes).TrimEnd("=")
}

function Read-EnvValue {
    param(
        [string]$Path,
        [string]$Name
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match "^$([regex]::Escape($Name))=(.*)$") {
            return $Matches[1]
        }
    }
    return $null
}

function Set-EnvValue {
    param(
        [string]$Path,
        [string]$Name,
        [string]$Value
    )

    $lines = @()
    if (Test-Path -LiteralPath $Path) {
        $lines = Get-Content -LiteralPath $Path
    }

    $found = $false
    $updated = foreach ($line in $lines) {
        if ($line -match "^$([regex]::Escape($Name))=") {
            $found = $true
            "$Name=$Value"
        } else {
            $line
        }
    }

    if (-not $found) {
        $updated += "$Name=$Value"
    }

    Set-Content -LiteralPath $Path -Value $updated -Encoding UTF8
}

function Set-EnvValueIfMissing {
    param(
        [string]$Path,
        [string]$Name,
        [string]$Value,
        [string[]]$PlaceholderValues = @()
    )

    $current = Read-EnvValue $Path $Name
    if (-not $current -or $PlaceholderValues -contains $current) {
        Set-EnvValue $Path $Name $Value
    }
}

function Convert-ToIntOrDefault {
    param(
        [string]$Value,
        [int]$DefaultValue
    )

    $parsed = 0
    if ([int]::TryParse($Value, [ref]$parsed)) {
        return $parsed
    }
    return $DefaultValue
}

function Join-Url {
    param(
        [string]$HostName,
        [int]$Port
    )

    return "http://${HostName}:$Port"
}

function Format-PSString {
    param([string]$Value)
    return "'" + $Value.Replace("'", "''") + "'"
}

function Test-PortListening {
    param([int]$Port)

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $async = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(500)) {
            return $false
        }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Get-PortOwners {
    param([int]$Port)

    $processIds = @()
    try {
        $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop
        $processIds += $connections | Where-Object { $_.OwningProcess } | ForEach-Object { [int]$_.OwningProcess }
    } catch {
    }

    if ($processIds.Count -eq 0) {
        $netstatLines = & netstat.exe -ano -p tcp 2>$null
        foreach ($line in $netstatLines) {
            $parts = $line -split "\s+" | Where-Object { $_ }
            if ($parts.Count -ge 5 -and $parts[0] -eq "TCP" -and $parts[1].EndsWith(":$Port") -and $parts[3] -eq "LISTENING") {
                $pidValue = 0
                if ([int]::TryParse($parts[4], [ref]$pidValue)) {
                    $processIds += $pidValue
                }
            }
        }
    }

    $owners = @()
    foreach ($processId in ($processIds | Sort-Object -Unique)) {
        try {
            $proc = Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction Stop
            $owners += [pscustomobject]@{
                Pid = [int]$processId
                Name = $proc.Name
                CommandLine = $proc.CommandLine
            }
        } catch {
            $proc = Get-Process -Id $processId -ErrorAction SilentlyContinue
            $owners += [pscustomobject]@{
                Pid = [int]$processId
                Name = if ($proc) { $proc.ProcessName } else { "unknown" }
                CommandLine = ""
            }
        }
    }

    return $owners | Sort-Object Pid -Unique
}

function Stop-ProcessByPid {
    param(
        [int]$ProcessId,
        [string]$Reason
    )

    $proc = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (-not $proc) {
        return
    }

    Write-Host "Stopping PID=$ProcessId ($Reason)."
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

function Stop-ProcessTree {
    param(
        [int]$ProcessId,
        [string]$Reason
    )

    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue
    foreach ($child in $children) {
        Stop-ProcessTree -ProcessId ([int]$child.ProcessId) -Reason $Reason
    }

    Stop-ProcessByPid -ProcessId $ProcessId -Reason $Reason
}

function Stop-TrackedProcess {
    param(
        [string]$Name,
        [string]$PidFile
    )

    if (-not (Test-Path -LiteralPath $PidFile)) {
        return
    }

    $pidText = (Get-Content -LiteralPath $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    $pidValue = 0
    if (-not [int]::TryParse($pidText, [ref]$pidValue)) {
        Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
        return
    }

    $proc = Get-CimInstance Win32_Process -Filter "ProcessId = $pidValue" -ErrorAction SilentlyContinue
    if ($proc -and $proc.CommandLine -and $proc.CommandLine.Contains($projectRoot)) {
        Stop-ProcessTree -ProcessId $pidValue -Reason "$Name from previous start-local.ps1 run"
    }

    Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
}

function Stop-PortOwners {
    param(
        [int]$Port,
        [string]$Name
    )

    $owners = @(Get-PortOwners $Port)
    foreach ($owner in $owners) {
        Stop-ProcessTree -ProcessId $owner.Pid -Reason "existing $Name on port $Port"
    }
}

function Assert-PortAvailable {
    param(
        [int]$Port,
        [string]$Name,
        [switch]$Force
    )

    if (-not (Test-PortListening $Port)) {
        return
    }

    $owners = @(Get-PortOwners $Port)
    if ($Force) {
        foreach ($owner in $owners) {
            Stop-ProcessByPid -ProcessId $owner.Pid -Reason "port $Port for $Name"
        }
        Start-Sleep -Seconds 1
        if (-not (Test-PortListening $Port)) {
            return
        }
    }

    $details = if ($owners.Count -gt 0) {
        ($owners | ForEach-Object { "PID=$($_.Pid) $($_.Name) $($_.CommandLine)" }) -join [Environment]::NewLine
    } else {
        "No process details available. Try: Get-NetTCPConnection -LocalPort $Port -State Listen"
    }

    throw "$Name port $Port is already in use.$([Environment]::NewLine)$details$([Environment]::NewLine)Run .\stop-local.ps1, close that process, choose another port in backend\.env.local, or rerun with -ForcePorts."
}

function Start-LoggedProcess {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string]$OutLog,
        [Parameter(Mandatory = $true)][string]$ErrLog,
        [Parameter(Mandatory = $true)][string]$PidFile
    )

    foreach ($path in @($OutLog, $ErrLog)) {
        if (Test-Path -LiteralPath $path) {
            $archive = [System.IO.Path]::Combine(
                [System.IO.Path]::GetDirectoryName($path),
                "$([System.IO.Path]::GetFileNameWithoutExtension($path)).$(Get-Date -Format 'yyyyMMddHHmmss')$([System.IO.Path]::GetExtension($path))"
            )
            Move-Item -LiteralPath $path -Destination $archive -Force -ErrorAction SilentlyContinue
        }
    }

    $escapedDir = $WorkingDirectory.Replace("'", "''")
    New-Item -ItemType File -Force -Path $ErrLog | Out-Null
    $script = "Set-Location -LiteralPath '$escapedDir'; $Command"
    $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($script))
    $cmdCommand = "powershell.exe -NoProfile -ExecutionPolicy Bypass -EncodedCommand $encodedCommand > `"$OutLog`" 2> `"$ErrLog`""
    $process = Start-Process `
        -FilePath "cmd.exe" `
        -ArgumentList @("/d", "/c", $cmdCommand) `
        -WindowStyle Hidden `
        -PassThru

    Set-Content -LiteralPath $PidFile -Value $process.Id -Encoding ASCII
    Write-Host "Started $Name. PID=$($process.Id)"
}

function Wait-Port {
    param(
        [int]$Port,
        [string]$Name,
        [int]$TimeoutSeconds = 45
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortListening $Port) {
            Write-Host "$Name is listening on http://localhost:$Port"
            return $true
        }
        Start-Sleep -Seconds 1
    }

    Write-Host "Warning: $Name did not listen on port $Port within $TimeoutSeconds seconds."
    return $false
}

function Wait-PortFree {
    param(
        [int]$Port,
        [string]$Name,
        [int]$TimeoutSeconds = 10
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (-not (Test-PortListening $Port)) {
            return $true
        }
        Start-Sleep -Milliseconds 300
    }

    Write-Host "Warning: $Name port $Port is still occupied after cleanup."
    return $false
}

$localEnv = Join-Path -Path $projectRoot -ChildPath "backend\.env.local"
if (-not (Test-Path -LiteralPath $localEnv)) {
    New-Item -ItemType File -Force -Path $localEnv | Out-Null
    $mysqlPassword = "pv-platform-local-" + (New-RandomSecret).Substring(0, 16)
    Set-EnvValue $localEnv "MYSQL_ROOT_PASSWORD" $mysqlPassword
    Set-EnvValue $localEnv "MYSQL_PASSWORD" $mysqlPassword
    Set-EnvValue $localEnv "JWT_SECRET" (New-RandomSecret)
    Set-EnvValue $localEnv "WEATHER_PROVIDER" "LOCAL"
    Set-EnvValue $localEnv "CACHE_TYPE" "simple"
    Set-EnvValue $localEnv "REDIS_ENABLED" "false"
    Write-Host "Created backend\.env.local for local development."
} else {
    $mysqlPassword = Read-EnvValue $localEnv "MYSQL_ROOT_PASSWORD"
    if (-not $mysqlPassword -or $mysqlPassword -eq "change-me") {
        $mysqlPassword = "pv-platform-local-" + (New-RandomSecret).Substring(0, 16)
        Set-EnvValue $localEnv "MYSQL_ROOT_PASSWORD" $mysqlPassword
        Set-EnvValue $localEnv "MYSQL_PASSWORD" $mysqlPassword
        Write-Host "Updated backend\.env.local MySQL password."
    }
    $backendMysqlPassword = Read-EnvValue $localEnv "MYSQL_PASSWORD"
    if (-not $backendMysqlPassword -or $backendMysqlPassword -eq "change-me" -or $backendMysqlPassword -eq "your-mysql-password") {
        Set-EnvValue $localEnv "MYSQL_PASSWORD" $mysqlPassword
    }
    $jwtSecret = Read-EnvValue $localEnv "JWT_SECRET"
    if (-not $jwtSecret -or $jwtSecret -eq "change-me-at-least-32-random-characters" -or [Text.Encoding]::UTF8.GetByteCount($jwtSecret) -lt 32) {
        Set-EnvValue $localEnv "JWT_SECRET" (New-RandomSecret)
    }
}

Set-EnvValueIfMissing $localEnv "BACKEND_HOST" "127.0.0.1"
Set-EnvValueIfMissing $localEnv "SERVER_PORT" "8080"
Set-EnvValueIfMissing $localEnv "FRONTEND_HOST" "127.0.0.1"
Set-EnvValueIfMissing $localEnv "FRONTEND_PORT" "5173"
Set-EnvValueIfMissing $localEnv "MODEL_HOST" "127.0.0.1"
Set-EnvValueIfMissing $localEnv "MODEL_PORT" "9000"

$backendHost = Read-EnvValue $localEnv "BACKEND_HOST"
if (-not $backendHost) {
    $backendHost = "127.0.0.1"
}
$frontendHost = Read-EnvValue $localEnv "FRONTEND_HOST"
if (-not $frontendHost) {
    $frontendHost = "127.0.0.1"
}
$modelHost = Read-EnvValue $localEnv "MODEL_HOST"
if (-not $modelHost) {
    $modelHost = "127.0.0.1"
}

$backendPort = Convert-ToIntOrDefault (Read-EnvValue $localEnv "SERVER_PORT") 8080
$frontendPort = Convert-ToIntOrDefault (Read-EnvValue $localEnv "FRONTEND_PORT") 5173
$modelPort = Convert-ToIntOrDefault (Read-EnvValue $localEnv "MODEL_PORT") 9000

$backendUrl = Join-Url $backendHost $backendPort
$frontendUrl = Join-Url $frontendHost $frontendPort
$modelUrl = Join-Url $modelHost $modelPort

$currentModelServiceBaseUrl = Read-EnvValue $localEnv "MODEL_SERVICE_BASE_URL"
if (-not $currentModelServiceBaseUrl -or $currentModelServiceBaseUrl -eq "http://localhost:9000" -or $currentModelServiceBaseUrl -eq "http://127.0.0.1:9000") {
    Set-EnvValue $localEnv "MODEL_SERVICE_BASE_URL" $modelUrl
}

$currentOAuthCallbackBaseUrl = Read-EnvValue $localEnv "OAUTH_CALLBACK_BASE_URL"
if (-not $currentOAuthCallbackBaseUrl -or $currentOAuthCallbackBaseUrl -eq "http://localhost:5173" -or $currentOAuthCallbackBaseUrl -eq "http://127.0.0.1:5173") {
    Set-EnvValue $localEnv "OAUTH_CALLBACK_BASE_URL" $frontendUrl
}

$logsDir = Join-Path -Path $projectRoot -ChildPath "logs"
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null

$startDockerMysql = $UseDocker -and -not $SkipDocker -and -not $FrontendOnly
if ($startDockerMysql) {
    $composeFile = Join-Path -Path $projectRoot -ChildPath "docker-compose.yml"
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($docker -and (Test-Path -LiteralPath $composeFile)) {
        docker compose up -d mysql
    } else {
        Write-Host "Warning: Docker MySQL requested, but Docker or docker-compose.yml is not available. Using configured MySQL instead."
    }
}

Wait-Port -Port 3306 -Name "MySQL" -TimeoutSeconds 5 | Out-Null
if (-not (Test-PortListening 3306)) {
    Write-Host "Warning: MySQL port 3306 is not listening. Start local MySQL first."
}

$backendDir = Join-Path -Path $projectRoot -ChildPath "backend"
$weatherProvider = Read-EnvValue $localEnv "WEATHER_PROVIDER"

$modelDir = Join-Path -Path $projectRoot -ChildPath "model-service"
$frontendDir = Join-Path -Path $projectRoot -ChildPath "web-frontend"
$condaPython = "C:\Users\99140\.conda\envs\d2l\python.exe"

$startBackend = -not $FrontendOnly -and -not $ModelOnly
$startFrontend = -not $BackendOnly -and -not $ModelOnly
$startModel = $ModelOnly -or ($WithModel -and -not $BackendOnly -and -not $FrontendOnly)

$selectedServices = @()
if ($startBackend) {
    $selectedServices += [pscustomobject]@{ Name = "Backend"; Port = $backendPort; PidFile = (Join-Path $logsDir "backend.pid") }
}
if ($startFrontend) {
    $selectedServices += [pscustomobject]@{ Name = "Frontend"; Port = $frontendPort; PidFile = (Join-Path $logsDir "frontend.pid") }
}
if ($startModel) {
    $selectedServices += [pscustomobject]@{ Name = "Model service"; Port = $modelPort; PidFile = (Join-Path $logsDir "model-service.pid") }
}

if (-not $NoRestart) {
    foreach ($service in $selectedServices) {
        Stop-TrackedProcess -Name $service.Name -PidFile $service.PidFile
        Stop-PortOwners -Port $service.Port -Name $service.Name
        Wait-PortFree -Port $service.Port -Name $service.Name | Out-Null
    }
}

foreach ($service in $selectedServices) {
    Assert-PortAvailable -Port $service.Port -Name $service.Name -Force:$ForcePorts
}

if ($startModel) {
    if (Test-Path -LiteralPath $condaPython) {
        $modelCommand = "& $(Format-PSString $condaPython) -m uvicorn app.main:app --host $modelHost --port $modelPort"
        Start-LoggedProcess `
            -Name "model-service" `
            -WorkingDirectory $modelDir `
            -Command $modelCommand `
            -OutLog (Join-Path $logsDir "model-service.out.log") `
            -ErrLog (Join-Path $logsDir "model-service.err.log") `
            -PidFile (Join-Path $logsDir "model-service.pid")
    } else {
        Write-Host "Warning: d2l python not found: $condaPython"
    }
}

if ($startBackend) {
    Write-Host "Starting backend with WEATHER_PROVIDER=$weatherProvider."
    Start-LoggedProcess `
        -Name "backend" `
        -WorkingDirectory $backendDir `
        -Command ".\run-local.ps1" `
        -OutLog (Join-Path $logsDir "backend.out.log") `
        -ErrLog (Join-Path $logsDir "backend.err.log") `
        -PidFile (Join-Path $logsDir "backend.pid")
}

if ($startFrontend) {
    if (-not (Test-Path -LiteralPath (Join-Path $frontendDir "node_modules"))) {
        Write-Host "Warning: web-frontend node_modules not found. Run 'npm install' in web-frontend first."
    }

    $frontendCommand = @(
        "`$env:CI='true'",
        "`$env:VITE_DEV_HOST=$(Format-PSString $frontendHost)",
        "`$env:VITE_DEV_PORT=$(Format-PSString ([string]$frontendPort))",
        "`$env:VITE_BACKEND_URL=$(Format-PSString $backendUrl)",
        "npm run dev -- --host $frontendHost --port $frontendPort --strictPort"
    ) -join "; "

    Start-LoggedProcess `
        -Name "frontend" `
        -WorkingDirectory $frontendDir `
        -Command $frontendCommand `
        -OutLog (Join-Path $logsDir "frontend.out.log") `
        -ErrLog (Join-Path $logsDir "frontend.err.log") `
        -PidFile (Join-Path $logsDir "frontend.pid")
}

if ($startModel) {
    Wait-Port -Port $modelPort -Name "Model service" -TimeoutSeconds 30 | Out-Null
}
if ($startBackend) {
    Wait-Port -Port $backendPort -Name "Backend" -TimeoutSeconds 60 | Out-Null
}
if ($startFrontend) {
    Wait-Port -Port $frontendPort -Name "Frontend" -TimeoutSeconds 40 | Out-Null
}

Write-Host ""
Write-Host "Local startup finished."
Write-Host "Frontend: $frontendUrl"
Write-Host "Backend:  $backendUrl"
if ($startModel) {
    Write-Host "Model:    $modelUrl"
} else {
    Write-Host "Model:    skipped (use -WithModel when prediction endpoints need it)"
}
Write-Host "Logs:     $logsDir"
