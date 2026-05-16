<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import api from '@/api'
import Pagination from '@/components/Pagination.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const notify = useNotificationStore()

const streamer = ref(null)
const contents = ref([])
const clips = ref([])
const schedules = ref([])
const boardPosts = ref([])
const isLoading = ref(true)
const isLoggedIn = computed(() => !!authStore.user)

const pageSize = 10
const contentPage = ref(1)
const clipPage = ref(1)
const pagedContents = computed(() => {
  const s = (contentPage.value - 1) * pageSize
  return contents.value.slice(s, s + pageSize)
})
const pagedClips = computed(() => {
  const s = (clipPage.value - 1) * pageSize
  return clips.value.slice(s, s + pageSize)
})
const contentTotalPages = computed(() => Math.max(Math.ceil(contents.value.length / pageSize), 1))
const clipTotalPages = computed(() => Math.max(Math.ceil(clips.value.length / pageSize), 1))

// 알림 구독
const isSubscribed = ref(false)

function checkSubscription() {
  const subs = JSON.parse(localStorage.getItem('najaks_streamer_subs') || '[]')
  isSubscribed.value = subs.includes(route.params.id?.toString())
}

async function toggleSubscription() {
  const streamerId = route.params.id?.toString()
  let subs = JSON.parse(localStorage.getItem('najaks_streamer_subs') || '[]')

  if (isSubscribed.value) {
    subs = subs.filter(id => id !== streamerId)
    localStorage.setItem('najaks_streamer_subs', JSON.stringify(subs))
    isSubscribed.value = false
    notify.success('알림이 해제되었습니다')
  } else {
    if ('Notification' in window && Notification.permission === 'default') {
      await Notification.requestPermission()
    }
    subs.push(streamerId)
    localStorage.setItem('najaks_streamer_subs', JSON.stringify(subs))
    isSubscribed.value = true
    notify.success('알림이 설정되었습니다! 방송 예고가 등록되면 알림을 받습니다.')
  }
}

async function fetchStreamer() {
  try {
    const { data } = await api.get(`/public/streamers/${route.params.id}`)
    streamer.value = data.data
  } catch {
    notify.error('스트리머 정보를 불러올 수 없습니다')
  } finally {
    isLoading.value = false
  }
}

async function fetchContents() {
  try {
    const { data } = await api.get(`/public/streamers/${route.params.id}/contents`)
    contents.value = data.data || []
  } catch {
    /* silent */
  }
}

async function fetchClips() {
  try {
    const { data } = await api.get(`/public/streamers/${route.params.id}/clips`)
    clips.value = data.data || []
  } catch {
    /* silent */
  }
}

async function fetchSchedules() {
  try {
    const { data } = await api.get(`/public/streamers/${route.params.id}/schedules`)
    schedules.value = data.data || []
  } catch {
    /* silent */
  }
}

async function fetchBoardPosts() {
  try {
    const { data } = await api.get(`/public/streamers/${route.params.id}/posts?page=0&size=10`)
    boardPosts.value = data.data?.content || data.data || []
  } catch {
    boardPosts.value = []
  }
}

function goWriteBoard() {
  if (!isLoggedIn.value) {
    notify.warning('로그인이 필요합니다')
    router.push('/login')
    return
  }
  router.push({ path: '/community/write', query: { streamerBoard: route.params.id } })
}

function formatBoardDate(dateStr) {
  if (!dateStr) return ''
  const d = Array.isArray(dateStr) ? new Date(dateStr[0], dateStr[1] - 1, dateStr[2], dateStr[3] || 0, dateStr[4] || 0) : new Date(dateStr)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleDateString('ko-KR', { year: '2-digit', month: '2-digit', day: '2-digit' }).replace(/\. /g, '.').replace(/\.$/, '')
}

function formatScheduleDate(dateStr) {
  if (!dateStr) return ''
  const d = Array.isArray(dateStr) ? new Date(dateStr[0], dateStr[1] - 1, dateStr[2], dateStr[3] || 0, dateStr[4] || 0) : new Date(dateStr)
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const isToday = d.toDateString() === now.toDateString()
  const prefix = isToday ? '오늘' : `${d.getMonth() + 1}/${d.getDate()}`
  return `${prefix} ${d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}`
}

