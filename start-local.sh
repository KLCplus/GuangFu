#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
backend_dir="$project_root/backend"
frontend_dir="$project_root/web-frontend"
env_file="$backend_dir/.env.local"
runtime_dir="$project_root/.local/run"
log_dir="$project_root/.local/logs"

backend_only=0
frontend_only=0
init_db=0
force_db_reset=0
with_redis=0

usage() {
  cat <<'USAGE'
Usage: ./start-local.sh [options]

Options:
  --backend-only       Start only Spring Boot backend.
  --frontend-only      Start only Vue frontend.
  --init-db            Import backend/src/main/resources/sql/init.sql before starting.
  --force-db-reset     Allow --init-db to reset existing tables without prompt.
  --with-redis         Enable Redis cache and start redis-server if port 6379 is closed.
  -h, --help           Show help.

Default:
  Start backend and frontend with Linux local settings.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backend-only) backend_only=1 ;;
    --frontend-only) frontend_only=1 ;;
    --init-db) init_db=1 ;;
    --force-db-reset) force_db_reset=1 ;;
    --with-redis) with_redis=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
  shift
done

if [[ "$backend_only" == "1" && "$frontend_only" == "1" ]]; then
  echo "--backend-only and --frontend-only cannot be used together." >&2
  exit 1
fi

mkdir -p "$runtime_dir" "$log_dir"

read_env_value() {
  local name="$1"
  [[ -f "$env_file" ]] || return 0
  awk -F= -v key="$name" '$1 == key {print substr($0, index($0, "=") + 1); exit}' "$env_file" | tr -d '\r'
}

upsert_env_value() {
  local name="$1"
  local value="$2"
  mkdir -p "$(dirname "$env_file")"
  touch "$env_file"

  if grep -qE "^${name}=" "$env_file"; then
    local tmp_file
    tmp_file="$(mktemp)"
    awk -v key="$name" -v replacement="${name}=${value}" '
      BEGIN { done = 0 }
      $0 ~ "^" key "=" { print replacement; done = 1; next }
      { print }
      END { if (!done) print replacement }
    ' "$env_file" > "$tmp_file"
    mv "$tmp_file" "$env_file"
  else
    printf '%s=%s\n' "$name" "$value" >> "$env_file"
  fi
}

