<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api'
import Pagination from '@/components/Pagination.vue'

const route = useRoute()
const router = useRouter()

const query = ref(route.query.q || '')
const results = ref({ streamers: [], contents: [], clips: [] })
const loading = ref(false)
const totalCount = ref(0)

const pageSize = 10
const streamerPage = ref(1)
const contentPage = ref(1)
const clipPage = ref(1)

const pagedStreamers = computed(() => {
  const list = results.value.streamers || []
  const s = (streamerPage.value - 1) * pageSize
  return list.slice(s, s + pageSize)
})
const pagedContents = computed(() => {
  const list = results.value.contents || []
  const s = (contentPage.value - 1) * pageSize
  return list.slice(s, s + pageSize)
})
const pagedClips = computed(() => {
  const list = results.value.clips || []
  const s = (clipPage.value - 1) * pageSize
  return list.slice(s, s + pageSize)
})
const streamerTotalPages = computed(() => Math.max(Math.ceil((results.value.streamers?.length || 0) / pageSize), 1))
const contentTotalPages = computed(() => Math.max(Math.ceil((results.value.contents?.length || 0) / pageSize), 1))
const clipTotalPages = computed(() => Math.max(Math.ceil((results.value.clips?.length || 0) / pageSize), 1))

async function search() {
  if (!query.value.trim()) return
  loading.value = true
  streamerPage.value = 1
  contentPage.value = 1
  clipPage.value = 1
  try {
    const { data } = await api.get(`/public/search?q=${encodeURIComponent(query.value)}`)
    results.value = data.data || { streamers: [], contents: [], clips: [] }
    totalCount.value = (results.value.streamers?.length || 0) + (results.value.contents?.length || 0) + (results.value.clips?.length || 0)
  } catch {
    results.value = { streamers: [], contents: [], clips: [] }
    totalCount.value = 0
  }
  loading.value = false
}

function getClipThumbnail(url) {
  if (!url) return null
  const ytMatch = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/|youtube\.com\/shorts\/)([a-zA-Z0-9_-]{11})/)
  if (ytMatch) return `https://img.youtube.com/vi/${ytMatch[1]}/hqdefault.jpg`
  if (url.includes('chzzk.naver.com')) return '/chzzk-logo.png'
  return null
}

onMounted(search)

watch(() => route.query.q, (newQ) => {
  query.value = newQ || ''
  search()
})
</script>

