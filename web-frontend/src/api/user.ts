import request from './request'
import type { FaceStatus } from './auth'
import type { DateTimeString } from './types'

export interface UserProfile {
  userId: number
  username: string
  nickname: string
  email: string
  phone?: string
  avatarUrl?: string
  gender?: number
  status: number
  roles: string[]
  createdAt: DateTimeString
}

export interface UpdateProfilePayload {
  nickname?: string
  email?: string
  phone?: string
  avatarUrl?: string
  gender?: number
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}

export interface CancelAccountPayload {
  password: string
}

export interface AvatarUploadResult {
  fileId: number
  avatarUrl: string
}

export interface OAuthAccount {
  oauthId: number
  provider: string
  providerUserId: string
  nickname?: string
  avatarUrl?: string
  bindTime?: DateTimeString
}

export interface BindOAuthAccountPayload {
  code?: string
  state?: string
  redirectUri?: string
}

export const getProfile = () => request.get<UserProfile>('/user/profile')
export const updateProfile = (data: UpdateProfilePayload) => request.put<UserProfile>('/user/profile', data)
export const changePassword = (data: ChangePasswordPayload) => request.put<void>('/user/password', data)
export const cancelAccount = (data: CancelAccountPayload) => request.post<void>('/user/account/cancel', data)
export const uploadAvatar = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return request.post<AvatarUploadResult>('/users/me/avatar', form)
}
export const deleteAvatar = () => request.delete<void>('/users/me/avatar')
export const getOAuthAccounts = () => request.get<OAuthAccount[]>('/user/oauth-accounts')
export const bindOAuthAccount = (provider: string, data: BindOAuthAccountPayload) =>
  request.post<OAuthAccount>(`/user/oauth-accounts/${provider}/bind`, data)
export const unbindOAuthAccount = (oauthId: number) => request.delete<void>(`/user/oauth-accounts/${oauthId}`)
export const enrollFace = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return request.post<FaceStatus>('/user/face/enroll', form)
}
export const getFaceStatus = () => request.get<FaceStatus>('/user/face')
export const deleteFace = () => request.delete<void>('/user/face')
