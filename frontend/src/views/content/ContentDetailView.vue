<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import api from '@/api'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const notify = useNotificationStore()

const content = ref(null)
const isLoading = ref(true)
const activeImage = ref(0)
const lightboxOpen = ref(false)
const lightboxIndex = ref(0)

function openLightbox(idx) {
  lightboxIndex.value = idx
  lightboxOpen.value = true
  document.body.style.overflow = 'hidden'
}

function closeLightbox() {
  lightboxOpen.value = false
  document.body.style.overflow = ''
}

function prevLightbox() {
  const total = galleryImages.value.length
  lightboxIndex.value = (lightboxIndex.value - 1 + total) % total
}

function nextLightbox() {
  const total = galleryImages.value.length
  lightboxIndex.value = (lightboxIndex.value + 1) % total
}

const normalizedApplyLink = computed(() => {
  const raw = content.value?.applyLink
  if (!raw) return ''
  const trimmed = raw.trim()
  if (/^https?:\/\//i.test(trimmed)) return trimmed
  if (trimmed.startsWith('//')) return 'https:' + trimmed
  return 'https://' + trimmed
})

const isOwner = computed(() =>
  authStore.user && content.value && authStore.user.id === content.value.streamerId
)
const isAdmin = computed(() => authStore.user?.role === 'ADMIN')
const canEdit = computed(() => isOwner.value || isAdmin.value)

const categories = {
  GAME: '게임', MUSIC: '음악', MUKBANG: '먹방', TALK: '토크', ETC: '기타'
}

const contactLabels = {
  discord: '디스코드',
  kakao: '카카오톡',
  email: '이메일',
  twitter: '트위터/X',
  etc: '기타'
}

const galleryImages = computed(() => {
  if (!content.value) return []
  const list = content.value.imageUrls && content.value.imageUrls.length > 0
    ? content.value.imageUrls
    : (content.value.thumbnailUrl ? [content.value.thumbnailUrl] : [])
  return list
})

const followerText = computed(() => {
  if (!content.value) return ''
  if (content.value.followerUnlimited) return '제한 없음'
  if (content.value.followerCount != null) return `${content.value.followerCount.toLocaleString()}명 이상`
  return ''
})

async function fetchContent() {
  try {
    const { data } = await api.get(`/public/contents/${route.params.id}`)
    content.value = data.data
    activeImage.value = 0
  } catch {
    notify.error('컨텐츠를 불러올 수 없습니다')
  } finally {
    isLoading.value = false
  }
}

async function handleClose() {
  try {
    await api.post(`/contents/${content.value.id}/close`)
    notify.success('컨텐츠가 마감되었습니다')
    content.value.status = 'CLOSED'
  } catch {
    notify.error('마감 처리에 실패했습니다')
  }
}

function formatDate(dateStr) {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric', month: 'long', day: 'numeric'
  })
}

function getStatusInfo(status) {
  switch (status) {
    case 'ONGOING': case 'APPROVED': return { text: '진행중', cls: 'status-ongoing' }
    case 'CLOSED': return { text: '마감', cls: 'status-closed' }
    case 'PENDING': return { text: '심사중', cls: 'status-pending' }
    default: return { text: status, cls: '' }
  }
}

onMounted(fetchContent)
</script>

