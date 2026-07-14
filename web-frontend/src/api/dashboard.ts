import request from './request'
import type { Station } from './station'
import type { RealtimePvData } from './pvData'
import type { CurrentWeather, WeatherForecastItem } from './weather'

export interface DashboardResource {
  name: string
  value: number
  detail: string
  level: 'healthy' | 'warning' | 'danger' | string
}


export interface DashboardRealtime {
  stationId: number
  snapshotTime: string
  pvPowerKw: number | null
  loadPowerKw: number | null
  gridPowerKw: number | null
  batteryPowerKw: number | null
  batterySoc: number | null
  voltageV: number | null
  currentA: number | null
  irradianceWm2: number | null
  temperatureC: number | null
  humidityPercent: number | null
  windSpeedMs: number | null
  dataSource: string
  quality: string
}

export interface DashboardWeather {
  text: string
  temperatureC: number | null
  humidityPercent: number | null
  windDirection: string | null
  windScale: string | null
  windSpeedMs: number | null
  updateTime: string | null
  dataSource: string
  cached: boolean
  forecast: WeatherForecastItem[]
}

export interface DashboardEnergyFlow {
  pvPowerKw: number | null
  loadPowerKw: number | null
  gridPowerKw: number | null
  batteryPowerKw: number | null
  batterySoc: number | null
  systemLossKw: number | null
  gridDirection: string
  batteryDirection: string
}

export interface DashboardHistoryPoint {
  time: string
  pvPowerKw: number | null
  loadPowerKw: number | null
  gridPowerKw: number | null
  predictionPowerKw: number | null
  irradianceWm2: number | null
}

export interface DashboardInfrastructure {
  backendStatus: string
  databaseStatus: string
  modelServiceStatus: string
  queueStatus: string
  checkedAt: string
  dataSource: string
}

export interface StationDashboard {
  station: Station
  snapshotTime: string
  dataSource: string
  quality: string
  realtime: DashboardRealtime
  weather: DashboardWeather
  energyFlow: DashboardEnergyFlow
  history: DashboardHistoryPoint[]
  infrastructure: DashboardInfrastructure
  sources: Record<string, string>
}

export interface DashboardOverview {
  stations: Station[]
  selectedStationId?: number
  weather?: CurrentWeather
  forecasts: WeatherForecastItem[]
  realtime?: RealtimePvData
  resources: DashboardResource[]
  dataSource: 'REMOTE' | 'PARTIAL' | string
  lastUpdate?: string
}

export const getDashboardOverview = (params?: { stationId?: number }) =>
  request.get<DashboardOverview>('/dashboard/overview', { params })

export const getStationDashboard = (stationId: number) =>
  request.get<StationDashboard>(`/dashboard/stations/${stationId}`)
