<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import api from '@/api'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const notify = useNotificationStore()

const streamer = ref(null)
const posts = ref([])
const currentPage = ref(1)
const totalPages = ref(1)
const loading = ref(false)
const pageSize = 10

async function fetchStreamer() {
  try {
    const { data } = await api.get(`/public/streamers/${route.params.id}`)
    streamer.value = data.data
  } catch {
    notify.error('스트리머 정보를 불러올 수 없습니다')
  }
}

async function fetchPosts() {
  loading.value = true
  try {
    const { data } = await api.get(
      `/public/streamers/${route.params.id}/posts?page=${currentPage.value - 1}&size=${pageSize}`
    )
    posts.value = data.data?.content || []
    totalPages.value = data.data?.totalPages || 1
  } catch {
    posts.value = []
  }
  loading.value = false
}

function goWrite() {
  if (!authStore.user) {
    notify.warning('로그인이 필요합니다')
    router.push('/login')
    return
  }
  router.push({ path: '/community/write', query: { streamerBoard: route.params.id } })
}

function goToPage(page) {
  currentPage.value = page
  fetchPosts()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = Array.isArray(dateStr)
    ? new Date(dateStr[0], dateStr[1] - 1, dateStr[2], dateStr[3] || 0, dateStr[4] || 0)
    : new Date(dateStr)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleDateString('ko-KR', { year: '2-digit', month: '2-digit', day: '2-digit' })
    .replace(/\. /g, '.').replace(/\.$/, '')
}

onMounted(() => {
  fetchStreamer()
  fetchPosts()
})

watch(() => route.params.id, () => {
  currentPage.value = 1
  fetchStreamer()
  fetchPosts()
})
</script>

<template>
  <div class="board-page">
    <div class="board-header">
      <router-link :to="`/streamers/${route.params.id}`" class="back-link">
        ← {{ streamer?.nickname || '스트리머' }} 프로필로
      </router-link>
      <div class="board-title-row">
        <div class="board-title-wrap">
          <img
            v-if="streamer?.profileImage"
            :src="streamer.profileImage"
            alt=""
            class="board-streamer-avatar"
          />
          <div v-else class="board-streamer-avatar avatar-fallback">
            {{ (streamer?.nickname || '?').charAt(0).toUpperCase() }}
          </div>
          <div>
            <h1>💬 {{ streamer?.nickname || '스트리머' }} 팬 게시판</h1>
            <p>팬들과 자유롭게 소통해보세요</p>
          </div>
        </div>
        <button class="btn btn-primary" @click="goWrite">✏️ 글쓰기</button>
      </div>
    </div>

    <div class="post-list" v-if="posts.length">
      <router-link
        v-for="post in posts"
        :key="post.id"
        :to="`/community/${post.id}`"
        class="post-item"
      >
        <div class="post-num">{{ post.id }}</div>
        <div class="post-content">
          <div class="post-title">{{ post.title }}</div>
          <div class="post-meta">
            <span class="post-author">
              <img
                v-if="post.authorProfileImage"
                :src="post.authorProfileImage"
                alt=""
                class="author-avatar"
              />
              <span v-else class="author-avatar avatar-fallback">{{ (post.authorNickname || '?').charAt(0).toUpperCase() }}</span>
              {{ post.authorNickname || '익명' }}
            </span>
            <span>💬 {{ post.commentCount || 0 }}</span>
            <span>👍 {{ post.likeCount || 0 }}</span>
          </div>
        </div>
        <div class="post-date">{{ formatDate(post.createdAt) }}</div>
      </router-link>
    </div>

    <div v-else-if="!loading" class="empty-state">
      아직 게시글이 없습니다. 첫 글을 남겨보세요!
    </div>

    <div v-if="loading" class="loading">
      <div class="spinner"></div>
    </div>

    <div class="pagination" v-if="totalPages > 1">
      <button
        v-for="page in Math.min(totalPages, 5)"
        :key="page"
        class="page-btn"
        :class="{ active: page === currentPage }"
        @click="goToPage(page)"
      >{{ page }}</button>
      <button
        v-if="currentPage < totalPages"
        class="page-btn"
        @click="goToPage(currentPage + 1)"
      >→</button>
    </div>
  </div>
</template>

<style scoped>
.board-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 2rem 1.25rem 4rem;
  font-family: 'Pretendard', sans-serif;
}

.board-header { margin-bottom: 2rem; }

