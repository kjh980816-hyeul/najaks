<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import api from '@/api'
import Pagination from '@/components/Pagination.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const allContents = ref([])
const activeFilter = ref('all')
const loading = ref(false)

const statusFilters = [
  { key: 'all', label: '전체' },
  { key: 'ongoing', label: '진행중' },
  { key: 'closing', label: '마감임박' },
  { key: 'ended', label: '마감' },
]
const categoryFilters = [
  { key: 'GAME', label: '게임' },
  { key: 'MUSIC', label: '음악' },
  { key: 'MUKBANG', label: '먹방' },
  { key: 'TALK', label: '일상' },
  { key: 'ETC', label: '기타' },
]
const filters = [...statusFilters, ...categoryFilters]

const thumbGradients = [
  'linear-gradient(135deg, #1a1060, #6c63ff)',
  'linear-gradient(135deg, #0a1a15, #00d4aa)',
  'linear-gradient(135deg, #1a0a15, #ff6b9d)',
  'linear-gradient(135deg, #1a1500, #ffb400)',
  'linear-gradient(135deg, #10001a, #cc44ff)',
  'linear-gradient(135deg, #001a1a, #0077ff)',
]

const emojis = ['🎨', '🏆', '🎵', '🍜', '🎭', '🖥️']
const avatarClasses = ['avatar-bg1', 'avatar-bg3', 'avatar-bg2', 'avatar-bg4', 'avatar-bg5', 'avatar-bg1']
const categoryMap = { GAME: '게임', MUSIC: '음악', MUKBANG: '먹방', TALK: '일상', ETC: '기타' }

async function fetchContents() {
  loading.value = true
  try {
    const { data } = await api.get('/public/contents')
    allContents.value = data.data || []
  } catch {
    allContents.value = []
  }
  loading.value = false
}

function sortByCreatedAt(list) {
  return [...list].sort((a, b) => {
    const ca = new Date(a.createdAt || 0).getTime()
    const cb = new Date(b.createdAt || 0).getTime()
    if (cb !== ca) return cb - ca
    return (b.id || 0) - (a.id || 0)
  })
}

const contents = computed(() => {
  const f = activeFilter.value
  const kw = (route.query.q || '').toString().toLowerCase().trim()
  let list = allContents.value

  if (kw) {
    list = list.filter(it =>
      (it.title || '').toLowerCase().includes(kw) ||
      (it.description || '').toLowerCase().includes(kw)
    )
  }

  if (f === 'ongoing') {
    list = list.filter(it => it.status !== 'CLOSED' && (!it.endDate || getDaysLeft(it.endDate) !== null))
  } else if (f === 'closing') {
    list = list.filter(it => {
      const d = getDaysLeft(it.endDate)
      return d !== null && d <= 3 && it.status !== 'CLOSED'
    })
  } else if (f === 'ended') {
    list = list.filter(it => it.status === 'CLOSED' || (it.endDate && getDaysLeft(it.endDate) === null))
  } else if (f !== 'all') {
    list = list.filter(it => it.category === f || (it.tags || []).includes(f))
  }

  return sortByCreatedAt(list)
})

function setFilter(key) {
  activeFilter.value = key
}

const currentPage = ref(1)
const pageSize = 10
const pagedContents = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return contents.value.slice(start, start + pageSize)
})
const totalPages = computed(() => Math.max(Math.ceil(contents.value.length / pageSize), 1))
watch(contents, () => { currentPage.value = 1 })
function onPageChange(p) {
  currentPage.value = p
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function getDaysLeft(endDate) {
  if (!endDate) return null
  const end = new Date(endDate)
  const now = new Date()
  const diff = Math.ceil((end - now) / (1000 * 60 * 60 * 24))
  if (diff < 0) return null
  return diff
}

// 1분마다 갱신되는 "지금" 타임스탬프 — 마감 카운트다운 실시간화용
const nowTick = ref(Date.now())
let tickTimer = null
onMounted(() => {
  tickTimer = setInterval(() => { nowTick.value = Date.now() }, 60_000)
})
onUnmounted(() => { if (tickTimer) clearInterval(tickTimer) })

function getStatusLabel(item) {
  if (item.status === 'CLOSED' || item.status === 'ENDED') {
    return { label: '마감', class: 'status-end' }
  }
  if (!item.endDate) return { label: '상시', class: 'status-ongoing' }
  const diff = new Date(item.endDate).getTime() - nowTick.value
  if (diff <= 0) return { label: '마감', class: 'status-end' }
  const d = Math.floor(diff / 86400000)
  const h = Math.floor((diff % 86400000) / 3600000)
  const m = Math.floor((diff % 3600000) / 60000)
  if (d >= 2) return { label: `D-${d}`, class: 'status-ongoing' }
  if (d === 1) return { label: `D-1 · ${h}시간 남음`, class: 'status-closing' }
  if (h >= 1) return { label: `${h}시간 ${m}분 남음`, class: 'status-closing' }
  return { label: `${m}분 남음 ⚡`, class: 'status-closing' }
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).replace(/\. /g, '.').replace(/\.$/, '')
}

