<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'
import api from '@/api'
import Pagination from '@/components/Pagination.vue'

const router = useRouter()
const notify = useNotificationStore()

const streamers = ref([])
const isLoading = ref(true)

const categories = [
  { label: '전체', value: '' },
  { label: '게임', value: 'GAME' },
  { label: '음악', value: 'MUSIC' },
  { label: '먹방', value: 'MUKBANG' },
  { label: '일상/토크', value: 'TALK' },
  { label: '스포츠', value: 'SPORTS' },
  { label: '기타', value: 'ETC' },
]

const sortOptions = [
  { label: '최근 가입순', value: 'recent' },
  { label: '이름순', value: 'name' },
  { label: '컨텐츠 많은순', value: 'content' },
]

const activeCategory = ref('')
const sortBy = ref('recent')

const filteredStreamers = computed(() => {
  let list = streamers.value

  if (activeCategory.value) {
    list = list.filter(s => s.category === activeCategory.value)
  }

  const sorted = [...list]
  if (sortBy.value === 'name') {
    sorted.sort((a, b) => (a.nickname || '').localeCompare(b.nickname || ''))
  } else if (sortBy.value === 'content') {
    sorted.sort((a, b) => (b.contentCount || 0) - (a.contentCount || 0))
  }

  return sorted
})

function getPlatformIcon(name) {
  const n = name?.toLowerCase() || ''
  if (n.includes('youtube') || n.includes('yt')) return { label: 'YT', color: '#ff4444' }
  if (n.includes('chzzk') || n.includes('치지직')) return { label: '치', color: '#00c73c' }
  if (n.includes('soop') || n.includes('숲')) return { label: '숲', color: '#4e8df5' }
  if (n.includes('twitch')) return { label: 'TW', color: '#9146ff' }
  return { label: n.charAt(0).toUpperCase(), color: 'var(--accent)' }
}

function goToStreamer(userId) {
  router.push(`/streamers/${userId}`)
}

const liveStreamerMap = ref(new Map())

async function fetchStreamers() {
  try {
    const { data } = await api.get('/public/streamers')
    streamers.value = data.data || []
  } catch {
    notify.error('스트리머 목록을 불러올 수 없습니다')
  } finally {
    isLoading.value = false
  }
}

async function fetchLiveIds() {
  try {
    const { data } = await api.get('/public/streamers/live', { params: { oauthOnly: true } })
    const m = new Map()
    ;(Array.isArray(data) ? data : []).forEach(s => { if (s.streamerId != null) m.set(s.streamerId, s) })
    liveStreamerMap.value = m
  } catch { liveStreamerMap.value = new Map() }
}

function streamerUserId(streamer) {
  return streamer.userId ?? streamer.streamerId ?? null
}
function isLive(streamer) {
  const id = streamerUserId(streamer)
  return id != null && liveStreamerMap.value.has(id)
}
function liveInfo(streamer) {
  const id = streamerUserId(streamer)
  return id != null ? liveStreamerMap.value.get(id) : null
}

const currentPage = ref(1)
const pageSize = 12
const pagedStreamers = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredStreamers.value.slice(start, start + pageSize)
})
const totalPages = computed(() => Math.max(Math.ceil(filteredStreamers.value.length / pageSize), 1))
watch([() => activeCategory.value, () => sortBy.value], () => { currentPage.value = 1 })
function onPageChange(p) {
  currentPage.value = p
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  fetchStreamers()
  fetchLiveIds()
})
</script>

