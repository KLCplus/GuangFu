# 天气第三方接口配置

## 本地模式

没有第三方凭证时使用：

```env
WEATHER_PROVIDER=LOCAL
```

`LOCAL` 会根据电站经纬度生成稳定的模拟天气，适合本地开发和前后端联调。

## 和风天气 QWeather JWT

### 需要准备

1. QWeather 项目 ID。
2. QWeather JWT 凭据 ID，也就是 `kid`，不是开发者 ID。
3. 项目绑定的 API Host。
4. Ed25519 私钥文件，格式为 PKCS#8 PEM。

私钥必须是 PKCS#8 PEM 文件。只配置 `QWEATHER_PRIVATE_KEY_PATH` 指向本地文件，不要把 PEM 内容复制进文档、脚本或 Git 仓库。

### 环境变量

真实值放入根目录 `.env`：

```env
WEATHER_PROVIDER=QWEATHER
WEATHER_AUTH_TYPE=JWT
WEATHER_BASE_URL=https://your-qweather-api-host
QWEATHER_PROJECT_ID=your-project-id
QWEATHER_KEY_ID=your-jwt-key-id
QWEATHER_PRIVATE_KEY_PATH=C:\path\ed25519-private.pem
WEATHER_CONNECT_TIMEOUT=3000
WEATHER_RESPONSE_TIMEOUT=5000
WEATHER_CACHE_MINUTES=10
WEATHER_FORECAST_DAYS=3
WEATHER_JWT_TTL_SECONDS=1800
```

### 后端请求方式

实时天气：

```http
GET {WEATHER_BASE_URL}/v7/weather/now?location={longitude},{latitude}&lang=zh
Authorization: Bearer <jwt>
```

天气预报：

```http
GET {WEATHER_BASE_URL}/v7/weather/{WEATHER_FORECAST_DAYS}d?location={longitude},{latitude}&lang=zh
Authorization: Bearer <jwt>
```

注意：`location` 顺序必须是 `longitude,latitude`。

JWT：

- Header：`alg=EdDSA`，`kid=QWEATHER_KEY_ID`
- Payload：`sub=QWEATHER_PROJECT_ID`，`iat=当前时间-30秒`，`exp=当前时间+WEATHER_JWT_TTL_SECONDS`
- 签名：Ed25519 私钥

后端不会把私钥、JWT 或 Key 打到日志。

## 验证

启动后端：

```powershell
./start-local.sh
```

登录获取 JWT：

```powershell
$login = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"username":"admin","password":"你的密码"}'
$token = $login.data.token
```

当前天气：

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/stations/1/weather/current" `
  -Headers @{ Authorization = "Bearer $token" }
```

天气预报：

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/stations/1/weather/forecast" `
  -Headers @{ Authorization = "Bearer $token" }
```

也可以访问：

```text
http://localhost:8080/weather-debug.html
```

## 接入新的天气服务

新增 Provider 时：

1. 在 `module/weather/client` 下新增实现类。
2. 实现 `WeatherProvider`。
3. `supports(String provider)` 返回新 provider 名称。
4. `source()` 返回写入 `weather_data.source` 的来源名。
5. 第三方调用失败时抛出 `BusinessException(502, "...")`，让 `WeatherService` 使用旧缓存降级。
