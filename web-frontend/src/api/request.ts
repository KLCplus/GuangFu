import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from './types'

type RequestInstance = Omit<AxiosInstance, 'get' | 'post' | 'put' | 'delete'> & {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
}

const service = axios.create({
  baseURL: '/api',
  timeout: 10000
})

service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

const unwrapResponse = (response: AxiosResponse<ApiResponse>): unknown => {
  const body = response.data
  if (typeof body?.code === 'number') {
    if (body.code !== 200) {
      return Promise.reject(new Error(body.message || '业务请求失败'))
    }
    return body.data
  }
  return body
}

function parseRequestData(data: unknown) {
  if (typeof data !== 'string') return data
  try {
    return JSON.parse(data)
  } catch {
    return data
  }
}

function maskSensitiveData(data: unknown) {
  if (!data || typeof data !== 'object' || data instanceof FormData) return data
  return Object.fromEntries(
    Object.entries(data as Record<string, unknown>).map(([key, value]) => [
      key,
      /password|token|secret/i.test(key) ? '******' : value
    ])
  )
}

function getErrorMessage(error: unknown) {
  if (!axios.isAxiosError(error)) {
    return error instanceof Error ? error.message : '请求失败'
  }

  const status = error.response?.status
  const data = error.response?.data as Partial<ApiResponse> | undefined
  if (data?.message) return data.message
  if (status === 500) return '服务器内部错误（500），请查看后端日志'
  if (status === 404) return '接口不存在（404）'
  if (status === 400) return '请求参数不正确（400）'
  if (status === 401) return '登录已失效，请重新登录'
  if (status === 403) return '没有权限访问'
  return error.message || '请求失败'
}

function logApiError(error: unknown) {
  if (!import.meta.env.DEV || !axios.isAxiosError(error)) return

  const method = error.config?.method?.toUpperCase() || 'REQUEST'
  const url = `${error.config?.baseURL || ''}${error.config?.url || ''}`
  console.groupCollapsed(`[API ERROR] ${method} ${url} -> ${error.response?.status || 'NO_RESPONSE'}`)
  console.log('params:', error.config?.params)
  console.log('data:', maskSensitiveData(parseRequestData(error.config?.data)))
  console.log('response:', error.response?.data)
  console.groupEnd()
}

service.interceptors.response.use(
  unwrapResponse as never,
  (error) => {
    logApiError(error)
    const status = error?.response?.status
    const requestUrl = String(error?.config?.url ?? '')
    const isAuthRequest = requestUrl.startsWith('/auth/')
    if (status === 401 && !isAuthRequest) {
      localStorage.removeItem('token')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userInfo')
      window.location.href = '/login'
    }
    if (status === 403) {
      window.location.href = '/403'
    }
    return Promise.reject(new Error(getErrorMessage(error)))
  }
)

const request = service as RequestInstance

export default request