<template>
  <div class="streamer-list-page">
    <!-- Header -->
    <div class="page-header">
      <h1 class="page-title">🎮 나작스 스트리머</h1>
      <p class="page-subtitle">현재 활동 중인 인증 스트리머들을 만나보세요.</p>
    </div>

    <!-- Toolbar -->
    <div class="toolbar">
      <div class="filter-group">
        <button
          v-for="cat in categories"
          :key="cat.value"
          :class="['filter-btn', { active: activeCategory === cat.value }]"
          @click="activeCategory = cat.value"
        >
          {{ cat.label }}
        </button>
      </div>

      <div class="sort-group">
        <select v-model="sortBy" class="sort-select">
          <option
            v-for="opt in sortOptions"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </option>
        </select>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="isLoading" class="loading">
      <div class="spinner"></div>
    </div>

    <!-- Empty -->
    <div v-else-if="filteredStreamers.length === 0" class="empty">
      <p>해당 카테고리에 등록된 스트리머가 없습니다.</p>
    </div>

    <!-- Grid -->
    <div v-else class="streamer-grid">
      <div
        v-for="streamer in pagedStreamers"
        :key="streamer.id"
        class="streamer-card"
        :class="{ 'is-live': isLive(streamer) }"
        @click="goToStreamer(streamer.userId)"
      >
        <!-- LIVE 배지 -->
        <span v-if="isLive(streamer)" class="card-live-badge">
          <span class="card-live-dot"></span>LIVE
        </span>

        <!-- Cover gradient -->
        <div class="card-cover">
          <div class="cover-gradient"></div>
          <div v-if="isLive(streamer) && liveInfo(streamer)?.viewerCount != null" class="card-live-viewers-wrap">
            <span class="card-live-viewers">👁 {{ liveInfo(streamer).viewerCount.toLocaleString() }}</span>
          </div>
        </div>

        <!-- Avatar -->
        <div class="card-avatar">
          <img
            v-if="streamer.profileImage || streamer.avatar"
            :src="streamer.profileImage || streamer.avatar"
            :alt="streamer.nickname"
          />
          <div v-else class="avatar-placeholder">
            {{ streamer.nickname?.charAt(0)?.toUpperCase() }}
          </div>
        </div>

        <div class="card-body">
          <!-- Name row -->
          <div class="card-name">
            <h3>{{ streamer.nickname }}</h3>
            <svg
              v-if="streamer.verified"
              class="verified-badge"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
            >
              <circle cx="12" cy="12" r="10" stroke="#6c63ff" stroke-width="2" fill="rgba(108,99,255,0.15)" />
              <path d="M8 12.5l2.5 2.5 5.5-5.5" stroke="#6c63ff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </div>

          <!-- Description -->
          <p class="card-desc">{{ streamer.bio || '소개가 아직 없습니다.' }}</p>

          <!-- Platform badges -->
          <div v-if="streamer.youtubeUrl || streamer.chzzkUrl || streamer.soopUrl" class="platform-badges">
            <a
              v-if="streamer.youtubeUrl"
              :href="streamer.youtubeUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="platform-badge plat-youtube"
              title="YouTube"
              @click.stop
            >
              <svg width="14" height="10" viewBox="0 0 28 20"><path d="M27.4 3.1s-.3-1.9-1.1-2.7C25.1-.8 23.7-.8 23-.9 19.2-1.2 14-1.2 14-1.2s-5.2 0-9 .3c-.7.1-2.1.1-3.3 1.2C.9 1.2.6 3.1.6 3.1S.3 5.3.3 7.5v2c0 2.2.3 4.4.3 4.4s.3 1.9 1.1 2.7c1.2 1.2 2.7 1.1 3.4 1.3 2.5.2 10.9.3 10.9.3s5.2 0 9-.3c.7-.1 2.1-.1 3.3-1.2.8-.8 1.1-2.7 1.1-2.7s.3-2.2.3-4.4v-2c0-2.2-.3-4.5-.3-4.5z" fill="#fff"/><path d="M11 13.5V5l7.5 4.3L11 13.5z" fill="#FF0000"/></svg>
            </a>
            <a
              v-if="streamer.chzzkUrl"
              :href="streamer.chzzkUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="platform-badge plat-chzzk"
              title="치지직"
              @click.stop
            >
              <img src="/chzzk-logo.png" alt="치지직" />
            </a>
            <a
              v-if="streamer.soopUrl"
              :href="streamer.soopUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="platform-badge plat-soop"
              title="SOOP"
              @click.stop
            >
              <svg width="14" height="14" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" fill="none" stroke="#fff" stroke-width="2.5"/><circle cx="12" cy="12" r="4" fill="#fff"/></svg>
            </a>
          </div>

          <!-- Stats -->
          <div class="card-stats">
            <span class="stat">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="18" height="18" rx="2" />
                <path d="M9 9h6M9 13h4" />
              </svg>
              컨텐츠 {{ streamer.contentCount || 0 }}
            </span>
            <span class="stat">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M15 10l-4 4l6 6l4-16l-18 7l4 2l2 6l3-4" />
              </svg>
              클립 {{ streamer.clipCount || 0 }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <Pagination
      v-if="!isLoading && filteredStreamers.length > 0"
      :current-page="currentPage"
      :total-pages="totalPages"
      @change="onPageChange"
    />
  </div>
</template>

<style scoped>
.streamer-list-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 2.5rem 1.5rem;
}

/* Header */
.page-header {
  text-align: center;
  margin-bottom: 2.5rem;
}

.page-title {
  font-size: 2rem;
  font-weight: 800;
  color: var(--text);
  margin-bottom: 0.5rem;
}

.page-subtitle {
  font-size: 1rem;
  color: var(--text2);
}

/* Toolbar */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1rem;
  margin-bottom: 2rem;
}

