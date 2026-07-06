# Miniapp

微信小程序端基础结构。当前只包含首页、电站、预测、新闻和个人中心占位页面。

后续通过 `utils/request.js` 调用 Spring Boot 的同一套 RESTful API，禁止直接访问 FastAPI 模型服务。联调前应将 `app.js` 中的 `apiBaseUrl` 改为已备案并配置 HTTPS 的后端域名，并在微信公众平台添加 request 合法域名。
