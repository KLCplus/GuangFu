import request from './request'
import type { DateTimeString } from './types'

export type ModelType = 'NUMERIC' | 'IMAGE' | string
export type ModelStatus = 'ONLINE' | 'OFFLINE' | 'TESTING' | string

export interface ModelQuery {
  type?: ModelType
}

/** GET /api/models 当前真实返回字段。 */
export interface ModelListItem {
  modelId: number
  modelName: string
  modelCode: string
  modelType: ModelType
  modelVersion?: string
  status: ModelStatus
  description?: string
}

/** GET /api/models/{modelId} 当前真实返回字段。 */
export interface ModelDetail extends ModelListItem {
  inputWindowMinutes?: number
  inputFrameIntervalSeconds?: number
  outputSteps?: number
  outputStepMinutes?: number
  serviceModelName?: string
  apiPath?: string
  inputSchema?: string
  outputSchema?: string
  createdAt?: DateTimeString
  updatedAt?: DateTimeString
}

export interface Model {
  modelId: number
  modelCode?: string
  modelName: string
  modelType: ModelType
  modelVersion?: string
  inputWindowMinutes?: number
  inputFrameIntervalSeconds?: number
  outputSteps?: number
  outputStepMinutes?: number
  serviceModelName?: string
  apiPath?: string
  inputSchema?: string
  outputSchema?: string
  modelStatus?: ModelStatus
  status?: ModelStatus
  price?: number
  quota?: number
  category?: string
  tags?: string[]
  score?: number
  latency?: number
  description?: string
  createdAt?: DateTimeString
  updatedAt?: DateTimeString
}

export type ModelPayload = Omit<
  Model,
  'modelId' | 'modelStatus' | 'status' | 'price' | 'quota' | 'category' | 'tags' | 'score' | 'latency' | 'createdAt' | 'updatedAt'
>

export interface UpdateModelStatusPayload {
  modelStatus: ModelStatus
}

export const getModels = (params?: ModelQuery) => request.get<ModelListItem[]>('/models', { params })
export const getModel = (modelId: number) => request.get<ModelDetail>(`/models/${modelId}`)
export const getAdminModels = () => request.get<Model[]>('/admin/models')
export const createModel = (data: ModelPayload) => request.post<Model>('/admin/models', data)
export const updateModel = (modelId: number, data: ModelPayload) => request.put<Model>(`/admin/models/${modelId}`, data)
export const updateModelStatus = (modelId: number, data: UpdateModelStatusPayload) =>
  request.put<Model>(`/admin/models/${modelId}/status`, data)
