import request from './request'

export const getModels = () => request.get('/models')
export const getModel = (modelId: number) => request.get(`/models/${modelId}`)
