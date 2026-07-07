param(
    [switch]$SkipDocker,
    [switch]$BackendOnly
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

function New-RandomSecret {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
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
    if (-not $jwtSecret -or $jwtSecret -eq "change-me-at-least-32-random-characters") {
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

$backendDir = Join-Path -Path $projectRoot -ChildPath "backend"
$weatherProvider = Read-EnvValue $rootEnv "WEATHER_PROVIDER"
Write-Host "Starting backend with WEATHER_PROVIDER=$weatherProvider."
Set-Location $backendDir
& powershell -NoProfile -ExecutionPolicy Bypass -File ".\run-local.ps1"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
