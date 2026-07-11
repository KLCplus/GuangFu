import request from './request'
import type { DateTimeString } from './types'

export interface CurrentWeather {
  stationId: number
  weather: string
  temperature: number
  humidity: number
  windDirection: string
  windPower: string
  windSpeed: number
  reportTime: DateTimeString
  source: string
  cached: boolean
}


export interface WeatherLocationQuery {
  location?: string
  longitude?: number
  latitude?: number
}

export interface WeatherForecastItem {
  date: string
  dayWeather: string
  nightWeather: string
  dayTemp: number
  nightTemp: number
  humidity: number
  windDirection: string
  windPower: string
  source: string
  cached: boolean
}

export const getCurrentWeather = (stationId: number) =>
  request.get<CurrentWeather>(`/stations/${stationId}/weather/current`)
export const getForecast = (stationId: number) =>
  request.get<WeatherForecastItem[]>(`/stations/${stationId}/weather/forecast`)

export const getLocationCurrentWeather = (params: WeatherLocationQuery) =>
  request.get<CurrentWeather>('/weather/current', { params })
export const getLocationForecast = (params: WeatherLocationQuery) =>
  request.get<WeatherForecastItem[]>('/weather/forecast', { params })
