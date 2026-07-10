import request from './request'
import type { DateTimeString, PageQuery, PageResult } from './types'

export interface CreateAnalysisReportRequest {
  stationId: number
  taskId?: number
  title: string
  userInstruction?: string
  includeWeather: boolean
  includePrediction: boolean
}

export interface AnalysisSection {
  title?: string
  content?: string
  [key: string]: unknown
}

export interface AnalysisReportListItem {
  id?: number
  reportId?: number
  title: string
  summary?: string
  stationId?: number
  taskId?: number
  createdAt?: DateTimeString
  updatedAt?: DateTimeString
  status?: string
  [key: string]: unknown
}

export interface AnalysisReportDetail {
  id?: number
  reportId?: number
  userId?: number
  stationId?: number
  taskId?: number
  title?: string
  reportTitle?: string
  name?: string
  summary?: string
  abstract?: string
  overview?: string
  riskLevel?: string
  sections?: AnalysisSection[]
  suggestions?: string[]
  content?: string
  markdown?: string
  body?: string
  weatherAnalysis?: string
  predictionAnalysis?: string
  anomalyAnalysis?: string
  abnormalAnalysis?: string
  suggestion?: string
  recommendations?: string
  modelName?: string
  status?: string
  errorMessage?: string
  rawResponse?: string
  promptSnapshot?: string
  contextSnapshot?: string
  llmEnabled?: boolean
  llmProvider?: string
  reportContent?: string
  reportJson?: string
  createdAt?: DateTimeString
  createTime?: DateTimeString
  generatedAt?: DateTimeString
  updatedAt?: DateTimeString
  [key: string]: unknown
}

export type AnalysisReportPayload = CreateAnalysisReportRequest
export type AnalysisReport = AnalysisReportDetail

export interface AnalysisReportQuery extends PageQuery {
  stationId?: number
}

type ResponseEnvelope<T = unknown> = {
  code?: number
  message?: string
  data?: T
}

type PageLike<T> = {
  records?: T[]
  list?: T[]
  total?: number
  pageNum?: number
  pageSize?: number
}

export function normalizeReportId(report: Pick<AnalysisReportListItem, 'id' | 'reportId'>): number | null {
  const value = report.reportId ?? report.id
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function isObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

export function unwrapResponse<T = unknown>(res: unknown): T {
  if (isObject(res) && ('code' in res || 'data' in res)) {
    const envelope = res as ResponseEnvelope<T>
    if (typeof envelope.code === 'number' && envelope.code !== 200) {
      throw new Error(envelope.message || '业务请求失败')
    }
    if ('data' in envelope) return envelope.data as T
  }
  return res as T
}

function pageRecords<T>(value: unknown): T[] {
  if (Array.isArray(value)) return value as T[]
  if (!isObject(value)) return []
  const page = value as PageLike<T>
  if (Array.isArray(page.records)) return page.records
  if (Array.isArray(page.list)) return page.list
  return []
}

export function normalizeReportList(res: unknown): AnalysisReportListItem[] {
  const body = unwrapResponse(res)
  const data = isObject(body) && 'data' in body ? unwrapResponse(body) : body
  return pageRecords<AnalysisReportListItem>(data)
}

export function normalizeReportDetail(res: unknown): AnalysisReportDetail | null {
  const body = unwrapResponse(res)
  return isObject(body) ? body as AnalysisReportDetail : null
}

export const createReport = (data: CreateAnalysisReportRequest) =>
  request.post<unknown>('/analysis/report', data)
export const getReports = (params?: AnalysisReportQuery) =>
  request.get<PageResult<AnalysisReportListItem> | AnalysisReportListItem[] | unknown>('/analysis/reports', { params })
export const getReport = (reportId: number) => request.get<unknown>(`/analysis/reports/${reportId}`)
