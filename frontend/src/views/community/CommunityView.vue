<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import BoardIcon from '@/components/common/BoardIcon.vue'
import api from '@/api'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const posts = ref([])
const currentPage = ref(1)
const totalPages = ref(1)
const loading = ref(false)

// 그룹 정의 — 헤더 메뉴와 1:1 대응
const GROUPS = {
  community: {
    label: '커뮤니티',
    description: '스트리머와 팬이 함께하는 소통 공간',
    boards: [
      { key: 'NOTICE', icon: 'megaphone', label: '공지사항', categories: ['NOTICE'] },
      { key: 'FREE', icon: 'message', label: '자유게시판', categories: ['FREE'] },
      { key: 'QUESTION', icon: 'help', label: '질문게시판', categories: ['QUESTION'] },
      { key: 'HUMOR', icon: 'smile', label: '유머게시판', categories: ['HUMOR'] },
      { key: 'TIP', icon: 'lightbulb', label: '팁/정보', categories: ['TIP'] },
      { key: 'INQUIRY', icon: 'help', label: '문의 게시판', categories: ['INQUIRY'] },
      { key: 'STREAMER_ONLY', icon: 'lock', label: '스트리머 전용', categories: ['STREAMER_ONLY'] },
    ],
  },
  pride: {
    label: '나작스 자랑',
    description: '직접 만든 작품·팬아트·클립·굿즈를 자랑하는 공간',
    boards: [
      { key: 'ARTWORK', icon: 'palette', label: '작품 공유', categories: ['ARTWORK'] },
      { key: 'FANART', icon: 'heart', label: '팬아트', categories: ['FANART'] },
      { key: 'CLIP_SHOWCASE', icon: 'film', label: '클립 자랑', categories: ['CLIP_SHOWCASE'] },
      { key: 'GOODS', icon: 'gift', label: '굿즈 자랑', categories: ['GOODS'] },
    ],
  },
}

const activeGroupKey = ref('community')
const activeBoardKey = ref('NOTICE')

const activeGroup = computed(() => GROUPS[activeGroupKey.value] || GROUPS.community)
const activeBoard = computed(() =>
  activeGroup.value.boards.find(b => b.key === activeBoardKey.value) || activeGroup.value.boards[0]
)

function defaultBoardKey(groupKey) {
  const group = GROUPS[groupKey] || GROUPS.community
  // community 그룹은 공지사항을 기본, 나머지 그룹은 첫 번째 게시판
  if (groupKey === 'community') return 'NOTICE'
  return group.boards[0]?.key || 'FREE'
}

function syncFromRoute() {
  const groupQ = route.query.group
  const newGroupKey = (groupQ && GROUPS[groupQ]) ? groupQ : 'community'
  activeGroupKey.value = newGroupKey

  const boardQ = route.query.board
  const group = GROUPS[newGroupKey]
  const found = group.boards.find(b => b.key === boardQ)
  activeBoardKey.value = found ? boardQ : defaultBoardKey(newGroupKey)
}

const isStreamer = computed(() => authStore.isStreamer || authStore.isAdmin)
const isAdmin = computed(() => authStore.isAdmin)
const userName = computed(() => authStore.userNickname || 'User')
const isStreamerBoard = computed(() => activeBoardKey.value === 'STREAMER_ONLY')
const isNoticeBoard = computed(() => activeBoardKey.value === 'NOTICE')
const canWrite = computed(() => !isNoticeBoard.value || isAdmin.value)
const showStreamerOnlyUI = computed(() => isStreamer.value && isStreamerBoard.value)

// 이미지 중심 게시판 — 4열 그리드로 표시
const GRID_BOARDS = ['ARTWORK', 'FANART', 'CLIP_SHOWCASE', 'GOODS']
const isGridView = computed(() => GRID_BOARDS.includes(activeBoardKey.value))

function getPostThumb(post) {
  if (post.thumbnailUrl) return post.thumbnailUrl
  if (post.imageUrl) return post.imageUrl
  // 클립 자랑: 본문에 유튜브/치지직 URL이 있으면 썸네일 추출
  const content = post.content || ''
  const yt = content.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/shorts\/|youtube\.com\/embed\/)([a-zA-Z0-9_-]{11})/)
  if (yt) return `https://img.youtube.com/vi/${yt[1]}/hqdefault.jpg`
  if (/chzzk\.naver\.com/.test(content)) return '/chzzk-logo.png'
  return null
}

