import request from './request'

export interface PredictionPayload {
  stationId: number
  modelId: number
  inputMode: string
  inputStartTime?: string
  inputEndTime?: string
}

export const createPrediction = (data: PredictionPayload) => request.post('/predictions', data)
export const getPrediction = (taskId: number) => request.get(`/predictions/${taskId}`)
export const getPredictionResults = (taskId: number) => request.get(`/predictions/${taskId}/results`)
export const getPredictionHistory = () => request.get('/predictions/history')