function openUrl(url) {
  if (url) window.open(url, '_blank')
}

onMounted(() => { fetchContents() })
</script>

<template>
  <div class="content-promo-wrap">
    <div class="promo-header">
      <div>
        <h1>🎯 스트리머 이벤트 · 컨텐츠 홍보</h1>
        <p>스트리머가 직접 등록한 이벤트와 컨텐츠 홍보를 확인하고 참여하세요.</p>
      </div>
      <button
        v-if="authStore.user?.role === 'STREAMER' || authStore.user?.role === 'ADMIN'"
        class="btn-register"
        @click="router.push('/contents/create')"
      >+ 컨텐츠 등록</button>
    </div>

    <div class="promo-filter">
      <button
        v-for="f in filters" :key="f.key"
        class="filter-btn" :class="{ active: activeFilter === f.key }"
        @click="setFilter(f.key)"
      >{{ f.label }}</button>
    </div>

    <div class="promo-list" v-if="contents.length > 0">
      <div
        v-for="(item, i) in pagedContents" :key="item.id"
        class="promo-row"
        @click="router.push(`/contents/${item.id}`)"
      >
        <div class="promo-row-thumb" :style="{ background: item.thumbnailUrl ? `url(${item.thumbnailUrl}) center/cover` : thumbGradients[i % 6] }">
          <span v-if="!item.thumbnailUrl" class="thumb-emoji">{{ emojis[i % 6] }}</span>
        </div>

        <div class="promo-row-main">
          <div class="promo-row-top">
            <span :class="['row-status', getStatusLabel(item).class]">{{ getStatusLabel(item).label }}</span>
            <span v-if="item.category" class="row-tag">{{ categoryMap[item.category] || item.category }}</span>
            <span v-if="item.hostName" class="row-host">주최 · {{ item.hostName }}</span>
          </div>

          <h3 class="promo-row-title">{{ item.title }}</h3>
          <p class="promo-row-desc">{{ item.description }}</p>

          <div class="promo-row-meta">
            <span class="meta-item"><span class="meta-label">스트리머</span>{{ item.streamerNickname }}</span>
            <span v-if="item.prize" class="meta-item meta-prize"><span class="meta-label">🏆 상금</span>{{ item.prize }}</span>
            <span v-if="item.recruitCount" class="meta-item"><span class="meta-label">👥 모집</span>{{ item.recruitCount }}</span>
            <span v-if="item.followerUnlimited || item.followerCount != null" class="meta-item">
              <span class="meta-label">⭐ 팔로워</span>{{ item.followerUnlimited ? '제한 없음' : `${item.followerCount}명 이상` }}
            </span>
          </div>
        </div>

        <div class="promo-row-side">
          <div class="promo-row-date">
            <div v-if="item.endDate" class="date-label">마감</div>
            <div class="date-val">{{ item.endDate ? formatDate(item.endDate) : '상시' }}</div>
          </div>
          <button
            v-if="item.applyLink && getStatusLabel(item).class === 'status-ongoing'"
            class="row-cta"
            @click.stop="openUrl(item.applyLink)"
          >신청하기</button>
          <span v-else-if="getStatusLabel(item).class === 'status-end'" class="row-cta disabled">마감</span>
          <span v-else class="row-cta">자세히</span>
        </div>
      </div>
    </div>

    <div v-else-if="!loading" class="empty-state">
      등록된 컨텐츠가 없습니다
    </div>

    <div v-if="loading" style="display:flex;justify-content:center;padding:40px;">
      <div class="spinner"></div>
    </div>

    <Pagination
      :current-page="currentPage"
      :total-pages="totalPages"
      @change="onPageChange"
    />
  </div>
</template>

<style scoped>
.content-promo-wrap { padding: 32px 40px; }

.promo-header {
  margin-bottom: 28px; display: flex; justify-content: space-between; align-items: flex-start;
}
.promo-header h1 { font-size: 28px; font-weight: 900; margin-bottom: 8px; }
.promo-header p { font-size: 14px; color: var(--text2); }

.btn-register {
  padding: 10px 20px; border: none; border-radius: 10px; font-size: 14px; font-weight: 700;
  background: var(--gradient); color: #fff; cursor: pointer; transition: opacity 0.2s;
  white-space: nowrap; font-family: 'Pretendard', sans-serif;
}
.btn-register:hover { opacity: 0.9; }

