# GitHub 登录 & 人脸登录 现状与清单

> 2026-07-06 | 后端已实现，前端 login.html 已就绪，联调中

## 一、GitHub OAuth 登录

### 已完成

| 组件 | 文件 | 状态 |
|---|---|---|
| Provider 配置读取 | `config/OAuthProperties.java` | ✅ |
| OAuth State 签名/校验（防 CSRF） | `module/auth/oauth/OAuthStateSigner.java` | ✅ HMAC-SHA256，10min 过期 |
| Provider 配置 Bean | `module/auth/oauth/OAuthProvidersConfig.java` | ✅ 读取 yml → provider list |
| 授权 URL 生成 | `OAuthService.authorize()` | ✅ client_id + redirect_uri + state |
| GET 回调（GitHub→后端） | `AuthController.oauthGetCallback()` | ✅ 302 跳回前端带 token |
| POST 回调（Mock 用） | `AuthController.oauthCallback()` | ✅ |
| 用户信息提取（GitHub API） | `OAuthService.exchangeAndFetch()` | ✅ code→token→userinfo |
| 自动创建/绑定用户 | `OAuthService.callback()` | ✅ 首次登录建用户+绑 openId |
| 绑定/解绑/列表 | `OAuthService.bind/unbind/listMyOAuthAccounts()` | ✅ |
| 前端 GitHub 登录按钮 | `login.html` | ✅ 跳转授权→回调登录 |
| Mock Provider（无凭据调试） | `OAuthProviderConfig.mock()` | ✅ 始终可用 |

### 待完成——需要你手动操作

**1. GitHub OAuth App 回调 URL 必须完全一致：**

去 https://github.com/settings/developers → 你的 App → **Authorization callback URL**，设置为：

```
http://localhost:8080/api/auth/oauth/github/callback
```

⚠ 不能有 `https`、不能多 `/`、不能少字母。GitHub 严格校验，不匹配就 404。

**2. .env 凭证确认：**

```
OAUTH_GITHUB_ENABLED=true
OAUTH_GITHUB_CLIENT_ID=Ov23liODztHS01YKW4kt
OAUTH_GITHUB_CLIENT_SECRET=<仅在本地环境变量中配置，不要提交>
OAUTH_CALLBACK_BASE_URL=http://localhost:8080
```

改完 callback URL 后，打开 `http://localhost:8080/login.html`，点击黑色 **GitHub 登录**。

### 流程图

```
1. 用户点 "GitHub 登录"
         ↓
2. GET /api/auth/oauth/github/authorize?redirectUri=http://localhost:8080/login.html
         ↓
3. 后端生成 GitHub 授权 URL（redirect_uri=后端回调地址，state=签名后的前端地址）
         ↓
4. 浏览器跳转 GitHub 授权页
         ↓
5. 用户授权 → GitHub 302 到 http://localhost:8080/api/auth/oauth/github/callback?code=xxx&state=xxx
         ↓
6. 后端校验 state → POST Github token 接口换 access_token → GET userinfo
         ↓
7. 查 sys_oauth_account (provider=github, open_id=用户github id)
   ├─ 已有 → 登录已有用户 → 签 JWT
   └─ 没有 → 创建新用户 + 绑定 → 签 JWT
         ↓
8. 302 跳回 http://localhost:8080/login.html?token=JWT&refreshToken=REFRESH
         ↓
9. login.html 读取 token → 显示 "欢迎回来"
```

### 调试方法

如果还有问题，查看浏览器地址栏：
- 如果在 **github.com** 域名上看到 404 → callback URL 没配对
- 如果跳回 **localhost:8080** 看到错误 → 看页面上的红色提示或 `?error=xxx`
- 如果一直在转圈 → 检查后端是否启动

---

## 二、人脸识别登录

### 已完成

