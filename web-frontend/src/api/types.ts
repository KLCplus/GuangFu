/** 无请求参数 */
export type NoParams = Record<string, never>

/** 无请求体 */
export type NoBody = undefined

/** 后端统一时间格式：yyyy-MM-dd HH:mm:ss */
export type DateTimeString = string

/** 日期格式：yyyy-MM-dd */
export type DateString = string

/** 后端统一响应结构 */
export interface ApiResponse<T = unknown> {
  /** 业务状态码，成功为 200 */
  code: number
  /** 响应消息 */
  message: string
  /** 响应数据 */
  data: T
}

/** 通用分页查询参数 */
export interface PageQuery {
  /** 当前页码，从 1 开始 */
  pageNum?: number
  /** 每页条数 */
  pageSize?: number
}

/** 通用分页响应数据 */
export interface PageResult<T> {
  /** 总记录数 */
  total: number
  /** 当前页码 */
  pageNum: number
  /** 每页条数 */
  pageSize: number
  /** 当前页数据列表 */
  records: T[]
}

/** 接口三件套定义，便于统一描述 params/body/response */
export interface ApiEndpoint<Params = NoParams, Body = NoBody, Response = void> {
  /** 请求参数，包含路径参数和查询参数 */
  params: Params
  /** 请求体，GET/DELETE 等无 body 接口使用 NoBody */
  body: Body
  /** 业务响应数据，不包含外层 ApiResponse 包装 */
  response: Response
}

/** 通用启停状态 */
export type ApiStatus = 0 | 1

/** 用户角色编码 */
export type RoleCode = 'ADMIN' | 'USER' | 'API_USER' | string

/** OAuth 服务提供方 */
export type OAuthProvider = 'mock' | 'github' | string

/** 电站运行状态 */
export type StationStatus = 'RUNNING' | 'STOPPED' | 'MAINTENANCE' | string

/** 光伏历史数据聚合间隔 */
export type HistoryInterval = '1min' | '5min' | '15min' | '1h'

/** 导入重复数据处理策略 */
export type DuplicateStrategy = 'SKIP' | 'UPDATE' | 'FAIL'

/** 模型类型 */
export type ModelType = 'NUMERIC' | 'MULTIMODAL' | 'IMAGE' | string

/** 模型状态 */
export type ModelStatus = 'ONLINE' | 'OFFLINE' | 'TESTING' | string

/** 预测输入模式 */
export type PredictionInputMode = 'STATION_HISTORY' | string

/** 预测任务状态 */
export type PredictionStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | string

/** API Key 状态 */
export type ApiKeyStatus = 'ACTIVE' | 'DISABLED' | string

/** 新闻类型 */
export type NewsType = 'NEWS' | 'NOTICE' | 'MODEL_UPDATE' | 'ALERT' | 'SYSTEM_NOTICE' | 'INDUSTRY_NEWS' | string

/** 新闻目标角色 */
export type NewsTargetRole = 'ALL' | 'USER' | 'API_USER' | 'ADMIN' | string

/** 新闻发布状态 */
export type NewsStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE' | string

/** 通知阅读状态 */
export type ReadStatus = 0 | 1

/** 登录结果中的用户信息 */
export interface LoginUserInfo {
  /** 用户 ID */
  userId: number
  /** 用户名 */
  username: string
  /** 昵称 */
  nickname: string
  /** 角色编码列表 */
  roles: RoleCode[]
}

/** 登录响应数据 */
export interface LoginResponse {
  /** JWT 访问令牌 */
  token: string
  /** 访问令牌有效期，单位秒 */
  expiresIn: number
  /** 刷新令牌 */
  refreshToken: string
  /** 刷新令牌有效期，单位秒 */
  refreshExpiresIn: number
  /** 当前登录用户信息 */
  userInfo: LoginUserInfo
}

/** 刷新 Token 响应数据 */
export interface RefreshTokenResponse {
  /** JWT 访问令牌 */
  token: string
  /** 访问令牌有效期，单位秒 */
  expiresIn: number
  /** 刷新令牌 */
  refreshToken: string
  /** 刷新令牌有效期，单位秒 */
  refreshExpiresIn: number
}

/** 注册响应数据 */
export interface RegisterResponse {
  /** 用户 ID */
  userId: number
  /** 用户名 */
  username: string
}

/** OAuth 授权地址响应 */
export interface OAuthAuthorizeResponse {
  /** 第三方授权跳转地址 */
  authorizeUrl: string
  /** OAuth 状态参数 */
  state: string
}

/** 人脸录入状态 */
export interface FaceStatusResponse {
  /** 是否已录入人脸 */
  enrolled: boolean
  /** 录入时间 */
  enrolledAt?: DateTimeString | null
}

/** 用户资料 */
export interface UserProfileResponse {
  /** 用户 ID */
  userId: number
  /** 用户名 */
  username: string
  /** 昵称 */
  nickname: string
  /** 邮箱 */
  email: string
  /** 手机号 */
  phone?: string
  /** 头像访问地址 */
  avatarUrl?: string
  /** 性别：0 未知，1 男，2 女 */
  gender?: number
  /** 账号状态 */
  status: number
  /** 角色编码列表 */
  roles: RoleCode[]
  /** 创建时间 */
  createdAt: DateTimeString
}