function extractClipUrl(post) {
  const content = post.content || ''
  const match = content.match(/https?:\/\/[^\s]+/)
  return match ? match[0] : null
}

function handleGridClick(post) {
  if (activeBoardKey.value === 'CLIP_SHOWCASE') {
    const url = extractClipUrl(post)
    if (url) {
      window.open(url, '_blank', 'noopener,noreferrer')
      return
    }
  }
  router.push(`/community/${post.id}`)
}

async function fetchPosts() {
  loading.value = true
  try {
    const cats = activeBoard.value.categories
    const query = `page=${currentPage.value - 1}&size=10&${cats.map(c => 'categories=' + c).join('&')}`
    const { data } = await api.get(`/posts?${query}`)
    posts.value = data.data?.content || data.data || []
    totalPages.value = data.data?.totalPages || 1
  } catch {
    posts.value = []
  }
  loading.value = false
}

function selectBoard(key) {
  activeBoardKey.value = key
  currentPage.value = 1
  router.replace({ path: '/community', query: { group: activeGroupKey.value, board: key } })
  fetchPosts()
}

function goToPage(page) {
  currentPage.value = page
  fetchPosts()
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  let d
  if (Array.isArray(dateStr)) {
    d = new Date(dateStr[0], dateStr[1] - 1, dateStr[2], dateStr[3] || 0, dateStr[4] || 0)
  } else {
    d = new Date(dateStr)
  }
  if (isNaN(d.getTime())) return ''
  return d.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).replace(/\. /g, '.').replace(/\.$/, '')
}

onMounted(() => {
  syncFromRoute()
  fetchPosts()
})

watch(() => route.fullPath, () => {
  if (route.path !== '/community') return
  syncFromRoute()
  currentPage.value = 1
  fetchPosts()
})
</script>

