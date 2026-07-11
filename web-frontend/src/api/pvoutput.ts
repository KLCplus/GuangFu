import request from './request'
import type { CurrentWeather, WeatherForecastItem } from './weather'

export interface PvOutputStation {
  id?: number
  source?: string
  externalSystemId: number
  systemName?: string
  systemSizeW?: number
  postcode?: string
  orientation?: string
  outputs?: number
  lastOutputText?: string
  panel?: string
  inverter?: string
  distanceKm?: number
  latitude?: number
  longitude?: number
  enabled?: boolean
  lastSyncTime?: string
  lastSyncStatus?: string
  lastSyncError?: string
}

export interface PvOutputStatus {
  id?: number
  externalSystemId: number
  sampleTime: string
  energyGenerationWh?: number
  powerGenerationW?: number
  energyConsumptionWh?: number
  powerConsumptionW?: number
  normalisedOutput?: number
  temperatureC?: number
  voltageV?: number
  fetchedAt?: string
}

export interface PvOutputSyncResult {
  stationId: number
  externalSystemId: number
  systemName?: string
  status: string
  message: string
  latestStatus?: PvOutputStatus
}

export function searchPvOutputStations(params: { keyword?: string; countryCode?: string; seenDays?: number }) {
  return request.get<PvOutputStation[]>('/pvoutput/stations/search', { params })
}

export function savePvOutputStation(payload: PvOutputStation) {
  return request.post<PvOutputStation>('/pvoutput/stations', payload)
}

export function loadPvOutputStations(params?: { enabled?: boolean; keyword?: string }) {
  return request.get<PvOutputStation[]>('/pvoutput/stations', { params })
}

export function setPvOutputStationEnabled(id: number, enabled: boolean) {
  return request.patch<PvOutputStation>(`/pvoutput/stations/${id}/enabled`, undefined, { params: { enabled } })
}

export function syncPvOutputStation(id: number) {
  return request.post<PvOutputSyncResult>(`/pvoutput/stations/${id}/sync`)
}

export function syncAllPvOutputStations() {
  return request.post<PvOutputSyncResult[]>('/pvoutput/stations/sync-all')
}

export function syncPvOutputLiveStations() {
  return request.post<PvOutputSyncResult[]>('/pvoutput/stations/sync-live')
}

export function loadPvOutputLatestStatus(id: number) {
  return request.get<PvOutputStatus | null>(`/pvoutput/stations/${id}/latest-status`)
}

export function loadPvOutputHistory(id: number, params?: { startTime?: string; endTime?: string }) {
  return request.get<PvOutputStatus[]>(`/pvoutput/stations/${id}/status`, { params })
}

export function getPvOutputCurrentWeather(id: number) {
  return request.get<CurrentWeather>(`/pvoutput/stations/${id}/weather/current`)
}

export function getPvOutputForecast(id: number) {
  return request.get<WeatherForecastItem[]>(`/pvoutput/stations/${id}/weather/forecast`)
}
