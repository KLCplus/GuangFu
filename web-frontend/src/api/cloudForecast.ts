import request from './request'

export interface CloudForecastPayload {
  modelName: string
  inputImages: string[]
}

export interface CloudForecastFrame {
  frameIndex: number
  timeOffset: number
  image: string
  confidence?: number
  cloudCoverage?: number
}

export interface CloudForecastResult {
  modelName: string
  predictions: CloudForecastFrame[]
  costTime: number
}

export const predictCloudForecast = (data: CloudForecastPayload) =>
  request.post<CloudForecastResult>('/cloud-forecast/predict', data, { timeout: 120000 })
