import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') ?? '')
  const role = ref('USER')
  function setToken(value: string) {
    token.value = value
    localStorage.setItem('token', value)
  }
  return { token, role, setToken }
})
