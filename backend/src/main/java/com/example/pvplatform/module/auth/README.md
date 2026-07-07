# auth 模块实现现状

## 模块职责

`auth` 负责用户注册、密码登录、JWT 签发与刷新、退出登录、邮箱验证码、找回密码、OAuth 登录/绑定、人脸登录和登录审计日志。认证后的用户身份通过 `SecurityContext` 暴露给其他模块。

## 已实现

- 用户名/密码注册与登录，密码使用 BCrypt 哈希。
- 注册时自动绑定启用状态的 `USER` 角色。
- 登录成功签发 access token 和 refresh token，token 中包含用户 ID、用户名、角色、tokenVersion 和类型。
- refresh token 可换发新 token；退出登录、重置密码会递增 `sys_user.token_version` 使旧 token 失效。
- `JwtAuthenticationFilter` 已接入 Spring Security，`/api/**` 默认需要登录，`/api/admin/**` 需要 `ADMIN` 角色。
- 登录失败次数使用内存计数做短期锁定。
- 登录、注册、刷新、退出、密码重置、人脸登录等写入 `sys_login_log`。
- 邮箱验证码发送、邮箱验证码登录、邮箱验证码注册、忘记密码、重置密码已实现；验证码和发送频控目前是内存态。
- OAuth 已实现 mock provider 和 GitHub 配置路径，支持授权 URL、回调登录、账号绑定、解绑和账号列表。
- 人脸认证支持 local mock provider；配置为 Aliyun 时走阿里云人脸库和 OSS 相关 provider。
- 管理端登录日志分页查询已实现。

## 未实现或限制

- 登录失败锁定和验证码状态只保存在 JVM 内存中，服务重启或多实例部署会丢失/不一致。
- refresh token 没有独立服务端会话表或黑名单，只依赖 tokenVersion 粗粒度失效。
- OAuth 真实 provider 目前主要是 GitHub；微信、QQ 仍是预留配置。
- local 人脸识别是开发/测试实现，不适合作为真实识别能力。
- 邮件发送依赖配置，`mail.enabled=false` 时通常只适合本地调试。
- `SecurityConfig` 中 `/api/auth/forgot-password` 重复配置了一次，不影响运行但可清理。

## 主要接口

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`
- `POST /api/auth/email/code/send`
- `POST /api/auth/email/code/login`
- `POST /api/auth/email/code/register`
- `GET /api/auth/oauth/{provider}/authorize`
- `GET|POST /api/auth/oauth/{provider}/callback`
- `POST /api/auth/face-login`
- `GET /api/admin/login-logs`

## 相关表

- `sys_user`
- `sys_role`
- `sys_user_role`
- `sys_oauth_account`
- `sys_face_auth`
- `sys_login_log`

## 测试情况

- 已有 `AuthServiceTest` 和 `JwtTokenServiceTest`。
- 建议补充 Security 过滤链集成测试、OAuth 真实 provider 失败路径测试、人脸 provider 配置切换测试、验证码频控和过期测试。
