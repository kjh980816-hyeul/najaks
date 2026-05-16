<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import api from '@/api'

const router = useRouter()
const authStore = useAuthStore()
const canApplyStreamer = computed(() => {
  const role = authStore.user?.role
  return !role || role === 'FAN'
})

const contents = ref([])
const schedules = ref([])
const popularClips = ref([])
const communityPosts = ref([])
const banners = ref([])
const liveStreamers = ref([])

async function fetchHomeData() {
  try {
    const { data } = await api.get('/public/contents')
    const list = data.data || []
    contents.value = [...list].sort((a, b) => {
      const aIsAdmin = a.streamerId === 1
      const bIsAdmin = b.streamerId === 1
      if (aIsAdmin !== bIsAdmin) return aIsAdmin ? 1 : -1
      const ca = new Date(a.createdAt || 0).getTime()
      const cb = new Date(b.createdAt || 0).getTime()
      if (cb !== ca) return cb - ca
      return (b.id || 0) - (a.id || 0)
    })
  } catch { contents.value = [] }

  try {
    const { data } = await api.get('/public/schedules')
    const raw = data.data || []
    schedules.value = raw.slice(0, 3).map(s => {
      const dt = new Date(s.scheduledAt)
      const now = new Date()
      const isToday = dt.toDateString() === now.toDateString()
      const tomorrow = new Date(now); tomorrow.setDate(now.getDate() + 1)
      const isTomorrow = dt.toDateString() === tomorrow.toDateString()
      const timeStr = dt.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
      let label = (dt.getMonth() + 1) + '/' + dt.getDate() + ' '
      if (isToday) label = '오늘 '
      else if (isTomorrow) label = '내일 '
      return {
        id: s.id,
        streamerId: s.streamerId,
        streamer: s.streamerNickname,
        title: s.title,
        imageUrl: s.imageUrl,
        time: label + timeStr,
        emoji: s.category === 'GAME' ? '🎮' : s.category === 'MUSIC' ? '🎤' : '🌙'
      }
    })
  } catch { schedules.value = [] }

  try {
    const { data } = await api.get('/public/clips')
    popularClips.value = (data.data || []).slice(0, 10).map(c => ({
      id: c.id, title: c.title, streamer: c.streamerNickname,
      views: formatViews(c.viewCount), thumbnail: getClipThumbnail(c.url, c.thumbnailUrl), url: c.url
    }))
  } catch { popularClips.value = [] }

  try {
    const { data } = await api.get('/public/posts/popular')
    communityPosts.value = (data.data || []).slice(0, 3).map(p => ({
      id: p.id, title: p.title, author: p.authorNickname,
      likes: p.likeCount, comments: p.commentCount
    }))
  } catch { communityPosts.value = [] }

  try {
    const { data } = await api.get('/public/banners')
    banners.value = data.data || []
  } catch { banners.value = [] }
}

function formatViews(count) {
  if (!count) return '0'
  if (count >= 10000) return (count / 10000).toFixed(1) + '만'
  if (count >= 1000) return (count / 1000).toFixed(1) + '천'
  return count.toString()
}

function getClipThumbnail(url, thumbnailUrl) {
  if (thumbnailUrl) return thumbnailUrl
  if (!url) return null
  const ytMatch = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/|youtube\.com\/shorts\/)([a-zA-Z0-9_-]{11})/)
  if (ytMatch) return `https://img.youtube.com/vi/${ytMatch[1]}/hqdefault.jpg`
  if (url.includes('chzzk.naver.com')) return '/chzzk-logo.png'
  return null
}

function openClipUrl(clip) {
  if (!clip?.url) return
  window.open(clip.url, '_blank')
  if (clip.id) {
    api.post(`/public/clips/${clip.id}/view`).catch(() => {})
  }
}

function getCategoryLabel(cat) {
  const map = { GAME: '게임', MUSIC: '음악', MUKBANG: '먹방', TALK: '토크', ETC: '기타' }
  return map[cat] || cat || '컨텐츠'
}

const thumbGradients = [
  'linear-gradient(135deg, #1a1060, #6c63ff)',
  'linear-gradient(135deg, #1a0a15, #ff6b9d)',
  'linear-gradient(135deg, #0a1a15, #00d4aa)',
  'linear-gradient(135deg, #1a1500, #ffb400)',
]

const emojis = ['🎨', '🎵', '🎮', '🍜']
const avatarClasses = ['avatar-bg1', 'avatar-bg2', 'avatar-bg3', 'avatar-bg4']