/** 头像上传响应 */
export interface AvatarUploadResponse {
  /** 头像访问地址 */
  avatarUrl: string
}

/** OAuth 账号绑定信息 */
export interface OAuthAccountResponse {
  /** OAuth 绑定 ID */
  oauthId: number
  /** OAuth 提供方 */
  provider: OAuthProvider
  /** 第三方平台用户 ID */
  providerUserId: string
  /** 第三方昵称 */
  nickname?: string
  /** 第三方头像地址 */
  avatarUrl?: string
  /** 绑定时间 */
  bindTime?: DateTimeString
}

/** 管理员用户列表项 */
export interface AdminUserItem {
  /** 用户 ID */
  userId: number
  /** 用户名 */
  username: string
  /** 昵称 */
  nickname?: string
  /** 邮箱 */
  email?: string
  /** 手机号 */
  phone?: string
  /** 账号状态 */
  status: number
  /** 角色编码列表 */
  roles: RoleCode[]
  /** 创建时间 */
  createdAt: DateTimeString
}

/** 登录日志 */
export interface LoginLogItem {
  /** 日志 ID */
  logId: number
  /** 用户 ID */
  userId?: number
  /** 用户名 */
  username?: string
  /** 登录类型 */
  loginType: string
  /** 登录 IP */
  loginIp?: string
  /** 兼容前端旧字段：登录 IP */
  ipAddress?: string
  /** User-Agent 信息 */
  userAgent?: string
  /** 登录状态 */
  status: string
  /** 登录消息 */
  message?: string
  /** 登录时间 */
  loginTime?: DateTimeString
  /** 兼容前端旧字段：创建时间 */
  createdAt?: DateTimeString
}

/** 角色信息 */
export interface RoleItem {
  /** 角色 ID */
  roleId: number
  /** 角色编码 */
  roleCode: RoleCode
  /** 角色名称 */
  roleName: string
  /** 角色描述 */
  description?: string
  /** 角色状态 */
  status?: number
  /** 绑定用户数量 */
  userCount?: number
  /** 创建时间 */
  createdAt?: DateTimeString
}

/** 电站列表项 */
export interface StationListItem {
  /** 电站 ID */
  stationId: number
  /** 电站名称 */
  stationName: string
  /** 所在城市 */
  city: string
  /** 装机容量，单位 kW */
  capacity: number
  /** 电站状态 */
  status: StationStatus
}

/** 电站详情 */
export interface StationDetail extends StationListItem {
  /** 所在省份 */
  province?: string
  /** 详细地址 */
  address?: string
  /** 经度 */
  longitude?: number
  /** 纬度 */
  latitude?: number
  /** 电站描述 */
  description?: string
}

/** 实时光伏数据 */
export interface RealtimePvDataResponse {
  /** 电站 ID */
  stationId: number
  /** 采集时间 */
  collectTime: DateTimeString
  /** 当前功率，单位 kW */
  power: number
  /** 电压，单位 V */
  voltage: number
  /** 电流，单位 A */
  current: number
  /** 辐照度，单位 W/m2 */
  irradiance: number
  /** 温度，单位摄氏度 */
  temperature: number
  /** 湿度，单位百分比 */
  humidity: number
  /** 风速，单位 m/s */
  windSpeed: number
}

/** 光伏历史数据点 */
export interface PvHistoryItem {
  /** 数据时间 */
  time: DateTimeString
  /** 功率，单位 kW */
  power: number
  /** 辐照度，单位 W/m2 */
  irradiance: number
  /** 温度，单位摄氏度 */
  temperature: number
}

/** 导入错误明细 */
export interface PvDataImportErrorItem {
  /** 行号 */
  row: number
  /** 错误消息 */
  message: string
}

/** 光伏数据导入结果 */
export interface PvDataImportResponse {
  /** 导入任务 ID */
  importId: number
  /** 导入状态 */
  status: string
  /** 总行数 */
  totalCount: number
  /** 成功行数 */
  successCount: number
  /** 失败行数 */
  failCount: number
  /** 错误明细列表 */
  errors: PvDataImportErrorItem[]
  /** 兼容前端旧字段：总行数 */
  totalRows?: number
  /** 兼容前端旧字段：成功行数 */
  successRows?: number
  /** 兼容前端旧字段：失败行数 */
  failedRows?: number
  /** 导入消息 */
  message?: string
}

/** 光伏数据导入任务 */
export interface PvDataImportTaskResponse extends PvDataImportResponse {
  /** 电站 ID */
  stationId: number
  /** 原始文件名 */
  fileName?: string
  /** 错误消息 */
  errorMessage?: string
  /** 创建时间 */
  createdAt?: DateTimeString
  /** 完成时间 */
  finishedAt?: DateTimeString
  /** 兼容前端旧字段：完成时间 */
  completedAt?: DateTimeString
}