<template>
  <div class="community-page">
    <div class="streamer-banner" v-if="showStreamerOnlyUI">
      <span>🔐 스트리머 전용 공간입니다. 이 공간의 내용은 외부에 공유할 수 없으며, 위반 시 계정이 정지될 수 있습니다.</span>
    </div>

    <div class="community-wrap">
      <div class="community-sidebar">
        <div class="sidebar-title">{{ activeGroup.label }}</div>
        <div
          v-for="board in activeGroup.boards"
          :key="board.key"
          class="sidebar-menu-item"
          :class="{ active: activeBoardKey === board.key }"
          @click="selectBoard(board.key)"
        >
          <BoardIcon :name="board.icon" :size="18" class="icon" /> {{ board.label }}
        </div>
        <div class="locked-notice" v-if="showStreamerOnlyUI">🔒 이 공간은 인증된 스트리머만 접근 가능합니다.</div>
        <div class="watermark-notice" v-if="showStreamerOnlyUI">💧 이 게시판은 닉네임 워터마크가 적용됩니다.</div>
      </div>

      <div class="community-main">
        <div class="watermark-overlay" v-if="showStreamerOnlyUI">
          <div class="watermark-text" v-for="i in 18" :key="i">{{ userName }} · #{{ authStore.user?.id || '0000' }}</div>
        </div>

        <div class="community-header">
          <div class="community-title-wrap">
            <h2>{{ isStreamerBoard ? '스트리머 전용 커뮤니티' : activeBoard.label }}</h2>
            <p>{{ isStreamerBoard ? '인증된 스트리머들만의 소통 공간입니다' : activeGroup.label + ' · ' + activeGroup.description }}</p>
          </div>
          <div class="community-actions">
            <div class="streamer-only-badge" v-if="showStreamerOnlyUI">🔐 STREAMER ONLY</div>
            <button v-if="canWrite" class="btn btn-primary btn-sm" @click="router.push({ path: '/community/write', query: { category: activeBoardKey } })">✏️ 글쓰기</button>
          </div>
        </div>

        <!-- 이미지 중심 그리드 뷰 (작품공유/팬아트/클립자랑/굿즈) -->
        <div v-if="isGridView" class="post-grid">
          <div
            v-for="post in posts" :key="post.id"
            class="post-grid-card"
            @click="handleGridClick(post)"
          >
            <div class="grid-thumb">
              <img v-if="getPostThumb(post)" :src="getPostThumb(post)" :alt="post.title" loading="lazy" />
              <div v-else class="grid-thumb-empty">🎨</div>
              <div v-if="activeBoardKey === 'CLIP_SHOWCASE'" class="grid-play">▶</div>
            </div>
            <div class="grid-body">
              <div class="grid-title">{{ post.title }}</div>
              <div class="grid-meta">
                <span class="grid-author">
                  <img
                    v-if="post.authorProfileImage"
                    :src="post.authorProfileImage"
                    alt=""
                    class="author-avatar-xs"
                  />
                  <span v-else class="author-avatar-xs avatar-fallback-xs">{{ (post.authorNickname || '?').charAt(0).toUpperCase() }}</span>
                  {{ post.authorNickname || '익명' }}
                </span>
                <span class="grid-stats">
                  <span>👍 {{ post.likeCount || 0 }}</span>
                  <span>💬 {{ post.commentCount || 0 }}</span>
                </span>
              </div>
            </div>
          </div>
          <div v-if="posts.length === 0 && !loading" class="empty-state grid-empty">아직 게시글이 없습니다</div>
        </div>

        <!-- 기본 리스트 뷰 -->
        <div v-else class="post-list">
          <div
            v-for="post in posts" :key="post.id"
            class="post-item" :class="{ 'post-notice': post.pinned || post.category === 'notice' }"
            @click="router.push(`/community/${post.id}`)"
          >
            <div class="post-num">{{ post.pinned ? '📌' : post.id }}</div>
            <div class="post-content">
              <div class="post-title">
                <span v-if="post.pinned || post.category === 'notice'" class="notice-tag">공지</span>
                {{ post.title }}
              </div>
              <div class="post-meta2">
                <span class="post-author">
                  <img
                    v-if="post.authorProfileImage"
                    :src="post.authorProfileImage"
                    alt=""
                    class="author-avatar-sm"
                  />
                  <span v-else class="author-avatar-sm avatar-fallback">{{ (post.authorNickname || '?').charAt(0).toUpperCase() }}</span>
                  {{ post.authorNickname || '익명' }}
                </span>
                <span>💬 {{ post.commentCount || 0 }}</span>
                <span>👍 {{ post.likeCount || 0 }}</span>
              </div>
            </div>
            <div class="post-stats">
              <span>💬 {{ post.commentCount || 0 }}</span>
              <span>👍 {{ post.likeCount || 0 }}</span>
            </div>
            <div class="post-date">{{ formatDate(post.createdAt) }}</div>
          </div>
          <div v-if="posts.length === 0 && !loading" class="empty-state">아직 게시글이 없습니다</div>
        </div>

        <div class="pagination" v-if="totalPages > 1">
          <button v-for="page in Math.min(totalPages, 5)" :key="page" class="page-btn" :class="{ active: page === currentPage }" @click="goToPage(page)">{{ page }}</button>
          <button v-if="currentPage < totalPages" class="page-btn" @click="goToPage(currentPage + 1)">→</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.community-page { width: 100%; }

.streamer-banner {
  background: rgba(108,99,255,0.08);
  border-bottom: 1px solid rgba(108,99,255,0.2);
  padding: 10px 40px;
  font-size: 13px;
  color: #a89fff;
}

.community-wrap {
  display: grid;
  grid-template-columns: 240px 1fr;
  min-height: calc(100vh - 200px);
}

.community-sidebar {
  background: var(--bg2);
  border-right: 1px solid var(--border);
  padding: 24px 16px;
}

.sidebar-title {
  font-size: 11px; font-weight: 700; color: var(--text2);
  text-transform: uppercase; letter-spacing: 1px;
  padding: 0 8px; margin-bottom: 12px;
}