const clipGradients = [
  ['#10001a', '#cc44ff'],
  ['#001a1a', '#0077ff'],
  ['#1a0810', '#ff5b8a'],
  ['#0a1a08', '#39c46b'],
]

/* ─── Banner Scroll ─── */
const bannerTrack = ref(null)
const bannerScrollPos = ref(0)
const canScrollLeft = ref(false)
const canScrollRight = ref(false)

function updateBannerScroll() {
  const el = bannerTrack.value
  if (!el) return
  bannerScrollPos.value = el.scrollLeft
  canScrollLeft.value = el.scrollLeft > 4
  canScrollRight.value = el.scrollLeft < el.scrollWidth - el.clientWidth - 4
}

function scrollBanners(dir) {
  const el = bannerTrack.value
  if (!el) return
  // 카드 1장 너비 + gap(12px) 기준으로 스크롤
  const card = el.querySelector('.banner-card')
  const step = card ? card.offsetWidth + 12 : 252
  el.scrollBy({ left: dir * step, behavior: 'smooth' })
}

// 마우스 드래그로 스크롤 (모멘텀 포함)
const isDragging = ref(false)
const dragStartX = ref(0)
const dragScrollLeft = ref(0)
const dragMoved = ref(false)
let dragLastX = 0
let dragLastTime = 0
let dragVelocity = 0
let momentumRaf = null

function cancelMomentum() {
  if (momentumRaf) {
    cancelAnimationFrame(momentumRaf)
    momentumRaf = null
  }
}

function startDrag(e) {
  const el = bannerTrack.value
  if (!el) return
  cancelMomentum()
  pauseAutoScroll()
  isDragging.value = true
  dragMoved.value = false
  dragStartX.value = e.pageX - el.offsetLeft
  dragScrollLeft.value = el.scrollLeft
  dragLastX = e.pageX
  dragLastTime = performance.now()
  dragVelocity = 0
  el.style.cursor = 'grabbing'
  el.style.scrollSnapType = 'none'
}

function onDrag(e) {
  if (!isDragging.value) return
  e.preventDefault()
  const el = bannerTrack.value
  const x = e.pageX - el.offsetLeft
  const walk = x - dragStartX.value
  if (Math.abs(walk) > 5) dragMoved.value = true
  el.scrollLeft = dragScrollLeft.value - walk

  // 속도 계산 (px/ms)
  const now = performance.now()
  const dt = now - dragLastTime
  if (dt > 0) {
    dragVelocity = (dragLastX - e.pageX) / dt
  }
  dragLastX = e.pageX
  dragLastTime = now
}

function endDrag() {
  if (!isDragging.value) return
  isDragging.value = false
  const el = bannerTrack.value
  if (!el) return
  el.style.cursor = 'grab'

  // 모멘텀 적용 (속도가 충분할 때만)
  if (Math.abs(dragVelocity) > 0.05) {
    let v = dragVelocity * 16 // px/frame 근사
    const decay = 0.92
    const step = () => {
      if (!el) return
      el.scrollLeft += v
      v *= decay
      if (Math.abs(v) > 0.5) {
        momentumRaf = requestAnimationFrame(step)
      } else {
        momentumRaf = null
        el.style.scrollSnapType = 'x mandatory'
        resumeAutoScroll()
      }
    }
    momentumRaf = requestAnimationFrame(step)
  } else {
    el.style.scrollSnapType = 'x mandatory'
    resumeAutoScroll()
  }
}

function onBannerClick(e) {
  if (dragMoved.value) e.preventDefault()
}

/* ─── 자동 슬라이드 ─── */
let autoScrollTimer = null
const autoScrollPaused = ref(false)
const AUTO_SCROLL_INTERVAL = 4500

function autoScrollTick() {
  const el = bannerTrack.value
  if (!el || autoScrollPaused.value || isDragging.value) return
  if (banners.value.length <= 1) return

  const card = el.querySelector('.banner-card')
  const step = card ? card.offsetWidth + 12 : 252
  const maxScroll = el.scrollWidth - el.clientWidth

  if (el.scrollLeft + step > maxScroll - 4) {
    // 끝에 도달하면 처음으로
    el.scrollTo({ left: 0, behavior: 'smooth' })
  } else {
    el.scrollBy({ left: step, behavior: 'smooth' })
  }
}

function startAutoScroll() {
  stopAutoScroll()
  autoScrollTimer = setInterval(autoScrollTick, AUTO_SCROLL_INTERVAL)
}

function stopAutoScroll() {
  if (autoScrollTimer) {
    clearInterval(autoScrollTimer)
    autoScrollTimer = null
  }
}

