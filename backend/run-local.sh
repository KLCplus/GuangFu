#!/usr/bin/env bash
set -euo pipefail

backend_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$backend_dir"

env_file="$backend_dir/.env.local"

set_default() {
  local name="$1"
  local value="$2"
  if [[ -z "${!name-}" ]]; then
    export "$name=$value"
  fi
}

import_env_file() {
  local file="$1"
  [[ -f "$file" ]] || return 0

  while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
    local line="${raw_line#"${raw_line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    [[ "$line" == *"="* ]] || continue

    local name="${line%%=*}"
    local value="${line#*=}"
    name="${name%"${name##*[![:space:]]}"}"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"

    if [[ "$value" == \"*\" && "$value" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi

    export "$name=$value"
  done < "$file"
}

set_default BACKEND_HOST "127.0.0.1"
set_default SERVER_PORT "8080"
set_default FRONTEND_HOST "127.0.0.1"
set_default FRONTEND_PORT "5173"

set_default MYSQL_URL "jdbc:mysql://localhost:3306/pv_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
set_default MYSQL_USERNAME "debian-sys-maint"
set_default MYSQL_PASSWORD "rI7mEoN3K246uYNS"
set_default MYSQL_POOL_MAX_SIZE "10"
set_default MYSQL_POOL_MIN_IDLE "2"

set_default JWT_SECRET "pv-platform-linux-local-dev-secret-at-least-32-bytes"
set_default JWT_ACCESS_TOKEN_EXPIRATION "7200"
set_default JWT_REFRESH_TOKEN_EXPIRATION "604800"

set_default MODEL_SERVICE_BASE_URL "http://127.0.0.1:9000"
set_default WEATHER_PROVIDER "LOCAL"
set_default ANALYSIS_LLM_ENABLED "false"
set_default REDIS_ENABLED "false"
set_default CACHE_TYPE "simple"
set_default REDIS_HOST "127.0.0.1"
set_default REDIS_PORT "6379"
set_default REDIS_PASSWORD ""
set_default REDIS_DATABASE "0"
set_default REDIS_TIMEOUT "3000"

set_default MAIL_ENABLED "false"
set_default OAUTH_GITHUB_ENABLED "false"
set_default FACE_PROVIDER "local"
set_default SECURITY_DEBUG_OPEN "true"
set_default PVOUTPUT_ENABLED "true"

set_default FACE_STORAGE_DIR "./data/faces"
set_default AVATAR_STORAGE_DIR "./data/avatars"
set_default PV_IMPORT_STORAGE_DIR "./data/pv-imports"

import_env_file "$env_file"

if [[ ${#JWT_SECRET} -lt 32 ]]; then
  echo "JWT_SECRET must be at least 32 characters." >&2
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "java not found. Install JDK 21 first." >&2
  exit 1
fi

java_version="$(java -version 2>&1 | awk -F '"' '/version/ {print $2; exit}')"
java_major="${java_version%%.*}"
if [[ "$java_major" != "21" && "$java_major" != "22" && "$java_major" != "23" && "$java_major" != "24" && "$java_major" != "25" ]]; then
  echo "This project needs Java 21+. Current java version: $java_version" >&2
  exit 1
fi

mkdir -p "$backend_dir/.m2/repository" "$backend_dir/data/faces" "$backend_dir/data/avatars" "$backend_dir/data/pv-imports"

echo "Starting backend: http://${BACKEND_HOST}:${SERVER_PORT}"
echo "MySQL user: ${MYSQL_USERNAME}"
echo "Weather provider: ${WEATHER_PROVIDER}"
echo "Redis enabled: ${REDIS_ENABLED}"

exec mvn "-Dmaven.repo.local=$backend_dir/.m2/repository" "$@"
