<script setup>
import { ref, onMounted, computed } from 'vue'
import api from '@/api'

const allResources = ref([])
const loading = ref(true)

const categoryIcons = { '방송 도구': '🎙️', '디자인/편집': '🎨', '음악/효과음': '🎵', '채팅/커뮤니티': '💬', '수익화/분석': '📊', '기타': '🔗' }

async function fetchResources() {
  try {
    const { data } = await api.get('/public/resources')
    allResources.value = data.data || []
  } catch { /* silent */ }
  loading.value = false
}

onMounted(fetchResources)

const activeCategory = ref('')

const categories = computed(() => {
  const map = {}
  for (const r of allResources.value) {
    if (!map[r.category]) {
      map[r.category] = { name: r.category, icon: categoryIcons[r.category] || '🔗', links: [] }
    }
    map[r.category].links.push({ name: r.name, url: r.url, desc: r.description || '' })
  }
  return Object.values(map)
})
</script>

<template>
  <div class="resources-page">
    <div class="resources-header">
      <h1>스트리머 도구 모음</h1>
      <p>방송에 도움이 되는 유용한 사이트와 도구를 모아두었습니다.</p>
    </div>

    <div class="category-filters">
      <button :class="['filter-btn', { active: activeCategory === '' }]" @click="activeCategory = ''">전체</button>
      <button v-for="cat in categories" :key="cat.name" :class="['filter-btn', { active: activeCategory === cat.name }]" @click="activeCategory = cat.name">
        {{ cat.icon }} {{ cat.name }}
      </button>
    </div>

    <div v-for="cat in categories" :key="cat.name">
      <template v-if="!activeCategory || activeCategory === cat.name">
        <div class="category-section">
          <h2>{{ cat.icon }} {{ cat.name }}</h2>
          <div class="resource-grid">
            <a v-for="link in cat.links" :key="link.name" :href="link.url" target="_blank" rel="noopener noreferrer" class="resource-card">
              <div class="resource-name">{{ link.name }}</div>
              <div class="resource-desc">{{ link.desc }}</div>
              <div class="resource-url">{{ link.url.replace('https://', '').replace('www.', '').split('/')[0] }} ↗</div>
            </a>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.resources-page { max-width: 1000px; margin: 0 auto; padding: 2.5rem 1.5rem; }
.resources-header { text-align: center; margin-bottom: 2rem; }
.resources-header h1 { font-size: 2rem; font-weight: 800; margin-bottom: 0.5rem; }
.resources-header p { color: var(--text2); }

.category-filters { display: flex; flex-wrap: wrap; gap: 0.5rem; justify-content: center; margin-bottom: 2rem; }
.filter-btn {
  padding: 0.5rem 1rem; border: 1px solid var(--border); border-radius: 9999px;
  background: var(--card, rgba(255,255,255,0.04)); color: var(--text2);
  font-size: 0.85rem; cursor: pointer; transition: all 0.2s;
}
.filter-btn:hover { background: rgba(108,99,255,0.08); color: var(--text); }
.filter-btn.active { background: var(--accent, #6c63ff); color: #fff; border-color: var(--accent); }

.category-section { margin-bottom: 2.5rem; }
.category-section h2 { font-size: 1.2rem; font-weight: 700; margin-bottom: 1rem; padding-bottom: 0.5rem; border-bottom: 1px solid var(--border); }

.resource-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; }

.resource-card {
  background: var(--card, rgba(255,255,255,0.04)); border: 1px solid var(--border);
  border-radius: 14px; padding: 1.25rem; text-decoration: none; color: var(--text);
  transition: all 0.2s;
}
.resource-card:hover { border-color: var(--accent); transform: translateY(-2px); box-shadow: 0 4px 16px rgba(108,99,255,0.1); }
.resource-name { font-size: 1rem; font-weight: 700; margin-bottom: 0.4rem; }
.resource-desc { font-size: 0.85rem; color: var(--text2); line-height: 1.5; margin-bottom: 0.5rem; }
.resource-url { font-size: 0.75rem; color: var(--accent); font-weight: 600; }

@media (max-width: 768px) { .resource-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 480px) { .resource-grid { grid-template-columns: 1fr; } }
</style>
