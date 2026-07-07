import request from './request'
import type { DateTimeString, PageQuery, PageResult } from './types'

export type PredictionInputMode = 'STATION_HISTORY' | string
export type PredictionStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | string

export interface PredictionPayload {
  stationId: number
  modelId: number
  inputMode: PredictionInputMode
  inputStartTime?: DateTimeString
  inputEndTime?: DateTimeString
}

export interface PredictionResult {
  timeOffset: number
  predictTime: DateTimeString
  predictPower: number
  actualPowerKw: number | null
  errorValue: number | null
  errorRate: number | null
}

export interface PredictionTask {
  taskId: number
  taskNo: string
  taskStatus: PredictionStatus
  modelName: string
  modelCode: string
  stationId: number
  inputMode: PredictionInputMode
  createdAt: DateTimeString
  costTime?: number
  predictions?: PredictionResult[]
}

export interface PredictionHistoryQuery extends PageQuery {
  stationId?: number
  modelId?: number
  status?: PredictionStatus
}

export const createPrediction = (data: PredictionPayload) => request.post<PredictionTask>('/predictions', data)
export const getPrediction = (taskId: number) => request.get<PredictionTask>(`/predictions/${taskId}`)
export const getPredictionResults = (taskId: number) =>
  request.get<PredictionResult[]>(`/predictions/${taskId}/results`)
export const getPredictionHistory = (params?: PredictionHistoryQuery) =>
  request.get<PageResult<PredictionTask>>('/predictions/history', { params })
