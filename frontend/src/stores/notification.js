import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref([])
  let nextId = 1

  const unreadCount = computed(() => notifications.value.filter((n) => !n.read).length)
  const hasUnread = computed(() => unreadCount.value > 0)

  function addNotification({ type = 'info', message, duration = 5000 }) {
    const id = nextId++
    notifications.value.push({
      id,
      type,
      message,
      read: false,
      createdAt: new Date()
    })

    if (duration > 0) {
      setTimeout(() => {
        removeNotification(id)
      }, duration)
    }

    return id
  }

  function removeNotification(id) {
    const index = notifications.value.findIndex((n) => n.id === id)
    if (index !== -1) {
      notifications.value.splice(index, 1)
    }
  }

  function markAsRead(id) {
    const notification = notifications.value.find((n) => n.id === id)
    if (notification) {
      notification.read = true
    }
  }

  function markAllAsRead() {
    notifications.value.forEach((n) => {
      n.read = true
    })
  }

  function clearAll() {
    notifications.value = []
  }

  function success(message, duration) {
    return addNotification({ type: 'success', message, duration })
  }

  function error(message, duration) {
    return addNotification({ type: 'error', message, duration })
  }

  function warning(message, duration) {
    return addNotification({ type: 'warning', message, duration })
  }

  function info(message, duration) {
    return addNotification({ type: 'info', message, duration })
  }

  return {
    notifications,
    unreadCount,
    hasUnread,
    addNotification,
    removeNotification,
    markAsRead,
    markAllAsRead,
    clearAll,
    success,
    error,
    warning,
    info
  }
})