function getPlatformLabel(platform) {
  const map = { youtube: '유튜브', chzzk: '치지직', soop: '숲' }
  return map[platform?.toLowerCase()] || platform
}

function getPlatformClass(platform) {
  const key = platform?.toLowerCase()
  if (key === 'youtube') return 'plat-youtube'
  if (key === 'chzzk') return 'plat-chzzk'
  if (key === 'soop') return 'plat-soop'
  return ''
}

function getDayLabel(day) {
  const map = {
    MON: '월', TUE: '화', WED: '수', THU: '목',
    FRI: '금', SAT: '토', SUN: '일',
    mon: '월', tue: '화', wed: '수', thu: '목',
    fri: '금', sat: '토', sun: '일'
  }
  return map[day] || day
}

function getClipThumbnail(url, thumbnailUrl) {
  if (thumbnailUrl) return thumbnailUrl
  if (!url) return null
  const ytMatch = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/|youtube\.com\/shorts\/)([a-zA-Z0-9_-]{11})/)
  if (ytMatch) return `https://img.youtube.com/vi/${ytMatch[1]}/hqdefault.jpg`
  if (url.includes('chzzk.naver.com')) return '/chzzk-logo.png'
  return null
}

function isChzzkThumb(url, thumbnailUrl) {
  if (thumbnailUrl) return false
  return url && url.includes('chzzk.naver.com')
}

function onClipClick(clip) {
  if (clip?.id) {
    api.post(`/public/clips/${clip.id}/view`).catch(() => {})
  }
}

onMounted(() => {
  fetchStreamer()
  fetchContents()
  fetchClips()
  fetchSchedules()
  fetchBoardPosts()
  checkSubscription()
})
</script>

