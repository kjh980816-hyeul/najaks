<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import BoardIcon from '@/components/common/BoardIcon.vue'
import api from '@/api'

const router = useRouter()
const authStore = useAuthStore()

const posts = ref([])
const activeBoard = ref('PORTFOLIO')
const loading = ref(false)
const currentPage = ref(1)
const totalPages = ref(1)

const isStreamer = computed(() => authStore.user?.role === 'STREAMER' || authStore.user?.role === 'ADMIN')

const boards = [
  { key: 'PORTFOLIO', icon: 'briefcase', label: '포트폴리오', categories: ['PORTFOLIO'] },
  { key: 'WORK_REVIEW', icon: 'star', label: '작업 후기', categories: ['WORK_REVIEW'] },
  { key: 'JOB_SEEKING', icon: 'search', label: '구직', categories: ['JOB_SEEKING'] },
  { key: 'RECRUITMENT', icon: 'users', label: '모집', categories: ['RECRUITMENT'] },
]

const activeBoardObj = computed(() => boards.find(b => b.key === activeBoard.value) || boards[0])

const categoryLabelMap = {
  PORTFOLIO: '포트폴리오',
  WORK_REVIEW: '작업 후기',
  JOB_SEEKING: '구직',
  RECRUITMENT: '모집',
}

async function fetchPosts() {
  loading.value = true
  try {
    const cats = activeBoardObj.value.categories
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
  activeBoard.value = key
  currentPage.value = 1
  fetchPosts()
}

function goToPage(page) {
  currentPage.value = page
  fetchPosts()
}

function goToWrite() {
  router.push({ path: '/community/write', query: { category: activeBoard.value } })
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('ko-KR', { month: '2-digit', day: '2-digit' })
}

onMounted(() => { fetchPosts() })
</script>

<template>
  <div class="creator-page">
    <div class="creator-wrap">
      <div class="creator-sidebar">
        <div class="sidebar-title">구인/구직</div>
        <div
          v-for="board in boards" :key="board.key"
          class="sidebar-menu-item"
          :class="{ active: activeBoard === board.key }"
          @click="selectBoard(board.key)"
        >
          <BoardIcon :name="board.icon" :size="18" class="icon" /> {{ board.label }}
        </div>
      </div>

      <div class="creator-main">
        <div class="creator-header">
          <div class="creator-title-wrap">
            <h2>{{ activeBoardObj.label }}</h2>
            <p>스트리머와 크리에이터가 서로를 찾는 공간입니다.</p>
          </div>
          <div class="creator-actions">
            <button v-if="authStore.isAuthenticated" class="btn btn-primary btn-sm" @click="goToWrite">✏️ 글쓰기</button>
          </div>
        </div>

        <div v-if="loading" class="loading-state"><div class="spinner"></div></div>

        <div class="post-list" v-else-if="posts.length > 0">
          <div
            v-for="post in posts" :key="post.id"
            class="post-item"
            @click="router.push(`/community/${post.id}`)"
          >
            <div class="post-num">{{ post.id }}</div>
            <div class="post-content">
              <div class="post-title">
                <span class="cat-tag" v-if="categoryLabelMap[post.category]">{{ categoryLabelMap[post.category] }}</span>
                {{ post.title }}
              </div>
              <div class="post-meta2">
                <span>{{ post.authorNickname || '익명' }}</span>
                <span>💬 {{ post.commentCount || 0 }}</span>
                <span>💙 {{ post.likeCount || 0 }}</span>
              </div>
            </div>
            <div class="post-stats">
              <span>💬 {{ post.commentCount || 0 }}</span>
              <span>💙 {{ post.likeCount || 0 }}</span>
              <span>👁 {{ post.viewCount || 0 }}</span>
            </div>
            <div class="post-date">{{ formatDate(post.createdAt) }}</div>
          </div>
        </div>

        <div v-else class="empty-state">아직 게시글이 없습니다. 첫 글을 작성해보세요!</div>

        <div class="pagination" v-if="totalPages > 1">
          <button v-if="currentPage > 1" class="page-btn" @click="goToPage(currentPage - 1)">←</button>
          <button
            v-for="page in Math.min(totalPages, 5)" :key="page"
            class="page-btn" :class="{ active: page === currentPage }"
            @click="goToPage(page)"
          >{{ page }}</button>
          <button v-if="currentPage < totalPages" class="page-btn" @click="goToPage(currentPage + 1)">→</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.creator-page { width: 100%; }

.creator-wrap {
  display: grid;
  grid-template-columns: 240px 1fr;
  min-height: calc(100vh - 200px);
}

.creator-sidebar {
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

.creator-main { padding: 28px 32px; }

.creator-header {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px;
}

.creator-title-wrap h2 { font-size: 22px; font-weight: 800; margin-bottom: 4px; }
.creator-title-wrap p { font-size: 13px; color: var(--text2); }

.creator-actions { display: flex; gap: 10px; align-items: center; }

.btn { padding: 0.55rem 1.1rem; border: none; border-radius: 8px; font-size: 0.88rem; font-weight: 600; cursor: pointer; transition: all 0.2s; font-family: 'Pretendard', sans-serif; }
.btn-primary { background: var(--gradient); color: #fff; }
.btn-primary:hover { opacity: 0.9; }
.btn-sm { padding: 0.45rem 0.9rem; font-size: 0.82rem; }

.loading-state { display: flex; justify-content: center; padding: 3rem 0; }
.spinner { width: 36px; height: 36px; border: 3px solid var(--border); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.post-list {
  display: flex; flex-direction: column; gap: 1px;
  background: var(--border); border-radius: 12px; overflow: hidden;
}

.post-item {
  display: flex; align-items: center; gap: 16px;
  padding: 16px 20px; background: var(--bg2);
  cursor: pointer; transition: background 0.15s;
}

.post-item:hover { background: var(--bg3); }

.post-num { font-size: 13px; color: var(--text3); width: 32px; text-align: center; flex-shrink: 0; }
.post-content { flex: 1; }
.post-title { font-size: 14px; font-weight: 600; margin-bottom: 4px; }

.cat-tag {
  display: inline-block; background: rgba(108,99,255,0.15); color: var(--accent);
  font-size: 10px; font-weight: 700; padding: 1px 6px; border-radius: 4px; margin-right: 6px;
}

.post-meta2 { font-size: 12px; color: var(--text2); display: flex; gap: 12px; }
.post-stats { display: flex; gap: 16px; font-size: 12px; color: var(--text2); }
.post-date { font-size: 12px; color: var(--text3); width: 80px; text-align: right; flex-shrink: 0; }

.empty-state {
  padding: 40px; text-align: center; color: var(--text3); background: var(--bg2);
  border: 1px solid var(--border); border-radius: 12px;
}

.pagination { display: flex; justify-content: center; gap: 6px; margin-top: 24px; }

.page-btn {
  padding: 6px 12px; border-radius: 6px; border: 1px solid var(--border);
  background: transparent; color: var(--text2); font-size: 13px; cursor: pointer;
  font-family: 'Pretendard', sans-serif;
}

.page-btn.active { background: var(--accent); color: white; border-color: var(--accent); font-weight: 700; }

@media (max-width: 1024px) {
  .creator-wrap { grid-template-columns: 1fr; }
  .creator-sidebar { display: none; }
  .creator-main { padding: 24px; }
}

@media (max-width: 480px) {
  .creator-main { padding: 14px; }
  .creator-header { flex-direction: column; align-items: flex-start; gap: 10px; }
  .post-stats, .post-date { display: none; }
}
</style>
