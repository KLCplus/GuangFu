import { getModel, getModels } from './model'
import type { Model, ModelQuery } from './model'
import {
  applyApiKey,
  deleteApiKey,
  getApiKeys,
  getCallLogs,
  updateApiKeyStatus
} from './open'
import type { ApiCallLog, ApiCallLogQuery, ApiKey, ApiKeyApplyPayload } from './open'
import { getNews, getNewsList } from './news'
import type { News, NewsQuery } from './news'
import {
  getNotifications,
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead
} from './notification'
import type { Notification, NotificationQuery } from './notification'
import {
  bindOAuthAccount,
  changePassword,
  getFaceStatus,
  getOAuthAccounts,
  getProfile,
  unbindOAuthAccount,
  updateProfile
} from './user'
import type {
  BindOAuthAccountPayload,
  ChangePasswordPayload,
  OAuthAccount,
  UpdateProfilePayload,
  UserProfile
} from './user'
import type { FaceStatus } from './auth'
import type { PageResult } from './types'
import {
  mockApiEntitlements,
  mockApiKeys,
  mockCallLogs,
  mockApiUsageSeries,
  mockApiUsageSummary,
  mockMarketplaceModels,
  mockNews,
  mockNotifications,
  mockUserProfile,
  mockWallet,
  mockWalletRecords,
  modelCategoryOptions
} from '../data/mock'
import type {
  MarketplaceModel,
  MockApiEntitlement,
  MockApiUsagePoint,
  MockApiUsageSummary,
  MockWallet,
  MockWalletRecord,
  ModelCategory,
  ModelStatus
} from '../data/mock'

export type DataSource = 'remote' | 'mock' | 'mixed'

export interface DataResult<T> {
  data: T
  source: DataSource
  error?: unknown
}

export interface MarketplaceModelQuery extends ModelQuery {
  category?: ModelCategory | 'ALL'
  keyword?: string
  status?: ModelStatus | 'ALL'
}

export interface MarketplaceTrialResult {
  trialId: number
  modelId: number
  modelName: string
  expireTime: string
  quota: number
}

export interface ApiUsageStats {
  summary: MockApiUsageSummary
  series: MockApiUsagePoint[]
}

export interface NormalizedApiCallLog {
  logId: number
  apiKeyId?: number
  modelName?: string
  path: string
  method: string
  status: string
  statusCode?: number
  costTime: number
  createdAt: string
  errorMessage?: string
}

export interface ProfileOverview {
  profile: UserProfile
  wallet: MockWallet
  walletRecords: MockWalletRecord[]
  apiEntitlements: MockApiEntitlement[]
  oauthAccounts: OAuthAccount[]
  faceStatus?: FaceStatus
  apiKeys: ApiKey[]
}

function remoteResult<T>(data: T): DataResult<T> {
  return { data, source: 'remote' }
}

function mockResult<T>(data: T, error?: unknown): DataResult<T> {
  return { data, source: 'mock', error }
}

function mixedResult<T>(data: T, error?: unknown): DataResult<T> {
  return { data, source: 'mixed', error }
}

function pageResult<T>(records: T[], pageNum = 1, pageSize = records.length || 10): PageResult<T> {
  return {
    total: records.length,
    pageNum,
    pageSize,
    records
  }
}

function inferCategory(model: Partial<Model>): ModelCategory {
  const name = `${model.modelCode ?? ''} ${model.modelName ?? ''}`.toLowerCase()
  if (['cnn', '3d', 'convlstm_lstm'].some((key) => name.includes(key)) && !name.includes('pred')) {
    return 'VISION_FUSION'
  }
  if (['simvp', 'tau', 'predrnn', 'e3d', 'swin', 'sunset', 'video'].some((key) => name.includes(key))) {
    return 'VIDEO_RECURSIVE'
  }
  return 'TIME_SERIES'
}

function categoryName(category: ModelCategory) {
  return modelCategoryOptions.find((item) => item.value === category)?.label ?? '时序基线'
}

function normalizeModelStatus(value?: string): ModelStatus {
  if (value === 'ONLINE' || value === 'OFFLINE' || value === 'TESTING') {
    return value
  }
  return 'ONLINE'
}

