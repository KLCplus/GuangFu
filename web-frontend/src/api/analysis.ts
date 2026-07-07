import request from './request'
import type { DateTimeString, PageQuery, PageResult } from './types'

export interface AnalysisReportPayload {
  stationId: number
  taskId?: number
  title: string
  includeWeather: boolean
  includePrediction: boolean
}

export interface AnalysisReport {
  reportId: number
  userId: number
  stationId: number
  taskId?: number
  title: string
  summary: string
  weatherAnalysis?: string
  predictionAnalysis?: string
  abnormalAnalysis?: string
  suggestion?: string
  reportContent?: string
  reportJson?: string
  createdAt: DateTimeString
}

export interface AnalysisReportQuery extends PageQuery {
  stationId?: number
}

export const createReport = (data: AnalysisReportPayload) => request.post<AnalysisReport>('/analysis/report', data)
export const getReports = (params?: AnalysisReportQuery) =>
  request.get<PageResult<AnalysisReport>>('/analysis/reports', { params })
export const getReport = (reportId: number) => request.get<AnalysisReport>(`/analysis/reports/${reportId}`)