<template>
  <div class="sd-page">
    <!-- Loading -->
    <div v-if="isLoading" class="sd-loading">
      <div class="sd-spinner"></div>
    </div>

    <template v-else-if="streamer">
      <!-- Profile Cover -->
      <div class="sd-cover-wrap">
        <div
          class="sd-cover"
          :class="{ 'has-cover': !!streamer.coverImage }"
          :style="streamer.coverImage ? { backgroundImage: `url(${streamer.coverImage})` } : {}"
        >
          <div class="sd-cover-gradient"></div>
        </div>
      </div>

      <!-- Profile Info Bar -->
      <div class="sd-info-bar">
        <div class="sd-info-inner">
          <div class="sd-avatar">
            <img
              v-if="streamer.profileImage"
              :src="streamer.profileImage"
              :alt="streamer.nickname"
            />
            <div v-else class="sd-avatar-fallback">
              {{ streamer.nickname?.charAt(0)?.toUpperCase() }}
            </div>
          </div>

          <div class="sd-identity">
            <div class="sd-name-row">
              <h1 class="sd-name">{{ streamer.nickname }}</h1>
              <a v-if="streamer.youtubeUrl" :href="streamer.youtubeUrl" target="_blank" class="sd-platform-mark youtube" title="YouTube">
                <svg width="14" height="10" viewBox="0 0 28 20"><path d="M27.4 3.1s-.3-1.9-1.1-2.7C25.1-.8 23.7-.8 23-.9 19.2-1.2 14-1.2 14-1.2s-5.2 0-9 .3c-.7.1-2.1.1-3.3 1.2C.9 1.2.6 3.1.6 3.1S.3 5.3.3 7.5v2c0 2.2.3 4.4.3 4.4s.3 1.9 1.1 2.7c1.2 1.2 2.7 1.1 3.4 1.3 2.5.2 10.9.3 10.9.3s5.2 0 9-.3c.7-.1 2.1-.1 3.3-1.2.8-.8 1.1-2.7 1.1-2.7s.3-2.2.3-4.4v-2c0-2.2-.3-4.5-.3-4.5z" fill="#fff"/><path d="M11 13.5V5l7.5 4.3L11 13.5z" fill="#FF0000"/></svg>
              </a>
              <a v-if="streamer.chzzkUrl" :href="streamer.chzzkUrl" target="_blank" class="sd-platform-mark chzzk" title="치지직">
                <img src="/chzzk-logo.png" alt="치지직" style="width: 18px; height: 18px; border-radius: 4px;" />
              </a>
              <a v-if="streamer.soopUrl" :href="streamer.soopUrl" target="_blank" class="sd-platform-mark soop" title="SOOP">
                <svg width="14" height="14" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" fill="none" stroke="#fff" stroke-width="2.5"/><circle cx="12" cy="12" r="4" fill="#fff"/></svg>
              </a>
            </div>
            <div class="sd-stats">
              <span class="sd-stat">컨텐츠 <strong>{{ streamer.contentCount ?? contents.length }}</strong></span>
              <span class="sd-stat-sep"></span>
              <span class="sd-stat">클립 <strong>{{ streamer.clipCount ?? clips.length }}</strong></span>
            </div>
          </div>

          <div class="sd-actions">
          </div>
        </div>
      </div>

      <!-- Profile Body -->
      <div class="sd-body">
        <!-- Main Column -->
        <div class="sd-main">
          <!-- 자기소개 -->
          <div class="sd-card">
            <h2 class="sd-card-title">자기소개</h2>
            <p class="sd-bio-text">{{ streamer.bio || '등록된 자기소개가 없습니다.' }}</p>
          </div>

          <!-- 일정표 -->
          <div class="sd-card">
            <h2 class="sd-card-title">📅 일정표</h2>
            <div v-if="streamer.scheduleImageUrl" class="sd-schedule-image">
              <img :src="streamer.scheduleImageUrl" alt="일정표" />
            </div>
            <div v-else class="sd-empty">아직 등록된 일정표가 없습니다.</div>
          </div>

          <!-- 컨텐츠 -->
          <div class="sd-section">
            <h2 class="sd-section-title">&#x1F3AF; 컨텐츠</h2>
            <div v-if="contents.length" class="sd-grid-2">
              <router-link
                v-for="item in pagedContents"
                :key="item.id"
                :to="`/contents/${item.id}`"
                class="sd-content-card"
              >
                <div class="sd-content-thumb" :style="item.thumbnailUrl ? { backgroundImage: `url(${item.thumbnailUrl})` } : {}">
                  <span v-if="item.category" class="sd-content-badge">{{ item.category }}</span>
                </div>
                <div class="sd-content-info">
                  <h3>{{ item.title }}</h3>
                  <p>{{ item.description }}</p>
                </div>
              </router-link>
            </div>
            <Pagination v-if="contents.length" :current-page="contentPage" :total-pages="contentTotalPages" @change="p => contentPage = p" />
            <div v-if="!contents.length" class="sd-empty">아직 등록된 컨텐츠가 없습니다.</div>
          </div>

          <!-- 최근 클립 -->
          <div class="sd-section">
            <h2 class="sd-section-title">최근 클립</h2>
            <div v-if="clips.length" class="sd-grid-3">
              <a
                v-for="clip in pagedClips"
                :key="clip.id"
                :href="clip.url"
                target="_blank"
                class="sd-clip-card"
                @click.stop="onClipClick(clip)"
              >
                <div class="sd-clip-thumb" :class="{ 'sd-clip-chzzk': isChzzkThumb(clip.url, clip.thumbnailUrl) }" :style="getClipThumbnail(clip.url, clip.thumbnailUrl) && !isChzzkThumb(clip.url, clip.thumbnailUrl) ? { backgroundImage: `url(${getClipThumbnail(clip.url, clip.thumbnailUrl)})` } : {}">
                  <img v-if="isChzzkThumb(clip.url, clip.thumbnailUrl)" src="/chzzk-logo.png" alt="치지직" class="sd-clip-chzzk-logo" />
                  <div class="sd-clip-play">▶</div>
                </div>
                <div class="sd-clip-info">
                  <h4>{{ clip.title }}</h4>
                </div>
              </a>
            </div>
            <Pagination v-if="clips.length" :current-page="clipPage" :total-pages="clipTotalPages" @change="p => clipPage = p" />
            <div v-if="!clips.length" class="sd-empty">아직 등록된 클립이 없습니다.</div>
          </div>
        </div>

        <!-- Sidebar -->
        <aside class="sd-sidebar">
          <!-- 플랫폼 링크 -->
          <div class="sd-sidebar-card">
            <h3 class="sd-sidebar-title">플랫폼 링크</h3>
            <div class="sd-platform-btns">
              <template v-if="streamer.platforms?.length">
                <a
                  v-for="p in streamer.platforms"
                  :key="p.name || p"
                  :href="p.url || '#'"
                  target="_blank"
                  class="sd-platform-btn"
                  :class="getPlatformClass(p.name || p)"
                >
                  {{ getPlatformLabel(p.name || p) }}
                </a>
              </template>
              <template v-else>
                <a v-if="streamer.youtubeUrl" :href="streamer.youtubeUrl" target="_blank" class="sd-platform-btn plat-youtube">유튜브</a>
                <a v-if="streamer.chzzkUrl" :href="streamer.chzzkUrl" target="_blank" class="sd-platform-btn plat-chzzk">치지직</a>
                <a v-if="streamer.soopUrl" :href="streamer.soopUrl" target="_blank" class="sd-platform-btn plat-soop">숲</a>
              </template>
            </div>
          </div>

          <!-- 방송 시간표 -->
          <div class="sd-sidebar-card">
            <h3 class="sd-sidebar-title">방송 시간표</h3>
            <div v-if="streamer.schedules?.length" class="sd-schedule-list">
              <div v-for="(s, i) in streamer.schedules" :key="i" class="sd-schedule-row">
                <span class="sd-schedule-day">{{ getDayLabel(s.day) }}</span>
                <span class="sd-schedule-time">{{ s.startTime }} - {{ s.endTime }}</span>
              </div>
            </div>
            <div v-else-if="streamer.broadcastSchedule" class="sd-schedule-text">
              {{ streamer.broadcastSchedule }}
            </div>
            <div v-else class="sd-empty-sm">등록된 시간표가 없습니다.</div>
          </div>

          <!-- 방송 예고 -->
          <div class="sd-sidebar-card">
            <h3 class="sd-sidebar-title">방송 예고</h3>
            <div v-if="schedules.length" class="sd-schedule-list">
              <div v-for="s in schedules" :key="s.id" class="sd-schedule-row">
                <span class="sd-schedule-day">{{ formatScheduleDate(s.scheduledAt) }}</span>
                <span class="sd-schedule-time">{{ s.title }}</span>
              </div>
            </div>
            <div v-else class="sd-empty-sm">등록된 방송 예고가 없습니다.</div>
          </div>

          <!-- 팬 게시판 진입 -->
          <router-link :to="`/streamers/${route.params.id}/board`" class="sd-board-entry">
            <div class="sd-board-entry-left">
              <div class="sd-board-entry-icon">💬</div>
              <div>
                <div class="sd-board-entry-title">팬 게시판</div>
                <div class="sd-board-entry-sub">
                  {{ boardPosts.length > 0
                    ? `최근 ${boardPosts.length}개의 글`
                    : '첫 글을 남겨보세요!' }}
                </div>
              </div>
            </div>
            <div class="sd-board-entry-arrow">→</div>
          </router-link>
        </aside>
      </div>
    </template>

    <!-- Not Found -->
    <template v-else>
      <div class="sd-not-found">
        <h1>스트리머를 찾을 수 없습니다</h1>
        <p>존재하지 않거나 삭제된 스트리머입니다.</p>
        <router-link to="/streamers" class="sd-btn sd-btn-primary">스트리머 목록으로</router-link>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* === CSS Variables === */