ensure_env_defaults() {
  [[ -f "$env_file" ]] || {
    cat > "$env_file" <<'ENV'
# Linux local development config. This file is ignored by Git.
ENV
  }
  sed -i 's/\r$//' "$env_file"

  upsert_env_value MYSQL_URL "jdbc:mysql://localhost:3306/pv_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
  upsert_env_value MYSQL_USERNAME "debian-sys-maint"
  upsert_env_value MYSQL_PASSWORD "rI7mEoN3K246uYNS"
  upsert_env_value SERVER_PORT "8080"
  upsert_env_value BACKEND_HOST "127.0.0.1"
  upsert_env_value FRONTEND_HOST "127.0.0.1"
  upsert_env_value FRONTEND_PORT "5173"
  upsert_env_value MODEL_SERVICE_BASE_URL "http://127.0.0.1:9000"
  upsert_env_value WEATHER_PROVIDER "LOCAL"
  upsert_env_value ANALYSIS_LLM_ENABLED "false"
  upsert_env_value FACE_PROVIDER "local"
  upsert_env_value OAUTH_GITHUB_ENABLED "false"
  upsert_env_value MAIL_ENABLED "false"
  upsert_env_value PVOUTPUT_ENABLED "true"
  upsert_env_value SECURITY_DEBUG_OPEN "true"
  upsert_env_value REDIS_HOST "127.0.0.1"
  upsert_env_value REDIS_PORT "6379"
  upsert_env_value REDIS_PASSWORD ""
  upsert_env_value REDIS_DATABASE "0"
  upsert_env_value REDIS_TIMEOUT "3000"

  if [[ "$with_redis" == "1" ]]; then
    upsert_env_value REDIS_ENABLED "true"
    upsert_env_value CACHE_TYPE "redis"
  else
    upsert_env_value REDIS_ENABLED "false"
    upsert_env_value CACHE_TYPE "simple"
  fi

  local jwt_secret
  jwt_secret="$(read_env_value JWT_SECRET || true)"
  if [[ ${#jwt_secret} -lt 32 ]]; then
    upsert_env_value JWT_SECRET "pv-platform-linux-local-dev-secret-at-least-32-bytes"
  fi
}

port_open() {
  local port="$1"
  timeout 1 bash -c "</dev/tcp/127.0.0.1/$port" >/dev/null 2>&1
}

stop_pid_file() {
  local pid_file="$1"
  [[ -f "$pid_file" ]] || return 0
  local pid
  pid="$(cat "$pid_file" 2>/dev/null || true)"
  if [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1; then
    kill "$pid" >/dev/null 2>&1 || true
    sleep 1
    kill -0 "$pid" >/dev/null 2>&1 && kill -9 "$pid" >/dev/null 2>&1 || true
  fi
  rm -f "$pid_file"
}

stop_previous() {
  stop_pid_file "$runtime_dir/backend.pid"
  stop_pid_file "$runtime_dir/frontend.pid"
}

mysql_args() {
  local user password
  user="$(read_env_value MYSQL_USERNAME)"
  password="$(read_env_value MYSQL_PASSWORD)"
  printf -- "-u%s\n-p%s\n" "$user" "$password"
}

init_database() {
  command -v mysql >/dev/null 2>&1 || {
    echo "mysql client not found. Install mysql-client first." >&2
    exit 1
  }

  local tables_count
  tables_count="$(mysql "$(mysql_args | sed -n '1p')" "$(mysql_args | sed -n '2p')" -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='pv_platform';" 2>/dev/null || echo 0)"

  if [[ "$tables_count" != "0" && "$force_db_reset" != "1" ]]; then
    echo "pv_platform already has ${tables_count} tables. init.sql contains DROP TABLE."
    read -r -p "Reset database now? Type RESET to continue: " answer
    if [[ "$answer" != "RESET" ]]; then
      echo "Database initialization skipped."
      return 0
    fi
  fi

  echo "Importing backend/src/main/resources/sql/init.sql ..."
  mysql "$(mysql_args | sed -n '1p')" "$(mysql_args | sed -n '2p')" < "$backend_dir/src/main/resources/sql/init.sql"

  if [[ -f "$backend_dir/src/main/resources/sql/local-analysis-seed.sql" ]]; then
    mysql "$(mysql_args | sed -n '1p')" "$(mysql_args | sed -n '2p')" pv_platform < "$backend_dir/src/main/resources/sql/local-analysis-seed.sql"
  fi
}

start_redis_if_needed() {
  [[ "$with_redis" == "1" ]] || return 0
  if port_open 6379; then
    echo "Redis already listening on 6379."
    return 0
  fi
  command -v redis-server >/dev/null 2>&1 || {
    echo "redis-server not found. Re-run without --with-redis or install Redis." >&2
    exit 1
  }
  redis-server --daemonize yes
}

start_backend() {
  chmod +x "$backend_dir/run-local.sh"
  nohup bash -c 'cd "$1" && exec ./run-local.sh spring-boot:run' _ "$backend_dir" > "$log_dir/backend.log" 2>&1 < /dev/null &
  echo $! > "$runtime_dir/backend.pid"
  echo "Backend starting, log: $log_dir/backend.log"
}

start_frontend() {
  command -v npm >/dev/null 2>&1 || {
    echo "npm not found. Install Node.js/npm first." >&2
    exit 1
  }
  if [[ ! -d "$frontend_dir/node_modules" ]]; then
    echo "Installing frontend dependencies with npm ci ..."
    (cd "$frontend_dir" && npm ci)
  fi
  local frontend_host frontend_port
  frontend_host="$(read_env_value FRONTEND_HOST)"
  frontend_port="$(read_env_value FRONTEND_PORT)"
  nohup bash -c 'cd "$1" && exec npm run dev -- --host "$2" --port "$3" --strictPort' _ "$frontend_dir" "$frontend_host" "$frontend_port" > "$log_dir/frontend.log" 2>&1 < /dev/null &
  echo $! > "$runtime_dir/frontend.pid"
  echo "Frontend starting, log: $log_dir/frontend.log"
}

ensure_env_defaults
stop_previous
start_redis_if_needed

if [[ "$init_db" == "1" ]]; then
  init_database
fi

if [[ "$frontend_only" != "1" ]]; then
  start_backend
fi

if [[ "$backend_only" != "1" ]]; then
  start_frontend
fi

echo
echo "Local services:"
[[ "$frontend_only" == "1" ]] || echo "  Backend:  http://127.0.0.1:$(read_env_value SERVER_PORT)"
[[ "$backend_only" == "1" ]] || echo "  Frontend: http://127.0.0.1:$(read_env_value FRONTEND_PORT)"
echo
echo "Stop with: ./stop-local.sh"
