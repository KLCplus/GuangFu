# 用户、认证与权限模块

本文描述当前已实现的后端能力。前端接入方式、TypeScript 示例和联调步骤见：

- [前端接入 README](../../../../../../../../../docs/user_management_README.md)
- [可运行的原生 HTML 示例](../../../../../../../../../docs/login.html)
- [完整接口文档](../../../../../../../../../docs/back_front_api.md)

## 1. 当前状态

以下能力已接入真实数据库和权限系统：

- 用户名密码注册、登录。
- 邮箱验证码登录和注册。
- Access Token、Refresh Token、刷新和退出。
- GitHub OAuth 登录；GitHub 邮箱与本地邮箱相同时自动绑定已有用户。
- 阿里云人脸录入、搜索登录和撤销。
- 当前用户资料、头像、密码修改和账号注销。
- OAuth 账号查询、绑定和解绑。
- 管理员用户分页、状态修改、角色修改和逻辑删除。
- Spring Security 的 401、403 和 `ADMIN` 路径控制。

不应再参考旧版本中的 mock token、全接口放行或“人脸/OAuth 仅预留”等说明。

## 2. 关键目录

```text
module/auth/
  controller/AuthController.java
  service/AuthService.java
  service/OAuthService.java
  service/FaceAuthService.java
  oauth/
  face/

module/user/
  controller/UserController.java
  controller/AdminUserController.java
  service/UserService.java
  service/AdminUserService.java

security/
  JwtAuthenticationFilter.java
  JwtTokenService.java
  SecurityUtils.java

persistence/
  entity/SysUserDO.java
  entity/SysOAuthAccountDO.java
  entity/SysFaceAuthDO.java
```

## 3. 认证模型

登录成功返回：

```json
{
  "token": "<access-token>",
  "expiresIn": 7200,
  "refreshToken": "<refresh-token>",
  "refreshExpiresIn": 604800,
  "userInfo": {
    "userId": 1,
    "username": "demo",
    "nickname": "Demo",
    "roles": ["ADMIN", "USER"]
  }
}
```

受保护请求必须携带：

```http
Authorization: Bearer <access-token>
```

JWT 过滤器每次请求都会重新检查：

- 用户是否存在且启用。
- Token 版本是否仍有效。
- 用户在数据库中的当前角色。

修改密码、退出和注销账号会增加 Token 版本，使旧 Token 失效。

## 4. 权限规则

| 路径 | 权限 |
|---|---|
| `/api/auth/**` 中的登录、注册、OAuth、人脸登录 | 匿名 |
| `/api/user/**` | 已登录 |
| `/api/admin/**` | `ADMIN` |
| `/api/avatars/**` | 匿名读取 |
| `/swagger-ui/**`、`/v3/api-docs/**` | 匿名 |

数据库角色编码使用 `ADMIN`、`USER`、`API_USER`，Spring Security 内部转换为
`ROLE_ADMIN`、`ROLE_USER`、`ROLE_API_USER`。

## 5. 接口总览

### 认证

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 密码登录 |
| POST | `/api/auth/refresh` | 刷新 Token |
| POST | `/api/auth/logout` | 退出并使旧 Token 失效 |
| POST | `/api/auth/email/code/send` | 发送邮箱验证码 |
| POST | `/api/auth/email/code/login` | 邮箱验证码登录 |
| GET | `/api/auth/oauth/github/authorize` | 获取 GitHub 授权地址 |
| GET | `/api/auth/oauth/github/callback` | GitHub 固定后端回调 |
| POST | `/api/auth/face-login` | 人脸登录 |

### 当前用户

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/PUT | `/api/user/profile` | 查询/修改资料 |
| POST | `/api/user/avatar` | 上传头像 |
| PUT | `/api/user/password` | 修改密码 |
| POST | `/api/user/account/cancel` | 注销账号 |
| GET | `/api/user/oauth-accounts` | OAuth 绑定列表 |
| DELETE | `/api/user/oauth-accounts/{oauthId}` | 解绑 |
| GET | `/api/user/face` | 人脸录入状态 |
| POST | `/api/user/face/enroll` | 录入或更新人脸 |
| DELETE | `/api/user/face` | 撤销人脸 |

### 管理员

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/users` | 分页和条件查询 |
| PUT | `/api/admin/users/{userId}/status` | 启用/禁用 |
| PUT | `/api/admin/users/{userId}/roles` | 修改角色 |
| DELETE | `/api/admin/users/{userId}` | 逻辑删除 |

管理员不能禁用、删除自己或移除自己的 `ADMIN` 角色，也不能删除最后一个管理员。

## 6. GitHub OAuth

GitHub OAuth App 必须配置：

```text
Homepage URL:              http://localhost:5173
Authorization callback:   http://localhost:8080/api/auth/oauth/github/callback
```

后端环境变量：

```text
OAUTH_GITHUB_ENABLED=true
OAUTH_GITHUB_CLIENT_ID=<从 GitHub 复制>
OAUTH_GITHUB_CLIENT_SECRET=<只放环境变量>
```

前端传给 `/authorize` 的 `redirectUri` 是授权完成后返回的前端地址，不是 GitHub
控制台中的 callback URL。后端会在固定 callback 中换取 GitHub 用户信息，再跳回该
`redirectUri`。

如果 GitHub 邮箱已存在于 `sys_user`，系统绑定该本地用户；不会创建重复邮箱用户。

## 7. 人脸识别

`FACE_PROVIDER=aliyun` 时：

```text
录入：图片 -> 私有 OSS 临时对象 -> AddFace -> sys_face_auth
登录：图片 -> 私有 OSS 临时对象 -> SearchFace -> 用户 -> JWT
```

阈值使用阿里云 `Confidence`（0～100），不是 `Score`（-1～1）。当前默认阈值为 75。
临时 OSS 对象在请求结束后删除。

当前没有活体检测，静态照片可以通过，因此仅适合课程演示或低风险环境，不能直接用于
生产身份认证。

## 8. 数据与安全约束

- 密码只保存 BCrypt 哈希。
- 邮箱、手机号、用户名由数据库唯一索引兜底。
- 删除用户使用逻辑删除。
- 用户列表不返回密码哈希。
- 手机号在资料和列表响应中脱敏。
- OAuth、OSS、邮件和 JWT Secret 禁止写入 README 或提交 Git。
- SQL、云厂商响应和堆栈不得返回给浏览器。

## 9. 本地验证

后端调试页：

```text
http://localhost:8080/login.html
```

前端参考页：

```text
docs/login.html
```

编译：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-23'
mvn -DskipTests compile
```

验证顺序：

1. 密码或 GitHub 登录。
2. 调用 `/api/user/profile` 验证 Token。
3. 以管理员 Token 查询 `/api/admin/users`。
4. 测试状态和角色修改的自我保护。
5. 录入一张正脸，再用另一张同人照片测试人脸登录。

## 10. 已知限制

- OAuth 演示回调将 JWT 放在查询参数中，正式环境应改成一次性 code 或安全 Cookie。
- 人脸登录没有活体检测。
- CORS 当前面向本地联调较宽松，上线前必须限制允许来源。
- Refresh Token 当前由前端管理，正式环境建议使用 HttpOnly、Secure Cookie。
