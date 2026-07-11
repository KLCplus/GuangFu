#!/bin/bash
# Start backend with local env vars
cd /home/konglingchen/code/CHENGDU/GuangFu/backend

export JWT_SECRET="pv-platform-linux-local-dev-secret-at-least-32-bytes"
export MYSQL_URL="jdbc:mysql://localhost:3306/pv_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
export MYSQL_USERNAME="debian-sys-maint"
export MYSQL_PASSWORD="rI7mEoN3K246uYNS"
export OAUTH_GITHUB_ENABLED="false"
export FACE_PROVIDER="local"
export WEATHER_PROVIDER="LOCAL"
export REDIS_ENABLED="false"
export CACHE_TYPE="simple"
export PVOUTPUT_ENABLED="false"
export SERVER_PORT="8080"
export MODEL_SERVICE_BASE_URL="http://127.0.0.1:9000"
export SECURITY_DEBUG_OPEN="true"
export ANALYSIS_LLM_ENABLED="false"

mvn spring-boot:run -Dspring-boot.run.profiles=local "$@"
