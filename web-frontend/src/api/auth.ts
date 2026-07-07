import request from './request'
import type { DateTimeString } from './types'
import type { UserProfile } from './user'

export interface LoginPayload {
  username: string
  password: string
}

export interface RegisterPayload extends LoginPayload {
  email: string
}

export interface RegisterResult {
  userId: number
  username: string
}

export interface LoginUserInfo {
  userId: number
  username: string
  nickname: string
  roles: string[]
}

export interface LoginResult {
  token: string
  expiresIn: number
  refreshToken: string
  refreshExpiresIn: number
  userInfo: LoginUserInfo
}

export interface RefreshTokenPayload {
  refreshToken: string
}

export interface SendEmailCodePayload {
  email: string
}

export interface ResetPasswordPayload {
  email: string
  code: string
  newPassword: string
}

export interface EmailCodeLoginPayload {
  email: string
  code: string
}

export interface EmailCodeRegisterPayload {
  username: string
  email: string
  code: string
}

export interface OAuthAuthorizeQuery {
  redirectUri: string
}

export interface OAuthAuthorizeResult {
  authorizeUrl: string
  state?: string
}

export interface OAuthCallbackPayload {
  code?: string
  state?: string
  redirectUri?: string
}

export interface FaceStatus {
  enrolled: boolean
  enrolledAt?: DateTimeString
}

export const login = (data: LoginPayload) => request.post<LoginResult>('/auth/login', data)
export const register = (data: RegisterPayload) => request.post<RegisterResult>('/auth/register', data)
export const getProfile = () => request.get<UserProfile>('/user/profile')
export const refreshToken = (data: RefreshTokenPayload) => request.post<LoginResult>('/auth/refresh', data)
export const logout = () => request.post<void>('/auth/logout')
export const sendForgotPasswordCode = (data: SendEmailCodePayload) =>
  request.post<void>('/auth/forgot-password', data)
export const resetPassword = (data: ResetPasswordPayload) => request.post<void>('/auth/reset-password', data)
export const sendEmailCode = (data: SendEmailCodePayload) => request.post<void>('/auth/email/code/send', data)
export const emailCodeLogin = (data: EmailCodeLoginPayload) => request.post<LoginResult>('/auth/email/code/login', data)
export const emailCodeRegister = (data: EmailCodeRegisterPayload) =>
  request.post<RegisterResult>('/auth/email/code/register', data)
export const getOAuthAuthorizeUrl = (provider: string, params: OAuthAuthorizeQuery) =>
  request.get<OAuthAuthorizeResult>(`/auth/oauth/${provider}/authorize`, { params })
export const oauthCallback = (provider: string, data: OAuthCallbackPayload) =>
  request.post<LoginResult>(`/auth/oauth/${provider}/callback`, data)
export const faceLogin = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return request.post<LoginResult>('/auth/face-login', form)
}
