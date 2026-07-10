param(
    [switch]$SkipDocker,
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [switch]$ModelOnly
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

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

function Merge-EnvTemplate {
    param(
        [string]$TemplatePath,
        [string]$TargetPath
    )

    Get-Content -LiteralPath $TemplatePath | ForEach-Object {
        $line = $_.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith("#")) {
            return
        }

        $index = $line.IndexOf("=")
        if ($index -le 0) {
            return
        }

        $name = $line.Substring(0, $index).Trim()
        $value = $line.Substring($index + 1).Trim()
        Set-EnvValueIfMissing $TargetPath $name $value
    }
}

function Test-PortListening {
    param([int]$Port)

    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    return $null -ne $connection
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

    if (Test-Path -LiteralPath $OutLog) {
        Remove-Item -LiteralPath $OutLog -Force
    }
    if (Test-Path -LiteralPath $ErrLog) {
        Remove-Item -LiteralPath $ErrLog -Force
    }

    $escapedDir = $WorkingDirectory.Replace("'", "''")
    $wrappedCommand = "Set-Location -LiteralPath '$escapedDir'; $Command"
    $process = Start-Process `
        -FilePath "powershell.exe" `
        -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $wrappedCommand) `
        -WindowStyle Hidden `
        -RedirectStandardOutput $OutLog `
        -RedirectStandardError $ErrLog `
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

$rootEnv = Join-Path -Path $projectRoot -ChildPath ".env"
$rootEnvExample = Join-Path -Path $projectRoot -ChildPath ".env.example"
if (-not (Test-Path -LiteralPath $rootEnv)) {
    Copy-Item -LiteralPath $rootEnvExample -Destination $rootEnv
    $mysqlPassword = "pv-platform-local-" + (New-RandomSecret).Substring(0, 16)
    Set-EnvValue $rootEnv "MYSQL_ROOT_PASSWORD" $mysqlPassword
    Set-EnvValue $rootEnv "MYSQL_PASSWORD" $mysqlPassword
    Set-EnvValue $rootEnv "JWT_SECRET" (New-RandomSecret)
    Set-EnvValue $rootEnv "WEATHER_PROVIDER" "LOCAL"
    Set-EnvValue $rootEnv "CACHE_TYPE" "simple"
    Set-EnvValue $rootEnv "REDIS_ENABLED" "false"
    Write-Host "Created project .env for Docker MySQL."
} else {
    Merge-EnvTemplate $rootEnvExample $rootEnv
    $mysqlPassword = Read-EnvValue $rootEnv "MYSQL_ROOT_PASSWORD"
    if (-not $mysqlPassword -or $mysqlPassword -eq "change-me") {
        $mysqlPassword = "pv-platform-local-" + (New-RandomSecret).Substring(0, 16)
        Set-EnvValue $rootEnv "MYSQL_ROOT_PASSWORD" $mysqlPassword
        Set-EnvValue $rootEnv "MYSQL_PASSWORD" $mysqlPassword
        Write-Host "Updated project .env MySQL password."
    }
    $backendMysqlPassword = Read-EnvValue $rootEnv "MYSQL_PASSWORD"
    if (-not $backendMysqlPassword -or $backendMysqlPassword -eq "change-me" -or $backendMysqlPassword -eq "your-mysql-password") {
        Set-EnvValue $rootEnv "MYSQL_PASSWORD" $mysqlPassword
    }
    $jwtSecret = Read-EnvValue $rootEnv "JWT_SECRET"
    if (-not $jwtSecret -or $jwtSecret -eq "change-me-at-least-32-random-characters" -or [Text.Encoding]::UTF8.GetByteCount($jwtSecret) -lt 32) {
        Set-EnvValue $rootEnv "JWT_SECRET" (New-RandomSecret)
    }
}

if (-not $BackendOnly -and -not $SkipDocker) {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($docker) {
        docker compose up -d mysql
    } else {
        Write-Host "Docker not found. Skipping MySQL container startup."
    }
}

$logsDir = Join-Path -Path $projectRoot -ChildPath "logs"
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null

if (-not $SkipDocker) {
    Wait-Port -Port 3306 -Name "MySQL" -TimeoutSeconds 25 | Out-Null
}
if (-not (Test-PortListening 3306)) {
    Write-Host "Warning: MySQL port 3306 is not listening. Start local MySQL first, or install Docker and rerun without -SkipDocker."
}

$backendDir = Join-Path -Path $projectRoot -ChildPath "backend"
$weatherProvider = Read-EnvValue $rootEnv "WEATHER_PROVIDER"

$modelDir = Join-Path -Path $projectRoot -ChildPath "model-service"
$frontendDir = Join-Path -Path $projectRoot -ChildPath "web-frontend"
$condaPython = "C:\Users\99140\.conda\envs\d2l\python.exe"

$startBackend = -not $FrontendOnly -and -not $ModelOnly
$startModel = -not $BackendOnly -and -not $FrontendOnly
$startFrontend = -not $BackendOnly -and -not $ModelOnly

if ($startModel) {
    if (Test-PortListening 9000) {
        Write-Host "Model service already listening on http://localhost:9000"
    } elseif (Test-Path -LiteralPath $condaPython) {
        Start-LoggedProcess `
            -Name "model-service" `
            -WorkingDirectory $modelDir `
            -Command "`"$condaPython`" -m uvicorn app.main:app --host 127.0.0.1 --port 9000" `
            -OutLog (Join-Path $logsDir "model-service.out.log") `
            -ErrLog (Join-Path $logsDir "model-service.err.log") `
            -PidFile (Join-Path $logsDir "model-service.pid")
    } else {
        Write-Host "Warning: d2l python not found: $condaPython"
    }
}

if ($startBackend) {
    if (Test-PortListening 8080) {
        Write-Host "Backend already listening on http://localhost:8080"
    } else {
        Write-Host "Starting backend with WEATHER_PROVIDER=$weatherProvider."
        Start-LoggedProcess `
            -Name "backend" `
            -WorkingDirectory $backendDir `
            -Command ".\run-local.ps1" `
            -OutLog (Join-Path $logsDir "backend.out.log") `
            -ErrLog (Join-Path $logsDir "backend.err.log") `
            -PidFile (Join-Path $logsDir "backend.pid")
    }
}

if ($startFrontend) {
    if (Test-PortListening 5173) {
        Write-Host "Frontend already listening on http://localhost:5173"
    } else {
        Start-LoggedProcess `
            -Name "frontend" `
            -WorkingDirectory $frontendDir `
            -Command "npm run dev -- --host 127.0.0.1" `
            -OutLog (Join-Path $logsDir "frontend.out.log") `
            -ErrLog (Join-Path $logsDir "frontend.err.log") `
            -PidFile (Join-Path $logsDir "frontend.pid")
    }
}

if ($startModel) {
    Wait-Port -Port 9000 -Name "Model service" -TimeoutSeconds 30 | Out-Null
}
if ($startBackend) {
    Wait-Port -Port 8080 -Name "Backend" -TimeoutSeconds 60 | Out-Null
}
if ($startFrontend) {
    Wait-Port -Port 5173 -Name "Frontend" -TimeoutSeconds 40 | Out-Null
}

Write-Host ""
Write-Host "Local startup finished."
Write-Host "Frontend: http://localhost:5173"
Write-Host "Backend:  http://localhost:8080"
Write-Host "Model:    http://localhost:9000"
Write-Host "Logs:     $logsDir"