function enrichMarketplaceModel(model: Model): MarketplaceModel {
  const matched =
    mockMarketplaceModels.find((item) => item.modelId === model.modelId) ??
    mockMarketplaceModels.find((item) => item.modelCode === model.modelCode || item.modelName === model.modelName)
  const category = matched?.category ?? inferCategory(model)
  const base = matched ?? mockMarketplaceModels[0]
  const status = normalizeModelStatus(model.modelStatus ?? model.status ?? base.modelStatus)

  return {
    ...base,
    modelId: model.modelId,
    modelName: model.modelName,
    modelCode: model.modelCode ?? base.modelCode,
    modelType: model.modelType ?? base.modelType,
    modelVersion: model.modelVersion ?? base.modelVersion,
    modelStatus: status,
    status,
    description: model.description ?? base.description,
    category,
    categoryName: categoryName(category),
    inputWindowMinutes: model.inputWindowMinutes ?? base.inputWindowMinutes,
    inputFrameIntervalSeconds: model.inputFrameIntervalSeconds ?? base.inputFrameIntervalSeconds,
    outputSteps: model.outputSteps ?? base.outputSteps,
    outputStepMinutes: model.outputStepMinutes ?? base.outputStepMinutes,
    serviceModelName: model.serviceModelName ?? model.modelCode ?? base.serviceModelName,
    apiPath: model.apiPath ?? base.apiPath,
    inputSchema: model.inputSchema ?? base.inputSchema,
    outputSchema: model.outputSchema ?? base.outputSchema
  }
}

function filterMarketplaceModels(models: MarketplaceModel[], query?: MarketplaceModelQuery) {
  const keyword = query?.keyword?.trim().toLowerCase()
  return models.filter((item) => {
    const matchesCategory = !query?.category || query.category === 'ALL' || item.category === query.category
    const matchesStatus = !query?.status || query.status === 'ALL' || item.modelStatus === query.status
    const matchesKeyword =
      !keyword ||
      item.modelName.toLowerCase().includes(keyword) ||
      item.modelCode.toLowerCase().includes(keyword) ||
      item.description.toLowerCase().includes(keyword) ||
      item.tags.some((tag) => tag.toLowerCase().includes(keyword))
    return matchesCategory && matchesStatus && matchesKeyword
  })
}

function normalizeApiKey(key: ApiKey): ApiKey {
  return {
    ...key,
    expireAt: key.expireAt ?? key.expireTime
  }
}

function mockApiKey(data: ApiKeyApplyPayload): ApiKey {
  const id = Date.now()
  return {
    apiKeyId: id,
    keyName: data.keyName,
    apiKey: `pv_mock_${id.toString(16)}_********************************`,
    apiKeyPrefix: `pv_mock_${id.toString(16).slice(-4)}`,
    status: 'ACTIVE',
    rateLimitPerMinute: 60,
    dailyQuota: 10000,
    expireTime: '2026-12-31 23:59:59',
    expireAt: '2026-12-31 23:59:59',
    createdAt: '2026-07-09 00:00:00'
  }
}

function normalizeApiCallLog(log: ApiCallLog): NormalizedApiCallLog {
  const status = log.status ?? log.bizStatus ?? (log.httpStatus && log.httpStatus >= 400 ? 'FAILED' : 'SUCCESS')
  return {
    logId: log.logId,
    apiKeyId: log.apiKeyId,
    modelName: log.modelName,
    path: log.path ?? log.requestPath ?? '/openapi/v1/predict',
    method: log.method ?? log.requestMethod ?? 'POST',
    status,
    statusCode: log.statusCode ?? log.httpStatus,
    costTime: log.costTime ?? log.costTimeMs ?? 0,
    createdAt: log.createdAt ?? log.requestTime ?? '',
    errorMessage: log.errorMessage
  }
}

function mockLogToNormalized(log: (typeof mockCallLogs)[number]): NormalizedApiCallLog {
  return {
    logId: log.id,
    path: log.apiPath,
    method: 'POST',
    status: log.statusCode >= 400 ? 'FAILED' : 'SUCCESS',
    statusCode: log.statusCode,
    costTime: log.costTime,
    createdAt: log.requestTime,
    errorMessage: log.statusCode >= 400 ? log.message : undefined
  }
}

function buildUsageStats(logs: NormalizedApiCallLog[], totalCalls?: number): ApiUsageStats {
  const successCalls = logs.filter((item) => item.status !== 'FAILED' && (item.statusCode ?? 200) < 400).length
  const failedCalls = logs.length - successCalls
  const avgLatency = logs.length
    ? Math.round(logs.reduce((sum, item) => sum + item.costTime, 0) / logs.length)
    : 0
  const seriesMap = logs.reduce<Record<string, MockApiUsagePoint>>((acc, item) => {
    const date = item.createdAt.slice(5, 10) || '今日'
    acc[date] ??= { date, calls: 0, errors: 0, avgLatency: 0 }
    acc[date].calls += 1
    acc[date].errors += item.status === 'FAILED' || (item.statusCode ?? 200) >= 400 ? 1 : 0
    acc[date].avgLatency += item.costTime
    return acc
  }, {})
  const series = Object.values(seriesMap).map((item) => ({
    ...item,
    avgLatency: item.calls ? Math.round(item.avgLatency / item.calls) : 0
  }))

  return {
    summary: {
      todayCalls: logs.length,
      totalCalls: totalCalls ?? logs.length,
      successCalls,
      failedCalls,
      errorRate: logs.length ? Number(((failedCalls / logs.length) * 100).toFixed(2)) : 0,
      avgLatency,
      remainingQuota: Math.max(0, 10000 - logs.length)
    },
    series
  }
}

