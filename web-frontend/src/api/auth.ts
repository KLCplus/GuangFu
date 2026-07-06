import request from './request'

export interface LoginPayload { username: string; password: string }
export interface RegisterPayload extends LoginPayload { email: string }

export const login = (data: LoginPayload) => request.post('/auth/login', data)
export const register = (data: RegisterPayload) => request.post('/auth/register', data)
export const getProfile = () => request.get('/user/profile')