.back-link {
  display: inline-block;
  color: var(--text2);
  font-size: 0.88rem;
  font-weight: 600;
  text-decoration: none;
  margin-bottom: 1rem;
  padding: 0.5rem 0.75rem;
  border-radius: 8px;
  transition: all 0.15s;
}
.back-link:hover { color: var(--accent); background: rgba(108,99,255,0.08); }

.board-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.board-title-wrap { display: flex; align-items: center; gap: 14px; }

.board-streamer-avatar {
  width: 56px; height: 56px; border-radius: 50%;
  object-fit: cover; flex-shrink: 0;
}
.board-streamer-avatar.avatar-fallback {
  background: linear-gradient(135deg, #6c63ff, #ff6b9d);
  color: #fff; font-size: 22px; font-weight: 800;
  display: flex; align-items: center; justify-content: center;
}

.board-header h1 { font-size: 1.5rem; font-weight: 800; color: var(--text); margin-bottom: 4px; }
.board-header p { font-size: 0.88rem; color: var(--text2); }

.btn {
  padding: 0.75rem 1.5rem; border: none; border-radius: 10px;
  font-size: 0.95rem; font-weight: 700; cursor: pointer;
  font-family: 'Pretendard', sans-serif; transition: all 0.2s;
}
.btn-primary {
  background: var(--gradient, linear-gradient(135deg, #6c63ff, #ff6b9d));
  color: #fff;
  box-shadow: 0 4px 14px rgba(108,99,255,0.3);
}
.btn-primary:hover { transform: translateY(-1px); box-shadow: 0 6px 18px rgba(108,99,255,0.4); }

.post-list { display: flex; flex-direction: column; gap: 10px; }

.post-item {
  display: flex; align-items: center; gap: 18px;
  padding: 18px 22px;
  background: var(--bg2);
  border: 1px solid var(--border);
  border-radius: 14px;
  text-decoration: none;
  transition: all 0.18s;
}
.post-item:hover {
  transform: translateY(-2px);
  border-color: rgba(108,99,255,0.35);
  box-shadow: 0 8px 24px rgba(108,99,255,0.08);
}

.post-num {
  width: 38px; text-align: center; flex-shrink: 0;
  font-size: 13px; font-weight: 600; color: var(--text3);
  font-variant-numeric: tabular-nums;
}

.post-content { flex: 1; min-width: 0; }

.post-title {
  font-size: 15px; font-weight: 700; margin-bottom: 6px;
  color: var(--text); letter-spacing: -0.2px;
}

.post-meta {
  font-size: 12px; color: var(--text2);
  display: flex; gap: 14px; align-items: center;
}

.post-author {
  display: inline-flex; align-items: center; gap: 6px;
  font-weight: 600;
  padding-right: 12px;
  border-right: 1px solid var(--border);
}

.author-avatar {
  width: 20px; height: 20px; border-radius: 50%;
  object-fit: cover;
  background: linear-gradient(135deg, #6c63ff, #ff6b9d);
  color: #fff; font-size: 10px; font-weight: 800;
  display: inline-flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}

.post-date {
  font-size: 12px; color: var(--text3);
  width: 80px; text-align: right; flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.empty-state {
  padding: 60px 40px; text-align: center; color: var(--text3);
  background: var(--bg2);
  border: 1px dashed var(--border);
  border-radius: 14px;
  font-size: 14px;
}

.loading { text-align: center; padding: 4rem 0; }
.spinner {
  width: 40px; height: 40px;
  border: 3px solid var(--border); border-top-color: var(--accent);
  border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto;
}
@keyframes spin { to { transform: rotate(360deg); } }

.pagination { display: flex; justify-content: center; gap: 8px; margin-top: 28px; }
.page-btn {
  min-width: 36px; height: 36px; padding: 0 12px; border-radius: 10px;
  border: 1px solid var(--border); background: var(--bg2);
  color: var(--text2); font-size: 13px; font-weight: 600; cursor: pointer;
  transition: all 0.15s;
}
.page-btn:hover { background: var(--bg3); color: var(--text); }
.page-btn.active {
  background: linear-gradient(135deg, var(--accent), #8b7cff);
  color: #fff; border-color: var(--accent); font-weight: 800;
}

@media (max-width: 640px) {
  .post-date { display: none; }
  .post-item { padding: 14px 16px; gap: 12px; }
  .board-title-row { flex-direction: column; align-items: flex-start; }
}
</style>