/** 当前天气 */
export interface CurrentWeatherResponse {
  /** 电站 ID */
  stationId: number
  /** 天气现象 */
  weather: string
  /** 温度，单位摄氏度 */
  temperature: number
  /** 湿度，单位百分比 */
  humidity: number
  /** 风向 */
  windDirection: string
  /** 风力等级 */
  windPower: string
  /** 风速，单位 m/s */
  windSpeed: number
  /** 天气发布时间 */
  reportTime: DateTimeString
  /** 天气数据来源 */
  source: string
  /** 是否来自缓存 */
  cached: boolean
}

/** 天气预报项 */
export interface WeatherForecastItem {
  /** 预报日期 */
  date: DateString
  /** 白天天气 */
  dayWeather: string
  /** 夜间天气 */
  nightWeather: string
  /** 白天温度 */
  dayTemp: number
  /** 夜间温度 */
  nightTemp: number
  /** 湿度，单位百分比 */
  humidity: number
  /** 风向 */
  windDirection: string
  /** 风力等级 */
  windPower: string
  /** 天气数据来源 */
  source: string
  /** 是否来自缓存 */
  cached: boolean
}

/** 模型列表项 */
export interface ModelListItem {
  /** 模型 ID */
  modelId: number
  /** 模型名称 */
  modelName: string
  /** 模型编码 */
  modelCode?: string
  /** 模型类型 */
  modelType: ModelType
  /** 模型版本 */
  modelVersion?: string
  /** 模型状态 */
  status?: ModelStatus
  /** 兼容前端旧字段：模型状态 */
  modelStatus?: ModelStatus
  /** 模型描述 */
  description?: string
}

/** 模型详情 */
export interface ModelDetail extends ModelListItem {
  /** 模型编码 */
  modelCode: string
  /** 模型版本 */
  modelVersion: string
  /** 输入窗口，单位分钟 */
  inputWindowMinutes: number
  /** 输入帧间隔，单位秒 */
  inputFrameIntervalSeconds: number
  /** 输出步数 */
  outputSteps: number
  /** 输出步长，单位分钟 */
  outputStepMinutes: number
  /** 模型服务中的模型名称 */
  serviceModelName: string
  /** 模型服务接口路径 */
  apiPath: string
  /** 输入 Schema */
  inputSchema: string
  /** 输出 Schema */
  outputSchema: string
  /** 创建时间 */
  createdAt?: DateTimeString
  /** 更新时间 */
  updatedAt?: DateTimeString
}

/** 预测结果点 */
export interface PredictionResultItem {
  /** 相对最后输入时间的分钟偏移 */
  timeOffset: number
  /** 预测时间 */
  predictTime: DateTimeString
  /** 预测功率，单位 kW */
  predictPower: number
  /** 实际功率，单位 kW */
  actualPowerKw: number | null
  /** 误差值 */
  errorValue: number | null
  /** 误差率 */
  errorRate: number | null
}

/** 预测任务 */
export interface PredictionTaskResponse {
  /** 任务 ID */
  taskId: number
  /** 任务编号 */
  taskNo: string
  /** 任务状态 */
  taskStatus?: PredictionStatus
  /** 兼容详情字段：任务状态 */
  status?: PredictionStatus
  /** 电站 ID */
  stationId: number
  /** 电站名称 */
  stationName?: string
  /** 模型 ID */
  modelId?: number
  /** 模型名称 */
  modelName: string
  /** 模型编码 */
  modelCode: string
  /** 输入模式 */
  inputMode: PredictionInputMode
  /** 创建时间 */
  createdAt: DateTimeString
  /** 开始时间 */
  startedAt?: DateTimeString
  /** 完成时间 */
  finishedAt?: DateTimeString
  /** 耗时，单位 ms */
  costTime?: number
  /** 兼容详情字段：耗时，单位 ms */
  costTimeMs?: number
  /** 错误消息 */
  errorMessage?: string
  /** 预测结果列表 */
  predictions?: PredictionResultItem[]
}

/** 分析报告列表项 */
export interface AnalysisReportListItem {
  /** 报告 ID */
  reportId: number
  /** 电站 ID */
  stationId: number
  /** 预测任务 ID */
  taskId: number
  /** 报告标题 */
  title: string
  /** 报告摘要 */
  summary: string
  /** 创建时间 */
  createdAt: DateTimeString
}

/** 分析报告详情 */
export interface AnalysisReportDetail extends AnalysisReportListItem {
  /** 用户 ID */
  userId: number
  /** 天气分析 */
  weatherAnalysis?: string
  /** 预测分析 */
  predictionAnalysis?: string
  /** 异常分析 */
  abnormalAnalysis?: string
  /** 运维建议 */
  suggestion?: string
  /** 报告正文 */
  reportContent?: string
  /** 报告 JSON 内容 */
  reportJson?: string
}

