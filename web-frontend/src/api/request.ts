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

service.interceptors.response.use(
  unwrapResponse as never,
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      window.location.href = '/login'
    }
    if (status === 403) {
      window.location.href = '/403'
    }
    return Promise.reject(error)
  }
)

const request = service as RequestInstance

export default request