function pauseAutoScroll() { autoScrollPaused.value = true }
function resumeAutoScroll() {
  autoScrollPaused.value = false
}

function handleMouseLeave() {
  if (isDragging.value) {
    endDrag()
  } else {
    resumeAutoScroll()
  }
}

watch(banners, () => {
  nextTick(() => {
    updateBannerScroll()
    if (banners.value.length > 1) startAutoScroll()
  })
})

/* ─── Content Scroll (컨텐츠 홍보 가로 스크롤) ─── */
const contentScroll = ref(null)
const canContentLeft = ref(false)
const canContentRight = ref(false)
const contentDragging = ref(false)
const contentDragMoved = ref(false)
let contentDragStartX = 0
let contentDragScrollLeft = 0

function updateContentScroll() {
  const el = contentScroll.value
  if (!el) return
  canContentLeft.value = el.scrollLeft > 4
  canContentRight.value = el.scrollLeft < el.scrollWidth - el.clientWidth - 4
}

function scrollContents(dir) {
  const el = contentScroll.value
  if (!el) return
  const card = el.querySelector('.content-card')
  const step = card ? card.offsetWidth + 14 : 260
  el.scrollBy({ left: dir * step * 2, behavior: 'smooth' })
}

function startContentDrag(e) {
  const el = contentScroll.value
  if (!el) return
  contentDragging.value = true
  contentDragMoved.value = false
  contentDragStartX = e.pageX - el.offsetLeft
  contentDragScrollLeft = el.scrollLeft
  el.style.cursor = 'grabbing'
}

function onContentDrag(e) {
  if (!contentDragging.value) return
  e.preventDefault()
  const el = contentScroll.value
  const x = e.pageX - el.offsetLeft
  const walk = x - contentDragStartX
  if (Math.abs(walk) > 5) contentDragMoved.value = true
  el.scrollLeft = contentDragScrollLeft - walk
}

function endContentDrag() {
  if (!contentDragging.value) return
  contentDragging.value = false
  const el = contentScroll.value
  if (el) el.style.cursor = 'grab'
}

function onContentClick(e, id) {
  if (contentDragMoved.value) { e.preventDefault(); return }
  router.push(`/contents/${id}`)
}

watch(contents, () => nextTick(updateContentScroll))

/* ─── Clip Scroll (인기 클립 가로 스크롤) ─── */
const clipScroll = ref(null)
const canClipLeft = ref(false)
const canClipRight = ref(false)
const clipDragging = ref(false)
const clipDragMoved = ref(false)
let clipDragStartX = 0
let clipDragScrollLeft = 0

function updateClipScroll() {
  const el = clipScroll.value
  if (!el) return
  canClipLeft.value = el.scrollLeft > 4
  canClipRight.value = el.scrollLeft < el.scrollWidth - el.clientWidth - 4
}

function scrollClips(dir) {
  const el = clipScroll.value
  if (!el) return
  const card = el.querySelector('.clip-card')
  const step = card ? card.offsetWidth + 14 : 260
  el.scrollBy({ left: dir * step * 2, behavior: 'smooth' })
}

function startClipDrag(e) {
  const el = clipScroll.value
  if (!el) return
  clipDragging.value = true
  clipDragMoved.value = false
  clipDragStartX = e.pageX - el.offsetLeft
  clipDragScrollLeft = el.scrollLeft
  el.style.cursor = 'grabbing'
}

function onClipDrag(e) {
  if (!clipDragging.value) return
  e.preventDefault()
  const el = clipScroll.value
  const x = e.pageX - el.offsetLeft
  const walk = x - clipDragStartX
  if (Math.abs(walk) > 5) clipDragMoved.value = true
  el.scrollLeft = clipDragScrollLeft - walk
}

function endClipDrag() {
  if (!clipDragging.value) return
  clipDragging.value = false
  const el = clipScroll.value
  if (el) el.style.cursor = 'grab'
}

function onClipCardClick(e, clip) {
  if (clipDragMoved.value) { e.preventDefault(); return }
  openClipUrl(clip)
}

watch(popularClips, () => nextTick(updateClipScroll))

async function fetchLiveStreamers() {
  try {
    const { data } = await api.get('/public/streamers/live?oauthOnly=true')
    liveStreamers.value = Array.isArray(data) ? data : []
  } catch { liveStreamers.value = [] }
}