.sd-page {
  --bg: #0a0a0f;
  --bg2: #12121a;
  --bg3: #1a1a26;
  --border: rgba(255, 255, 255, 0.07);
  --accent: #6c63ff;
  --accent2: #ff6b9d;
  --accent3: #00d4aa;
  --text: #f0f0f8;
  --text2: #9999bb;
  --text3: #555577;
  --card: rgba(255, 255, 255, 0.04);
  --card-hover: rgba(255, 255, 255, 0.07);
  --gradient: linear-gradient(135deg, #6c63ff, #ff6b9d);

  background: var(--bg);
  color: var(--text);
  min-height: 100vh;
  padding-bottom: 4rem;
}

/* === Loading === */
.sd-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}

.sd-spinner {
  width: 44px;
  height: 44px;
  border: 3px solid var(--bg3);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: sd-spin 0.8s linear infinite;
}

@keyframes sd-spin {
  to { transform: rotate(360deg); }
}

/* === Cover === */
.sd-cover-wrap {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 2rem;
  box-sizing: border-box;
}

.sd-cover {
  position: relative;
  width: 100%;
  height: 230px;
  overflow: hidden;
  border-radius: 16px;
  background-color: var(--bg2, #12121a);
  background-size: cover;
  background-position: center center;
  background-repeat: no-repeat;
}

.sd-cover-gradient {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(135deg, rgba(13,0,64,0.55) 0%, rgba(108,99,255,0.35) 50%, rgba(255,107,157,0.35) 100%);
}

/* 커버 이미지가 있을 때는 하단만 살짝 어둡게 해서 정보바 가독성 확보 */
.sd-cover.has-cover .sd-cover-gradient {
  background: linear-gradient(to bottom, rgba(0,0,0,0) 0%, rgba(0,0,0,0) 55%, rgba(10,10,15,0.85) 100%);
}

@media (max-width: 768px) {
  .sd-cover-wrap { padding: 0; }
  .sd-cover {
    border-radius: 0;
    height: 200px;
  }
}

/* === Info Bar === */
.sd-info-bar {
  position: relative;
  z-index: 2;
  margin-top: -110px;
  padding: 0 2rem;
  max-width: 1200px;
  margin-left: auto;
  margin-right: auto;
}

.sd-info-inner {
  display: flex;
  align-items: flex-end;
  gap: 1.25rem;
  flex-wrap: wrap;
}

.sd-avatar {
  width: 88px;
  height: 88px;
  border-radius: 20px;
  overflow: hidden;
  border: 4px solid var(--bg);
  flex-shrink: 0;
  background: var(--bg3);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
}

.sd-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.sd-avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--accent);
  color: #fff;
  font-size: 2rem;
  font-weight: 700;
}

