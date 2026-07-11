#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
runtime_dir="$project_root/.local/run"

stop_pid_file() {
  local name="$1"
  local pid_file="$2"
  [[ -f "$pid_file" ]] || return 0
  local pid
  pid="$(cat "$pid_file" 2>/dev/null || true)"
  if [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1; then
    echo "Stopping $name PID=$pid"
    kill "$pid" >/dev/null 2>&1 || true
    sleep 1
    kill -0 "$pid" >/dev/null 2>&1 && kill -9 "$pid" >/dev/null 2>&1 || true
  fi
  rm -f "$pid_file"
}

stop_pid_file "backend" "$runtime_dir/backend.pid"
stop_pid_file "frontend" "$runtime_dir/frontend.pid"
rm -f "$runtime_dir/backend.env"

echo "Stopped local project processes."
