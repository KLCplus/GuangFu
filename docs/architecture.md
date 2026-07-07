# 系统架构

```text
Vue Web / 微信小程序 / 第三方客户端
        |
        | REST API
        v
Spring Boot 后端
        |
        +--> MySQL：业务持久化
        |
        +--> Redis：缓存、验证码、登录锁定、OpenAPI 限流和额度
        |
        +--> FastAPI 模型服务：预测推理
        |
        +--> QWeather / Mail / OSS：第三方能力
```

## 边界

- 前端只访问 Spring Boot，不直接访问 MySQL、FastAPI、天气服务或 OSS。
- Spring Boot 负责认证、授权、业务规则、任务记录、统一响应和审计。
- FastAPI 只负责模型服务能力，不处理用户、电站权限和 API Key。
- Redis 是部署增强项；本地可以降级到内存状态。

## 后端分层

```text
Controller
  -> Service
    -> Mapper / Client / Provider
      -> MySQL / Redis / FastAPI / Third-party API
```

约束：

- Controller 只处理 HTTP 入参和出参。
- Service 承载权限、校验、事务和业务流程。
- Mapper 只访问数据库。
- 跨模块调用优先调用对方 Service。
- DO 不直接作为前端响应；前端使用 DTO/VO。

## 部署建议

单机开发：

```text
Vue Dev Server -> Spring Boot -> MySQL
                            -> FastAPI
```

部署/多实例：

```text
Nginx
  -> Spring Boot instance 1
  -> Spring Boot instance 2
       -> Redis
       -> MySQL
       -> FastAPI
```

多实例必须启用 Redis，否则验证码、登录锁定和 OpenAPI 限流会退回单实例内存，无法共享状态。

## 当前限制

- 预测任务当前仍是同步创建和执行，后续可升级为后台队列。
- 光伏数据导入当前同步处理，超大文件建议后续改为异步任务。
- 真实模型效果取决于 `model-service` 的实际模型实现。
- 生产环境需要补充 Dockerfile、CI、健康检查、日志采集和监控。
