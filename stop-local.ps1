$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$logsDir = Join-Path -Path $projectRoot -ChildPath "logs"

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
    if ([int]::TryParse($pidText, [ref]$pidValue)) {
        $proc = Get-CimInstance Win32_Process -Filter "ProcessId = $pidValue" -ErrorAction SilentlyContinue
        if ($proc -and $proc.CommandLine -and $proc.CommandLine.Contains($projectRoot)) {
            Stop-ProcessTree -ProcessId $pidValue -Reason "$Name from start-local.ps1"
        }
    }

    Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
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

    Write-Host "Warning: $Name port $Port is still occupied."
    return $false
}

$localEnv = Join-Path -Path $projectRoot -ChildPath "backend\.env.local"
$backendPort = Convert-ToIntOrDefault (Read-EnvValue $localEnv "SERVER_PORT") 8080
$frontendPort = Convert-ToIntOrDefault (Read-EnvValue $localEnv "FRONTEND_PORT") 5173
$modelPort = Convert-ToIntOrDefault (Read-EnvValue $localEnv "MODEL_PORT") 9000

$services = @(
    [pscustomobject]@{ Name = "Backend"; PidFile = (Join-Path $logsDir "backend.pid"); Port = $backendPort },
    [pscustomobject]@{ Name = "Frontend"; PidFile = (Join-Path $logsDir "frontend.pid"); Port = $frontendPort },
    [pscustomobject]@{ Name = "Model service"; PidFile = (Join-Path $logsDir "model-service.pid"); Port = $modelPort }
)

foreach ($service in $services) {
    Stop-TrackedProcess -Name $service.Name -PidFile $service.PidFile
    foreach ($owner in (Get-PortOwners $service.Port)) {
        Stop-ProcessTree -ProcessId $owner.Pid -Reason "port $($service.Port) for $($service.Name)"
    }
    Wait-PortFree -Port $service.Port -Name $service.Name | Out-Null
}

Write-Host "Local tracked processes stopped."
Write-Host "Configured local ports were also cleared."
