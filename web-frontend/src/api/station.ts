import request from './request'

export const getStations = (params?: Record<string, unknown>) => request.get('/stations', { params })
export const getStation = (stationId: number) => request.get(`/stations/${stationId}`)
export const getRealtime = (stationId: number) => request.get(`/stations/${stationId}/realtime`)
export const getHistory = (stationId: number, params?: Record<string, unknown>) =>
  request.get(`/stations/${stationId}/history`, { params })
