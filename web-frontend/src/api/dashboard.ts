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