function mockNewsToRemoteShape(item: (typeof mockNews)[number]): News {
  return {
    newsId: item.newsId,
    title: item.title,
    summary: item.content,
    content: item.content,
    newsType: item.type,
    targetRole: 'ALL',
    status: 'PUBLISHED',
    publishedAt: item.publishTime,
    createdAt: item.publishTime
  }
}

export function getMarketplaceModelCategories() {
  return modelCategoryOptions
}

export async function loadMarketplaceModels(query?: MarketplaceModelQuery): Promise<DataResult<MarketplaceModel[]>> {
  try {
    const models = await getModels(query?.type ? { type: query.type } : undefined)
    return remoteResult(filterMarketplaceModels(models.map(enrichMarketplaceModel), query))
  } catch (error) {
    return mockResult(filterMarketplaceModels(mockMarketplaceModels, query), error)
  }
}

export async function loadMarketplaceModelDetail(modelId: number): Promise<DataResult<MarketplaceModel>> {
  try {
    return remoteResult(enrichMarketplaceModel(await getModel(modelId)))
  } catch (error) {
    const fallback = mockMarketplaceModels.find((item) => item.modelId === modelId) ?? mockMarketplaceModels[0]
    return mockResult(fallback, error)
  }
}

export async function applyMarketplaceApi(
  modelId: number,
  data: Partial<ApiKeyApplyPayload> = {}
): Promise<DataResult<ApiKey>> {
  const model = mockMarketplaceModels.find((item) => item.modelId === modelId)
  const payload: ApiKeyApplyPayload = {
    keyName: data.keyName ?? `${model?.modelName ?? '模型'} API Key`,
    expireDays: data.expireDays ?? 90
  }

  try {
    return remoteResult(normalizeApiKey(await applyApiKey(payload)))
  } catch (error) {
    return mockResult(mockApiKey(payload), error)
  }
}

export async function requestMarketplaceTrial(modelId: number): Promise<DataResult<MarketplaceTrialResult>> {
  const model = mockMarketplaceModels.find((item) => item.modelId === modelId) ?? mockMarketplaceModels[0]
  return mockResult({
    trialId: Date.now(),
    modelId: model.modelId,
    modelName: model.modelName,
    expireTime: '2026-07-16 23:59:59',
    quota: model.category === 'TIME_SERIES' ? 100 : 30
  })
}

export async function loadApiKeys(): Promise<DataResult<ApiKey[]>> {
  try {
    return remoteResult((await getApiKeys()).map(normalizeApiKey))
  } catch (error) {
    return mockResult(mockApiKeys.map(normalizeApiKey), error)
  }
}

export async function createApiKey(data: ApiKeyApplyPayload): Promise<DataResult<ApiKey>> {
  try {
    return remoteResult(normalizeApiKey(await applyApiKey(data)))
  } catch (error) {
    return mockResult(mockApiKey(data), error)
  }
}

export async function loadApiCallLogs(
  query?: ApiCallLogQuery
): Promise<DataResult<PageResult<NormalizedApiCallLog>>> {
  try {
    const result = await getCallLogs(query)
    return remoteResult({
      ...result,
      records: result.records.map(normalizeApiCallLog)
    })
  } catch (error) {
    const pageNum = query?.pageNum ?? 1
    const pageSize = query?.pageSize ?? 10
    return mockResult(pageResult(mockCallLogs.map(mockLogToNormalized), pageNum, pageSize), error)
  }
}

export async function loadApiUsageStats(query?: ApiCallLogQuery): Promise<DataResult<ApiUsageStats>> {
  try {
    const result = await getCallLogs({ ...query, pageNum: 1, pageSize: 100 })
    const stats = buildUsageStats(result.records.map(normalizeApiCallLog), result.total)
    return remoteResult(stats)
  } catch (error) {
    return mockResult({ summary: mockApiUsageSummary, series: mockApiUsageSeries }, error)
  }
}

export async function setApiKeyEnabled(apiKeyId: number, enabled: boolean): Promise<DataResult<void>> {
  try {
    await updateApiKeyStatus(apiKeyId, { status: enabled ? 'ACTIVE' : 'DISABLED' })
    return remoteResult(undefined)
  } catch (error) {
    return mockResult(undefined, error)
  }
}

export async function removeApiKey(apiKeyId: number): Promise<DataResult<void>> {
  try {
    await deleteApiKey(apiKeyId)
    return remoteResult(undefined)
  } catch (error) {
    return mockResult(undefined, error)
  }
}