let liveTimer = null
onMounted(() => {
  fetchHomeData()
  fetchLiveStreamers()
  // 라이브 목록 2분마다 갱신
  liveTimer = setInterval(fetchLiveStreamers, 120000)
})
onUnmounted(() => {
  cancelMomentum()
  stopAutoScroll()
  if (liveTimer) clearInterval(liveTimer)
})
</script>

<template>
  <div class="home-page">
    <!-- Hero -->
    <div class="hero">
      <div class="hero-bg"></div>
      <div class="hero-grid"></div>
      <div class="hero-content">
        <div class="hero-badge">🔥 나작스 공식 플랫폼</div>
        <h1>스트리머와 팬이<br><span>함께 만드는</span> 공간</h1>
        <p>이벤트 참가, 방송 예고 확인, 클립 감상까지<br>좋아하는 스트리머와 더 가깝게</p>
        <div class="hero-actions">
          <router-link to="/contents" class="btn btn-primary">이벤트 둘러보기</router-link>
          <router-link v-if="canApplyStreamer" to="/streamer/apply" class="btn btn-outline">스트리머 신청</router-link>
        </div>
      </div>
      <div class="hero-right">
        <div class="event-card-mini" v-for="(item, i) in contents.slice(0, 2)" :key="'hero-' + i" @click="router.push(`/contents/${item.id}`)">
          <div class="tag">🎯 {{ getCategoryLabel(item.category) }}</div>
          <h4>{{ item.title }}</h4>
          <div class="meta">{{ item.streamerNickname }}</div>
        </div>
        <div class="event-card-mini" v-if="schedules.length > 0" @click="router.push('/streamers')">
          <div class="tag">📅 방송 예고</div>
          <h4>{{ schedules[0]?.title }}</h4>
          <div class="meta">{{ schedules[0]?.streamer }} · {{ schedules[0]?.time }}</div>
        </div>
      </div>
    </div>

    <!-- Ad Banners (치지직 스타일) -->
    <div class="ad-banner-strip" v-if="banners.length > 0">
      <div class="ad-banner-container">
        <button class="banner-arrow banner-arrow-left" v-if="canScrollLeft" @click="scrollBanners(-1)">‹</button>
        <div class="banner-track" ref="bannerTrack" @scroll="updateBannerScroll"
             @mousedown="startDrag" @mousemove="onDrag" @mouseup="endDrag"
             @mouseenter="pauseAutoScroll" @mouseleave="handleMouseLeave">
          <a
            v-for="banner in banners"
            :key="banner.id"
            :href="banner.linkUrl"
            target="_blank"
            class="banner-card"
            @click="onBannerClick($event)"
          >
            <div class="banner-card-img" v-if="banner.imageUrl">
              <img :src="banner.imageUrl" :alt="banner.title" loading="lazy" />
            </div>
            <div class="banner-card-img banner-card-placeholder" v-else>
              <span>AD</span>
            </div>
            <div class="banner-card-title">{{ banner.title }}</div>
          </a>
        </div>
        <button class="banner-arrow banner-arrow-right" v-if="canScrollRight" @click="scrollBanners(1)">›</button>
      </div>
    </div>

    <!-- 🔴 라이브 중인 스트리머 (컨텐츠 홍보 위, 작은 미니 카드) -->
    <section class="live-now-mini" v-if="liveStreamers.length > 0">
      <div class="live-mini-header">
        <span class="live-mini-title"><span class="live-dot"></span>지금 라이브</span>
        <span class="live-mini-count">{{ liveStreamers.length }}명</span>
      </div>
      <div class="live-mini-scroll">
        <a
          v-for="s in liveStreamers"
          :key="s.streamerId"
          class="live-mini-card"
          :href="s.chzzkUrl || '#'"
          target="_blank"
          rel="noopener"
          :title="`${s.nickname} · ${s.liveTitle || ''}`"
        >
          <div class="live-mini-avatar-wrap">
            <img :src="s.profileImage || '/default-avatar.png'" :alt="s.nickname" class="live-mini-avatar" />
            <span class="live-mini-badge">LIVE</span>
          </div>
          <div class="live-mini-info">
            <div class="live-mini-nick">{{ s.nickname }}</div>
            <div v-if="s.liveTitle" class="live-mini-live-title">{{ s.liveTitle }}</div>
            <div class="live-mini-meta">
              <span v-if="s.viewerCount != null">👁 {{ s.viewerCount.toLocaleString() }}</span>
              <span v-else-if="s.liveCategory" class="live-mini-cat">{{ s.liveCategory }}</span>
            </div>
          </div>
        </a>
      </div>
    </section>

    <!-- 스트리머 컨텐츠 -->
    <div class="section">
      <div class="section-header">
        <div class="section-title"><div class="dot"></div>🎯 스트리머 컨텐츠</div>
        <router-link to="/contents" class="see-all">전체 보기 →</router-link>
      </div>
      <div v-if="contents.length > 0" class="content-scroll-wrap">
        <button class="content-arrow content-arrow-left" v-if="canContentLeft" @click="scrollContents(-1)">‹</button>
        <div
          class="content-scroll"
          ref="contentScroll"
          @scroll="updateContentScroll"
          @mousedown="startContentDrag"
          @mousemove="onContentDrag"
          @mouseup="endContentDrag"
          @mouseleave="endContentDrag"
        >
          <div
            v-for="(item, i) in contents"
            :key="item.id"
            class="content-card"
            @click="onContentClick($event, item.id)"
          >
            <div class="content-card-thumb" :style="{ background: item.thumbnailUrl ? `url(${item.thumbnailUrl}) center/cover` : thumbGradients[i % 4] }">
              <span v-if="!item.thumbnailUrl" class="thumb-emoji">{{ emojis[i % 4] }}</span>
            </div>
            <div class="content-card-body">
              <span class="card-tag tag-event">{{ getCategoryLabel(item.category) }}</span>
              <div class="card-title">{{ item.title }}</div>
              <div class="card-meta">
                <span class="avatar-sm" :class="avatarClasses[i % 4]">{{ emojis[i % 4] }}</span>
                {{ item.streamerNickname }}
              </div>
            </div>
          </div>
        </div>
        <button class="content-arrow content-arrow-right" v-if="canContentRight" @click="scrollContents(1)">›</button>
      </div>
      <div v-else class="empty-state">아직 등록된 컨텐츠가 없습니다</div>
    </div>

    <div class="divider"></div>

    <!-- 방송 예고 + 인기 클립 (2열) -->
    <div class="section two-col">
      <div>
        <div class="section-header">
          <div class="section-title"><div class="dot" style="background:var(--accent3)"></div>📅 방송 예고</div>
          <router-link to="/streamers" class="see-all">전체 보기 →</router-link>
        </div>
        <div class="broadcast-feed" v-if="schedules.length > 0">
          <div class="broadcast-item" v-for="item in schedules" :key="item.id" @click="item.streamerId ? router.push(`/streamers/${item.streamerId}`) : null" style="cursor:pointer;">
            <img v-if="item.imageUrl" :src="item.imageUrl" alt="" class="broadcast-thumb" />
            <div v-else class="broadcast-avatar" :class="item.emoji === '🎮' ? 'avatar-bg1' : item.emoji === '🎤' ? 'avatar-bg2' : 'avatar-bg3'">{{ item.emoji }}</div>
            <div class="broadcast-info">
              <h4>{{ item.streamer }}</h4>
              <p>{{ item.title }}</p>
            </div>
            <div class="broadcast-time">{{ item.time }}</div>
          </div>
        </div>
        <div v-else class="empty-state">예정된 방송이 없습니다</div>
      </div>

      <div>
        <div class="section-header">
          <div class="section-title"><div class="dot" style="background:var(--accent2)"></div>🎥 인기 클립</div>
          <router-link to="/streamers" class="see-all">전체 보기 →</router-link>
        </div>
        <div v-if="popularClips.length > 0" class="clip-scroll-wrap">
          <button class="content-arrow content-arrow-left" v-if="canClipLeft" @click="scrollClips(-1)">‹</button>
          <div
            class="clip-scroll"
            ref="clipScroll"
            @scroll="updateClipScroll"
            @mousedown="startClipDrag"
            @mousemove="onClipDrag"
            @mouseup="endClipDrag"
            @mouseleave="endClipDrag"
          >
            <div
              v-for="(clip, i) in popularClips"
              :key="clip.id"
              class="clip-card"
              @click="onClipCardClick($event, clip)"
            >
              <div class="card-thumb clip-card-thumb" :style="{ background: clip.thumbnail ? `url(${clip.thumbnail}) center/cover` : `linear-gradient(135deg, ${clipGradients[i % 4][0]}, ${clipGradients[i % 4][1]})` }">
                <span v-if="!clip.thumbnail" class="thumb-emoji" style="font-size:22px;">🎬</span>
                <span class="clip-play-icon">▶</span>
              </div>
              <div class="card-body clip-card-body">
                <span class="card-tag tag-clip">클립</span>
                <div class="card-title">{{ clip.title }}</div>
                <div class="card-meta">{{ clip.streamer }} · {{ clip.views }} 뷰</div>
              </div>
            </div>
          </div>
          <button class="content-arrow content-arrow-right" v-if="canClipRight" @click="scrollClips(1)">›</button>
        </div>
        <div v-else class="empty-state">아직 클립이 없습니다</div>
      </div>
    </div>

    <!-- 나작스 인기글 -->
    <div class="divider"></div>
    <div class="section">
      <div class="section-header">
        <div class="section-title"><div class="dot" style="background:var(--accent2)"></div>💙 나작스 인기글</div>
        <router-link to="/community" class="see-all">전체 보기 →</router-link>
      </div>
      <div class="grid-3" v-if="communityPosts.length > 0">
        <div class="card community-card" v-for="post in communityPosts" :key="post.id" @click="router.push(`/community/${post.id}`)">
          <span class="card-tag tag-community">나작스</span>
          <div class="card-title">{{ post.title }}</div>
          <div class="card-meta" style="margin-top:8px;">
            <span class="avatar-sm avatar-bg1">👤</span>
            {{ post.author }} · 💙 {{ post.likes }} · 💬 {{ post.comments }}
          </div>
        </div>
      </div>
      <div v-else class="empty-state">아직 게시글이 없습니다</div>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  width: 100%;
}

