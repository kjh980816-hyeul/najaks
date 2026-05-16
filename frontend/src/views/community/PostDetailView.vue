<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import api from '@/api'
import Pagination from '@/components/Pagination.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const notify = useNotificationStore()

const post = ref(null)
const comments = ref([])
const isLoading = ref(true)
const newComment = ref('')
const isSubmittingComment = ref(false)

const isOwner = computed(() =>
  authStore.user && post.value && authStore.user.id === post.value.authorId
)
const isAdmin = computed(() => authStore.user?.role === 'ADMIN')
const isStreamerOnlyPost = computed(() => post.value?.category === 'STREAMER_ONLY')

const commentPageSize = 10
const commentPage = ref(1)
const pagedComments = computed(() => {
  const s = (commentPage.value - 1) * commentPageSize
  return comments.value.slice(s, s + commentPageSize)
})
const commentTotalPages = computed(() => Math.max(Math.ceil(comments.value.length / commentPageSize), 1))
watch(() => comments.value.length, () => {
  if (commentPage.value > commentTotalPages.value) commentPage.value = commentTotalPages.value
})

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function linkify(text) {
  if (!text) return ''
  const escaped = escapeHtml(text)
  return escaped.replace(/https?:\/\/[^\s<]+/g, (url) => {
    const trailing = url.match(/[)\].,!?;:'"]+$/)
    let href = url
    let tail = ''
    if (trailing) {
      href = url.slice(0, -trailing[0].length)
      tail = trailing[0]
    }
    return `<a href="${href}" target="_blank" rel="noopener noreferrer">${href}</a>${tail}`
  })
}

const renderedPostContent = computed(() => linkify(post.value?.content))
function renderCommentContent(content) {
  return linkify(content)
}

async function fetchPost() {
  try {
    const { data } = await api.get(`/posts/${route.params.id}`)
    post.value = data.data
  } catch {
    notify.error('게시글을 불러올 수 없습니다')
  } finally {
    isLoading.value = false
  }
}

async function fetchComments() {
  try {
    const { data } = await api.get(`/posts/${route.params.id}/comments`)
    comments.value = data.data || []
  } catch {
    comments.value = []
  }
}

async function handleLike() {
  try {
    const { data } = await api.post(`/posts/${post.value.id}/like`)
    post.value = data.data
  } catch {
    notify.error('좋아요 처리에 실패했습니다')
  }
}

async function handleComment() {
  if (!newComment.value.trim()) return

  isSubmittingComment.value = true
  try {
    await api.post(`/posts/${post.value.id}/comments`, { content: newComment.value })
    newComment.value = ''
    fetchComments()
    post.value.commentCount++
  } catch {
    notify.error('댓글 작성에 실패했습니다')
  } finally {
    isSubmittingComment.value = false
  }
}

async function handleDeletePost() {
  try {
    await api.delete(`/posts/${post.value.id}`)
    notify.success('게시글이 삭제되었습니다')
    router.push('/community')
  } catch {
    notify.error('삭제에 실패했습니다')
  }
}

async function handleDeleteComment(commentId) {
  try {
    await api.delete(`/comments/${commentId}`)
    comments.value = comments.value.filter(c => c.id !== commentId)
    post.value.commentCount--
  } catch {
    notify.error('댓글 삭제에 실패했습니다')
  }
}

