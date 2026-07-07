# 后端多层架构与性能说明

## 目标结构

```text
Client / Frontend
        |
        v
Nginx / Web Server
        |
        v
Spring Boot API instances
        |
        +--> Redis: cache, rate limit, verification code, login lock
        |
        +--> MySQL: persistent business data
        |
        +--> FastAPI model service / QWeather / mail / OSS
```

## 已完成的后端升级

- 安全层：JWT、Spring Security、管理员接口鉴权、统一 401/403。
- 数据层：MyBatis Plus、MySQL、逻辑删除、分页插件、HikariCP 参数化。
- 缓存层：Spring Cache，支持 `simple` 和 `redis`。
- 分布式状态：验证码、登录失败锁定、OpenAPI 限流/额度支持 Redis，Redis 关闭时降级本机内存。
- 异步能力：应用线程池，OpenAPI 调用日志可异步写入。
- 第三方天气：`LOCAL` 和 `QWEATHER` Provider，QWeather 使用 JWT + Ed25519。
- Web 服务器参数：Tomcat 线程数、连接数和排队数支持环境变量配置。

## Redis 配置

本地开发：

```env
REDIS_ENABLED=false
CACHE_TYPE=simple
```

部署建议：

```env
REDIS_ENABLED=true
CACHE_TYPE=redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DATABASE=0
REDIS_TIMEOUT=3000
CACHE_REDIS_TTL=600000
```

启用 Redis 后共享：

- `pv:auth:verify:*`：邮箱验证码、验证码尝试、发送冷却、每日发送次数。
- `pv:auth:login:*`：登录失败计数和账号锁定。
- `pv:openapi:quota:*`：API Key 每分钟限流和每日额度。
- Spring Cache：电站、模型、新闻等热点数据。

## Nginx 示例

```nginx
upstream pv_backend {
    server 127.0.0.1:8080;
    # server 127.0.0.1:8081;
}

server {
    listen 80;
    server_name example.com;

    client_max_body_size 20m;

    location / {
        proxy_pass http://pv_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## 仍可继续升级

- 预测任务改为真正异步队列：创建任务立即返回，后台 worker 调模型。
- 文件导入改为后台任务，避免大文件占用请求线程。
- OpenAPI 调用日志改为消息队列批量入库。
- 权限缓存做精确失效。
- 接入 Prometheus/Grafana 监控请求耗时、线程池、连接池、Redis 命中率。