.promo-filter {
  display: flex; gap: 8px; margin-bottom: 28px; flex-wrap: wrap;
}

.content-promo-wrap { max-width: 1100px; margin: 0 auto; }

.promo-list {
  display: flex; flex-direction: column; gap: 12px;
}

.promo-row {
  display: grid;
  grid-template-columns: 180px 1fr 140px;
  gap: 18px;
  padding: 14px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s;
  align-items: center;
}

.promo-row:hover {
  border-color: rgba(108,99,255,0.5);
  background: var(--card-hover, rgba(108,99,255,0.04));
  transform: translateY(-1px);
}

.promo-row-thumb {
  width: 180px; height: 110px;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 40px;
  flex-shrink: 0;
  background-size: cover;
  background-position: center;
}

.thumb-emoji { font-size: 40px; }

.promo-row-main { min-width: 0; }

.promo-row-top {
  display: flex; flex-wrap: wrap; align-items: center; gap: 8px;
  margin-bottom: 6px;
}

.row-status {
  padding: 3px 10px; border-radius: 6px;
  font-size: 11px; font-weight: 800; letter-spacing: 0.2px;
}

.status-ongoing { background: rgba(0,212,170,0.2); color: var(--accent3, #00d4aa); }
.status-closing { background: rgba(255,107,107,0.18); color: #ff8080; animation: pulse-closing 1.6s ease-in-out infinite; }
.status-end { background: rgba(255,255,255,0.08); color: var(--text3); }

@keyframes pulse-closing {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.65; }
}

.row-tag {
  padding: 3px 9px; border-radius: 6px;
  background: rgba(108,99,255,0.15); color: var(--accent);
  font-size: 11px; font-weight: 700;
}

.row-host {
  font-size: 11px; color: var(--text3);
  padding-left: 2px;
}

.promo-row-title {
  font-size: 16px; font-weight: 800; color: var(--text);
  margin: 0 0 4px; line-height: 1.35;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

.promo-row-desc {
  font-size: 13px; color: var(--text2); line-height: 1.5;
  margin: 0 0 10px;
  display: -webkit-box; -webkit-line-clamp: 1; -webkit-box-orient: vertical; overflow: hidden;
}

.promo-row-meta {
  display: flex; flex-wrap: wrap; gap: 12px;
  font-size: 12px; color: var(--text2);
}

.meta-item {
  display: inline-flex; align-items: center; gap: 6px;
}

.meta-item.meta-prize {
  color: #ffb400; font-weight: 700;
}

.meta-label {
  color: var(--text3); font-weight: 600;
  font-size: 11px;
}

.promo-row-side {
  display: flex; flex-direction: column; align-items: stretch;
  gap: 8px;
  border-left: 1px solid var(--border);
  padding-left: 16px;
}

.promo-row-date { text-align: center; }

.date-label {
  font-size: 10px; color: var(--text3);
  font-weight: 700; letter-spacing: 0.5px;
  text-transform: uppercase;
}

.date-val {
  font-size: 13px; color: var(--text);
  font-weight: 700; margin-top: 2px;
}

.row-cta {
  display: flex; align-items: center; justify-content: center;
  background: var(--gradient); color: #fff;
  border: none; border-radius: 8px;
  padding: 9px 14px;
  font-size: 12px; font-weight: 800;
  cursor: pointer; transition: opacity 0.2s;
}

.row-cta:hover:not(.disabled) { opacity: 0.88; }
.row-cta.disabled { background: var(--bg3); color: var(--text3); cursor: not-allowed; }

.empty-state {
  padding: 60px; text-align: center; color: var(--text3); font-size: 14px;
  background: var(--card); border: 1px solid var(--border); border-radius: 14px;
}

@media (max-width: 768px) {
  .content-promo-wrap { padding: 20px; }
  .promo-row {
    grid-template-columns: 120px 1fr;
    gap: 12px;
  }
  .promo-row-thumb { width: 120px; height: 80px; font-size: 28px; }
  .promo-row-side {
    grid-column: 1 / -1;
    flex-direction: row;
    border-left: none;
    border-top: 1px solid var(--border);
    padding-left: 0;
    padding-top: 10px;
    justify-content: space-between;
    align-items: center;
  }
  .promo-row-date { text-align: left; }
  .row-cta { padding: 8px 18px; }
}

@media (max-width: 480px) {
  .content-promo-wrap { padding: 14px; }
  .promo-filter { gap: 6px; }
  .filter-btn { padding: 6px 12px; font-size: 11px; }
  .promo-row-title { font-size: 14px; }
  .promo-row-meta { gap: 8px; font-size: 11px; }
}
</style>
