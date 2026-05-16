<script setup>
import AppHeader from '@/components/common/AppHeader.vue'
import AppFooter from '@/components/common/AppFooter.vue'
import { useNotificationStore } from '@/stores/notification'
import { computed } from 'vue'

const notifyStore = useNotificationStore()
const toasts = computed(() => notifyStore.notifications)
</script>

<template>
  <div class="default-layout">
    <AppHeader />

    <main class="main-content">
      <slot />
    </main>

    <AppFooter />

    <!-- Toast notifications -->
    <div class="toast-container">
      <transition-group name="toast">
        <div
          v-for="toast in toasts"
          :key="toast.id"
          class="toast-item"
          :class="'toast-' + toast.type"
        >
          <span class="toast-message">{{ toast.message }}</span>
          <button class="toast-close" @click="notifyStore.removeNotification(toast.id)">&times;</button>
        </div>
      </transition-group>
    </div>
  </div>
</template>

<style scoped>
.default-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}

.main-content {
  flex: 1;
}

.toast-container {
  position: fixed;
  top: 80px;
  right: 20px;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 360px;
}

.toast-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  backdrop-filter: blur(10px);
  border: 1px solid var(--border);
  box-shadow: 0 8px 24px rgba(0,0,0,0.4);
}

.toast-success {
  background: rgba(0,212,170,0.15);
  color: var(--accent3);
  border-color: rgba(0,212,170,0.3);
}

.toast-error {
  background: rgba(255,107,157,0.15);
  color: var(--accent2);
  border-color: rgba(255,107,157,0.3);
}

.toast-warning {
  background: rgba(255,180,0,0.15);
  color: #ffb400;
  border-color: rgba(255,180,0,0.3);
}

.toast-info {
  background: rgba(108,99,255,0.15);
  color: #a89fff;
  border-color: rgba(108,99,255,0.3);
}

.toast-close {
  background: none;
  border: none;
  color: inherit;
  font-size: 1.2rem;
  cursor: pointer;
  margin-left: 12px;
  opacity: 0.7;
}

.toast-close:hover { opacity: 1; }

.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(100px);
}
</style>
