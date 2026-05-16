<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useServerNotificationStore } from '@/stores/serverNotification'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const serverNotify = useServerNotificationStore()

const searchQuery = ref('')
const showUserMenu = ref(false)
const showMobileMenu = ref(false)
const showNotifications = ref(false)

// 테마 토글
const isDark = ref(true)

function initTheme() {
  const saved = localStorage.getItem('najaks_theme')
  if (saved === 'light') {
    isDark.value = false
    document.documentElement.setAttribute('data-theme', 'light')
  } else {
    isDark.value = true
    document.documentElement.setAttribute('data-theme', 'dark')
  }
}

function toggleTheme() {
  isDark.value = !isDark.value
  const theme = isDark.value ? 'dark' : 'light'
  document.documentElement.setAttribute('data-theme', theme)
  localStorage.setItem('najaks_theme', theme)
}

const isLoggedIn = computed(() => authStore.isAuthenticated)
const userName = computed(() => authStore.userNickname || 'User')
const isStreamer = computed(() => authStore.isStreamer)
const isAdmin = computed(() => authStore.isAdmin)

function handleSearch() {
  if (searchQuery.value.trim()) {
    router.push({ path: '/search', query: { q: searchQuery.value.trim() } })
    searchQuery.value = ''
  }
}

function handleLogout() {
  authStore.logout()
  showUserMenu.value = false
  router.push('/')
}

function closeMenus() {
  showUserMenu.value = false
  showMobileMenu.value = false
  showNotifications.value = false
}

function toggleNotifications() {
  showNotifications.value = !showNotifications.value
  showUserMenu.value = false
  if (showNotifications.value) {
    serverNotify.fetchNotifications()
  }
}

function toggleUserMenu() {
  showUserMenu.value = !showUserMenu.value
  showNotifications.value = false
}

function isActive(path) {
  return route.path === path || route.path.startsWith(path + '/')
}

function isActiveGroup(group) {
  return route.path === '/community' && (route.query.group || 'community') === group
}

onMounted(() => {
  initTheme()
  if (authStore.isAuthenticated) {
    serverNotify.startPolling()
  }
})

onUnmounted(() => {
  serverNotify.stopPolling()
})

watch(() => authStore.isAuthenticated, (val) => {
  if (val) serverNotify.startPolling()
  else serverNotify.stopPolling()
})
</script>