<template>
  <div class="content-detail-page">
    <div v-if="isLoading" class="loading">
      <div class="spinner"></div>
    </div>

    <template v-else-if="content">
      <div class="detail-layout">
        <!-- 좌측: 본문 -->
        <div class="detail-main">
          <div class="meta-row">
            <span class="category-tag">{{ categories[content.category] || content.category }}</span>
            <span
              v-for="tag in (content.tags || []).filter(t => t !== content.category)"
              :key="tag"
              class="category-tag sub"
            >{{ categories[tag] || tag }}</span>
            <span :class="['status-badge', getStatusInfo(content.status).cls]">
              {{ getStatusInfo(content.status).text }}
            </span>
          </div>

          <h1>{{ content.title }}</h1>

          <div class="author-row">
            <router-link :to="`/streamers/${content.streamerId}`" class="author-link">
              {{ content.streamerNickname }}
            </router-link>
            <span class="date">{{ formatDate(content.createdAt) }}</span>
            <span v-if="content.endDate" class="date">~ {{ formatDate(content.endDate) }}</span>
          </div>

          <!-- 이미지 갤러리 -->
          <div v-if="galleryImages.length > 0" class="gallery">
            <div class="gallery-main" @click="openLightbox(activeImage)" role="button" title="클릭하여 확대">
              <img :src="galleryImages[activeImage]" alt="" />
              <div class="gallery-zoom-hint">🔍 클릭하여 확대</div>
            </div>
            <div v-if="galleryImages.length > 1" class="gallery-thumbs">
              <button
                v-for="(src, idx) in galleryImages"
                :key="idx"
                type="button"
                class="gallery-thumb"
                :class="{ active: idx === activeImage }"
                @click="activeImage = idx"
              >
                <img :src="src" alt="" />
              </button>
            </div>
          </div>

          <div v-if="content.description" class="description">
            {{ content.description }}
          </div>

          <div v-if="content.requirements" class="info-block">
            <h3>참가 조건</h3>
            <p>{{ content.requirements }}</p>
          </div>

          <div v-if="content.prize" class="info-block">
            <h3>상금 / 혜택</h3>
            <p>{{ content.prize }}</p>
          </div>

          <!-- 스트리머 본인/관리자 액션 -->
          <div v-if="canEdit" class="owner-actions">
            <button
              class="btn btn-primary"
              @click="router.push({ path: '/contents/create', query: { edit: content.id } })"
            >✏️ 수정</button>
            <button v-if="isOwner && content.status !== 'CLOSED'" class="btn btn-danger" @click="handleClose">이벤트 마감</button>
          </div>
        </div>

        <!-- 우측: 신청 정보 -->
        <div class="detail-sidebar">
          <div class="sidebar-card">
            <div class="sidebar-streamer">
              <div class="sidebar-avatar">{{ (content.streamerNickname || '?').charAt(0) }}</div>
              <div>
                <div class="sidebar-name">{{ content.streamerNickname }}</div>
                <div class="sidebar-category">{{ categories[content.category] || content.category }}</div>
              </div>
            </div>

            <div v-if="content.hostName" class="sidebar-meta">
              <div class="sidebar-date-item">
                <span class="sidebar-label">주최</span>
                <span>{{ content.hostName }}</span>
              </div>
            </div>

            <div v-if="content.startDate || content.endDate" class="sidebar-dates">
              <div v-if="content.startDate" class="sidebar-date-item">
                <span class="sidebar-label">시작</span>
                <span>{{ formatDate(content.startDate) }}</span>
              </div>
              <div v-if="content.endDate" class="sidebar-date-item">
                <span class="sidebar-label">마감</span>
                <span>{{ formatDate(content.endDate) }}</span>
              </div>
            </div>

            <div v-if="content.recruitCount || followerText" class="sidebar-meta">
              <div v-if="content.recruitCount" class="sidebar-date-item">
                <span class="sidebar-label">모집 인원</span>
                <span>{{ content.recruitCount }}</span>
              </div>
              <div v-if="followerText" class="sidebar-date-item">
                <span class="sidebar-label">팔로워</span>
                <span>{{ followerText }}</span>
              </div>
            </div>

            <div v-if="content.contactMethod || content.contactInfo" class="sidebar-meta">
              <div class="sidebar-date-item">
                <span class="sidebar-label">{{ contactLabels[content.contactMethod] || '연락' }}</span>
                <span>{{ content.contactInfo || '-' }}</span>
              </div>
            </div>

            <div v-if="content.applyLink && content.status !== 'CLOSED'" class="sidebar-action">
              <a :href="normalizedApplyLink" target="_blank" rel="noopener noreferrer" class="btn btn-apply btn-full">
                신청하기
              </a>
              <a :href="normalizedApplyLink" target="_blank" rel="noopener noreferrer" class="apply-url-text">
                {{ content.applyLink }}
              </a>
            </div>

            <div v-if="content.status === 'CLOSED'" class="closed-notice">
              이 이벤트는 마감되었습니다.
            </div>

            <div v-if="!content.applyLink && content.status !== 'CLOSED'" class="sidebar-info">
              별도 신청 링크가 없습니다.
            </div>
          </div>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="not-found">
        <h1>컨텐츠를 찾을 수 없습니다</h1>
        <router-link to="/contents" class="btn btn-primary">목록으로 돌아가기</router-link>
      </div>
    </template>

    <!-- 이미지 라이트박스 -->
    <div v-if="lightboxOpen" class="lightbox" @click.self="closeLightbox">
      <button class="lightbox-close" @click="closeLightbox" aria-label="닫기">✕</button>
      <button
        v-if="galleryImages.length > 1"
        class="lightbox-nav lightbox-prev"
        @click.stop="prevLightbox"
        aria-label="이전"
      >‹</button>
      <img :src="galleryImages[lightboxIndex]" alt="" class="lightbox-image" @click.stop />
      <button
        v-if="galleryImages.length > 1"
        class="lightbox-nav lightbox-next"
        @click.stop="nextLightbox"
        aria-label="다음"
      >›</button>
      <div v-if="galleryImages.length > 1" class="lightbox-counter">
        {{ lightboxIndex + 1 }} / {{ galleryImages.length }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.content-detail-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 2rem 1rem;
  font-family: 'Pretendard', sans-serif;
}

