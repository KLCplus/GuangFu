# 用户管理前端接入 README

本文面向 Web 前端同学，覆盖登录、Token、GitHub、人脸、个人资料和管理员用户管理。
可以直接运行 [login.html](./login.html) 查看原生 JavaScript 示例。

## 1. 联调地址

```text
后端 API:       http://localhost:8080/api
调试登录页:     http://localhost:8080/login.html
Swagger:        http://localhost:8080/swagger-ui.html
前端开发地址:   http://localhost:5173
```

Vite 推荐代理：

```ts
// vite.config.ts
export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

使用代理时前端配置：

```ts
const API_BASE = '/api'
```

不使用代理时：

```ts
const API_BASE = 'http://localhost:8080/api'
```

## 2. 统一响应

业务接口统一返回：

```ts
interface ApiResult<T> {
  code: number
  message: string
  data: T
}
```

成功时 `code === 200`。HTTP 状态码和业务 `code` 通常一致；前端应同时处理 Axios
异常响应和业务错误。

登录结果：

```ts
interface LoginResult {
  token: string
  expiresIn: number
  refreshToken: string
  refreshExpiresIn: number
  score?: number // 人脸登录时为阿里云 Confidence，范围 0～100
  userInfo: {
    userId: number
    username: string
    nickname: string
    roles: string[]
  }
}

interface RefreshResult {
  token: string
  expiresIn: number
  refreshToken: string
  refreshExpiresIn: number
}
```

## 3. Axios 基础封装

```ts
// src/api/request.ts
import axios from 'axios'

export const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      sessionStorage.removeItem('accessToken')
      sessionStorage.removeItem('refreshToken')
      if (location.pathname !== '/login') location.assign('/login')
    }
    return Promise.reject(error)
  }
)
```

示例使用 `sessionStorage` 便于联调。正式环境不要长期把 Refresh Token 放在
`localStorage`；推荐由后端写入 HttpOnly、Secure、SameSite Cookie。

## 4. 密码登录与刷新

```ts
export async function passwordLogin(username: string, password: string) {
  const response = await request.post<ApiResult<LoginResult>>('/auth/login', {
    username,
    password
  })
  const result = response.data.data
  sessionStorage.setItem('accessToken', result.token)
  sessionStorage.setItem('refreshToken', result.refreshToken)
  return result
}
```

刷新：

```ts
export async function refreshAccessToken() {
  const refreshToken = sessionStorage.getItem('refreshToken')
  const response = await request.post<ApiResult<RefreshResult>>('/auth/refresh', {
    refreshToken
  })
  const result = response.data.data
  sessionStorage.setItem('accessToken', result.token)
  sessionStorage.setItem('refreshToken', result.refreshToken)
  return result
}
```

不要把 Refresh Token 当 Access Token 放进 `Authorization`。

## 5. GitHub 登录

GitHub 控制台固定配置：

```text
Homepage URL: http://localhost:5173
Authorization callback URL:
http://localhost:8080/api/auth/oauth/github/callback
```

前端发起登录：

```ts
export async function loginWithGitHub() {
  // 授权完成后后端应返回这个前端页面。
  const redirectUri = `${location.origin}/login`
  const response = await request.get<
    ApiResult<{ authorizeUrl: string; state: string }>
  >('/auth/oauth/github/authorize', {
    params: { redirectUri }
  })
  location.assign(response.data.data.authorizeUrl)
}
```

在 `/login` 页面处理后端回跳：

```ts
const params = new URLSearchParams(location.search)
const token = params.get('token')
const refreshToken = params.get('refreshToken')
const error = params.get('error')

if (error) {
  history.replaceState(null, '', location.pathname)
  throw new Error(error)
}

if (token) {
  sessionStorage.setItem('accessToken', token)
  if (refreshToken) sessionStorage.setItem('refreshToken', refreshToken)
  history.replaceState(null, '', location.pathname)
  location.replace('/')
}
```

注意：

- `redirectUri` 必须是前端白名单内地址，不能接受任意外部 URL。
- `state` 由后端签名，前端不要自行生成。
- GitHub 邮箱与本地邮箱相同会自动绑定已有用户。
- 查询参数携带 Token 是当前演示方案；生产环境应改为一次性 code。

## 6. 人脸录入与登录

录入需要先登录：

```ts
export async function enrollFace(file: File) {
  const form = new FormData()
  form.append('file', file)
  return request.post<ApiResult<null>>('/user/face/enroll', form)
}
```

检查状态和撤销：

```ts
export const getFaceStatus = () =>
  request.get<ApiResult<{ enrolled: boolean; enrolledAt?: string }>>('/user/face')

export const revokeFace = () =>
  request.delete<ApiResult<null>>('/user/face')
```

人脸登录不需要 Token：

```ts
export async function faceLogin(file: Blob) {
  const form = new FormData()
  form.append('file', file, 'face.jpg')
  const response = await request.post<ApiResult<LoginResult>>('/auth/face-login', form)
  return response.data.data
}
```

摄像头拍照：

```ts
const stream = await navigator.mediaDevices.getUserMedia({
  video: { facingMode: 'user', width: 640, height: 480 },
  audio: false
})