/** API Key 信息 */
export interface ApiKeyItem {
  /** API Key ID */
  apiKeyId: number
  /** API Key 名称 */
  keyName: string
  /** API Key 明文，仅创建时返回 */
  apiKey?: string
  /** API Key 前缀 */
  apiKeyPrefix?: string
  /** API Key 状态 */
  status: ApiKeyStatus
  /** 每分钟限流次数 */
  rateLimitPerMinute?: number
  /** 每日调用额度 */
  dailyQuota?: number
  /** 过期时间 */
  expireTime?: DateTimeString
  /** 兼容前端旧字段：过期时间 */
  expireAt?: DateTimeString
  /** 最近使用时间 */
  lastUsedAt?: DateTimeString
  /** 创建时间 */
  createdAt?: DateTimeString
}

/** API 调用日志 */
export interface ApiCallLogItem {
  /** 日志 ID */
  logId: number
  /** API Key ID */
  apiKeyId?: number
  /** 模型名称 */
  modelName?: string
  /** 请求路径 */
  path?: string
  /** 请求方法 */
  method?: string
  /** 调用状态 */
  status: string
  /** HTTP 状态码 */
  statusCode?: number
  /** 调用耗时，单位 ms */
  costTime: number
  /** 请求时间 */
  requestTime?: DateTimeString
  /** 创建时间 */
  createdAt?: DateTimeString
}

/** 开放预测输入帧 */
export interface OpenPredictInputFrame {
  /** 输入时间 */
  time: DateTimeString
  /** 功率，单位 kW */
  power: number
  /** 温度，单位摄氏度 */
  temperature: number
  /** 辐照度，单位 W/m2 */
  irradiance: number
}

/** 开放预测结果点 */
export interface OpenPredictResultItem {
  /** 相对最后输入时间的分钟偏移 */
  timeOffset: number
  /** 预测功率，单位 kW */
  predictPower: number
  /** 预测时间 */
  predictTime?: DateTimeString
}

/** 开放预测响应 */
export interface OpenPredictResponse {
  /** 任务 ID */
  taskId: number
  /** 任务编号 */
  taskNo: string
  /** 任务状态 */
  status: PredictionStatus
  /** 模型名称 */
  modelName: string
  /** 预测结果列表 */
  predictions: OpenPredictResultItem[]
  /** 耗时，单位 ms */
  costTime: number
}

/** 新闻信息 */
export interface NewsItem {
  /** 新闻 ID */
  newsId: number
  /** 标题 */
  title: string
  /** 摘要 */
  summary: string
  /** 正文内容 */
  content: string
  /** 封面图地址 */
  coverUrl?: string
  /** 新闻类型 */
  newsType: NewsType
  /** 目标角色 */
  targetRole: NewsTargetRole
  /** 发布状态 */
  status?: NewsStatus
  /** 发布人 ID */
  publisherId?: number
  /** 发布时间 */
  publishedAt?: DateTimeString
  /** 创建时间 */
  createdAt?: DateTimeString
  /** 更新时间 */
  updatedAt?: DateTimeString
}

/** 通知信息 */
export interface NotificationItem {
  /** 通知 ID */
  notificationId: number
  /** 通知标题 */
  title: string
  /** 通知内容 */
  content: string
  /** 通知类型 */
  notificationType?: string
  /** 关联业务类型 */
  relatedType?: string
  /** 关联业务 ID */
  relatedId?: number
  /** 阅读状态：0 未读，1 已读 */
  readStatus: ReadStatus
  /** 阅读时间 */
  readTime?: DateTimeString
  /** 创建时间 */
  createdAt: DateTimeString
}

/** 未读通知数 */
export interface UnreadCountResponse {
  /** 未读数量 */
  count: number
}

/** 注册请求体 */
export interface RegisterBody {
  /** 用户名 */
  username: string
  /** 密码 */
  password: string
  /** 邮箱 */
  email: string
}

/** 登录请求体 */
export interface LoginBody {
  /** 用户名 */
  username: string
  /** 密码 */
  password: string
}

/** 刷新 Token 请求体 */
export interface RefreshTokenBody {
  /** 刷新令牌 */
  refreshToken: string
}

/** 发送邮箱验证码请求体 */
export interface SendEmailCodeBody {
  /** 邮箱 */
  email: string
}

/** 重置密码请求体 */
export interface ResetPasswordBody {
  /** 邮箱 */
  email: string
  /** 验证码 */
  code: string
  /** 新密码 */
  newPassword: string
}

/** 邮箱验证码登录请求体 */
export interface EmailCodeLoginBody {
  /** 邮箱 */
  email: string
  /** 验证码 */
  code: string
}

/** 邮箱验证码注册请求体 */
export interface EmailCodeRegisterBody {
  /** 用户名 */
  username: string
  /** 邮箱 */
  email: string
  /** 验证码 */
  code: string
}

/** OAuth 授权参数 */
export interface OAuthAuthorizeParams {
  /** OAuth 提供方 */
  provider: OAuthProvider
  /** 前端回跳地址 */
  redirectUri: string
}

