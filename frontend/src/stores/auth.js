import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api'

export const useAuthStore = defineStore('auth', () => {
  // State
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))
  const accessToken = ref(localStorage.getItem('accessToken') || '')
  const isAuthenticated = ref(!!localStorage.getItem('accessToken'))

  // Getters
  const isStreamer = computed(() => user.value?.role === 'STREAMER')
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const isFan = computed(() => user.value?.role === 'FAN')
  const userNickname = computed(() => user.value?.nickname || '')
  const userRole = computed(() => user.value?.role || '')

  // Actions
  async function login(email, password) {
    const { data } = await api.post('/auth/login', { email, password })
    setAuthData(data)
    return data
  }

  async function register(payload) {
    try {
      const { data } = await api.post('/auth/register', payload)
      setAuthData(data)
      return data
    } catch (error) {
      throw error.response?.data || error
    }
  }

  async function refreshToken() {
    try {
      const refresh = localStorage.getItem('refreshToken')
      if (!refresh) throw new Error('No refresh token')

      const { data } = await api.post('/auth/refresh', { refreshToken: refresh })
      accessToken.value = data.accessToken
      localStorage.setItem('accessToken', data.accessToken)
      if (data.refreshToken) {
        localStorage.setItem('refreshToken', data.refreshToken)
      }
      return data
    } catch (error) {
      logout()
      throw error
    }
  }

  async function fetchUser() {
    try {
      const { data } = await api.get('/auth/me')
      user.value = data
      localStorage.setItem('user', JSON.stringify(data))
      return data
    } catch (error) {
      throw error.response?.data || error
    }
  }

  function logout() {
    user.value = null
    accessToken.value = ''
    isAuthenticated.value = false
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')
  }

  function setAuthData(data) {
    accessToken.value = data.accessToken
    isAuthenticated.value = true
    localStorage.setItem('accessToken', data.accessToken)
    if (data.refreshToken) {
      localStorage.setItem('refreshToken', data.refreshToken)
    }
    if (data.user) {
      user.value = data.user
      localStorage.setItem('user', JSON.stringify(data.user))
    }
  }

  return {
    user,
    accessToken,
    isAuthenticated,
    isStreamer,
    isAdmin,
    isFan,
    userNickname,
    userRole,
    login,
    register,
    refreshToken,
    fetchUser,
    logout
  }
})