| 组件 | 文件 | 状态 |
|---|---|---|
| Provider 抽象接口 | `FaceRecognitionProvider.java` | ✅ extract / match / enrollFace / searchFace |
| LocalMock Provider | `LocalMockFaceProvider.java` | ✅ SHA-256 同图匹配（默认） |
| 阿里云人脸 Provider | `AliyunFaceRecognitionProvider.java` | ✅ 需 FACE_PROVIDER=aliyun |
| 阿里云 OSS 上传 | `AliyunOssService.java` | ✅ 私有 bucket + 临时签名 URL |
| 人脸录入 | `FaceAuthService.enroll()` | ✅ 上传OSS→调AddFace→写DB |
| 人脸登录 | `FaceAuthService.login()` | ✅ 上传OSS→调SearchFace→查user |
| 人脸撤销 | `FaceAuthService.revoke()` | ✅ |
| 前端摄像头拍照 | `login.html` (startCamera/captureFace) | ✅ |
| 前端文件上传 | `login.html` (faceFileLogin) | ✅ |
| Aliyun 懒加载 | 启动时不连阿里云 | ✅ 不会拖垮启动 |

### 两种模式

**Local Mock（默认 `FACE_PROVIDER=local`）：**
- 录入：图片 SHA-256 当特征存入 DB
- 登录：上传 → SHA-256 → 遍历 DB 比对 → 匹配成功签 JWT
- ⚠ 同一张图=同一个人，不同图=不同人。仅演示用。

**阿里云（`FACE_PROVIDER=aliyun`）：**
- 录入：上传 OSS → 调人脸人体 CreateFaceDb/AddFaceEntity/AddFace
- 登录：上传 OSS → 临时签名 URL → SearchFace → Confidence≥75 → 通过
- 需要 OSS Bucket + 人脸人体服务开通

### 待完成

| 项 | 说明 |
|---|---|
| 阿里云 API 实际联调 | 当前框架已写好但未真实跑过（需开通人脸人体服务） |
| 摄像头权限 | `localhost` 允许 `getUserMedia`，不需要 HTTPS |
| 活体检测 | 未实现（需真实 SDK） |

### 如何切换到阿里云

```bash
export FACE_PROVIDER=aliyun
# 以下已在 .env 中配置好
export ALIYUN_ACCESS_KEY_ID=xxx
export ALIYUN_ACCESS_KEY_SECRET=xxx
export ALIYUN_OSS_BUCKET=pv-platform-face-1
```

---

## 三、当前可用的完整登录方式

| 方式 | 状态 | 说明 |
|---|---|---|
| 用户名+密码 | ✅ 可测 | demo / Demo@1234 |
| 邮箱验证码 | ✅ 可测 | QQ SMTP 发真实邮件 |
| GitHub OAuth | ⚠ 差一步 | callback URL 配好即可 |
| 人脸 Mock | ✅ 可测 | 同图录入→同图登录 |
| 人脸阿里云 | ⚠ 待联调 | 框架已就绪 |

---

## 四、相关文件一览

```
backend/src/main/java/com/example/pvplatform/
  config/OAuthProperties.java              # GitHub OAuth 配置
  config/FaceProperties.java               # 人脸识别配置
  config/MailProperties.java               # 邮箱配置
  common/AliyunOssService.java             # OSS 上传
  module/auth/
    oauth/OAuthStateSigner.java            # State 签名
    oauth/OAuthProvidersConfig.java        # Provider 注册
    oauth/OAuthUserInfo.java               # OAuth 用户信息
    face/FaceRecognitionProvider.java      # 人脸接口
    face/LocalMockFaceProvider.java        # 本地 Mock
    face/AliyunFaceRecognitionProvider.java # 阿里云人脸
    service/OAuthService.java              # OAuth 业务
    service/FaceAuthService.java           # 人脸业务
    service/VerificationCodeService.java   # 验证码
    controller/AuthController.java         # /api/auth/* 端点
  persistence/entity/
    SysOAuthAccountDO.java                 # 第三方账号表
    SysFaceAuthDO.java                     # 人脸认证表
    SysLoginLogDO.java                     # 登录日志表

backend/src/main/resources/static/login.html   # 登录页

docs/debug-user-auth.html                      # 接口调试页（旧）
docs/login.html                                # 前端登录页（备用副本）
docs/back_front_api.md                         # 接口文档
docs/backend_completion_status.md              # 后端完成度审计
docs/github_face_status.md                     # 本文档
```
