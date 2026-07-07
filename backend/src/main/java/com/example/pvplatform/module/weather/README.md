# weather 模块实现现状

## 模块职责

`weather` 负责按电站经纬度获取当前天气和天气预报，并缓存到 `weather_data`，供页面展示、预测输入和分析报告使用。

## 已实现

- 当前天气接口：`GET /api/stations/{stationId}/weather/current`。
- 天气预报接口：`GET /api/stations/{stationId}/weather/forecast`。
- 电站查看权限校验复用 `StationPermissionService.requireView`。
- 电站缺少经纬度时返回 `BusinessException(400, "电站未配置经纬度，无法获取天气")`。
- 当前天气和预报都会按 `weather.cache-minutes` 读取数据库缓存。
- 缓存不存在或过期时调用当前配置的 `WeatherProvider`，并把结果写入 `weather_data`。
- 支持 `LOCAL` provider，适合本地开发和没有第三方凭证的联调。
- 支持 `QWEATHER` provider，使用 QWeather JWT + Ed25519 私钥调用和风天气。
- QWeather 请求使用 `Authorization: Bearer <jwt>`，不在 URL 暴露 key。
- 第三方调用失败时抛出 502；如果存在旧缓存，`WeatherService` 会降级返回旧缓存。
- 静态调试页：`/weather-debug.html`。

## 配置

完整模板见：

```text
.env.example
```

本地真实值放在：

```text
.env
```

QWeather 关键配置：

```env
WEATHER_PROVIDER=QWEATHER
WEATHER_AUTH_TYPE=JWT
WEATHER_BASE_URL=https://your-qweather-api-host
QWEATHER_PROJECT_ID=your-project-id
QWEATHER_KEY_ID=your-jwt-key-id
QWEATHER_PRIVATE_KEY_PATH=C:\path\ed25519-private.pem
WEATHER_CACHE_MINUTES=10
WEATHER_FORECAST_DAYS=3
```

## 相关表

- `weather_data`
- 依赖 `power_station.longitude` 和 `power_station.latitude`

## 限制

- 当前只内置 `LOCAL` 和 `QWEATHER`。
- 预报缓存使用 `weather_data.weather_code` 的 `FORECAST:` 前缀区分，没有单独预报表。
- 没有定时刷新任务，只在接口调用时懒加载/刷新。
- `LOCAL` provider 是确定性模拟数据，不代表真实天气。

## 测试

- `WeatherServiceTest` 覆盖缓存命中和服务流程。
- `QWeatherProviderTest` 覆盖 QWeather JWT 请求认证和接口调用构造。