<template>
  <div class="search-page">
    <div class="search-header">
      <h1>"{{ query }}" 검색 결과</h1>
      <p v-if="!loading">총 {{ totalCount }}건</p>
    </div>

    <div v-if="loading" class="loading"><div class="spinner"></div></div>

    <template v-else>
      <!-- 스트리머 -->
      <div v-if="results.streamers?.length" class="result-section">
        <h2>스트리머 ({{ results.streamers.length }})</h2>
        <div class="result-grid">
          <div v-for="s in pagedStreamers" :key="s.userId" class="result-card" @click="router.push(`/streamers/${s.userId}`)">
            <div class="result-avatar">
              <img v-if="s.avatar || s.profileImage" :src="s.avatar || s.profileImage" alt="" />
              <div v-else class="avatar-fallback">{{ s.nickname?.charAt(0) }}</div>
            </div>
            <div class="result-info">
              <strong>{{ s.nickname }}</strong>
              <span class="result-sub">{{ s.bio || '스트리머' }}</span>
            </div>
          </div>
        </div>
        <Pagination :current-page="streamerPage" :total-pages="streamerTotalPages" @change="p => streamerPage = p" />
      </div>

      <!-- 컨텐츠 -->
      <div v-if="results.contents?.length" class="result-section">
        <h2>컨텐츠 ({{ results.contents.length }})</h2>
        <div class="result-grid">
          <div v-for="c in pagedContents" :key="c.id" class="result-card" @click="router.push(`/contents/${c.id}`)">
            <div class="result-thumb" :style="c.thumbnailUrl ? { backgroundImage: `url(${c.thumbnailUrl})` } : {}">
              <span v-if="!c.thumbnailUrl" class="thumb-icon">🎯</span>
            </div>
            <div class="result-info">
              <strong>{{ c.title }}</strong>
              <span class="result-sub">{{ c.streamerNickname }} · {{ c.category }}</span>
            </div>
          </div>
        </div>
        <Pagination :current-page="contentPage" :total-pages="contentTotalPages" @change="p => contentPage = p" />
      </div>

      <!-- 클립 -->
      <div v-if="results.clips?.length" class="result-section">
        <h2>클립 ({{ results.clips.length }})</h2>
        <div class="result-grid">
          <a v-for="c in pagedClips" :key="c.id" :href="c.url" target="_blank" class="result-card">
            <div class="result-thumb" :class="{ 'chzzk-bg': c.url?.includes('chzzk.naver.com') && !c.thumbnailUrl }" :style="getClipThumbnail(c.url) && !c.url?.includes('chzzk.naver.com') ? { backgroundImage: `url(${getClipThumbnail(c.url)})` } : {}">
              <img v-if="c.url?.includes('chzzk.naver.com') && !c.thumbnailUrl" src="/chzzk-logo.png" alt="치지직" class="chzzk-thumb-logo" />
              <span v-else-if="!getClipThumbnail(c.url)" class="thumb-icon">🎬</span>
            </div>
            <div class="result-info">
              <strong>{{ c.title }}</strong>
              <span class="result-sub">{{ c.streamerNickname }}</span>
            </div>
          </a>
        </div>
        <Pagination :current-page="clipPage" :total-pages="clipTotalPages" @change="p => clipPage = p" />
      </div>

      <!-- 결과 없음 -->
      <div v-if="totalCount === 0 && !loading" class="empty">
        <p>"{{ query }}"에 대한 검색 결과가 없습니다.</p>
      </div>
    </template>
  </div>
</template>

<style scoped>
.search-page { max-width: 900px; margin: 0 auto; padding: 2rem 1.5rem; }
.search-header { margin-bottom: 2rem; }
.search-header h1 { font-size: 1.5rem; font-weight: 800; margin-bottom: 0.25rem; }
.search-header p { color: var(--text2); font-size: 0.9rem; }

.loading { text-align: center; padding: 4rem 0; }
.spinner { width: 36px; height: 36px; border: 3px solid var(--border); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto; }
@keyframes spin { to { transform: rotate(360deg); } }

.result-section { margin-bottom: 2.5rem; }
.result-section h2 { font-size: 1.1rem; font-weight: 700; margin-bottom: 1rem; padding-bottom: 0.5rem; border-bottom: 1px solid var(--border); }

.result-grid { display: flex; flex-direction: column; gap: 0.5rem; }

.result-card {
  display: flex; align-items: center; gap: 1rem; padding: 0.875rem 1rem;
  background: var(--card, rgba(255,255,255,0.04)); border: 1px solid var(--border);
  border-radius: 12px; cursor: pointer; transition: all 0.2s; text-decoration: none; color: var(--text);
}
.result-card:hover { background: var(--card-hover, rgba(255,255,255,0.07)); border-color: rgba(108,99,255,0.3); }

.result-avatar { width: 44px; height: 44px; border-radius: 12px; overflow: hidden; flex-shrink: 0; background: var(--bg3); }
.result-avatar img { width: 100%; height: 100%; object-fit: cover; }
.avatar-fallback { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; background: var(--accent); color: #fff; font-weight: 700; font-size: 1.1rem; }

.result-thumb { width: 80px; height: 50px; border-radius: 8px; flex-shrink: 0; background: var(--bg3); background-size: cover; background-position: center; display: flex; align-items: center; justify-content: center; position: relative; }
.result-thumb.chzzk-bg { background: #0b0b0b; }
.chzzk-thumb-logo { width: 32px; height: 32px; border-radius: 6px; }
.thumb-icon { font-size: 1.25rem; }

.result-info { display: flex; flex-direction: column; gap: 0.15rem; min-width: 0; }
.result-info strong { font-size: 0.95rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.result-sub { font-size: 0.8rem; color: var(--text2); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.empty { text-align: center; padding: 4rem 1rem; color: var(--text3); font-size: 1rem; }
</style>