.sidebar-menu-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px; border-radius: 8px; cursor: pointer;
  font-size: 14px; font-weight: 500; color: var(--text2);
  transition: all 0.2s; margin-bottom: 2px;
}

.sidebar-menu-item:hover { background: var(--card); color: var(--text); }
.sidebar-menu-item.active { background: rgba(108,99,255,0.15); color: var(--accent); font-weight: 700; }
.sidebar-menu-item .icon { width: 20px; height: 20px; flex-shrink: 0; opacity: 0.85; }
.sidebar-menu-item.active .icon { opacity: 1; }

.locked-notice {
  margin-top: 24px; padding: 14px;
  background: rgba(255,107,157,0.08); border: 1px solid rgba(255,107,157,0.2);
  border-radius: 10px; font-size: 12px; color: var(--accent2); line-height: 1.5;
}

.watermark-notice {
  margin-top: 12px; padding: 14px;
  background: rgba(108,99,255,0.08); border: 1px solid rgba(108,99,255,0.2);
  border-radius: 10px; font-size: 12px; color: #a89fff; line-height: 1.5;
}

.community-main { padding: 28px 32px; position: relative; }

.community-header {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px;
}

.community-title-wrap h2 { font-size: 22px; font-weight: 800; margin-bottom: 4px; }
.community-title-wrap p { font-size: 13px; color: var(--text2); }

.community-actions { display: flex; gap: 10px; align-items: center; }

.streamer-only-badge {
  display: inline-flex; align-items: center; gap: 6px;
  background: rgba(108,99,255,0.15); border: 1px solid rgba(108,99,255,0.3);
  border-radius: 8px; padding: 6px 12px; font-size: 12px; font-weight: 700; color: var(--accent);
}

.watermark-overlay {
  position: absolute; inset: 0; pointer-events: none; overflow: hidden; z-index: 10;
  display: grid; grid-template-columns: repeat(3, 1fr); grid-template-rows: repeat(6, 1fr);
}

.watermark-text {
  display: flex; align-items: center; justify-content: center;
  color: rgba(255,255,255,0.04); font-size: 13px; font-weight: 700;
  white-space: nowrap; transform: rotate(-30deg); user-select: none;
}

/* ─── 게시글 목록 (premium inner) ─── */
.post-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: transparent;
}

.post-item {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 18px 22px;
  background: var(--bg2);
  border: 1px solid var(--border);
  border-radius: 14px;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
  position: relative;
  overflow: hidden;
}

.post-item::before {
  content: '';
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 3px;
  background: transparent;
  transition: background 0.18s ease;
}

.post-item:hover {
  transform: translateY(-2px);
  border-color: rgba(108,99,255,0.35);
  box-shadow: 0 8px 24px rgba(108,99,255,0.08), 0 2px 6px rgba(0,0,0,0.12);
  background: var(--bg2);
}
.post-item:hover::before { background: var(--accent); }

.post-notice {
  background: linear-gradient(90deg, rgba(255,107,157,0.08), transparent 70%);
  border-color: rgba(255,107,157,0.3);
}
.post-notice::before { background: var(--accent2) !important; }

.post-num {
  font-size: 13px; color: var(--text3); width: 38px;
  text-align: center; flex-shrink: 0; font-weight: 600;
  font-variant-numeric: tabular-nums;
  font-family: 'SF Mono', 'Consolas', monospace;
}

.post-content { flex: 1; min-width: 0; }

.post-title {
  font-size: 15px; font-weight: 700; margin-bottom: 6px;
  color: var(--text);
  display: flex; align-items: center; gap: 8px;
  letter-spacing: -0.2px;
}

.notice-tag {
  display: inline-block;
  background: linear-gradient(135deg, rgba(255,107,157,0.28), rgba(255,107,157,0.14));
  color: var(--accent2);
  font-size: 10px; font-weight: 800;
  padding: 3px 9px; border-radius: 6px;
  border: 1px solid rgba(255,107,157,0.35);
  letter-spacing: 0.6px;
  flex-shrink: 0;
}

