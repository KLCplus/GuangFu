# GitHub OAuth 与人脸登录联调状态

更新时间：2026-07-07

## 当前结论

后端已经提供 OAuth 和人脸登录相关接口，前端联调时按 [back_front_api.md](back_front_api.md) 调用即可。真实第三方凭证统一放在根目录 `.env`，不要写入源码目录或提交到 Git。

## GitHub OAuth

### 已实现

- OAuth provider 配置读取。
- OAuth state 签名和校验。
- 授权 URL 生成。
- GitHub code 换 token。
- GitHub 用户信息读取。
- 首次登录自动创建本地用户并绑定。
- 已绑定用户再次登录直接签发 JWT。
- 当前用户 OAuth 账号列表、绑定、解绑。
- `mock` provider 可用于无第三方凭证调试。

### 接口

```http
GET  /api/auth/oauth/{provider}/authorize?redirectUri=http://localhost:5173
GET  /api/auth/oauth/{provider}/callback
POST /api/auth/oauth/{provider}/callback
GET  /api/user/oauth-accounts
POST /api/user/oauth-accounts/{provider}/bind
DELETE /api/user/oauth-accounts/{oauthId}
```

`provider` 当前使用：

- `mock`
- `github`

### 配置

在根目录 `.env` 中配置：

```env
OAUTH_CALLBACK_BASE_URL=http://localhost:5173
OAUTH_GITHUB_ENABLED=true
OAUTH_GITHUB_CLIENT_ID=your-client-id
OAUTH_GITHUB_CLIENT_SECRET=your-client-secret
```

GitHub OAuth App 的 callback URL 需要和前端流程保持一致。当前后端同时支持 GET callback 和 POST callback；前端可按 `authorize` 返回的 `authorizeUrl` 跳转。

## 人脸登录

### 已实现

- `FaceRecognitionProvider` 抽象。
- `local` provider：基于图片 SHA-256 的流程演示。
- `aliyun` provider：预留阿里云人脸服务和 OSS 接入。
- 人脸录入。
- 人脸登录。
- 人脸状态查询。
- 人脸撤销。

### 接口

```http
POST   /api/auth/face-login        multipart field: file
POST   /api/user/face/enroll       multipart field: file
GET    /api/user/face
DELETE /api/user/face
```

### 本地模式

```env
FACE_PROVIDER=local
FACE_MATCH_THRESHOLD=75
FACE_STORAGE_DIR=./data/faces
```

`local` 模式仅用于演示流程：同一张图片录入后再用同一张图片登录可以匹配，不是真实人脸识别。

### 阿里云模式

```env
FACE_PROVIDER=aliyun
ALIYUN_ACCESS_KEY_ID=your-access-key-id
ALIYUN_ACCESS_KEY_SECRET=your-access-key-secret
ALIYUN_FACE_REGION=cn-shanghai
ALIYUN_FACE_ENDPOINT=facebody.cn-shanghai.aliyuncs.com
ALIYUN_FACE_DB_NAME=pv_platform
ALIYUN_OSS_REGION=cn-shanghai
ALIYUN_OSS_ENDPOINT=oss-cn-shanghai.aliyuncs.com
ALIYUN_OSS_BUCKET=your-bucket
```

阿里云模式需要确认 FaceBody 服务和 OSS Bucket 已开通，后端进程可以访问外网。

## 联调注意

1. 所有真实凭证只放根目录 `.env`。
2. 推荐从根目录启动：`powershell -ExecutionPolicy Bypass -File .\start-local.ps1`。
3. 前端不要保存 GitHub Secret、阿里云 Secret 或人脸服务凭证。
4. `mock` OAuth 和 `local` face 可用于无外部服务时验证流程。
5. 生产环境上线前需要重新验证 GitHub callback、OSS 权限和阿里云人脸服务结果。
