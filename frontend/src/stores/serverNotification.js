import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api'

export const useServerNotificationStore = defineStore('serverNotification', () => {
  const notifications = ref([])
  const unreadCount = ref(0)
  let pollingInterval = null

  const hasUnread = computed(() => unreadCount.value > 0)

  async function fetchNotifications() {
    try {
      const { data } = await api.get('/notifications')
      notifications.value = data.data || []
      unreadCount.value = notifications.value.filter(n => !n.isRead).length
    } catch {
      // silent fail
    }
  }

  async function fetchUnreadCount() {
    try {
      const { data } = await api.get('/notifications/count')
      unreadCount.value = data.data?.count || 0
    } catch {
      // silent fail
    }
  }

  async function markAsRead(id) {
    try {
      await api.post(`/notifications/${id}/read`)
      notifications.value = notifications.value.filter(n => n.id !== id)
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch {
      // silent fail
    }
  }

  async function markAllAsRead() {
    try {
      await api.post('/notifications/read-all')
      notifications.value = []
      unreadCount.value = 0
    } catch {
      // silent fail
    }
  }

  function startPolling() {
    fetchUnreadCount()
    pollingInterval = setInterval(fetchUnreadCount, 30000) // 30초
  }

  function stopPolling() {
    if (pollingInterval) {
      clearInterval(pollingInterval)
      pollingInterval = null
    }
  }

  return {
    notifications,
    unreadCount,
    hasUnread,
    fetchNotifications,
    fetchUnreadCount,
    markAsRead,
    markAllAsRead,
    startPolling,
    stopPolling
  }
})