<template>
  <header class="site-header">
    <!-- 좌측: 로고 + 검색바 -->
    <div class="header-left">
      <div class="logo" @click="router.push('/')" style="cursor:pointer;">NAJAKS</div>
      <form class="search-bar" @submit.prevent="handleSearch">
        <span class="search-icon">🔍</span>
        <input
          v-model="searchQuery"
          type="text"
          placeholder="검색어를 입력하세요"
        />
      </form>
    </div>

    <!-- 가운데: 네비 -->
    <nav class="header-nav">
      <router-link to="/streamers" :class="{ active: isActive('/streamers') }">스트리머</router-link>
      <router-link
        :to="{ path: '/community', query: { group: 'community' } }"
        :class="{ active: isActiveGroup('community') }"
      >커뮤니티</router-link>
      <router-link
        :to="{ path: '/community', query: { group: 'pride' } }"
        :class="{ active: isActiveGroup('pride') }"
      >나작스 자랑</router-link>
      <router-link to="/creator" :class="{ active: isActive('/creator') }">구인/구직</router-link>
      <router-link to="/contents" :class="{ active: isActive('/contents') }">대회·컨텐츠</router-link>
      <router-link to="/resources" :class="{ active: isActive('/resources') }">도구 모음</router-link>
    </nav>

    <!-- 우측: 액션들 -->
    <div class="header-actions">
      <router-link v-if="isAdmin" to="/admin" class="btn btn-admin" :class="{ active: isActive('/admin') }">관리자</router-link>

      <!-- Logged Out -->
      <template v-if="!isLoggedIn">
        <router-link to="/login" class="btn btn-outline">로그인</router-link>
        <router-link to="/register" class="btn btn-primary">가입하기</router-link>
      </template>

      <!-- Logged In -->
      <template v-else>
        <!-- Notification -->
        <div class="notification-wrapper">
          <button class="icon-btn" @click="toggleNotifications" title="알림">
            🔔
            <span v-if="serverNotify.hasUnread" class="notif-badge">
              {{ serverNotify.unreadCount > 9 ? '9+' : serverNotify.unreadCount }}
            </span>
          </button>
          <div v-if="showNotifications" class="notif-dropdown">
            <div class="notif-header">
              <strong>알림</strong>
              <button v-if="serverNotify.hasUnread" class="notif-read-all" @click="serverNotify.markAllAsRead()">모두 읽음</button>
            </div>
            <div v-if="serverNotify.notifications.length === 0" class="notif-empty">새 알림이 없습니다</div>
            <div v-for="n in serverNotify.notifications" :key="n.id" class="notif-item" @click="serverNotify.markAsRead(n.id)">
              <p class="notif-message">{{ n.message }}</p>
              <span class="notif-time">{{ new Date(n.createdAt).toLocaleDateString('ko-KR') }}</span>
            </div>
          </div>
        </div>

        <!-- User Menu -->
        <div class="user-menu-wrapper">
          <button class="user-btn" @click="toggleUserMenu">
            <img v-if="authStore.user?.profileImage" :src="authStore.user.profileImage" :alt="userName" class="user-avatar-img" />
            <span v-else class="user-avatar-sm">{{ userName.charAt(0) }}</span>
            <span class="user-name-text">{{ userName }}</span>
            <span v-if="isStreamer" class="verified-badge-sm">✓</span>
          </button>
          <div v-if="showUserMenu" class="user-dropdown">
            <div class="dropdown-header">
              <span class="dropdown-name">{{ userName }}</span>
              <span class="dropdown-role">{{ authStore.userRole }}</span>
            </div>
            <div class="dropdown-divider" />
            <router-link to="/mypage" class="dropdown-item" @click="closeMenus">마이페이지</router-link>
            <router-link v-if="isAdmin" to="/admin" class="dropdown-item" @click="closeMenus">관리자 패널</router-link>
            <div class="dropdown-divider" />
            <button class="dropdown-item logout" @click="handleLogout">로그아웃</button>
          </div>
        </div>
      </template>

      <!-- Mobile hamburger -->
      <div class="hamburger" @click="showMobileMenu = !showMobileMenu">
        <span></span><span></span><span></span>
      </div>

      <button class="theme-toggle" @click="toggleTheme" :title="isDark ? '라이트 모드' : '다크 모드'">
        {{ isDark ? '☀️' : '🌙' }}
      </button>
    </div>
  </header>

  <!-- Mobile Nav -->
  <nav class="mobile-nav" :class="{ open: showMobileMenu }">
    <router-link to="/" @click="closeMenus">🏠 메인</router-link>
    <router-link to="/streamers" @click="closeMenus">🎮 스트리머</router-link>
    <router-link :to="{ path: '/community', query: { group: 'community' } }" @click="closeMenus">💬 커뮤니티</router-link>
    <router-link :to="{ path: '/community', query: { group: 'pride' } }" @click="closeMenus">✨ 나작스 자랑</router-link>
    <router-link to="/creator" @click="closeMenus">💼 구인/구직</router-link>
    <router-link v-if="isAdmin" to="/admin" @click="closeMenus">🛠 관리자</router-link>
    <router-link to="/contents" @click="closeMenus">🏆 대회·컨텐츠</router-link>
    <router-link to="/resources" @click="closeMenus">🔧 도구 모음</router-link>
    <template v-if="!isLoggedIn">
      <router-link to="/login" @click="closeMenus" style="color:var(--accent);">로그인 / 가입하기</router-link>
    </template>
    <template v-else>
      <router-link to="/mypage" @click="closeMenus">👤 마이페이지</router-link>
      <a href="#" @click.prevent="handleLogout" style="color:var(--accent2);">로그아웃</a>
    </template>
  </nav>
</template>

<style scoped>
.site-header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 14px 32px;
  background: var(--bg2);
  border-bottom: 1px solid var(--border);
  backdrop-filter: blur(20px);
  position: sticky;
  top: 0;
  z-index: 100;
  gap: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-self: start;
}

.logo {
  font-size: 22px;
  font-weight: 900;
  background: var(--gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.5px;
  flex-shrink: 0;
}

.header-nav {
  display: flex;
  gap: 14px;
  align-items: center;
  justify-content: center;
  overflow-x: auto;
  scrollbar-width: none;
}

.header-nav::-webkit-scrollbar { display: none; }

.header-nav a {
  color: var(--text2);
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
  transition: color 0.2s;
  white-space: nowrap;
}

.header-nav a:hover,
.header-nav a.active {
  color: var(--text);
}

.header-nav a.active {
  color: var(--accent);
  font-weight: 700;
}

.btn-admin {
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--accent2);
  text-decoration: none;
  border: 1px solid rgba(255, 107, 107, 0.3);
  background: rgba(255, 107, 107, 0.08);
  transition: all 0.2s;
  white-space: nowrap;
}

.btn-admin:hover,
.btn-admin.active {
  background: rgba(255, 107, 107, 0.18);
  border-color: var(--accent2);
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-self: end;
}

.theme-toggle {
  background: none;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 5px 8px;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}
