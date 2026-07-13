#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
backend_dir="$project_root/backend"
frontend_dir="$project_root/web-frontend"
env_file="$project_root/.env"
runtime_dir="$project_root/.local/run"
log_dir="$project_root/.local/logs"

backend_only=0
frontend_only=0
init_db=0
force_db_reset=0
with_redis=0
install_missing=1
agent_runtime_mode="auto"

usage() {
  cat <<'USAGE'
Usage: ./start-local.sh [options]

Options:
  --backend-only       Start only Spring Boot backend.
  --frontend-only      Start only Vue frontend.
  --init-db            Import backend/src/main/resources/sql/init.sql before starting.
  --force-db-reset     Allow --init-db to reset existing tables without prompt.
  --with-redis         Enable Redis cache and start redis-server if port 6379 is closed.
  --agent-runtime      Start Python migrated agent-runtime.
  --no-agent-runtime   Do not start Python migrated agent-runtime.
  --no-install         Do not try to install missing system packages.
  -h, --help           Show help.

Default:
  Start backend and frontend with the existing local database, QWeather
  cached for 20 minutes, and Aliyun face/OSS enabled.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backend-only) backend_only=1 ;;
    --frontend-only) frontend_only=1 ;;
    --init-db) init_db=1 ;;
    --force-db-reset) force_db_reset=1 ;;
    --with-redis) with_redis=1 ;;
    --agent-runtime) agent_runtime_mode="on" ;;
    --no-agent-runtime) agent_runtime_mode="off" ;;
    --no-install) install_missing=0 ;;
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

log() {
  printf '[local] %s\n' "$*"
}

die() {
  printf '[local] ERROR: %s\n' "$*" >&2
  exit 1
}

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

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

default_env_value() {
  local name="$1"
  local value="$2"
  local current
  current="$(read_env_value "$name" || true)"
  if [[ -z "$current" ]]; then
    upsert_env_value "$name" "$value"
  fi
}

