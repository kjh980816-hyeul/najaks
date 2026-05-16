<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'

const route = useRoute()

const page = ref(null)
const isLoading = ref(false)
const error = ref(false)

async function fetchPage(slug) {
  if (!slug) return
  isLoading.value = true
  error.value = false
  page.value = null
  try {
    const { data } = await api.get(`/public/pages/${slug}`)
    const raw = data.data
    let sections = []
    try { sections = JSON.parse(raw.content) } catch { sections = [] }
    page.value = {
      title: raw.title,
      updated: raw.updatedAt ? raw.updatedAt.substring(0, 10) : '',
      sections
    }
  } catch {
    error.value = true
  } finally {
    isLoading.value = false
  }
}

watch(() => route.params.slug, (slug) => fetchPage(slug), { immediate: true })
</script>

<template>
  <div class="static-page">
    <div v-if="isLoading" class="loading">
      <p>페이지를 불러오는 중...</p>
    </div>

    <div v-else-if="error" class="not-found">
      <h1>페이지를 찾을 수 없습니다</h1>
      <router-link to="/" class="back-link">← 홈으로 돌아가기</router-link>
    </div>

    <div v-else-if="!page" class="not-found">
      <h1>페이지를 찾을 수 없습니다</h1>
      <router-link to="/" class="back-link">← 홈으로 돌아가기</router-link>
    </div>

    <div v-else class="static-card">
      <div class="page-header">
        <h1>{{ page.title }}</h1>
        <p class="updated">최종 업데이트: {{ page.updated }}</p>
      </div>

      <div class="sections">
        <section v-for="(s, idx) in page.sections" :key="idx">
          <h2>{{ s.heading }}</h2>
          <p>{{ s.body }}</p>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.static-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 3rem 1.5rem;
  font-family: 'Pretendard', sans-serif;
}

.static-card {
  background: var(--bg2, #12121a);
  border: 1px solid var(--border, rgba(255,255,255,0.07));
  border-radius: 16px;
  padding: 2.5rem 2.25rem;
}

.page-header {
  border-bottom: 1px solid var(--border, rgba(255,255,255,0.07));
  padding-bottom: 1.25rem;
  margin-bottom: 1.75rem;
}

.page-header h1 {
  font-size: 1.6rem;
  font-weight: 800;
  color: var(--text, #f0f0f8);
  margin: 0 0 0.5rem;
}

.updated {
  font-size: 0.78rem;
  color: var(--text3, #555577);
}

.sections {
  display: flex;
  flex-direction: column;
  gap: 1.75rem;
}

.sections h2 {
  font-size: 1.02rem;
  font-weight: 700;
  color: var(--accent, #6c63ff);
  margin: 0 0 0.6rem;
}

.sections p {
  font-size: 0.92rem;
  line-height: 1.75;
  color: var(--text2, #9999bb);
  white-space: pre-wrap;
  margin: 0;
}

.loading {
  text-align: center;
  padding: 4rem 0;
  color: var(--text2, #9999bb);
}

.not-found {
  text-align: center;
  padding: 4rem 0;
}

.not-found h1 {
  color: var(--text);
  font-size: 1.4rem;
  margin-bottom: 1rem;
}

.back-link {
  display: inline-block;
  margin-top: 1rem;
  color: var(--accent, #6c63ff);
  text-decoration: none;
  font-weight: 600;
}
.back-link:hover { text-decoration: underline; }
</style>
