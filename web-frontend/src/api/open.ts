import request from './request'
import type { DateTimeString, PageQuery, PageResult } from './types'

export type ApiKeyStatus = 'ACTIVE' | 'DISABLED' | string

export interface ApiKeyApplyPayload {
  keyName: string
  expireDays?: number
}

export interface ApiKey {
  apiKeyId: number
  keyName: string
  apiKey?: string
  apiKeyPrefix?: string
  status: ApiKeyStatus
  rateLimitPerMinute?: number
  dailyQuota?: number
  expireTime?: DateTimeString
  expireAt?: DateTimeString
  lastUsedAt?: DateTimeString
  createdAt?: DateTimeString
}

export interface UpdateApiKeyStatusPayload {
  status: ApiKeyStatus
}

export interface UpdateApiKeyNamePayload {
  keyName: string
}

export interface ApiCallLogQuery extends PageQuery {
  apiKeyId?: number
  status?: string
  startTime?: string
  endTime?: string
  modelId?: number
}

export interface ApiCallLog {
  logId: number
  apiKeyId?: number
  modelId?: number
  modelName?: string
  path?: string
  method?: string
  status?: string
  statusCode?: number
  requestIp?: string
  requestTime?: DateTimeString
  responseTime?: DateTimeString
  costTime?: number
  requestPath?: string
  requestMethod?: string
  costTimeMs?: number
  httpStatus?: number
  bizStatus?: string
  errorMessage?: string
  requestSummary?: string
  responseSummary?: string
  inputTokens?: number
  outputTokens?: number
  totalTokens?: number
  createdAt?: DateTimeString
}

// ---- 使用统计类型 ----

export interface ApiUsageSummaryQuery {
  startTime?: string
  endTime?: string
  apiKeyId?: number
  modelId?: number
}

export interface ApiUsageSummary {
  totalCalls: number
  successCalls: number
  failedCalls: number
  successRate: number
  avgCostTimeMs: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
}

export interface ApiUsageTrendQuery {
  startTime?: string
  endTime?: string
  apiKeyId?: number
  modelId?: number
  granularity?: 'DAY' | 'HOUR'
}

export interface ApiUsageTrendItem {
  timeBucket: string
  totalCalls: number
  successCalls: number
  failedCalls: number
  avgCostTimeMs: number
  totalTokens: number
}

export interface ApiUsageByModelQuery {
  startTime?: string
  endTime?: string
  apiKeyId?: number
}

export interface ApiUsageByModelItem {
  modelId: number | null
  modelName: string
  totalCalls: number
  successCalls: number
  failedCalls: number
  avgCostTimeMs: number
  totalTokens: number
}

export interface ApiUsageByKeyQuery {
  startTime?: string
  endTime?: string
  modelId?: number
}

export interface ApiUsageByKeyItem {
  apiKeyId: number | null
  keyName: string
  apiKeyPrefix: string
  totalCalls: number
  successCalls: number
  failedCalls: number
  avgCostTimeMs: number
  totalTokens: number
}

export interface CallLogExportQuery {
  startTime?: string
  endTime?: string
  apiKeyId?: number
  modelId?: number
  status?: string
}

// ---- 其他类型 ----

export interface MarketplaceTrialPayload {
  modelId: number
}

export interface MarketplaceTrialResult {
  trialId: number
  modelId: number
  modelName: string
  expireTime: DateTimeString
  quota: number
}

export interface ApiEntitlement {
  entitlementId: number
  modelId?: number
  modelName: string
  apiKeyName: string
  quotaTotal: number
  quotaUsed: number
  expireTime?: DateTimeString
  status: string
}

export interface WalletRecord {
  recordId: number
  orderNo?: string
  type: string
  amount: number
  balanceAfter?: number
  title: string
  remark?: string
  createdAt: DateTimeString
}

export interface Wallet {
  balance: number
  frozenBalance: number
  monthlyCost: number
  currency: string
  records?: WalletRecord[]
}

export interface WalletRechargePayload {
  amount: number
  channel?: 'MOCK'
}