.loading { text-align: center; padding: 6rem 0; }
.spinner { width: 40px; height: 40px; border: 3px solid var(--border); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto; }
@keyframes spin { to { transform: rotate(360deg); } }

.detail-layout { display: grid; grid-template-columns: 1fr 320px; gap: 1.5rem; align-items: start; }

.detail-main {
  background: var(--card); border: 1px solid var(--border); border-radius: 16px; padding: 2rem;
}

.gallery { margin-bottom: 1.5rem; }
.gallery-main {
  border-radius: 12px; overflow: hidden; border: 1px solid var(--border);
  max-height: 420px; display: flex; align-items: center; justify-content: center; background: var(--bg2);
  cursor: zoom-in; position: relative;
}
.gallery-main:hover .gallery-zoom-hint { opacity: 1; }
.gallery-zoom-hint {
  position: absolute; bottom: 12px; right: 12px;
  background: rgba(0,0,0,0.7); color: #fff;
  padding: 6px 10px; border-radius: 8px;
  font-size: 0.78rem; font-weight: 600;
  opacity: 0; transition: opacity 0.2s; pointer-events: none;
}
.gallery-main img { width: 100%; height: auto; max-height: 420px; object-fit: contain; }

.apply-url-text {
  display: block;
  margin-top: 0.5rem;
  font-size: 0.78rem;
  color: var(--accent);
  text-align: center;
  word-break: break-all;
  text-decoration: none;
}
.apply-url-text:hover { text-decoration: underline; }

/* Lightbox */
.lightbox {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.92);
  display: flex; align-items: center; justify-content: center;
  z-index: 9999; padding: 2rem;
}
.lightbox-image {
  max-width: 92vw; max-height: 90vh;
  object-fit: contain; border-radius: 6px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.5);
}
.lightbox-close {
  position: absolute; top: 20px; right: 24px;
  width: 44px; height: 44px; border-radius: 50%;
  background: rgba(255,255,255,0.12); border: none;
  color: #fff; font-size: 22px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
}
.lightbox-close:hover { background: rgba(255,255,255,0.22); }
.lightbox-nav {
  position: absolute; top: 50%; transform: translateY(-50%);
  width: 52px; height: 52px; border-radius: 50%;
  background: rgba(255,255,255,0.12); border: none;
  color: #fff; font-size: 32px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
}
.lightbox-nav:hover { background: rgba(255,255,255,0.22); }
.lightbox-prev { left: 24px; }
.lightbox-next { right: 24px; }
.lightbox-counter {
  position: absolute; bottom: 24px; left: 50%;
  transform: translateX(-50%);
  color: #fff; background: rgba(0,0,0,0.5);
  padding: 6px 14px; border-radius: 20px;
  font-size: 0.88rem; font-weight: 600;
}
.gallery-thumbs { display: flex; gap: 8px; margin-top: 10px; overflow-x: auto; }
.gallery-thumb {
  flex: 0 0 64px; height: 64px; border-radius: 8px; overflow: hidden;
  border: 2px solid transparent; padding: 0; cursor: pointer; background: var(--bg2);
}
.gallery-thumb.active { border-color: var(--accent); }
.gallery-thumb img { width: 100%; height: 100%; object-fit: cover; }

