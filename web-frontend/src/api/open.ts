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

export interface ApiCallLogQuery extends PageQuery {
  apiKeyId?: number
  status?: string
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
  createdAt?: DateTimeString
}

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
  type: string
  amount: number
  title: string
  createdAt: DateTimeString
}

export interface Wallet {
  balance: number
  frozenBalance: number
  monthlyCost: number
  currency: string
  records?: WalletRecord[]
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

export const applyApiKey = (data: ApiKeyApplyPayload) => request.post<ApiKey>("/open/apply-key", data)
export const getApiKeys = () => request.get<ApiKey[]>("/open/keys")
export const updateApiKeyStatus = (apiKeyId: number, data: UpdateApiKeyStatusPayload) =>
  request.put<void>(`/open/keys/${apiKeyId}/status`, data)
export const deleteApiKey = (apiKeyId: number) => request.delete<void>(`/open/keys/${apiKeyId}`)
export const resetOpenApiKey = (apiKeyId: number) => request.post<ApiKey>(`/open/keys/${apiKeyId}/reset`)
export const getCallLogs = (params?: ApiCallLogQuery) => request.get<PageResult<ApiCallLog>>("/open/call-logs", { params })
export const requestMarketplaceTrialApi = (data: MarketplaceTrialPayload) =>
  request.post<MarketplaceTrialResult>("/open/trials", data)
export const getOpenEntitlements = () => request.get<ApiEntitlement[]>("/open/entitlements")
export const getOpenWallet = () => request.get<Wallet>("/open/wallet")
export const getOpenPlans = () => request.get<OpenPlan[]>("/open/plans")
export const getOpenOverview = () => request.get<OpenAccountOverview>("/open/overview")
export const openPredict = (apiKey: string, data: OpenPredictPayload) =>
  request.post<OpenPredictResult>('/openapi/v1/predict', data, {
    baseURL: '',
    headers: {
      'X-API-KEY': apiKey
    }
  })
export const getAdminApiKeys = () => request.get<ApiKey[]>('/admin/api-keys')
export const updateAdminApiKeyStatus = (apiKeyId: number, data: UpdateApiKeyStatusPayload) =>
  request.put<void>(`/admin/api-keys/${apiKeyId}/status`, data)
export const getAdminApiCallLogs = (params?: ApiCallLogQuery) =>
  request.get<PageResult<ApiCallLog>>('/admin/api-call-logs', { params })