.sd-identity {
  flex: 1;
  min-width: 0;
  padding-bottom: 4px;
}

.sd-name-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.sd-name {
  font-size: 1.5rem;
  font-weight: 800;
  color: var(--text);
  margin: 0;
  line-height: 1.3;
}

.sd-verified {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
}

.sd-platform-mark {
  display: inline-flex; align-items: center; justify-content: center;
  width: 24px; height: 24px; border-radius: 6px;
  text-decoration: none; transition: transform 0.15s, opacity 0.15s;
}
.sd-platform-mark:hover { transform: scale(1.15); opacity: 0.85; }
.sd-platform-mark.youtube { background: #FF0000; }
.sd-platform-mark.chzzk { background: #03C75A; }
.sd-platform-mark.soop { background: #0078ff; }

.sd-platform-icons {
  display: inline-flex;
  gap: 6px;
  margin-left: 4px;
}

.sd-plat-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--text3);
}

.sd-plat-dot.plat-youtube { background: #ff4444; }
.sd-plat-dot.plat-chzzk { background: #00d4aa; }
.sd-plat-dot.plat-soop { background: #4dabf7; }

.sd-stats {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-top: 6px;
}

.sd-stat {
  color: var(--text2);
  font-size: 0.875rem;
}

.sd-stat strong {
  color: var(--text);
  font-weight: 700;
  margin-left: 4px;
}

.sd-stat-sep {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--text3);
}

.sd-actions {
  display: flex;
  gap: 0.625rem;
  flex-shrink: 0;
  padding-bottom: 4px;
}

/* === Buttons === */
.sd-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.6rem 1.25rem;
  border-radius: 10px;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  text-decoration: none;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.sd-btn-outline {
  background: transparent;
  border: 1.5px solid var(--border);
  color: var(--text);
}

.sd-btn-outline:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: rgba(108, 99, 255, 0.08);
}

.sd-btn-primary {
  background: var(--gradient);
  border: none;
  color: #fff;
}

.sd-btn-primary:hover {
  opacity: 0.9;
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(108, 99, 255, 0.3);
}

.sd-btn-subscribed {
  background: rgba(0, 212, 170, 0.15);
  border: 1.5px solid rgba(0, 212, 170, 0.4);
  color: var(--accent3);
}

.sd-btn-subscribed:hover {
  background: rgba(0, 212, 170, 0.25);
}

/* === Body Grid === */
.sd-body {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 1.5rem;
  max-width: 1200px;
  margin: 2rem auto 0;
  padding: 0 2rem;
}

/* === Main Column === */
.sd-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}

.sd-card-title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--text);
  margin: 0 0 0.75rem;
}