/* ─── Hero ─── */
.hero {
  position: relative;
  height: 480px;
  overflow: hidden;
  display: flex;
  align-items: center;
}

.hero-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(108,99,255,0.3) 0%, rgba(255,107,157,0.2) 50%, rgba(0,212,170,0.15) 100%);
}

.hero-bg::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at 20% 50%, rgba(108,99,255,0.4) 0%, transparent 60%),
              radial-gradient(ellipse at 80% 30%, rgba(255,107,157,0.3) 0%, transparent 50%);
}

.hero-grid {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px),
                    linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px);
  background-size: 40px 40px;
}

.hero-content {
  position: relative;
  z-index: 2;
  padding: 0 40px;
  max-width: 600px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(108,99,255,0.2);
  border: 1px solid rgba(108,99,255,0.4);
  border-radius: 20px;
  padding: 4px 12px;
  font-size: 12px;
  color: #a89fff;
  margin-bottom: 20px;
}

.hero h1 {
  font-size: 46px;
  font-weight: 900;
  line-height: 1.1;
  letter-spacing: -1.5px;
  margin-bottom: 16px;
}

.hero h1 span {
  background: var(--gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero p {
  color: var(--text2);
  font-size: 16px;
  line-height: 1.6;
  margin-bottom: 28px;
}

.hero-actions {
  display: flex;
  gap: 12px;
}

.hero-right {
  position: absolute;
  right: 40px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  gap: 12px;
  z-index: 2;
}

.event-card-mini {
  background: rgba(255,255,255,0.06);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 14px;
  padding: 14px 18px;
  width: 280px;
  cursor: pointer;
  transition: all 0.2s;
}

.event-card-mini:hover {
  transform: translateX(-4px);
  background: rgba(255,255,255,0.09);
}

.event-card-mini .tag {
  font-size: 10px;
  font-weight: 700;
  color: var(--accent2);
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 6px;
}

.event-card-mini h4 {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 4px;
}

.event-card-mini .meta {
  font-size: 11px;
  color: var(--text2);
}

/* ─── 🔴 라이브 중인 스트리머 (미니, 컨텐츠 홍보 위) ─── */
.live-now-mini {
  margin: 1.5rem 0 0; padding: 0 40px;
}
.live-mini-header {
  display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.75rem;
}
.live-mini-title {
  display: inline-flex; align-items: center; gap: 0.4rem;
  font-size: 0.95rem; font-weight: 700; color: var(--text);
}
.live-dot {
  width: 8px; height: 8px; border-radius: 50%; background: #ff3b3b;
  box-shadow: 0 0 0 0 rgba(255, 59, 59, 0.5); animation: live-pulse 1.4s ease-out infinite;
}
@keyframes live-pulse {
  0%   { box-shadow: 0 0 0 0 rgba(255, 59, 59, 0.7); }
  70%  { box-shadow: 0 0 0 8px rgba(255, 59, 59, 0); }
  100% { box-shadow: 0 0 0 0 rgba(255, 59, 59, 0); }
}
.live-mini-count { color: var(--text-muted); font-size: 0.82rem; }

.live-mini-scroll {
  display: flex; gap: 0.75rem; overflow-x: auto; padding-bottom: 0.5rem;
}
.live-mini-scroll::-webkit-scrollbar { height: 4px; }
.live-mini-card {
  flex: 0 0 260px; display: flex; align-items: center; gap: 0.75rem;
  padding: 0.75rem 0.9rem; background: var(--card); border: 1px solid var(--border);
  border-radius: 12px; text-decoration: none; color: inherit;
  transition: transform 0.15s, border-color 0.15s, box-shadow 0.15s;
}
.live-mini-card:hover {
  transform: translateY(-1px); border-color: #ff3b3b;
  box-shadow: 0 4px 14px rgba(255,59,59,0.25);
}
.live-mini-avatar-wrap { position: relative; flex-shrink: 0; }
.live-mini-avatar {
  width: 46px; height: 46px; border-radius: 50%; object-fit: cover;
  border: 2px solid #ff3b3b;
}
.live-mini-badge {
  position: absolute; bottom: -3px; left: 50%; transform: translateX(-50%);
  background: #ff3b3b; color: #fff;
  font-size: 0.6rem; font-weight: 800; padding: 1px 5px; border-radius: 3px;
  letter-spacing: 0.3px; line-height: 1;
}
.live-mini-info { min-width: 0; flex: 1; }
.live-mini-nick {
  font-weight: 700; font-size: 0.85rem; color: var(--text);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.live-mini-live-title {
  font-size: 0.75rem; color: var(--text-muted); font-weight: 500;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  margin-top: 2px;
}
.live-mini-meta {
  font-size: 0.72rem; color: #ff7070; font-weight: 600; line-height: 1.2;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.live-mini-cat { color: var(--text-muted); font-weight: 500; }

/* ─── Ad Banner Strip (치지직 스타일) ─── */
.ad-banner-strip {
  border-bottom: 1px solid var(--border);
  background: var(--bg2);
  padding: 14px 0;
}

.ad-banner-container {
  position: relative;
  max-width: 100%;
  margin: 0 auto;
  padding: 0 48px;
}

.banner-track {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  overflow-y: hidden;
  scroll-snap-type: x mandatory;
  scrollbar-width: none;
  -ms-overflow-style: none;
  padding: 2px 4px;
  cursor: grab;
  user-select: none;
}

.banner-track::before,
.banner-track::after {
  content: '';
  flex-shrink: 0;
  width: 1px;
}

@supports (margin-inline: auto) {
  .banner-track { margin-inline: auto; }
}

.banner-track::-webkit-scrollbar {
  display: none;
}

.banner-card {
  flex-shrink: 0;
  width: 240px;
  text-decoration: none;
  color: inherit;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--border);
  background: var(--card);
  transition: all 0.2s;
  scroll-snap-align: start;
  cursor: pointer;
}

.banner-card:hover {
  border-color: var(--accent);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(108,99,255,0.15);
}

.banner-card-img {
  width: 100%;
  height: 135px;
  overflow: hidden;
  background: var(--bg3);
}

.banner-card-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.banner-card-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 800;
  color: var(--text3);
  background: linear-gradient(135deg, rgba(108,99,255,0.15), rgba(255,107,157,0.1));
}

.banner-card-title {
  padding: 8px 10px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: center;
}

.banner-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 5;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text);
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}