async function handleReport(targetType, targetId) {
  const reason = prompt('신고 사유를 입력해주세요')
  if (!reason) return

  try {
    await api.post('/reports', { targetType, targetId, reason })
    notify.success('신고가 접수되었습니다')
  } catch {
    notify.error('신고 처리에 실패했습니다')
  }
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
  return d.toLocaleDateString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

onMounted(() => {
  fetchPost()
  fetchComments()
})
</script>

<template>
  <div class="post-detail-page">
    <div v-if="isLoading" class="loading"><div class="spinner"></div></div>

    <template v-else-if="post">
      <div class="back-bar">
        <button class="back-btn" @click="router.back()">← 목록으로</button>
        <router-link
          v-if="post.targetStreamerId"
          :to="`/streamers/${post.targetStreamerId}`"
          class="board-badge"
        >🎯 {{ post.targetStreamerNickname }} 팬 게시판</router-link>
      </div>

      <div class="post-card">
        <div class="watermark-overlay" v-if="isStreamerOnlyPost">
          <div class="watermark-text" v-for="i in 12" :key="i">{{ authStore.userNickname || 'User' }} · #{{ authStore.user?.id || '0000' }}</div>
        </div>

        <div class="post-header">
          <h1 class="post-title">{{ post.title }}</h1>
          <div class="post-meta">
            <div class="author-block">
              <img
                v-if="post.authorProfileImage"
                :src="post.authorProfileImage"
                alt=""
                class="author-avatar author-avatar-img"
              />
              <div v-else class="author-avatar">{{ post.authorNickname?.charAt(0)?.toUpperCase() || '?' }}</div>
              <div class="author-info">
                <span class="author-name">{{ post.authorNickname }}</span>
                <span class="author-date">{{ formatDate(post.createdAt) }}</span>
              </div>
            </div>
            <div class="meta-right">
              <span class="meta-pill">👁 {{ post.viewCount }}</span>
              <span class="meta-pill">💬 {{ post.commentCount || 0 }}</span>
              <span class="meta-pill">♥ {{ post.likeCount || 0 }}</span>
            </div>
          </div>
        </div>

        <div class="post-body">
          <div class="post-content" v-html="renderedPostContent"></div>
          <div v-if="post.imageUrl" class="post-image">
            <img :src="post.imageUrl" alt="게시글 이미지" />
          </div>
        </div>

        <div class="post-actions">
          <button :class="['like-btn', { liked: post.liked }]" @click="handleLike">
            <span class="heart">{{ post.liked ? '♥' : '♡' }}</span>
            <span>좋아요 {{ post.likeCount }}</span>
          </button>
          <div class="actions-right">
            <button class="action-btn report" @click="handleReport('POST', post.id)">🚩 신고</button>
            <button
              v-if="isOwner || isAdmin"
              class="action-btn edit"
              @click="router.push({ path: '/community/write', query: { edit: post.id } })"
            >✏️ 수정</button>
            <button v-if="isOwner || isAdmin" class="action-btn danger" @click="handleDeletePost">삭제</button>
          </div>
        </div>
      </div>

      <!-- 댓글 -->
      <div class="comments-section">
        <div class="comments-header">
          <h3>💬 댓글 <span class="count">{{ comments.length }}</span></h3>
        </div>

        <form @submit.prevent="handleComment" class="comment-form">
          <textarea
            v-model="newComment"
            rows="3"
            placeholder="따뜻한 댓글을 남겨주세요"
          ></textarea>
          <div class="comment-form-actions">
            <span class="comment-hint">· 욕설·비방 댓글은 신고될 수 있습니다</span>
            <button type="submit" class="btn btn-primary btn-sm" :disabled="isSubmittingComment">
              {{ isSubmittingComment ? '작성 중...' : '댓글 작성' }}
            </button>
          </div>
        </form>

        <div class="comment-list">
          <div v-if="comments.length === 0" class="comment-empty">
            아직 댓글이 없습니다. 첫 댓글을 남겨보세요!
          </div>
          <div v-for="comment in pagedComments" :key="comment.id" class="comment-item">
            <div class="comment-avatar">{{ comment.authorNickname?.charAt(0)?.toUpperCase() || '?' }}</div>
            <div class="comment-body">
              <div class="comment-header">
                <span class="comment-author">{{ comment.authorNickname }}</span>
                <span class="comment-date">{{ formatDate(comment.createdAt) }}</span>
              </div>
              <p class="comment-content" v-html="renderCommentContent(comment.content)"></p>
              <div class="comment-actions">
                <button class="action-btn-sm report" @click="handleReport('COMMENT', comment.id)">🚩 신고</button>
                <button
                  v-if="authStore.user?.id === comment.authorId || isAdmin"
                  class="action-btn-sm danger"
                  @click="handleDeleteComment(comment.id)"
                >삭제</button>
              </div>
            </div>
          </div>
          <Pagination
            v-if="comments.length > 0"
            :current-page="commentPage"
            :total-pages="commentTotalPages"
            @change="p => commentPage = p"
          />
        </div>
      </div>
    </template>

    <template v-else>
      <div class="not-found">
        <h1>게시글을 찾을 수 없습니다</h1>
        <router-link to="/community" class="btn btn-primary">목록으로</router-link>
      </div>
    </template>
  </div>
</template>

<style scoped>
.post-detail-page {
  max-width: 860px;
  margin: 0 auto;
  padding: 2.5rem 1.25rem 4rem;
  font-family: 'Pretendard', sans-serif;
}

.loading { text-align: center; padding: 6rem 0; }

.spinner {
  width: 40px; height: 40px;
  border: 3px solid var(--border); border-top-color: var(--accent);
  border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto;
}

@keyframes spin { to { transform: rotate(360deg); } }

/* ─── Back bar ─── */
.back-bar {
  margin-bottom: 1rem;
  display: flex;
  align-items: center;
  gap: 12px;
}
.board-badge {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 14px;
  background: rgba(108,99,255,0.15);
  border: 1px solid rgba(108,99,255,0.35);
  color: var(--accent);
  border-radius: 999px;
  font-size: 0.82rem;
  font-weight: 700;
  text-decoration: none;
  transition: all 0.15s;
}
.board-badge:hover { background: var(--accent); color: #fff; }
.back-btn {
  background: none;
  border: none;
  color: var(--text2);
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  padding: 0.5rem 0.75rem;
  border-radius: 8px;
  transition: all 0.15s;
}
.back-btn:hover {
  color: var(--accent);
  background: rgba(108,99,255,0.08);
}

/* ─── Post card ─── */
.post-card {
  position: relative;
  overflow: hidden;
  background: var(--bg2);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 2.75rem 2.5rem;
  margin-bottom: 1.5rem;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08), 0 1px 3px rgba(0,0,0,0.05);
}

.post-header {
  margin-bottom: 2rem;
  padding-bottom: 1.5rem;
  border-bottom: 1px solid var(--border);
}

.post-title {
  font-size: 1.75rem;
  font-weight: 800;
  color: var(--text);
  line-height: 1.35;
  letter-spacing: -0.5px;
  margin-bottom: 1.25rem;
  word-break: break-word;
}

.post-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.author-block {
  display: flex;
  align-items: center;
  gap: 12px;
}

.author-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6c63ff, #ff6b9d);
  color: white;
  font-size: 16px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(108,99,255,0.25);
}
.author-avatar-img {
  object-fit: cover;
  background: var(--bg3);
}