.meta-row { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 1rem; flex-wrap: wrap; }
.category-tag { padding: 0.2rem 0.6rem; background: rgba(108,99,255,0.15); color: var(--accent); border-radius: 4px; font-size: 0.8rem; font-weight: 600; }
.category-tag.sub { background: rgba(255,255,255,0.06); color: var(--text2); }
.status-badge { padding: 0.2rem 0.6rem; border-radius: 12px; font-size: 0.75rem; font-weight: 600; }
.status-ongoing { background: rgba(0,212,170,0.15); color: var(--accent3); }
.status-closed { background: rgba(255,255,255,0.06); color: var(--text3); }
.status-pending { background: rgba(251,191,36,0.15); color: #fbbf24; }

h1 { font-size: 1.5rem; font-weight: 700; color: var(--text); margin-bottom: 0.75rem; }
.author-row { display: flex; align-items: center; gap: 1rem; margin-bottom: 1.5rem; font-size: 0.9rem; flex-wrap: wrap; }
.author-link { color: var(--accent); text-decoration: none; font-weight: 600; }
.author-link:hover { text-decoration: underline; }
.date { color: var(--text3); }
.description { color: var(--text2); line-height: 1.8; white-space: pre-line; font-size: 1rem; }

.info-block { margin-top: 1.5rem; padding-top: 1.25rem; border-top: 1px solid var(--border); }
.info-block h3 { font-size: 0.95rem; font-weight: 700; color: var(--text); margin-bottom: 0.5rem; }
.info-block p { color: var(--text2); line-height: 1.7; white-space: pre-line; font-size: 0.95rem; }

.owner-actions { padding-top: 1rem; margin-top: 1.5rem; border-top: 1px solid var(--border); }

/* Sidebar */
.detail-sidebar { position: sticky; top: 100px; }
.sidebar-card { background: var(--card); border: 1px solid var(--border); border-radius: 16px; padding: 1.5rem; }
.sidebar-streamer { display: flex; align-items: center; gap: 0.75rem; margin-bottom: 1.25rem; padding-bottom: 1rem; border-bottom: 1px solid var(--border); }
.sidebar-avatar { width: 40px; height: 40px; border-radius: 10px; background: var(--gradient); display: flex; align-items: center; justify-content: center; font-size: 16px; font-weight: 700; color: white; }
.sidebar-name { font-size: 0.95rem; font-weight: 700; color: var(--text); }
.sidebar-category { font-size: 0.78rem; color: var(--text3); }
.sidebar-dates, .sidebar-meta { display: flex; flex-direction: column; gap: 0.5rem; margin-bottom: 1.25rem; padding-bottom: 1rem; border-bottom: 1px solid var(--border); }
.sidebar-date-item { display: flex; justify-content: space-between; font-size: 0.85rem; color: var(--text2); gap: 8px; }
.sidebar-date-item span:last-child { text-align: right; word-break: break-all; }
.sidebar-label { font-weight: 600; color: var(--text3); font-size: 0.8rem; flex: 0 0 auto; }
.sidebar-action { margin-bottom: 0.5rem; }
.sidebar-info { font-size: 0.85rem; color: var(--text3); text-align: center; padding: 0.5rem; }

.btn { padding: 0.7rem 1.5rem; border: none; border-radius: 8px; font-size: 0.95rem; font-weight: 600; cursor: pointer; text-decoration: none; display: inline-block; transition: all 0.2s; font-family: 'Pretendard', sans-serif; }
.btn-apply { background: var(--gradient); color: #fff; }
.btn-apply:hover { opacity: 0.9; }
.btn-full { display: block; width: 100%; text-align: center; }
.btn-primary { background: var(--gradient); color: #fff; }
.btn-danger { background: #ef4444; color: #fff; }

.closed-notice { background: rgba(255,255,255,0.04); border: 1px solid var(--border); border-radius: 8px; padding: 1rem; text-align: center; color: var(--text3); font-weight: 600; }
.not-found { text-align: center; padding: 6rem 1rem; }
.not-found h1 { margin-bottom: 1.5rem; color: var(--text); }

@media (max-width: 768px) {
  .detail-layout { grid-template-columns: 1fr; }
  .detail-sidebar { position: static; }
}
</style>
