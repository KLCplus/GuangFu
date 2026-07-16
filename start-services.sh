#!/bin/bash
# 启动所有服务，脱离终端会话独立运行
LOG_DIR="/root/shixun/GuangFu/logs"
mkdir -p "$LOG_DIR"

# 1. MySQL
service mysql start 2>/dev/null
sleep 1

# 2. 后端 Java
cd /root/shixun/GuangFu/backend
nohup java -jar target/pv-platform-backend-0.1.0-SNAPSHOT.jar > "$LOG_DIR/backend.log" 2>&1 &
echo $! > "$LOG_DIR/backend.pid"
echo "后端已启动 PID=$(cat $LOG_DIR/backend.pid)"

# 3. 模型服务 FastAPI
source /usr/local/miniconda3/etc/profile.d/conda.sh
conda activate pv
cd /root/shixun/GuangFu/model-service
nohup uvicorn app.main:app --host 0.0.0.0 --port 9000 > "$LOG_DIR/model-service.log" 2>&1 &
echo $! > "$LOG_DIR/model-service.pid"
echo "模型服务已启动 PID=$(cat $LOG_DIR/model-service.pid)"

# 4. 前端 Vite Preview
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"
nvm use 22
cd /root/shixun/GuangFu/web-frontend
nohup node node_modules/vite/bin/vite.js preview --host 0.0.0.0 --port 5173 > "$LOG_DIR/frontend.log" 2>&1 &
echo $! > "$LOG_DIR/frontend.pid"
echo "前端已启动 PID=$(cat $LOG_DIR/frontend.pid)"

echo "=== 全部启动完成 ==="