/** OAuth 回调参数 */
export interface OAuthCallbackParams {
  /** OAuth 提供方 */
  provider: OAuthProvider
  /** 授权码 */
  code?: string
  /** 状态参数 */
  state?: string
}

/** OAuth 回调请求体 */
export interface OAuthCallbackBody {
  /** 授权码 */
  code: string
  /** 状态参数 */
  state: string
  /** 前端回跳地址 */
  redirectUri?: string
}

/** 文件上传请求体 */
export interface FileUploadBody {
  /** 上传文件 */
  file: File
}

/** 修改用户资料请求体 */
export interface UpdateProfileBody {
  /** 昵称 */
  nickname?: string
  /** 邮箱 */
  email?: string
  /** 手机号 */
  phone?: string
  /** 头像访问地址 */
  avatarUrl?: string
  /** 性别：0 未知，1 男，2 女 */
  gender?: number
}

/** 修改密码请求体 */
export interface ChangePasswordBody {
  /** 旧密码 */
  oldPassword: string
  /** 新密码 */
  newPassword: string
}

/** 注销账号请求体 */
export interface CancelAccountBody {
  /** 当前密码 */
  password: string
}

/** OAuth 账号路径参数 */
export interface OAuthAccountParams {
  /** OAuth 绑定 ID */
  oauthId?: number
  /** OAuth 提供方 */
  provider?: OAuthProvider
}

/** 管理员用户查询参数 */
export interface AdminUserListParams extends PageQuery {
  /** 搜索关键词 */
  keyword?: string
  /** 账号状态 */
  status?: number
  /** 角色编码 */
  role?: RoleCode
}

/** 更新用户状态请求体 */
export interface UpdateUserStatusBody {
  /** 账号状态 */
  status: number
}

/** 更新用户角色请求体 */
export interface UpdateUserRolesBody {
  /** 角色编码列表 */
  roles: RoleCode[]
}

/** 用户路径参数 */
export interface UserIdParams {
  /** 用户 ID */
  userId: number
}

/** 登录日志查询参数 */
export interface LoginLogListParams extends PageQuery {
  /** 用户名 */
  username?: string
  /** 登录状态 */
  status?: string
  /** 登录类型 */
  loginType?: string
}

/** 角色路径参数 */
export interface RoleIdParams {
  /** 角色 ID */
  roleId: number
}

/** 角色请求体 */
export interface RoleBody {
  /** 角色编码 */
  roleCode: RoleCode
  /** 角色名称 */
  roleName: string
  /** 角色描述 */
  description?: string
}

/** 更新角色状态请求体 */
export interface UpdateRoleStatusBody {
  /** 角色状态 */
  status: number
}

/** 电站查询参数 */
export interface StationListParams extends PageQuery {
  /** 搜索关键词 */
  keyword?: string
  /** 电站状态 */
  status?: StationStatus
}

/** 电站路径参数 */
export interface StationIdParams {
  /** 电站 ID */
  stationId: number
}

/** 电站请求体 */
export interface StationBody {
  /** 电站名称 */
  stationName: string
  /** 所在省份 */
  province?: string
  /** 所在城市 */
  city: string
  /** 详细地址 */
  address?: string
  /** 经度 */
  longitude?: number
  /** 纬度 */
  latitude?: number
  /** 装机容量，单位 kW */
  capacity: number
  /** 电站状态 */
  status: StationStatus
  /** 电站描述 */
  description?: string
}

/** 光伏历史数据查询参数 */
export interface PvHistoryParams extends StationIdParams {
  /** 开始时间 */
  startTime?: DateTimeString
  /** 结束时间 */
  endTime?: DateTimeString
  /** 聚合间隔 */
  interval?: HistoryInterval
}

/** 数据上传参数 */
export interface PvDataUploadParams extends StationIdParams {
  /** 重复数据处理策略 */
  duplicateStrategy?: DuplicateStrategy
}

/** 导入任务路径参数 */
export interface PvImportTaskParams extends StationIdParams {
  /** 导入任务 ID */
  importId: number
}

/** 模型查询参数 */
export interface ModelListParams {
  /** 模型类型 */
  type?: ModelType
}

/** 模型路径参数 */
export interface ModelIdParams {
  /** 模型 ID */
  modelId: number
}

/** 模型请求体 */
export interface ModelBody {
  /** 模型编码 */
  modelCode: string
  /** 模型名称 */
  modelName: string
  /** 模型类型 */
  modelType: ModelType
  /** 模型版本 */
  modelVersion?: string
  /** 输入窗口，单位分钟 */
  inputWindowMinutes?: number
  /** 输入帧间隔，单位秒 */
  inputFrameIntervalSeconds?: number
  /** 输出步数 */
  outputSteps?: number
  /** 输出步长，单位分钟 */
  outputStepMinutes?: number
  /** 模型服务中的模型名称 */
  serviceModelName: string
  /** 模型服务接口路径 */
  apiPath?: string
  /** 输入 Schema */
  inputSchema?: string
  /** 输出 Schema */
  outputSchema?: string
  /** 模型描述 */
  description?: string
}