.theme-toggle:hover {
  border-color: var(--accent);
  background: rgba(108,99,255,0.08);
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--bg3);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 7px 14px;
  width: 400px;
  flex-shrink: 1;
  min-width: 200px;
}

.search-icon {
  color: var(--text2);
  font-size: 14px;
  flex-shrink: 0;
}

.search-bar input {
  background: none;
  border: none;
  outline: none;
  color: var(--text);
  font-size: 13px;
  width: 100%;
}

.search-bar input::placeholder {
  color: var(--text2);
}

/* Icon button */
.icon-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 18px;
  position: relative;
  padding: 4px;
}

.notif-badge {
  position: absolute;
  top: -4px;
  right: -6px;
  background: var(--accent2);
  color: white;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 10px;
  min-width: 16px;
  text-align: center;
}

.notification-wrapper {
  position: relative;
}

.notif-dropdown {
  position: absolute;
  top: calc(100% + 12px);
  right: 0;
  width: 320px;
  max-height: 400px;
  overflow-y: auto;
  background: var(--bg2);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(0,0,0,0.5);
  z-index: 200;
}

.notif-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border);
  font-size: 14px;
}

.notif-read-all {
  background: none;
  border: none;
  color: var(--accent);
  font-size: 12px;
  cursor: pointer;
  font-weight: 600;
}

.notif-empty {
  padding: 2rem 1rem;
  text-align: center;
  color: var(--text3);
  font-size: 13px;
}

.notif-item {
  padding: 12px 18px;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
  transition: background 0.2s;
}

.notif-item:hover {
  background: var(--card);
}

.notif-message {
  font-size: 13px;
  color: var(--text);
  margin-bottom: 4px;
}

.notif-time {
  font-size: 11px;
  color: var(--text3);
}

/* User Menu */
.user-menu-wrapper {
  position: relative;
}

.user-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  background: none;
  border: none;
  cursor: pointer;
  font-size: 13px;
  color: var(--text);
  padding: 4px;
}

.user-avatar-sm {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: white;
}

.user-avatar-img {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  object-fit: cover;
  border: 1px solid var(--border);
}

.user-name-text {
  font-weight: 600;
}

.verified-badge-sm {
  background: var(--gradient);
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 10px;
  font-weight: 700;
  color: white;
}

.user-dropdown {
  position: absolute;
  top: calc(100% + 12px);
  right: 0;
  width: 220px;
  background: var(--bg2);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(0,0,0,0.5);
  padding: 6px 0;
  z-index: 200;
}

.dropdown-header {
  padding: 12px 18px;
  display: flex;
  flex-direction: column;
}

.dropdown-name {
  font-weight: 700;
  font-size: 14px;
}

.dropdown-role {
  font-size: 12px;
  color: var(--text2);
  text-transform: capitalize;
}

.dropdown-divider {
  height: 1px;
  background: var(--border);
  margin: 4px 0;
}

.dropdown-item {
  display: block;
  padding: 10px 18px;
  color: var(--text);
  text-decoration: none;
  font-size: 13px;
  transition: background 0.2s;
  border: none;
  background: none;
  width: 100%;
  text-align: left;
  cursor: pointer;
  font-weight: 500;
}

.dropdown-item:hover {
  background: var(--card);
}

.dropdown-item.logout {
  color: var(--accent2);
}

/* Hamburger */
.hamburger {
  display: none;
  flex-direction: column;
  gap: 5px;
  cursor: pointer;
  padding: 4px;
  flex-shrink: 0;
}

.hamburger span {
  width: 22px;
  height: 2px;
  background: var(--text);
  border-radius: 2px;
  transition: all 0.3s;
}

/* Mobile Nav */
.mobile-nav {
  display: none;
  flex-direction: column;
  background: var(--bg2);
  border-bottom: 1px solid var(--border);
  padding: 8px 0;
}

.mobile-nav.open {
  display: flex;
}

.mobile-nav a {
  padding: 14px 20px;
  color: var(--text2);
  text-decoration: none;
  font-size: 15px;
  font-weight: 500;
  border-bottom: 1px solid var(--border);
  transition: all 0.2s;
}

.mobile-nav a:hover {
  color: var(--text);
  background: var(--card);
}

/* Responsive */
@media (max-width: 1024px) {
  .site-header { grid-template-columns: auto 1fr; padding: 14px 20px; }
  .header-nav { display: none !important; }
  .search-bar { display: none !important; }
  .hamburger { display: flex !important; }
}

@media (max-width: 480px) {
  .site-header { padding: 12px 16px; }
  .logo { font-size: 18px; }
  .user-name-text { display: none; }
  .header-actions { gap: 6px; }
}
</style>