export async function resetApiKey(apiKeyId: number): Promise<DataResult<ApiKey>> {
  const matched = mockApiKeys.find((item) => item.apiKeyId === apiKeyId)
  return mockResult({
    ...(matched ?? mockApiKeys[0]),
    apiKey: `pv_mock_reset_${Date.now().toString(16)}_********************************`,
    apiKeyPrefix: `pv_reset_${apiKeyId}`
  })
}

export async function loadNewsPage(query?: NewsQuery): Promise<DataResult<PageResult<News>>> {
  try {
    return remoteResult(await getNewsList(query))
  } catch (error) {
    return mockResult(pageResult(mockNews.map(mockNewsToRemoteShape), query?.pageNum, query?.pageSize), error)
  }
}

export async function loadNewsDetail(newsId: number): Promise<DataResult<News>> {
  try {
    return remoteResult(await getNews(newsId))
  } catch (error) {
    const fallback = mockNews.find((item) => item.newsId === newsId) ?? mockNews[0]
    return mockResult(mockNewsToRemoteShape(fallback), error)
  }
}

export async function loadNotifications(query?: NotificationQuery): Promise<DataResult<PageResult<Notification>>> {
  try {
    return remoteResult(await getNotifications(query))
  } catch (error) {
    const records = mockNotifications.filter((item) => query?.readStatus === undefined || item.readStatus === query.readStatus)
    return mockResult(pageResult(records, query?.pageNum, query?.pageSize), error)
  }
}

export async function loadUnreadNotificationCount(): Promise<DataResult<number>> {
  try {
    const result = await getUnreadCount()
    return remoteResult(result.count ?? result.unreadCount ?? 0)
  } catch (error) {
    return mockResult(mockNotifications.filter((item) => item.readStatus === 0).length, error)
  }
}

export async function markNotificationAsRead(notificationId: number): Promise<DataResult<void>> {
  try {
    await markNotificationRead(notificationId)
    return remoteResult(undefined)
  } catch (error) {
    return mockResult(undefined, error)
  }
}

export async function markEveryNotificationRead(): Promise<DataResult<void>> {
  try {
    await markAllNotificationsRead()
    return remoteResult(undefined)
  } catch (error) {
    return mockResult(undefined, error)
  }
}

export async function loadUserProfile(): Promise<DataResult<UserProfile>> {
  try {
    return remoteResult(await getProfile())
  } catch (error) {
    return mockResult(mockUserProfile, error)
  }
}

export async function saveUserProfile(data: UpdateProfilePayload): Promise<DataResult<UserProfile>> {
  try {
    return remoteResult(await updateProfile(data))
  } catch (error) {
    return mockResult({ ...mockUserProfile, ...data }, error)
  }
}

export async function updateUserPassword(data: ChangePasswordPayload): Promise<DataResult<void>> {
  try {
    await changePassword(data)
    return remoteResult(undefined)
  } catch (error) {
    return mockResult(undefined, error)
  }
}

export async function bindUserEmail(email: string): Promise<DataResult<UserProfile>> {
  return saveUserProfile({ email })
}

export async function loadProfileOverview(): Promise<DataResult<ProfileOverview>> {
  const [profileResult, oauthResult, faceResult, keyResult] = await Promise.allSettled([
    getProfile(),
    getOAuthAccounts(),
    getFaceStatus(),
    getApiKeys()
  ])

  const hasRejected = [profileResult, oauthResult, faceResult, keyResult].some((item) => item.status === 'rejected')
  const data: ProfileOverview = {
    profile: profileResult.status === 'fulfilled' ? profileResult.value : mockUserProfile,
    wallet: mockWallet,
    walletRecords: mockWalletRecords,
    apiEntitlements: mockApiEntitlements,
    oauthAccounts: oauthResult.status === 'fulfilled' ? oauthResult.value : [],
    faceStatus: faceResult.status === 'fulfilled' ? faceResult.value : undefined,
    apiKeys: keyResult.status === 'fulfilled' ? keyResult.value.map(normalizeApiKey) : mockApiKeys.map(normalizeApiKey)
  }

  return hasRejected ? mixedResult(data) : remoteResult(data)
}

export async function bindOAuthProvider(
  provider: string,
  data: BindOAuthAccountPayload
): Promise<DataResult<OAuthAccount>> {
  try {
    return remoteResult(await bindOAuthAccount(provider, data))
  } catch (error) {
    return mockResult(
      {
        oauthId: Date.now(),
        provider,
        providerUserId: `mock-${provider}-user`,
        nickname: `${provider} 用户`,
        bindTime: '2026-07-09 00:00:00'
      },
      error
    )
  }
}

export async function unbindOAuthProvider(oauthId: number): Promise<DataResult<void>> {
  try {
    await unbindOAuthAccount(oauthId)
    return remoteResult(undefined)
  } catch (error) {
    return mockResult(undefined, error)
  }
}