.author-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.author-name {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--text);
}

.author-date {
  font-size: 0.78rem;
  color: var(--text3);
  font-variant-numeric: tabular-nums;
}

.meta-right {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.meta-pill {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--text2);
  background: var(--bg3);
  border: 1px solid var(--border);
  padding: 6px 12px;
  border-radius: 20px;
}

/* ─── Post body ─── */
.post-body {
  padding: 0.5rem 0 1rem;
}

.post-content {
  color: var(--text);
  line-height: 1.85;
  font-size: 1rem;
  white-space: pre-line;
  min-height: 120px;
  word-break: break-word;
}
.post-content a,
.comment-content a {
  color: var(--brand, #c084ff);
  text-decoration: underline;
  word-break: break-all;
}
.post-content a:hover,
.comment-content a:hover {
  opacity: 0.85;
}

.post-image {
  margin: 1.75rem 0 0.5rem;
}
.post-image img {
  width: 100%;
  max-height: 600px;
  object-fit: contain;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--bg3);
}

.post-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding-top: 1.75rem;
  border-top: 1px solid var(--border);
  margin-top: 1.75rem;
}

.actions-right {
  display: flex;
  gap: 0.6rem;
  align-items: center;
}

.like-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 22px;
  border: 1.5px solid var(--border);
  border-radius: 999px;
  background: var(--bg3);
  color: var(--text2);
  font-size: 0.92rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}

.like-btn .heart {
  font-size: 1.15rem;
  transition: transform 0.2s;
}

.like-btn:hover {
  border-color: var(--accent2);
  color: var(--accent2);
  transform: translateY(-1px);
}
.like-btn:hover .heart { transform: scale(1.15); }

