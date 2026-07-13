import request from './request'
import type { PageQuery, PageResult } from './types'
import {
  getHistory as getPvHistory,
  getImportTask,
  getRealtime,
  uploadStationData
} from './pvData'
import { getCurrentWeather, getForecast } from './weather'

export type StationStatus = 'RUNNING' | 'STOPPED' | 'MAINTENANCE' | string

export interface Station {
  stationId: number
  stationName: string
  province: string
  city: string
  address: string
  longitude?: number
  latitude?: number
  capacity: number
  status: StationStatus
  description?: string
}

export interface StationQuery extends PageQuery {
  keyword?: string
  status?: StationStatus
}

export type StationPayload = Omit<Station, 'stationId'>

export const getStations = (params?: StationQuery) => request.get<PageResult<Station>>('/stations', { params })
export const getStation = (stationId: number) => request.get<Station>(`/stations/${stationId}`)
export const createStation = (data: StationPayload) => request.post<Station>('/admin/stations', data)
export const updateStation = (stationId: number, data: StationPayload) =>
  request.put<Station>(`/admin/stations/${stationId}`, data)
export const deleteStation = (stationId: number) => request.delete<void>(`/admin/stations/${stationId}`)
export { getCurrentWeather, getForecast, getImportTask, getPvHistory, getRealtime, uploadStationData }
export const getHistory = getPvHistory
