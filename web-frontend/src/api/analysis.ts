import request from './request'

export const createReport = (data: Record<string, unknown>) => request.post('/analysis/report', data)