write_env_exports() {
  local source_file="$1"
  local target_file="$2"
  : > "$target_file"
  [[ -f "$source_file" ]] || return 0

  while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
    local line name value
    line="$(trim "$raw_line")"
    [[ -z "$line" || "$line" == \#* || "$line" != *"="* ]] && continue

    name="$(trim "${line%%=*}")"
    value="$(trim "${line#*=}")"
    if [[ "${value:0:1}" == '"' && "${value: -1}" == '"' ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value:0:1}" == "'" && "${value: -1}" == "'" ]]; then
      value="${value:1:${#value}-2}"
    fi
    printf 'export %s=%q\n' "$name" "$value" >> "$target_file"
  done < "$source_file"
}

ensure_env_defaults() {
  if [[ ! -f "$env_file" ]]; then
    cat > "$env_file" <<'ENV'
# Linux local development config. This file is ignored by Git.
ENV
  fi
  sed -i 's/\r$//' "$env_file"

  default_env_value MYSQL_URL "jdbc:mysql://127.0.0.1:3306/pv_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
  default_env_value MYSQL_USERNAME "root"
  default_env_value MYSQL_PASSWORD ""
  default_env_value MYSQL_ROOT_PASSWORD ""
  upsert_env_value SERVER_PORT "8080"
  upsert_env_value BACKEND_HOST "127.0.0.1"
  upsert_env_value FRONTEND_HOST "127.0.0.1"
  upsert_env_value FRONTEND_PORT "5173"
  upsert_env_value MODEL_SERVICE_BASE_URL "http://127.0.0.1:9000"

  upsert_env_value WEATHER_PROVIDER "QWEATHER"
  upsert_env_value WEATHER_CACHE_MINUTES "20"
  upsert_env_value FACE_PROVIDER "aliyun"
  default_env_value PVOUTPUT_ENABLED "false"
  default_env_value PVOUTPUT_INITIAL_DELAY_MS "30000"
  default_env_value PVOUTPUT_SYNC_INTERVAL_MS "600000"
  default_env_value ANALYSIS_LLM_ENABLED "true"
  default_env_value AGENT_RUNTIME_MODE "migrated"
  default_env_value AGENT_RUNTIME_PORT "9101"
  default_env_value AGENT_RUNTIME_HOST "127.0.0.1"
  default_env_value AGENT_RUNTIME_URL "http://127.0.0.1:9101"
  default_env_value AGENT_INTERNAL_TOKEN "local-agent-runtime-token"
  default_env_value OAUTH_GITHUB_ENABLED "false"
  default_env_value MAIL_ENABLED "false"
  upsert_env_value SECURITY_DEBUG_OPEN "true"

  upsert_env_value REDIS_HOST "127.0.0.1"
  upsert_env_value REDIS_PORT "6379"
  upsert_env_value REDIS_PASSWORD ""
  upsert_env_value REDIS_DATABASE "0"
  upsert_env_value REDIS_TIMEOUT "3000"
  upsert_env_value REDIS_CONNECT_TIMEOUT "3000"

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

install_packages() {
  [[ "$install_missing" == "1" ]] || return 1
  command -v apt-get >/dev/null 2>&1 || return 1
  log "Installing missing packages with apt-get. sudo may ask for your password."
  sudo apt-get update
  sudo apt-get install -y "$@"
}

require_command() {
  local command_name="$1"
  local package_name="$2"
  if command -v "$command_name" >/dev/null 2>&1; then
    return 0
  fi
  install_packages "$package_name" || die "$command_name not found. Install package '$package_name' and retry."
}

port_open() {
  local port="$1"
  timeout 1 bash -c "</dev/tcp/127.0.0.1/$port" >/dev/null 2>&1
}

wait_for_port() {
  local name="$1"
  local port="$2"
  local seconds="$3"
  local start
  start="$(date +%s)"
  while (( "$(date +%s)" - start < seconds )); do
    if port_open "$port"; then
      log "$name is listening on 127.0.0.1:$port"
      return 0
    fi
    sleep 1
  done
  return 1
}

stop_pid_file() {
  local pid_file="$1"
  [[ -f "$pid_file" ]] || return 0
  local pid
  pid="$(cat "$pid_file" 2>/dev/null || true)"
  if [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1; then
    kill -- "-$pid" >/dev/null 2>&1 || true
    kill "$pid" >/dev/null 2>&1 || true
    sleep 1
    kill -0 "$pid" >/dev/null 2>&1 && kill -- "-$pid" >/dev/null 2>&1 || true
    kill -0 "$pid" >/dev/null 2>&1 && kill -9 "$pid" >/dev/null 2>&1 || true
  fi
  rm -f "$pid_file"
}

stop_matching_processes() {
  local name="$1"
  local pattern="$2"
  command -v pgrep >/dev/null 2>&1 || return 0

  local pids
  pids="$(pgrep -f "$pattern" 2>/dev/null || true)"
  [[ -n "$pids" ]] || return 0

  log "Stopping stale $name process(es): $(echo "$pids" | tr '\n' ' ')"
  while IFS= read -r pid; do
    [[ -n "$pid" && "$pid" != "$$" ]] || continue
    kill "$pid" >/dev/null 2>&1 || true
  done <<< "$pids"

  sleep 1
  pids="$(pgrep -f "$pattern" 2>/dev/null || true)"
  while IFS= read -r pid; do
    [[ -n "$pid" && "$pid" != "$$" ]] || continue
    kill -9 "$pid" >/dev/null 2>&1 || true
  done <<< "$pids"
}

stop_previous() {
  stop_pid_file "$runtime_dir/backend.pid"
  stop_pid_file "$runtime_dir/agent-runtime.pid"
  stop_pid_file "$runtime_dir/frontend.pid"
  stop_matching_processes "backend" "$backend_dir/target/classes.*com.example.pvplatform.PvPlatformApplication"
  stop_matching_processes "agent-runtime" "$project_root/agent-runtime/server.py"
  stop_matching_processes "frontend" "$frontend_dir/node_modules/.bin/vite.*--port"
}

start_mysql_if_needed() {
  require_command mysql mysql-client
  if port_open 3306; then
    return 0
  fi

  if command -v systemctl >/dev/null 2>&1; then
    log "Starting MySQL service. sudo may ask for your password."
    sudo systemctl start mysql 2>/dev/null || sudo systemctl start mariadb 2>/dev/null || true
  fi

  if ! port_open 3306; then
    if ! command -v mysqld >/dev/null 2>&1; then
      install_packages mysql-server || true
    fi
  fi

  port_open 3306 || die "MySQL is not running on 127.0.0.1:3306."
}

mysql_project_cmd() {
  local database="${1:-}"
  local user password
  user="$(read_env_value MYSQL_USERNAME)"
  password="$(read_env_value MYSQL_PASSWORD)"
  if [[ -n "$database" ]]; then
    MYSQL_PWD="$password" mysql --protocol=tcp -h127.0.0.1 -u"$user" "$database"
  else
    MYSQL_PWD="$password" mysql --protocol=tcp -h127.0.0.1 -u"$user"
  fi
}

mysql_project_query() {
  local sql="$1"
  local database="${2:-}"
  local user password
  user="$(read_env_value MYSQL_USERNAME)"
  password="$(read_env_value MYSQL_PASSWORD)"
  if [[ -n "$database" ]]; then
    MYSQL_PWD="$password" mysql --protocol=tcp -h127.0.0.1 -u"$user" "$database" -N -B -e "$sql"
  else
    MYSQL_PWD="$password" mysql --protocol=tcp -h127.0.0.1 -u"$user" -N -B -e "$sql"
  fi
}

mysql_admin_exec() {
  local sql="$1"
  if sudo -n mysql -e "$sql" >/dev/null 2>&1; then
    return 0
  fi

  local root_password
  root_password="$(read_env_value MYSQL_ROOT_PASSWORD || true)"
  if [[ -n "$root_password" ]] && MYSQL_PWD="$root_password" mysql --protocol=tcp -h127.0.0.1 -uroot -e "$sql" >/dev/null 2>&1; then
    return 0
  fi

  log "Need MySQL admin access to create the local project database/user."
  if [[ ! -t 0 ]]; then
    die "Run ./start-local.sh from an interactive terminal once, or create MySQL user '$(read_env_value MYSQL_USERNAME)' for database 'pv_platform' manually."
  fi
  log "Trying sudo mysql now. Enter your Linux password if prompted."
  sudo mysql -e "$sql"
}

ensure_database_user() {
  start_mysql_if_needed

  if mysql_project_query "SELECT 1;" >/dev/null 2>&1; then
    return 0
  fi

  local user password sql
  user="$(read_env_value MYSQL_USERNAME)"
  password="$(read_env_value MYSQL_PASSWORD)"
  if [[ "$user" == "root" ]]; then
    die "Cannot connect to MySQL with MYSQL_USERNAME=root from $env_file. Update MYSQL_PASSWORD/MYSQL_URL there, then retry."
  fi
  sql="
CREATE DATABASE IF NOT EXISTS pv_platform
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${user}'@'127.0.0.1' IDENTIFIED BY '${password}';
CREATE USER IF NOT EXISTS '${user}'@'localhost' IDENTIFIED BY '${password}';
ALTER USER '${user}'@'127.0.0.1' IDENTIFIED BY '${password}';
ALTER USER '${user}'@'localhost' IDENTIFIED BY '${password}';
GRANT ALL PRIVILEGES ON pv_platform.* TO '${user}'@'127.0.0.1';
GRANT ALL PRIVILEGES ON pv_platform.* TO '${user}'@'localhost';
FLUSH PRIVILEGES;"

  mysql_admin_exec "$sql"
  mysql_project_query "SELECT 1;" >/dev/null 2>&1 || die "Cannot connect to MySQL with $(read_env_value MYSQL_USERNAME)."
}

table_count() {
  mysql_project_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='pv_platform';" 2>/dev/null || printf '0\n'
}

init_database() {
  ensure_database_user

  local tables
  tables="$(table_count | tr -d '\r\n[:space:]')"
  if [[ -z "$tables" ]]; then
    tables=0
  fi

  if [[ "$tables" != "0" && "$force_db_reset" != "1" ]]; then
    log "pv_platform already has ${tables} tables. Keeping existing data."
    log "Use --init-db --force-db-reset if you explicitly want to re-import init.sql."
    return 0
  fi

  log "Importing backend/src/main/resources/sql/init.sql ..."
  mysql_project_cmd < "$backend_dir/src/main/resources/sql/init.sql"

  if [[ -f "$backend_dir/src/main/resources/sql/local-analysis-seed.sql" ]]; then
    mysql_project_cmd pv_platform < "$backend_dir/src/main/resources/sql/local-analysis-seed.sql"
  fi
}

ensure_schema_if_empty() {
  [[ "$frontend_only" == "1" ]] && return 0
  ensure_database_user
  local tables
  tables="$(table_count | tr -d '\r\n[:space:]')"
  if [[ -z "$tables" || "$tables" == "0" ]]; then
    log "pv_platform is empty; importing init.sql once."
    force_db_reset=1
    init_database
  fi
}

start_redis_if_needed() {
  [[ "$with_redis" == "1" ]] || return 0
  if port_open 6379; then
    log "Redis already listening on 6379."
    return 0
  fi
  require_command redis-server redis-server
  log "Starting redis-server."
  redis-server --daemonize yes
}

ensure_backend_tools() {
  require_command java openjdk-21-jdk
  require_command mvn maven
}

ensure_frontend_tools() {
  require_command npm npm
}

ensure_agent_runtime_tools() {
  require_command python3 python3
}

start_backend() {
  ensure_backend_tools
  mkdir -p "$backend_dir/.m2/repository" "$backend_dir/data/faces" "$backend_dir/data/avatars" "$backend_dir/data/pv-imports"
  local backend_env="$runtime_dir/backend.env"
  write_env_exports "$env_file" "$backend_env"
  log "Starting backend, log: $log_dir/backend.log"
  setsid bash -c 'source "$1" && cd "$2" && exec mvn "-Dmaven.repo.local=$3" spring-boot:run' _ "$backend_env" "$backend_dir" "$backend_dir/.m2/repository" > "$log_dir/backend.log" 2>&1 < /dev/null &
  echo $! > "$runtime_dir/backend.pid"
}

start_frontend() {
  ensure_frontend_tools
  if [[ ! -d "$frontend_dir/node_modules" ]]; then
    log "Installing frontend dependencies with npm ci ..."
    (cd "$frontend_dir" && npm ci)
  fi
  local frontend_host frontend_port
  frontend_host="$(read_env_value FRONTEND_HOST)"
  frontend_port="$(read_env_value FRONTEND_PORT)"
  log "Starting frontend, log: $log_dir/frontend.log"
  setsid bash -c 'cd "$1" && exec npm run dev -- --host "$2" --port "$3" --strictPort' _ "$frontend_dir" "$frontend_host" "$frontend_port" > "$log_dir/frontend.log" 2>&1 < /dev/null &
  echo $! > "$runtime_dir/frontend.pid"
}

should_start_agent_runtime() {
  [[ "$frontend_only" == "1" ]] && return 1
  [[ "$agent_runtime_mode" == "on" ]] && return 0
  [[ "$agent_runtime_mode" == "off" ]] && return 1
  [[ "$(read_env_value AGENT_RUNTIME_MODE)" == "migrated" ]]
}

start_agent_runtime() {
  ensure_agent_runtime_tools
  local runtime_env="$runtime_dir/backend.env"
  local spring_url runtime_host runtime_port
  spring_url="http://127.0.0.1:$(read_env_value SERVER_PORT)"
  runtime_host="$(read_env_value AGENT_RUNTIME_HOST)"
  runtime_port="$(read_env_value AGENT_RUNTIME_PORT)"
  log "Starting agent-runtime, log: $log_dir/agent-runtime.log"
  setsid bash -c 'source "$1" && cd "$2" && PYTHONPATH="$2/agent-runtime" exec python3 "$2/agent-runtime/server.py" --host "$3" --port "$4" --spring-base-url "$5"' _ "$runtime_env" "$project_root" "$runtime_host" "$runtime_port" "$spring_url" > "$log_dir/agent-runtime.log" 2>&1 < /dev/null &
  echo $! > "$runtime_dir/agent-runtime.pid"
}

check_process() {
  local name="$1"
  local pid_file="$2"
  local log_file="$3"
  local pid
  pid="$(cat "$pid_file" 2>/dev/null || true)"
  if [[ -z "$pid" ]] || ! kill -0 "$pid" >/dev/null 2>&1; then
    tail -n 80 "$log_file" >&2 || true
    die "$name exited during startup. See $log_file"
  fi
}

ensure_env_defaults
stop_previous
start_redis_if_needed

if [[ "$init_db" == "1" ]]; then
  init_database
else
  ensure_schema_if_empty
fi

if [[ "$frontend_only" != "1" ]]; then
  start_backend
fi

if [[ "$backend_only" != "1" ]]; then
  start_frontend
fi

backend_port="$(read_env_value SERVER_PORT)"
frontend_port="$(read_env_value FRONTEND_PORT)"

if [[ "$frontend_only" != "1" ]]; then
  if ! wait_for_port "Backend" "$backend_port" 90; then
    check_process "Backend" "$runtime_dir/backend.pid" "$log_dir/backend.log"
    tail -n 120 "$log_dir/backend.log" >&2 || true
    die "Backend did not become ready on port $backend_port."
  fi
fi

agent_runtime_started=0
if should_start_agent_runtime; then
  start_agent_runtime
  agent_runtime_started=1
  runtime_port="$(read_env_value AGENT_RUNTIME_PORT)"
  if ! wait_for_port "Agent runtime" "$runtime_port" 30; then
    check_process "Agent runtime" "$runtime_dir/agent-runtime.pid" "$log_dir/agent-runtime.log"
    tail -n 120 "$log_dir/agent-runtime.log" >&2 || true
    die "Agent runtime did not become ready on port $runtime_port."
  fi
fi

if [[ "$backend_only" != "1" ]]; then
  if ! wait_for_port "Frontend" "$frontend_port" 60; then
    check_process "Frontend" "$runtime_dir/frontend.pid" "$log_dir/frontend.log"
    tail -n 80 "$log_dir/frontend.log" >&2 || true
    die "Frontend did not become ready on port $frontend_port."
  fi
fi

echo
echo "Local services:"
[[ "$frontend_only" == "1" ]] || echo "  Backend:  http://127.0.0.1:$backend_port"
[[ "$agent_runtime_started" == "1" ]] && echo "  Agent runtime: http://127.0.0.1:$(read_env_value AGENT_RUNTIME_PORT)"
[[ "$backend_only" == "1" ]] || echo "  Frontend: http://127.0.0.1:$frontend_port"
echo
echo "Logs:"
[[ "$frontend_only" == "1" ]] || echo "  Backend:  $log_dir/backend.log"
[[ "$agent_runtime_started" == "1" ]] && echo "  Agent runtime: $log_dir/agent-runtime.log"
[[ "$backend_only" == "1" ]] || echo "  Frontend: $log_dir/frontend.log"
echo
echo "Stop with: ./stop-local.sh"