.banner-arrow:hover {
  background: var(--accent);
  color: #fff;
  border-color: var(--accent);
}

.banner-arrow-left {
  left: 10px;
}

.banner-arrow-right {
  right: 10px;
}

/* ─── Cards ─── */
.card-thumb {
  width: 100%;
  aspect-ratio: 16/9;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.thumb-emoji {
  font-size: 32px;
}

.card-body {
  padding: 14px;
}

.card-tag {
  display: inline-block;
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 4px;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.card-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 6px;
  line-height: 1.4;
}

.card-meta {
  font-size: 12px;
  color: var(--text2);
  display: flex;
  align-items: center;
  gap: 8px;
}

.avatar-sm {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  flex-shrink: 0;
}

.community-card {
  padding: 18px;
}

/* ─── Two Column Layout ─── */
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 40px;
}
.two-col > div {
  min-width: 0;
}

/* ─── Broadcast Feed ─── */
.broadcast-feed {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.broadcast-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 18px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.broadcast-item:hover {
  background: var(--card-hover);
  border-color: rgba(0,212,170,0.3);
}

.broadcast-thumb {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  object-fit: cover;
  flex-shrink: 0;
  border: 1px solid var(--border);
}

.broadcast-avatar {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.broadcast-info {
  flex: 1;
}

.broadcast-info h4 {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 3px;
}

.broadcast-info p {
  font-size: 12px;
  color: var(--text2);
}

.broadcast-time {
  font-size: 12px;
  font-weight: 600;
  color: var(--accent3);
  background: rgba(0,212,170,0.1);
  border: 1px solid rgba(0,212,170,0.2);
  border-radius: 6px;
  padding: 4px 10px;
  white-space: nowrap;
}

/* ─── Clip Scroll (인기 클립 가로) ─── */
.clip-scroll-wrap {
  position: relative;
}
.clip-scroll {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  overflow-y: hidden;
  scroll-snap-type: x mandatory;
  scrollbar-width: none;
  -ms-overflow-style: none;
  padding: 4px 2px 10px;
  cursor: grab;
  user-select: none;
}
.clip-scroll::-webkit-scrollbar { display: none; }
.clip-card {
  flex: 0 0 200px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.15s, border-color 0.15s, box-shadow 0.15s;
  scroll-snap-align: start;
}
.clip-card:hover {
  transform: translateY(-2px);
  border-color: var(--accent2);
  box-shadow: 0 6px 20px rgba(0,0,0,0.15);
}

.clip-card-thumb {
  aspect-ratio: 16/9;
  position: relative;
  width: 100%;
}

.clip-card-body {
  padding: 12px !important;
}

.clip-card-body .card-tag { font-size: 10px; margin-bottom: 6px; display: inline-block; }
.clip-card-body .card-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 6px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.clip-card-body .card-meta { font-size: 12px; color: var(--text2); }

.clip-play-icon {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(0,0,0,0.55);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  transition: background 0.2s;
}

.clip-card:hover .clip-play-icon {
  background: rgba(255,107,157,0.7);
}

/* ─── Content Scroll (컨텐츠 홍보 가로) ─── */
.content-scroll-wrap {
  position: relative;
}
.content-scroll {
  display: flex;
  gap: 14px;
  overflow-x: auto;
  overflow-y: hidden;
  scroll-snap-type: x mandatory;
  scrollbar-width: none;
  -ms-overflow-style: none;
  padding: 4px 2px 10px;
  cursor: grab;
  user-select: none;
}
.content-scroll::-webkit-scrollbar { display: none; }
.content-card {
  flex: 0 0 240px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.15s, border-color 0.15s, box-shadow 0.15s;
  scroll-snap-align: start;
}
.content-card:hover {
  transform: translateY(-2px);
  border-color: rgba(108,99,255,0.5);
  box-shadow: 0 6px 18px rgba(108,99,255,0.15);
}
.content-card-thumb {
  width: 100%;
  aspect-ratio: 16/9;
  display: flex;
  align-items: center;
  justify-content: center;
  background-size: cover;
  background-position: center;
}
.content-card-body { padding: 12px; }

.content-arrow {
  position: absolute;
  top: 40%;
  transform: translateY(-50%);
  z-index: 5;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text);
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(0,0,0,0.2);
}
.content-arrow:hover { background: var(--accent); color: #fff; border-color: var(--accent); }
.content-arrow-left { left: -10px; }
.content-arrow-right { right: -10px; }

@media (max-width: 480px) {
  .content-card { flex: 0 0 200px; }
  .content-arrow { display: none; }
}

/* ─── Empty State ─── */
.empty-state {
  padding: 40px;
  text-align: center;
  color: var(--text3);
  font-size: 14px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
}

/* ─── Responsive ─── */
@media (max-width: 1024px) {
  .hero-right { display: none; }
  .hero { height: auto; padding: 48px 0 36px; }
  .hero-content { padding: 0 24px; max-width: 100%; }
  .hero h1 { font-size: 34px; }
  .two-col { grid-template-columns: 1fr; gap: 32px; }
  .ad-banner-container { padding: 0 40px; }
  .banner-card { width: 210px; }
  .banner-card-img { height: 120px; }
  .live-now-mini { padding: 0 24px; }
}

@media (max-width: 480px) {
  .hero { padding: 28px 0 20px; }
  .hero-content { padding: 0 16px; }
  .hero h1 { font-size: 24px; letter-spacing: -0.5px; }
  .hero p { font-size: 13px; }
  .hero-actions { flex-direction: column; gap: 8px; }
  .hero-actions .btn { width: 100%; text-align: center; }
  .ad-banner-container { padding: 0 36px; }
  .banner-card { width: 180px; }
  .banner-card-img { height: 100px; }
  .banner-card-title { font-size: 11px; padding: 6px 8px; }
  .live-now-mini { padding: 0 16px; }
  .live-mini-card { flex: 0 0 220px; }
}
</style>