.like-btn.liked {
  border-color: var(--accent2);
  color: #fff;
  background: linear-gradient(135deg, #ff6b9d, #ff8fb8);
  box-shadow: 0 4px 16px rgba(255,107,157,0.35);
}

.action-btn, .action-btn-sm {
  padding: 0.5rem 0.85rem;
  border: 1px solid transparent;
  background: none;
  color: var(--text3);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  border-radius: 8px;
}

.action-btn:hover, .action-btn-sm:hover { color: var(--text); background: var(--bg3); }
.action-btn.danger, .action-btn-sm.danger { color: #ef4444; }
.action-btn.danger:hover, .action-btn-sm.danger:hover { color: #f87171; background: rgba(239,68,68,0.08); }
.action-btn.edit { color: var(--accent); border: 1px solid rgba(108,99,255,0.35); background: rgba(108,99,255,0.08); }
.action-btn.edit:hover { color: #fff; background: var(--accent); border-color: var(--accent); }

.action-btn.report, .action-btn-sm.report {
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.35);
  background: rgba(239, 68, 68, 0.08);
}
.action-btn.report:hover, .action-btn-sm.report:hover {
  color: #fff;
  background: #ef4444;
  border-color: #ef4444;
}

/* ─── Comments ─── */
.comments-section {
  background: var(--bg2);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 2rem 2.25rem;
  box-shadow: 0 4px 20px rgba(0,0,0,0.06);
}

.comments-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 1.25rem;
  margin-bottom: 1.5rem;
  border-bottom: 1px solid var(--border);
}

.comments-section h3 {
  font-size: 1.1rem;
  font-weight: 800;
  color: var(--text);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.comments-section h3 .count {
  color: var(--accent);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.comment-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-bottom: 2rem;
  padding: 1.25rem;
  background: var(--bg3);
  border: 1px solid var(--border);
  border-radius: 14px;
  transition: border-color 0.2s;
}
.comment-form:focus-within {
  border-color: rgba(108,99,255,0.4);
  box-shadow: 0 0 0 3px rgba(108,99,255,0.1);
}

.comment-form textarea {
  padding: 0;
  border: none;
  font-size: 0.95rem;
  outline: none;
  font-family: 'Pretendard', sans-serif;
  width: 100%;
  box-sizing: border-box;
  background: transparent;
  color: var(--text);
  resize: vertical;
  line-height: 1.6;
}

.comment-form textarea::placeholder { color: var(--text3); }

.comment-form-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 0.5rem;
  border-top: 1px solid var(--border);
}

.comment-hint {
  font-size: 0.75rem;
  color: var(--text3);
}

.btn { padding: 0.6rem 1.25rem; border: none; border-radius: 10px; font-size: 0.9rem; font-weight: 700; cursor: pointer; text-decoration: none; transition: all 0.2s; font-family: 'Pretendard', sans-serif; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-primary {
  background: var(--gradient);
  color: #fff;
  box-shadow: 0 4px 12px rgba(108,99,255,0.25);
}
.btn-primary:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 6px 16px rgba(108,99,255,0.35); }
.btn-sm { padding: 0.5rem 1.1rem; font-size: 0.84rem; }

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.comment-empty {
  text-align: center;
  color: var(--text3);
  font-size: 0.9rem;
  padding: 2rem 0;
}

.comment-item {
  display: flex;
  gap: 14px;
  padding: 1.25rem 0;
  border-bottom: 1px solid var(--border);
}

.comment-item:last-child { border-bottom: none; padding-bottom: 0; }
.comment-item:first-child { padding-top: 0; }

.comment-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #00d4aa, #6c63ff);
  color: white;
  font-size: 13px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-header {
  display: flex;
  gap: 0.75rem;
  align-items: baseline;
  margin-bottom: 0.5rem;
  font-size: 0.85rem;
}

.comment-author { font-weight: 700; color: var(--text); }
.comment-date { color: var(--text3); font-size: 0.76rem; font-variant-numeric: tabular-nums; }

.comment-content {
  color: var(--text);
  line-height: 1.65;
  font-size: 0.93rem;
  word-break: break-word;
  margin-bottom: 0.4rem;
}

.comment-actions {
  display: flex;
  gap: 0.4rem;
  margin-top: 0.25rem;
}

.not-found { text-align: center; padding: 6rem 1rem; }
.not-found h1 { margin-bottom: 1.5rem; font-size: 1.25rem; color: var(--text); }

.watermark-overlay {
  position: absolute; inset: 0; pointer-events: none; overflow: hidden; z-index: 10;
  display: grid; grid-template-columns: repeat(3, 1fr); grid-template-rows: repeat(4, 1fr);
}
.watermark-text {
  display: flex; align-items: center; justify-content: center;
  color: rgba(255,255,255,0.04); font-size: 13px; font-weight: 700;
  white-space: nowrap; transform: rotate(-30deg); user-select: none;
}

@media (max-width: 640px) {
  .post-detail-page { padding: 1.5rem 0.75rem 3rem; }
  .post-card { padding: 1.75rem 1.5rem; border-radius: 16px; }
  .post-title { font-size: 1.3rem; }
  .post-meta { flex-direction: column; align-items: flex-start; }
  .comments-section { padding: 1.5rem 1.25rem; border-radius: 16px; }
  .post-actions { flex-direction: column; align-items: stretch; }
  .actions-right { justify-content: flex-end; }
}
</style>
