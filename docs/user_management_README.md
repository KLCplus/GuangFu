# 用户与认证前端联调说明

本文档面向前端同学，说明用户、认证、管理员用户管理相关接口如何联调。完整接口字段以 [back_front_api.md](back_front_api.md) 为准。

## 地址

```text
后端 API: http://localhost:8080/api
Swagger:  http://localhost:8080/swagger-ui.html
前端:     http://localhost:5173
```

推荐 Vite 代理：

```ts
export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/openapi': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

前端请求 baseURL 使用：

```ts
const API_BASE = '/api'
```

## 统一响应

```ts
interface ApiResult<T> {
  code: number
  message: string
  data: T
}
```

登录成功：

```ts
interface LoginResult {
  token: string
  expiresIn: number
  refreshToken: string
  refreshExpiresIn: number
  userInfo: {
    userId: number
    username: string
    nickname: string
    roles: string[]
  }
}
```

请求头：

```http
Authorization: Bearer <token>
```

## 认证接口

```http
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
POST /api/auth/forgot-password
POST /api/auth/reset-password
POST /api/auth/email/code/send
POST /api/auth/email/code/login
POST /api/auth/email/code/register
```

## 用户接口

```http
GET    /api/user/profile
PUT    /api/user/profile
PUT    /api/user/password
POST   /api/user/account/cancel
POST   /api/user/avatar
GET    /api/avatars/{storageName}
GET    /api/user/oauth-accounts
POST   /api/user/oauth-accounts/{provider}/bind
DELETE /api/user/oauth-accounts/{oauthId}
POST   /api/user/face/enroll
GET    /api/user/face
DELETE /api/user/face
```

## 管理员用户接口

```http
GET    /api/admin/users?pageNum=1&pageSize=10&keyword=&status=&role=
PUT    /api/admin/users/{userId}/status
PUT    /api/admin/users/{userId}/roles
DELETE /api/admin/users/{userId}
GET    /api/admin/login-logs?pageNum=1&pageSize=10&username=&status=&loginType=
```

管理员接口必须使用 `ROLE_ADMIN` 账号。

## 角色接口

```http
GET    /api/admin/roles
POST   /api/admin/roles
PUT    /api/admin/roles/{roleId}
PUT    /api/admin/roles/{roleId}/status
DELETE /api/admin/roles/{roleId}
```

## 前端处理建议

1. 同时处理 HTTP 状态码和响应体 `code`。
2. 401 时清空 token 并跳转登录。
3. 403 时显示无权限。
4. 登录后保存 `token` 和 `refreshToken`。
5. access token 过期时调用 `/api/auth/refresh`。
6. 不在前端保存数据库密码、JWT_SECRET、OAuth Secret、阿里云 Secret 或天气凭证。