video.srcObject = stream
canvas.width = video.videoWidth
canvas.height = video.videoHeight
canvas.getContext('2d')!.drawImage(video, 0, 0)

const blob = await new Promise<Blob>((resolve, reject) => {
  canvas.toBlob((value) => value ? resolve(value) : reject(), 'image/jpeg', 0.92)
})
```

限制：

- 只支持 JPG、JPEG、PNG，最大 5 MB。
- 图片应包含清晰、无遮挡、光线充足的正脸。
- 返回 `score` 实际为阿里云 `Confidence`。
- 当前没有活体检测，不能作为生产级高安全认证。

## 7. 当前用户资料

```ts
export const getProfile = () =>
  request.get<ApiResult<UserProfile>>('/user/profile')

export const updateProfile = (data: {
  nickname?: string
  email?: string
  phone?: string
  avatarUrl?: string
  gender?: 0 | 1 | 2
}) => request.put<ApiResult<UserProfile>>('/user/profile', data)

export function uploadAvatar(file: File) {
  const form = new FormData()
  form.append('file', file)
  return request.post<ApiResult<{ avatarUrl: string }>>('/user/avatar', form)
}
```

资料响应中的手机号已脱敏，不要尝试用脱敏值回填并提交。

## 8. 管理员用户管理

只有 `roles` 包含 `ADMIN` 的用户可以访问 `/api/admin/**`。普通用户收到 403 时，
应显示“无管理员权限”，不要跳转成登录失败。

类型：

```ts
interface UserListItem {
  userId: number
  username: string
  nickname?: string
  email?: string
  phone?: string
  status: 0 | 1
  roles: Array<'ADMIN' | 'USER' | 'API_USER'>
  createdAt: string
}

interface PageResult<T> {
  total: number
  pageNum: number
  pageSize: number
  records: T[]
}
```

API：

```ts
export const listUsers = (params: {
  pageNum?: number
  pageSize?: number
  keyword?: string
  status?: 0 | 1
  role?: 'ADMIN' | 'USER' | 'API_USER'
}) => request.get<ApiResult<PageResult<UserListItem>>>('/admin/users', { params })

export const updateUserStatus = (userId: number, status: 0 | 1) =>
  request.put<ApiResult<null>>(`/admin/users/${userId}/status`, { status })

export const updateUserRoles = (
  userId: number,
  roles: Array<'ADMIN' | 'USER' | 'API_USER'>
) => request.put<ApiResult<null>>(`/admin/users/${userId}/roles`, { roles })

export const deleteUser = (userId: number) =>
  request.delete<ApiResult<null>>(`/admin/users/${userId}`)
```

页面建议：

- 查询条件变化时回到第 1 页。
- 状态、角色和删除操作必须二次确认。
- 操作成功后重新查询当前页。
- 禁止对当前管理员显示“禁用自己”“删除自己”“移除 ADMIN”的可用按钮。
- 删除是逻辑删除，但前端仍按不可恢复操作提示。

## 9. 路由和角色

登录完成后调用 `/user/profile` 获取最新角色，不要只相信登录时缓存的角色。

```ts
router.beforeEach(async (to) => {
  const token = sessionStorage.getItem('accessToken')
  if (!token && to.meta.requiresAuth) return '/login'

  if (to.meta.requiresAdmin) {
    const profile = await getProfile()
    if (!profile.data.data.roles.includes('ADMIN')) return '/403'
  }
})
```

前端路由守卫只改善体验，真正权限仍由后端控制。

## 10. 错误处理

| HTTP/code | 前端行为 |
|---:|---|
| 400 | 显示 `message`，保留当前表单 |
| 401 | 清理登录态并回登录页 |
| 403 | 显示无权限页，不清理有效 Token |
| 404 | 提示对象不存在并刷新列表 |
| 409 | 提示并发或唯一性冲突 |
| 500/502 | 显示通用服务异常，详细信息只看后端日志 |

禁止在 UI、日志平台或错误上报中记录完整 JWT、Refresh Token、密码和 Client Secret。

## 11. `docs/login.html` 怎么使用

该文件是零依赖参考实现，覆盖：

- 密码注册和登录。
- 邮箱验证码。
- GitHub OAuth 发起与回调。
- 人脸摄像头/文件登录。
- 登录后资料读取。
- 人脸录入状态、录入和撤销。

配置位于文件头部：

```html
<script>
window.PV_AUTH_CONFIG = {
  apiBase: 'http://localhost:8080/api'
}
</script>
```

直接双击打开可能受浏览器摄像头和跨域策略影响。建议通过本地 HTTP 服务访问：

```powershell
cd docs
python -m http.server 5174
```

然后打开：

```text
http://localhost:5174/login.html
```

如果用此地址测试 GitHub，传入的前端 `redirectUri` 会自动使用
`http://localhost:5174/login.html`；GitHub 控制台的固定 callback 仍然是后端 8080 地址。

`docs/login.html` 只用于学习和联调。正式 Vue 页面应复用 API 封装、Pinia 状态和路由，
不要直接复制整页 DOM。
