#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
runtime_dir="$project_root/.local/run"
backend_dir="$project_root/backend"
frontend_dir="$project_root/web-frontend"

stop_pid_file() {
  local name="$1"
  local pid_file="$2"
  [[ -f "$pid_file" ]] || return 0
  local pid
  pid="$(cat "$pid_file" 2>/dev/null || true)"
  if [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1; then
    echo "Stopping $name PID=$pid"
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

  echo "Stopping stale $name process(es): $(echo "$pids" | tr '\n' ' ')"
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

stop_pid_file "backend" "$runtime_dir/backend.pid"
stop_pid_file "agent-runtime" "$runtime_dir/agent-runtime.pid"
stop_pid_file "frontend" "$runtime_dir/frontend.pid"
stop_matching_processes "backend" "$backend_dir/target/classes.*com.example.pvplatform.PvPlatformApplication"
stop_matching_processes "agent-runtime" "$project_root/agent-runtime/server.py"
stop_matching_processes "frontend" "$frontend_dir/node_modules/.bin/vite.*--port"
rm -f "$runtime_dir/backend.env"
rm -f "$runtime_dir/agent-runtime.pid"
rm -f "$runtime_dir/frontend.env"

echo "Stopped local project processes."