export interface RechargeOrder {
  orderId: number
  orderNo: string
  amount: number
  currency: string
  channel: string
  status: string
  paidAt?: DateTimeString
  createdAt?: DateTimeString
}

export interface OpenPlan {
  planCode: string
  planName: string
  quota: number
  price: number
  validDays: number
  description: string
}

export interface OpenAccountOverview {
  wallet: Wallet
  apiEntitlements: ApiEntitlement[]
  plans: OpenPlan[]
}

export interface OpenPredictInputFrame {
  time: DateTimeString
  power: number
  temperature: number
  irradiance: number
}

export interface OpenPredictPayload {
  stationId: number
  modelName: string
  input: OpenPredictInputFrame[]
}

export interface OpenPredictResult {
  predictions: Array<{
    timeOffset?: number
    predictTime?: DateTimeString
    predictPower: number
  }>
}

// ---- API Key 管理 ----

export const applyApiKey = (data: ApiKeyApplyPayload) => request.post<ApiKey>('/open/apply-key', data)
export const getApiKeys = () => request.get<ApiKey[]>('/open/keys')
export const updateApiKeyStatus = (apiKeyId: number, data: UpdateApiKeyStatusPayload) =>
  request.put<void>(`/open/keys/${apiKeyId}/status`, data)
export const updateApiKeyName = (apiKeyId: number, data: UpdateApiKeyNamePayload) =>
  request.put<ApiKey>(`/open/keys/${apiKeyId}/name`, data)
export const deleteApiKey = (apiKeyId: number) => request.delete<void>(`/open/keys/${apiKeyId}`)
export const resetOpenApiKey = (apiKeyId: number) => request.post<ApiKey>(`/open/keys/${apiKeyId}/reset`)

// ---- 调用日志 ----

export const getCallLogs = (params?: ApiCallLogQuery) =>
  request.get<PageResult<ApiCallLog>>('/open/call-logs', { params })
export const exportCallLogs = (params?: CallLogExportQuery) =>
  request.get<ApiCallLog[]>('/open/call-logs/export', { params })

// ---- 使用统计 ----

export const getUsageSummary = (params?: ApiUsageSummaryQuery) =>
  request.get<ApiUsageSummary>('/open/usage/summary', { params })
export const getUsageTrend = (params?: ApiUsageTrendQuery) =>
  request.get<ApiUsageTrendItem[]>('/open/usage/trend', { params })
export const getUsageByModel = (params?: ApiUsageByModelQuery) =>
  request.get<ApiUsageByModelItem[]>('/open/usage/by-model', { params })
export const getUsageByKey = (params?: ApiUsageByKeyQuery) =>
  request.get<ApiUsageByKeyItem[]>('/open/usage/by-key', { params })

// ---- 开放平台 ----

export const requestMarketplaceTrialApi = (data: MarketplaceTrialPayload) =>
  request.post<MarketplaceTrialResult>('/open/trials', data)
export const getOpenEntitlements = () => request.get<ApiEntitlement[]>('/open/entitlements')
export const getOpenWallet = () => request.get<Wallet>('/open/wallet')
export const rechargeWallet = (data: WalletRechargePayload) => request.post<RechargeOrder>('/open/wallet/recharge', data)
export const getOpenPlans = () => request.get<OpenPlan[]>('/open/plans')
export const getOpenOverview = () => request.get<OpenAccountOverview>('/open/overview')
export const openPredict = (apiKey: string, data: OpenPredictPayload) =>
  request.post<OpenPredictResult>('/openapi/v1/predict', data, {
    baseURL: '',
    headers: {
      'X-API-KEY': apiKey
    }
  })

// ---- 管理员 ----

export const getAdminApiKeys = () => request.get<ApiKey[]>('/admin/api-keys')
export const updateAdminApiKeyStatus = (apiKeyId: number, data: UpdateApiKeyStatusPayload) =>
  request.put<void>(`/admin/api-keys/${apiKeyId}/status`, data)
export const getAdminApiCallLogs = (params?: ApiCallLogQuery) =>
  request.get<PageResult<ApiCallLog>>('/admin/api-call-logs', { params })
