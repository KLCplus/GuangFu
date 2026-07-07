import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'

export interface UserInfo {
  userId: number
  username: string
  nickname?: string
  email?: string
  roles: string[]
  role?: 'USER' | 'ADMIN' | 'API_USER'
  status?: 'ENABLE' | 'DISABLE' | number
}

const defaultUser: UserInfo = {
  userId: 0,
  username: '',
  nickname: '',
  email: '',
  roles: [],
  status: 'ENABLE'
}

function normalizeUserInfo(value: Partial<UserInfo> | null): UserInfo {
  const roles = Array.isArray(value?.roles)
    ? value.roles
    : value?.role
      ? [value.role]
      : []

  return {
    ...defaultUser,
    ...value,
    roles
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') ?? '')
  const storedUser = localStorage.getItem('userInfo')
  const userInfo = reactive<UserInfo>(normalizeUserInfo(storedUser ? JSON.parse(storedUser) : null))

  function setToken(value: string) {
    token.value = value
    localStorage.setItem('token', value)
  }

  function setUserInfo(value: Partial<UserInfo>) {
    Object.assign(userInfo, normalizeUserInfo({ ...userInfo, ...value }))
    localStorage.setItem('userInfo', JSON.stringify(userInfo))
  }

  function logout() {
    token.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    Object.assign(userInfo, defaultUser)
  }

  const hasRole = (role: string) => {
    const roles = Array.isArray(userInfo.roles) ? userInfo.roles : []
    return roles.includes(role) || userInfo.role === role
  }

  return { token, userInfo, setToken, setUserInfo, hasRole, logout }
})