/** 更新模型状态请求体 */
export interface UpdateModelStatusBody {
  /** 模型状态 */
  modelStatus: ModelStatus
}

/** 创建预测请求体 */
export interface PredictionCreateBody {
  /** 电站 ID */
  stationId: number
  /** 模型 ID */
  modelId: number
  /** 输入模式 */
  inputMode: PredictionInputMode
  /** 输入开始时间 */
  inputStartTime?: DateTimeString
  /** 输入结束时间 */
  inputEndTime?: DateTimeString
}

/** 预测任务路径参数 */
export interface PredictionTaskParams {
  /** 预测任务 ID */
  taskId: number
}

/** 预测历史查询参数 */
export interface PredictionHistoryParams extends PageQuery {
  /** 电站 ID */
  stationId?: number
  /** 模型 ID */
  modelId?: number
  /** 任务状态 */
  status?: PredictionStatus
}

/** 分析报告请求体 */
export interface AnalysisReportBody {
  /** 电站 ID */
  stationId: number
  /** 预测任务 ID */
  taskId: number
  /** 报告标题 */
  title?: string
  /** 是否包含天气分析 */
  includeWeather: boolean
  /** 是否包含预测分析 */
  includePrediction: boolean
}

/** 分析报告查询参数 */
export interface AnalysisReportListParams extends PageQuery {
  /** 电站 ID */
  stationId?: number
}

/** 分析报告路径参数 */
export interface AnalysisReportParams {
  /** 报告 ID */
  reportId: number
}

/** 申请 API Key 请求体 */
export interface ApiKeyApplyBody {
  /** API Key 名称 */
  keyName: string
  /** 有效天数 */
  expireDays?: number
}

/** API Key 路径参数 */
export interface ApiKeyParams {
  /** API Key ID */
  apiKeyId: number
}

/** 更新 API Key 状态请求体 */
export interface UpdateApiKeyStatusBody {
  /** API Key 状态 */
  status: ApiKeyStatus
}

/** API 调用日志查询参数 */
export interface ApiCallLogListParams extends PageQuery {
  /** API Key ID，前端兼容参数 */
  apiKeyId?: number
  /** 调用状态，前端兼容参数 */
  status?: string
}

/** 开放预测请求体 */
export interface OpenPredictBody {
  /** 电站 ID */
  stationId?: number
  /** 模型服务中的模型名称 */
  modelName: string
  /** 输入帧列表，开放接口要求正好 30 帧 */
  input: OpenPredictInputFrame[]
}

/** 新闻查询参数 */
export interface NewsListParams extends PageQuery {
  /** 新闻类型 */
  type?: NewsType
}

/** 管理员新闻查询参数 */
export interface AdminNewsListParams extends PageQuery {
  /** 新闻状态 */
  status?: NewsStatus
  /** 新闻类型 */
  type?: NewsType
}

/** 新闻路径参数 */
export interface NewsParams {
  /** 新闻 ID */
  newsId: number
}

/** 新闻请求体 */
export interface NewsBody {
  /** 标题 */
  title: string
  /** 摘要 */
  summary?: string
  /** 正文内容 */
  content: string
  /** 封面图地址 */
  coverUrl?: string
  /** 新闻类型 */
  newsType: NewsType
  /** 目标角色 */
  targetRole: NewsTargetRole
}

/** 通知查询参数 */
export interface NotificationListParams extends PageQuery {
  /** 阅读状态：0 未读，1 已读 */
  readStatus?: ReadStatus
}

/** 通知路径参数 */
export interface NotificationParams {
  /** 通知 ID */
  notificationId: number
}

/** 模型服务健康检查响应 */
export interface ModelServiceHealthResponse {
  /** 服务状态 */
  status: string
  /** 服务名称 */
  service: string
}

/** 模型服务模型项 */
export interface ModelServiceModelItem {
  /** 模型服务中的模型名称 */
  modelName: string
  /** 模型类型 */
  modelType: ModelType
  /** 模型描述 */
  description: string
}

/** 模型服务预测结果点 */
export interface ModelServicePredictionItem {
  /** 相对最后输入时间的分钟偏移 */
  timeOffset: number
  /** 预测功率，单位 kW */
  predictPower: number
}

/** 模型服务预测请求体 */
export interface ModelServicePredictBody {
  /** 模型服务中的模型名称 */
  modelName: string
  /** 输入帧列表 */
  input: OpenPredictInputFrame[]
}

/** 模型服务预测响应 */
export interface ModelServicePredictResponse {
  /** 实际执行模型 */
  modelName: string
  /** 预测结果列表 */
  predictions: ModelServicePredictionItem[]
  /** 推理耗时，单位 ms */
  costTime: number
}

