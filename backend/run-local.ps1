param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

if (-not $MavenArgs -or $MavenArgs.Count -eq 0) {
    $MavenArgs = @("spring-boot:run")
}

function Set-AppEnv {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [AllowEmptyString()][string]$Value
    )

    [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
}

function Import-EnvFile {
    param(
        [string]$Path,
        [string[]]$NamePrefixes = @()
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    Get-Content -LiteralPath $Path | ForEach-Object {
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

        if ($NamePrefixes.Count -gt 0) {
            $matched = $false
            foreach ($prefix in $NamePrefixes) {
                if ($name.StartsWith($prefix)) {
                    $matched = $true
                    break
                }
            }
            if (-not $matched) {
                return
            }
        }

        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        Set-AppEnv $name $value
    }
}

# Java / Maven
$javaHomeCandidates = @(
    $env:JAVA_HOME,
    "D:\Java\JDK",
    "C:\Program Files\Java\jdk-23"
) | Where-Object { $_ }

$javaHome = $javaHomeCandidates | Where-Object {
    Test-Path -LiteralPath (Join-Path -Path $_ -ChildPath "bin\javac.exe")
} | Select-Object -First 1

if ($javaHome) {
    Set-AppEnv "JAVA_HOME" $javaHome
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Server
Set-AppEnv "BACKEND_HOST" "127.0.0.1"
Set-AppEnv "SERVER_PORT" "8080"
Set-AppEnv "TOMCAT_MAX_THREADS" "200"
Set-AppEnv "TOMCAT_MIN_SPARE_THREADS" "10"
Set-AppEnv "TOMCAT_MAX_CONNECTIONS" "8192"
Set-AppEnv "TOMCAT_ACCEPT_COUNT" "100"
Set-AppEnv "FRONTEND_HOST" "127.0.0.1"
Set-AppEnv "FRONTEND_PORT" "5173"

# MySQL. Override real password in backend/.env.local.
Set-AppEnv "MYSQL_URL" "jdbc:mysql://localhost:3306/pv_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
Set-AppEnv "MYSQL_USERNAME" "root"
Set-AppEnv "MYSQL_PASSWORD" "change-me"
Set-AppEnv "MYSQL_POOL_MAX_SIZE" "10"
Set-AppEnv "MYSQL_POOL_MIN_IDLE" "2"

# JWT
Set-AppEnv "JWT_SECRET" "pv-platform-local-dev-secret-at-least-32-chars"
Set-AppEnv "JWT_ACCESS_TOKEN_EXPIRATION" "7200"
Set-AppEnv "JWT_REFRESH_TOKEN_EXPIRATION" "604800"

# Model service
Set-AppEnv "MODEL_HOST" "127.0.0.1"
Set-AppEnv "MODEL_PORT" "9000"
Set-AppEnv "MODEL_SERVICE_BASE_URL" "http://localhost:9000"

# Weather. Use LOCAL by default. Set QWeather credentials in backend/.env.local.
$qweatherPrivateKeyPath = Join-Path -Path $projectRoot -ChildPath "secrets\ed25519-private.pem"
Set-AppEnv "WEATHER_PROVIDER" "LOCAL"
Set-AppEnv "WEATHER_AUTH_TYPE" "JWT"
Set-AppEnv "WEATHER_API_KEY" ""
Set-AppEnv "WEATHER_BASE_URL" "https://your-qweather-api-host"
Set-AppEnv "QWEATHER_PROJECT_ID" ""
Set-AppEnv "QWEATHER_KEY_ID" ""
Set-AppEnv "QWEATHER_PRIVATE_KEY_PATH" $qweatherPrivateKeyPath
Set-AppEnv "WEATHER_CONNECT_TIMEOUT" "3000"
Set-AppEnv "WEATHER_RESPONSE_TIMEOUT" "5000"
Set-AppEnv "WEATHER_CACHE_MINUTES" "10"
Set-AppEnv "WEATHER_FORECAST_DAYS" "3"
Set-AppEnv "WEATHER_JWT_TTL_SECONDS" "1800"

# Redis/cache. Local development can run without Redis.
Set-AppEnv "REDIS_ENABLED" "false"
Set-AppEnv "CACHE_TYPE" "simple"
Set-AppEnv "REDIS_HOST" "localhost"
Set-AppEnv "REDIS_PORT" "6379"
Set-AppEnv "REDIS_PASSWORD" ""
Set-AppEnv "REDIS_DATABASE" "0"
Set-AppEnv "REDIS_TIMEOUT" "3000"
Set-AppEnv "CACHE_REDIS_TTL" "600000"

# Login and verification limits
Set-AppEnv "LOGIN_MAX_FAILURES" "5"
Set-AppEnv "LOGIN_LOCK_MINUTES" "15"
Set-AppEnv "VERIFY_CODE_EXPIRE_MINUTES" "5"
Set-AppEnv "VERIFY_CODE_LENGTH" "6"
Set-AppEnv "VERIFY_CODE_MAX_ATTEMPTS" "5"
Set-AppEnv "VERIFY_CODE_RESEND_COOLDOWN_SECONDS" "60"
Set-AppEnv "VERIFY_CODE_MAX_DAILY_SENDS" "20"

# Mail. Empty credentials make the app fall back to local log output for codes.
Set-AppEnv "MAIL_ENABLED" "false"
Set-AppEnv "MAIL_HOST" "smtp.qq.com"
Set-AppEnv "MAIL_PORT" "465"
Set-AppEnv "MAIL_USERNAME" ""
Set-AppEnv "MAIL_PASSWORD" ""
Set-AppEnv "MAIL_FROM" ""
Set-AppEnv "MAIL_SSL_ENABLED" "true"

# OAuth / face / file storage
Set-AppEnv "OAUTH_CALLBACK_BASE_URL" "http://localhost:5173"
Set-AppEnv "OAUTH_GITHUB_ENABLED" "false"
Set-AppEnv "OAUTH_GITHUB_CLIENT_ID" ""
Set-AppEnv "OAUTH_GITHUB_CLIENT_SECRET" ""
Set-AppEnv "FACE_PROVIDER" "local"
Set-AppEnv "FACE_MATCH_THRESHOLD" "75"
Set-AppEnv "FACE_TEMP_URL_EXPIRE_SECONDS" "300"
Set-AppEnv "FACE_STORAGE_DIR" "./data/faces"
Set-AppEnv "ALIYUN_ACCESS_KEY_ID" ""
Set-AppEnv "ALIYUN_ACCESS_KEY_SECRET" ""
Set-AppEnv "ALIYUN_FACE_REGION" "cn-shanghai"
Set-AppEnv "ALIYUN_FACE_ENDPOINT" "facebody.cn-shanghai.aliyuncs.com"
Set-AppEnv "ALIYUN_FACE_DB_NAME" "pv_platform"
Set-AppEnv "ALIYUN_OSS_REGION" "cn-shanghai"
Set-AppEnv "ALIYUN_OSS_ENDPOINT" "oss-cn-shanghai.aliyuncs.com"
Set-AppEnv "ALIYUN_OSS_BUCKET" ""
Set-AppEnv "AVATAR_STORAGE_DIR" "./data/avatars"
Set-AppEnv "AVATAR_MAX_SIZE" "2097152"
Set-AppEnv "PV_IMPORT_STORAGE_DIR" "./data/pv-imports"
Set-AppEnv "PV_IMPORT_MAX_FILE_SIZE" "10485760"
Set-AppEnv "PV_IMPORT_MAX_ROWS" "100000"

$localEnvPath = Join-Path -Path $projectRoot -ChildPath ".env.local"
if (Test-Path -LiteralPath $localEnvPath) {
    Import-EnvFile $localEnvPath
}
if (-not (Test-Path -LiteralPath $localEnvPath)) {
    Write-Host "Warning: backend/.env.local not found. Using safe defaults from run-local.ps1."
    Write-Host "Run from project root: powershell -ExecutionPolicy Bypass -File .\start-local.ps1"
}

if (-not $env:JWT_SECRET -or [Text.Encoding]::UTF8.GetByteCount($env:JWT_SECRET) -lt 32) {
    throw "JWT_SECRET must be at least 32 bytes. Update backend/.env.local or run start-local.ps1 to generate one."
}

$javaExe = Join-Path -Path $env:JAVA_HOME -ChildPath "bin\java.exe"
if (-not (Test-Path -LiteralPath $javaExe)) {
    throw "JAVA_HOME is invalid: $env:JAVA_HOME"
}

if ($env:WEATHER_PROVIDER -eq "QWEATHER" -and -not (Test-Path -LiteralPath $env:QWEATHER_PRIVATE_KEY_PATH)) {
    throw "QWeather private key file not found: $env:QWEATHER_PRIVATE_KEY_PATH"
}

Write-Host "Starting PV backend on http://localhost:$env:SERVER_PORT"
Write-Host "Weather provider: $env:WEATHER_PROVIDER"
Write-Host "Java: $env:JAVA_HOME"

$mavenRepo = Join-Path -Path $projectRoot -ChildPath ".m2\repository"
New-Item -ItemType Directory -Force -Path $mavenRepo | Out-Null

& mvn "-Dmaven.repo.local=$mavenRepo" @MavenArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
