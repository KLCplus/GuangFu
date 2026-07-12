import request from './request'
import type { DateTimeString } from './types'

export type HistoryInterval = '1min' | '5min' | '15min' | '1h'
export type DuplicateStrategy = 'SKIP' | 'UPDATE' | 'FAIL'

export interface RealtimePvData {
  stationId: number
  collectTime: DateTimeString
  power: number
  voltage: number
  current: number
  irradiance: number
  temperature: number
  humidity: number
  windSpeed: number
}

export interface PvHistoryQuery {
  startTime?: DateTimeString
  endTime?: DateTimeString
  interval?: HistoryInterval
}

export interface PvHistoryItem {
  time: DateTimeString
  power: number
  voltage: number
  current: number
  irradiance: number
  temperature: number
  humidity: number
  windSpeed: number
}

export interface PvDataImportResult {
  importId: number
  totalRows: number
  successRows: number
  failedRows: number
  status: string
  message?: string
}

export interface PvDataImportTask extends PvDataImportResult {
  stationId: number
  createdAt?: DateTimeString
  completedAt?: DateTimeString
}

export const getRealtime = (stationId: number) => request.get<RealtimePvData>(`/stations/${stationId}/realtime`)
export const getHistory = (stationId: number, params?: PvHistoryQuery) =>
  request.get<PvHistoryItem[]>(`/stations/${stationId}/history`, { params })
export const uploadStationData = (stationId: number, file: File, duplicateStrategy?: DuplicateStrategy) => {
  const form = new FormData()
  form.append('file', file)
  if (duplicateStrategy) form.append('duplicateStrategy', duplicateStrategy)
  return request.post<PvDataImportResult>(`/stations/${stationId}/data/upload`, form)
}
export const getImportTask = (stationId: number, importId: number) =>
  request.get<PvDataImportTask>(`/stations/${stationId}/data/imports/${importId}`)