export type RegisterParams = NoParams
export type RegisterApi = ApiEndpoint<RegisterParams, RegisterBody, RegisterResponse>
export type LoginParams = NoParams
export type LoginApi = ApiEndpoint<LoginParams, LoginBody, LoginResponse>
export type RefreshTokenParams = NoParams
export type RefreshTokenApi = ApiEndpoint<RefreshTokenParams, RefreshTokenBody, RefreshTokenResponse>
export type LogoutParams = NoParams
export type LogoutApi = ApiEndpoint<LogoutParams, NoBody, void>
export type ForgotPasswordParams = NoParams
export type ForgotPasswordApi = ApiEndpoint<ForgotPasswordParams, SendEmailCodeBody, void>
export type ResetPasswordParams = NoParams
export type ResetPasswordApi = ApiEndpoint<ResetPasswordParams, ResetPasswordBody, void>
export type EmailCodeSendParams = NoParams
export type EmailCodeSendApi = ApiEndpoint<EmailCodeSendParams, SendEmailCodeBody, void>
export type EmailCodeLoginParams = NoParams
export type EmailCodeLoginApi = ApiEndpoint<EmailCodeLoginParams, EmailCodeLoginBody, LoginResponse>
export type EmailCodeRegisterParams = NoParams
export type EmailCodeRegisterApi = ApiEndpoint<EmailCodeRegisterParams, EmailCodeRegisterBody, RegisterResponse>
export type OAuthAuthorizeApi = ApiEndpoint<OAuthAuthorizeParams, NoBody, OAuthAuthorizeResponse>
export type OAuthCallbackGetApi = ApiEndpoint<OAuthCallbackParams, NoBody, void>
export type OAuthCallbackPostParams = Pick<OAuthAuthorizeParams, 'provider'>
export type OAuthCallbackPostApi = ApiEndpoint<OAuthCallbackPostParams, OAuthCallbackBody, LoginResponse>
export type FaceLoginParams = NoParams
export type FaceLoginApi = ApiEndpoint<FaceLoginParams, FileUploadBody, LoginResponse>

export type UserProfileParams = NoParams
export type GetUserProfileApi = ApiEndpoint<UserProfileParams, NoBody, UserProfileResponse>
export type UpdateUserProfileApi = ApiEndpoint<UserProfileParams, UpdateProfileBody, UserProfileResponse>
export type ChangePasswordParams = NoParams
export type ChangePasswordApi = ApiEndpoint<ChangePasswordParams, ChangePasswordBody, void>
export type CancelAccountParams = NoParams
export type CancelAccountApi = ApiEndpoint<CancelAccountParams, CancelAccountBody, void>
export type UploadAvatarParams = NoParams
export type UploadAvatarApi = ApiEndpoint<UploadAvatarParams, FileUploadBody, AvatarUploadResponse>
export interface AvatarFileParams {
  /** 头像存储文件名 */
  storageName: string
}
export type GetAvatarApi = ApiEndpoint<AvatarFileParams, NoBody, Blob>
export type OAuthAccountsParams = NoParams
export type GetOAuthAccountsApi = ApiEndpoint<OAuthAccountsParams, NoBody, OAuthAccountResponse[]>
export type BindOAuthAccountApi = ApiEndpoint<Pick<OAuthAccountParams, 'provider'>, NoBody, OAuthAccountResponse>
export type UnbindOAuthAccountApi = ApiEndpoint<Pick<OAuthAccountParams, 'oauthId'>, NoBody, void>
export type EnrollFaceApi = ApiEndpoint<NoParams, FileUploadBody, FaceStatusResponse>
export type GetFaceStatusApi = ApiEndpoint<NoParams, NoBody, FaceStatusResponse>
export type DeleteFaceApi = ApiEndpoint<NoParams, NoBody, void>

export type AdminUserListApi = ApiEndpoint<AdminUserListParams, NoBody, PageResult<AdminUserItem>>
export type UpdateAdminUserStatusApi = ApiEndpoint<UserIdParams, UpdateUserStatusBody, void>
export type UpdateAdminUserRolesApi = ApiEndpoint<UserIdParams, UpdateUserRolesBody, void>
export type DeleteAdminUserApi = ApiEndpoint<UserIdParams, NoBody, void>
export type AdminLoginLogListApi = ApiEndpoint<LoginLogListParams, NoBody, PageResult<LoginLogItem>>
export type AdminRoleListApi = ApiEndpoint<NoParams, NoBody, RoleItem[]>
export type CreateRoleApi = ApiEndpoint<NoParams, RoleBody, RoleItem>
export type UpdateRoleApi = ApiEndpoint<RoleIdParams, RoleBody, RoleItem>
export type UpdateRoleStatusApi = ApiEndpoint<RoleIdParams, UpdateRoleStatusBody, void>
export type DeleteRoleApi = ApiEndpoint<RoleIdParams, NoBody, void>

export type StationListApi = ApiEndpoint<StationListParams, NoBody, PageResult<StationListItem>>
export type StationDetailApi = ApiEndpoint<StationIdParams, NoBody, StationDetail>
export type CreateStationApi = ApiEndpoint<NoParams, StationBody, StationDetail>
export type UpdateStationApi = ApiEndpoint<StationIdParams, StationBody, StationDetail>
export type DeleteStationApi = ApiEndpoint<StationIdParams, NoBody, void>