.sd-bio-text {
  color: var(--text2);
  font-size: 0.9375rem;
  line-height: 1.7;
  margin: 0;
  white-space: pre-line;
}

.sd-schedule-image img {
  width: 100%;
  border-radius: 10px;
  border: 1px solid var(--border);
}

.sd-section {
  margin-bottom: 2rem;
}

.sd-section-title {
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--text);
  margin: 0 0 1rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--border);
}

/* Content Grid (2 columns) */
.sd-grid-2 {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
}

.sd-content-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
  overflow: hidden;
  text-decoration: none;
  color: var(--text);
  transition: all 0.2s ease;
}

.sd-content-card:hover {
  background: var(--card-hover);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
}

.sd-content-thumb {
  height: 140px;
  background: var(--bg3);
  background-size: cover;
  background-position: center;
  position: relative;
}

.sd-content-badge {
  position: absolute;
  top: 10px;
  left: 10px;
  background: var(--accent);
  color: #fff;
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 600;
}

.sd-content-info {
  padding: 0.875rem;
}

.sd-content-info h3 {
  font-size: 0.9375rem;
  font-weight: 700;
  margin: 0 0 0.375rem;
  color: var(--text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.sd-content-info p {
  font-size: 0.8125rem;
  color: var(--text2);
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* Clip Grid (3 columns) */
.sd-grid-3 {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
}

.sd-clip-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  text-decoration: none;
  color: var(--text);
  transition: all 0.2s ease;
}

.sd-clip-card:hover {
  background: var(--card-hover);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
}

.sd-clip-thumb {
  height: 110px;
  background: var(--bg3);
  background-size: cover;
  background-position: center;
  position: relative;
}

.sd-clip-chzzk {
  background: #0b0b0b;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sd-clip-chzzk-logo {
  width: 50px;
  height: 50px;
  border-radius: 10px;
  object-fit: contain;
}

.sd-clip-play {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(0,0,0,0.6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  transition: background 0.2s;
}
.sd-clip-card:hover .sd-clip-play {
  background: rgba(108,99,255,0.8);
}

.sd-clip-info {
  padding: 0.625rem 0.75rem;
}

.sd-clip-info h4 {
  font-size: 0.8125rem;
  font-weight: 600;
  margin: 0;
  color: var(--text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.sd-empty {
  text-align: center;
  padding: 2.5rem 1rem;
  color: var(--text3);
  font-size: 0.9375rem;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
}

.sd-empty-sm {
  color: var(--text3);
  font-size: 0.8125rem;
  text-align: center;
  padding: 0.75rem 0;
}

/* === Fan board entry (sidebar) === */
.sd-board-entry {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 18px;
  background: linear-gradient(135deg, rgba(108,99,255,0.12), rgba(255,107,157,0.08));
  border: 1px solid rgba(108,99,255,0.25);
  border-radius: 14px;
  text-decoration: none;
  color: var(--text);
  transition: all 0.2s;
}
.sd-board-entry:hover {
  transform: translateY(-2px);
  border-color: rgba(108,99,255,0.5);
  box-shadow: 0 8px 22px rgba(108,99,255,0.18);
}
.sd-board-entry-left { display: flex; align-items: center; gap: 12px; min-width: 0; }
.sd-board-entry-icon {
  width: 40px; height: 40px; border-radius: 11px;
  background: var(--gradient, linear-gradient(135deg, #6c63ff, #ff6b9d));
  display: flex; align-items: center; justify-content: center;
  font-size: 19px; flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(108,99,255,0.3);
}
.sd-board-entry-title {
  font-size: 14px; font-weight: 800; color: var(--text); margin-bottom: 2px;
}
.sd-board-entry-sub { font-size: 11.5px; color: var(--text2); }
.sd-board-entry-arrow {
  font-size: 18px; color: var(--accent); font-weight: 700;
  transition: transform 0.2s;
  flex-shrink: 0;
}
.sd-board-entry:hover .sd-board-entry-arrow { transform: translateX(4px); }

/* === Sidebar === */
.sd-sidebar {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.sd-sidebar-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 18px;
}

.sd-sidebar-title {
  font-size: 0.9375rem;
  font-weight: 700;
  color: var(--text);
  margin: 0 0 0.875rem;
}

/* Platform Buttons */
.sd-platform-btns {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.sd-platform-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.6rem 1rem;
  border-radius: 10px;
  font-size: 0.875rem;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.2s ease;
  border: 1px solid var(--border);
  color: var(--text);
  background: var(--bg3);
}

.sd-platform-btn:hover {
  transform: translateY(-1px);
}

.sd-platform-btn.plat-youtube {
  border-color: rgba(255, 68, 68, 0.3);
  color: #ff6666;
}

.sd-platform-btn.plat-youtube:hover {
  background: rgba(255, 68, 68, 0.1);
}

.sd-platform-btn.plat-chzzk {
  border-color: rgba(0, 212, 170, 0.3);
  color: var(--accent3);
}

.sd-platform-btn.plat-chzzk:hover {
  background: rgba(0, 212, 170, 0.1);
}

.sd-platform-btn.plat-soop {
  border-color: rgba(77, 171, 247, 0.3);
  color: #4dabf7;
}

.sd-platform-btn.plat-soop:hover {
  background: rgba(77, 171, 247, 0.1);
}

/* Schedule */
.sd-schedule-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.sd-schedule-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 0.75rem;
  background: var(--bg3);
  border-radius: 8px;
}

.sd-schedule-day {
  font-weight: 700;
  font-size: 0.875rem;
  color: var(--accent);
  min-width: 24px;
  text-align: center;
}

.sd-schedule-time {
  font-size: 0.8125rem;
  color: var(--text2);
}

.sd-schedule-text {
  color: var(--text2);
  font-size: 0.875rem;
  line-height: 1.6;
  white-space: pre-line;
}

/* Upcoming */
.sd-upcoming-text {
  color: var(--text2);
  font-size: 0.875rem;
  line-height: 1.6;
  margin: 0;
}

/* === Not Found === */
.sd-not-found {
  text-align: center;
  padding: 8rem 1rem;
}

.sd-not-found h1 {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text);
  margin: 0 0 0.5rem;
}

.sd-not-found p {
  color: var(--text2);
  margin: 0 0 1.5rem;
}

/* === Responsive === */
@media (max-width: 1024px) {
  .sd-body {
    grid-template-columns: 1fr;
  }

  .sd-sidebar {
    flex-direction: row;
    flex-wrap: wrap;
  }

  .sd-sidebar-card {
    flex: 1;
    min-width: 250px;
  }
}

@media (max-width: 768px) {
  .sd-cover {
    height: 180px;
  }

  .sd-info-bar {
    padding: 0 1rem;
    margin-top: -100px;
  }

  .sd-info-inner {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .sd-name-row {
    justify-content: center;
  }

  .sd-stats {
    justify-content: center;
  }

  .sd-actions {
    width: 100%;
    justify-content: center;
  }

  .sd-body {
    padding: 0 1rem;
    gap: 1rem;
  }

  .sd-grid-2 {
    grid-template-columns: 1fr;
  }

  .sd-grid-3 {
    grid-template-columns: repeat(2, 1fr);
  }

  .sd-sidebar {
    flex-direction: column;
  }

  .sd-sidebar-card {
    min-width: unset;
  }
}

@media (max-width: 480px) {
  .sd-grid-3 {
    grid-template-columns: 1fr;
  }
}
</style>