.post-meta2 {
  font-size: 12px; color: var(--text2);
  display: flex; gap: 14px; align-items: center;
}
.post-meta2 .post-author {
  font-weight: 600; color: var(--text2);
  padding-right: 12px;
  border-right: 1px solid var(--border);
  display: inline-flex; align-items: center; gap: 6px;
}
.author-avatar-sm {
  width: 20px; height: 20px; border-radius: 50%;
  object-fit: cover;
  background: linear-gradient(135deg, #6c63ff, #ff6b9d);
  color: #fff;
  font-size: 10px; font-weight: 800;
  display: inline-flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.avatar-fallback { font-family: 'Pretendard', sans-serif; }

.post-stats {
  display: flex; gap: 16px; font-size: 12px;
  color: var(--text2); flex-shrink: 0;
  font-weight: 500;
}

.post-date {
  font-size: 12px; color: var(--text3);
  width: 88px; text-align: right; flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.empty-state {
  padding: 60px 40px; text-align: center; color: var(--text3);
  background: var(--bg2);
  border: 1px dashed var(--border);
  border-radius: 14px;
  font-size: 14px;
}

/* === 이미지 중심 4열 그리드 뷰 === */
.post-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 14px;
}

.post-grid-card {
  background: var(--card, rgba(255,255,255,0.04));
  border: 1px solid var(--border);
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
}

.post-grid-card:hover {
  transform: translateY(-2px);
  border-color: rgba(108,99,255,0.4);
  box-shadow: 0 10px 22px rgba(0,0,0,0.28);
}

.grid-thumb {
  position: relative;
  width: 100%;
  aspect-ratio: 5 / 4;
  background: var(--bg3);
  overflow: hidden;
}

.grid-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.3s;
}

.post-grid-card:hover .grid-thumb img {
  transform: scale(1.04);
}

.grid-thumb-empty {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  font-size: 32px;
  color: var(--text3);
  background: linear-gradient(135deg, #1a1060, #6c63ff);
  opacity: 0.5;
}

.grid-play {
  position: absolute;
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  width: 38px; height: 38px;
  background: rgba(0,0,0,0.6);
  color: #fff;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px;
  padding-left: 2px;
  backdrop-filter: blur(6px);
  transition: transform 0.2s, background 0.2s;
}
.post-grid-card:hover .grid-play {
  background: var(--accent, #6c63ff);
  transform: translate(-50%, -50%) scale(1.08);
}

.grid-body {
  padding: 10px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.grid-title {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--text);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.6em;
}

.grid-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: var(--text3);
  gap: 6px;
}

.grid-author {
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text2);
  font-weight: 500;
}

.author-avatar-xs {
  width: 16px; height: 16px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.avatar-fallback-xs {
  display: inline-flex; align-items: center; justify-content: center;
  background: var(--gradient, linear-gradient(135deg, #6c63ff, #ff6b9d));
  color: #fff;
  font-size: 9px;
  font-weight: 700;
}

.grid-stats {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.grid-empty {
  grid-column: 1 / -1;
}

@media (max-width: 700px) {
  .post-grid { grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 10px; }
  .grid-title { font-size: 12.5px; }
}

.pagination { display: flex; justify-content: center; gap: 8px; margin-top: 28px; }

.page-btn {
  min-width: 36px; height: 36px;
  padding: 0 12px; border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--bg2); color: var(--text2);
  font-size: 13px; font-weight: 600; cursor: pointer;
  transition: all 0.15s;
}
.page-btn:hover { background: var(--bg3); color: var(--text); border-color: rgba(108,99,255,0.3); }
.page-btn.active {
  background: linear-gradient(135deg, var(--accent), #8b7cff);
  color: white;
  border-color: var(--accent);
  font-weight: 800;
  box-shadow: 0 4px 12px rgba(108,99,255,0.25);
}

@media (max-width: 1024px) {
  .community-wrap { grid-template-columns: 1fr; }
  .community-sidebar { display: none; }
  .community-main { padding: 24px; }
}

@media (max-width: 480px) {
  .community-main { padding: 14px; }
  .community-header { flex-direction: column; align-items: flex-start; gap: 10px; }
  .post-stats, .post-date { display: none; }
  .post-item { padding: 14px 16px; gap: 12px; }
  .post-title { font-size: 14px; }
}
</style>
