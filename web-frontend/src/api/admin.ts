import request from './request'
import type { DateTimeString, PageQuery, PageResult } from './types'

export interface AdminUserQuery extends PageQuery {
  keyword?: string
  status?: number
  role?: string
}

export interface AdminUser {
  userId: number
  username: string
  nickname?: string
  email?: string
  phone?: string
  avatarUrl?: string
  gender?: number
  status: number
  roles: string[]
  createdAt: DateTimeString
}

export interface UpdateUserStatusPayload {
  status: number
}

export interface UpdateUserRolesPayload {
  roles: string[]
}

export interface LoginLogQuery extends PageQuery {
  username?: string
  status?: string
  loginType?: string
}

export interface LoginLog {
  logId: number
  userId?: number
  username?: string
  loginType: string
  status: string
  ipAddress?: string
  userAgent?: string
  message?: string
  createdAt: DateTimeString
}

export interface Role {
  roleId: number
  roleCode: string
  roleName: string
  description?: string
  status?: number
}

export interface RolePayload {
  roleCode: string
  roleName: string
  description?: string
}

export interface UpdateRoleStatusPayload {
  status: number
}

export const getAdminUsers = (params?: AdminUserQuery) => request.get<PageResult<AdminUser>>('/admin/users', { params })
export const updateAdminUserStatus = (userId: number, data: UpdateUserStatusPayload) =>
  request.put<void>(`/admin/users/${userId}/status`, data)
export const updateAdminUserRoles = (userId: number, data: UpdateUserRolesPayload) =>
  request.put<void>(`/admin/users/${userId}/roles`, data)
export const deleteAdminUser = (userId: number) => request.delete<void>(`/admin/users/${userId}`)
export const getAdminLoginLogs = (params?: LoginLogQuery) =>
  request.get<PageResult<LoginLog>>('/admin/login-logs', { params })
export const getRoles = () => request.get<Role[]>('/admin/roles')
export const createRole = (data: RolePayload) => request.post<Role>('/admin/roles', data)
export const updateRole = (roleId: number, data: RolePayload) => request.put<Role>(`/admin/roles/${roleId}`, data)
export const updateRoleStatus = (roleId: number, data: UpdateRoleStatusPayload) =>
  request.put<void>(`/admin/roles/${roleId}/status`, data)
export const deleteRole = (roleId: number) => request.delete<void>(`/admin/roles/${roleId}`)