export type RealtimePvDataApi = ApiEndpoint<StationIdParams, NoBody, RealtimePvDataResponse>
export type PvHistoryApi = ApiEndpoint<PvHistoryParams, NoBody, PvHistoryItem[]>
export type PvDataUploadApi = ApiEndpoint<PvDataUploadParams, FileUploadBody, PvDataImportResponse>
export type PvImportTaskApi = ApiEndpoint<PvImportTaskParams, NoBody, PvDataImportTaskResponse>

export type CurrentWeatherApi = ApiEndpoint<StationIdParams, NoBody, CurrentWeatherResponse>
export type WeatherForecastApi = ApiEndpoint<StationIdParams, NoBody, WeatherForecastItem[]>

export type ModelListApi = ApiEndpoint<ModelListParams, NoBody, ModelListItem[]>
export type ModelDetailApi = ApiEndpoint<ModelIdParams, NoBody, ModelDetail>
export type AdminModelListApi = ApiEndpoint<NoParams, NoBody, ModelListItem[]>
export type CreateModelApi = ApiEndpoint<NoParams, ModelBody, ModelDetail>
export type UpdateModelApi = ApiEndpoint<ModelIdParams, ModelBody, ModelDetail>
export type UpdateModelStatusApi = ApiEndpoint<ModelIdParams, UpdateModelStatusBody, void>

export type CreatePredictionApi = ApiEndpoint<NoParams, PredictionCreateBody, PredictionTaskResponse>
export type PredictionDetailApi = ApiEndpoint<PredictionTaskParams, NoBody, PredictionTaskResponse>
export type PredictionResultsApi = ApiEndpoint<PredictionTaskParams, NoBody, PredictionResultItem[]>
export type PredictionHistoryApi = ApiEndpoint<PredictionHistoryParams, NoBody, PageResult<PredictionTaskResponse>>

export type CreateAnalysisReportApi = ApiEndpoint<NoParams, AnalysisReportBody, AnalysisReportDetail>
export type AnalysisReportListApi = ApiEndpoint<AnalysisReportListParams, NoBody, PageResult<AnalysisReportListItem>>
export type AnalysisReportDetailApi = ApiEndpoint<AnalysisReportParams, NoBody, AnalysisReportDetail>

export type ApplyApiKeyApi = ApiEndpoint<NoParams, ApiKeyApplyBody, ApiKeyItem>
export type ApiKeyListApi = ApiEndpoint<NoParams, NoBody, ApiKeyItem[]>
export type UpdateApiKeyStatusApi = ApiEndpoint<ApiKeyParams, UpdateApiKeyStatusBody, void>
export type DeleteApiKeyApi = ApiEndpoint<ApiKeyParams, NoBody, void>
export type ApiCallLogListApi = ApiEndpoint<ApiCallLogListParams, NoBody, PageResult<ApiCallLogItem>>
export type OpenPredictApi = ApiEndpoint<NoParams, OpenPredictBody, OpenPredictResponse>
export type AdminApiKeyListApi = ApiEndpoint<NoParams, NoBody, ApiKeyItem[]>
export type UpdateAdminApiKeyStatusApi = ApiEndpoint<ApiKeyParams, UpdateApiKeyStatusBody, void>
export type AdminApiCallLogListApi = ApiEndpoint<ApiCallLogListParams, NoBody, PageResult<ApiCallLogItem>>

export type NewsListApi = ApiEndpoint<NewsListParams, NoBody, PageResult<NewsItem>>
export type NewsDetailApi = ApiEndpoint<NewsParams, NoBody, NewsItem>
export type AdminNewsListApi = ApiEndpoint<AdminNewsListParams, NoBody, PageResult<NewsItem>>
export type CreateNewsApi = ApiEndpoint<NoParams, NewsBody, NewsItem>
export type UpdateNewsApi = ApiEndpoint<NewsParams, NewsBody, NewsItem>
export type PublishNewsApi = ApiEndpoint<NewsParams, NoBody, void>
export type OfflineNewsApi = ApiEndpoint<NewsParams, NoBody, void>
export type DeleteNewsApi = ApiEndpoint<NewsParams, NoBody, void>

export type NotificationListApi = ApiEndpoint<NotificationListParams, NoBody, PageResult<NotificationItem>>
export type UnreadCountApi = ApiEndpoint<NoParams, NoBody, UnreadCountResponse>
export type MarkNotificationReadApi = ApiEndpoint<NotificationParams, NoBody, void>
export type MarkAllNotificationsReadApi = ApiEndpoint<NoParams, NoBody, void>

export type ModelServiceHealthApi = ApiEndpoint<NoParams, NoBody, ModelServiceHealthResponse>
export type ModelServiceModelListApi = ApiEndpoint<NoParams, NoBody, ApiResponse<ModelServiceModelItem[]>>
export type ModelServicePredictApi = ApiEndpoint<NoParams, ModelServicePredictBody, ApiResponse<ModelServicePredictResponse>>