.filter-group {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.filter-btn {
  padding: 0.5rem 1.1rem;
  border: 1px solid var(--border);
  border-radius: 9999px;
  background: var(--card);
  color: var(--text2);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.filter-btn:hover {
  background: var(--card-hover);
  color: var(--text);
}

.filter-btn.active {
  background: var(--accent);
  color: #fff;
  border-color: var(--accent);
}

.sort-group {
  flex-shrink: 0;
}

.sort-select {
  padding: 0.5rem 0.9rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg2);
  color: var(--text);
  font-size: 0.875rem;
  cursor: pointer;
  outline: none;
  appearance: auto;
}

.sort-select:focus {
  border-color: var(--accent);
}

/* Loading & Empty */
.loading {
  text-align: center;
  padding: 5rem 0;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty {
  text-align: center;
  padding: 5rem 0;
  color: var(--text3);
  font-size: 1rem;
}

/* Grid */
.streamer-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1.5rem;
}

/* Card */
.card-live-badge {
  position: absolute; top: 10px; right: 10px; z-index: 3;
  background: #ff3b3b; color: #fff; font-size: 0.7rem; font-weight: 800;
  padding: 3px 8px; border-radius: 4px; letter-spacing: 0.5px;
  display: inline-flex; align-items: center; gap: 4px;
  box-shadow: 0 2px 8px rgba(255,59,59,0.5);
}
.card-live-dot {
  width: 6px; height: 6px; border-radius: 50%; background: #fff;
  animation: card-live-blink 1.2s ease-in-out infinite;
}
@keyframes card-live-blink { 0%, 100% { opacity: 1 } 50% { opacity: 0.3 } }
.streamer-card.is-live { border: 2px solid #ff3b3b; }
.streamer-card.is-live:hover { box-shadow: 0 8px 24px rgba(255,59,59,0.25); }

.card-live-viewers-wrap {
  position: absolute; left: 0; right: 0; bottom: 0; z-index: 2;
  padding: 6px 10px;
  background: linear-gradient(180deg, rgba(0,0,0,0) 0%, rgba(0,0,0,0.78) 100%);
  display: flex; justify-content: flex-end;
  pointer-events: none;
}
.card-live-viewers {
  font-size: 0.7rem; font-weight: 700; color: #ffd3d3;
  white-space: nowrap;
  text-shadow: 0 1px 2px rgba(0,0,0,0.6);
}

.streamer-card {
  position: relative;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}

.streamer-card:hover {
  transform: translateY(-5px);
  border-color: rgba(108, 99, 255, 0.35);
  box-shadow: 0 8px 30px rgba(108, 99, 255, 0.15), 0 0 0 1px rgba(108, 99, 255, 0.1);
}

/* Cover */
.card-cover {
  height: 80px;
  position: relative;
  overflow: hidden;
}

.cover-gradient {
  width: 100%;
  height: 100%;
  background: var(--gradient);
  opacity: 0.7;
}

/* Avatar */
.card-avatar {
  width: 60px;
  height: 60px;
  border-radius: 14px;
  overflow: hidden;
  position: relative;
  margin: -30px 0 0 1.25rem;
  border: 3px solid var(--bg2);
  background: var(--bg3);
  z-index: 1;
  flex-shrink: 0;
}

.card-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg3);
  color: var(--accent);
  font-weight: 700;
  font-size: 1.35rem;
}

/* Body */
.card-body {
  padding: 0.75rem 1.25rem 1.25rem;
}

.card-name {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.35rem;
}

.card-name h3 {
  font-size: 1rem;
  font-weight: 700;
  color: var(--text);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.verified-badge {
  flex-shrink: 0;
}

.card-desc {
  font-size: 0.825rem;
  color: var(--text2);
  line-height: 1.45;
  margin: 0 0 0.75rem 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* Platform badges */
.platform-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-bottom: 0.75rem;
}

.platform-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  text-decoration: none;
  transition: transform 0.15s, box-shadow 0.15s;
}

.platform-badge img {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  display: block;
}

.platform-badge.plat-youtube { background: #ff0000; }
.platform-badge.plat-chzzk { background: #00ffa3; padding: 4px; }
.platform-badge.plat-soop { background: #3b63ff; }

.platform-badge:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(0,0,0,0.25);
}

/* Stats */
.card-stats {
  display: flex;
  gap: 1rem;
  padding-top: 0.65rem;
  border-top: 1px solid var(--border);
}

.stat {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.775rem;
  color: var(--text3);
}

.stat svg {
  opacity: 0.6;
}

/* Responsive */
@media (max-width: 1100px) {
  .streamer-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 850px) {
  .streamer-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .page-title {
    font-size: 1.6rem;
  }
}

@media (max-width: 550px) {
  .streamer-list-page {
    padding: 1.5rem 1rem;
  }

  .streamer-grid {
    grid-template-columns: 1fr;
  }

  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-group {
    justify-content: center;
  }

  .sort-group {
    text-align: center;
  }

  .sort-select {
    width: 100%;
  }
}
</style>
