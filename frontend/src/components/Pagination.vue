<script setup>
import { computed } from 'vue'

const props = defineProps({
  currentPage: { type: Number, required: true },
  totalPages: { type: Number, required: true },
  maxButtons: { type: Number, default: 7 },
})
const emit = defineEmits(['change'])

const pages = computed(() => {
  const total = Math.max(props.totalPages, 1)
  const cur = Math.min(Math.max(props.currentPage, 1), total)
  const max = props.maxButtons

  if (total <= max) {
    return Array.from({ length: total }, (_, i) => i + 1)
  }

  const side = Math.floor((max - 3) / 2)
  let start = Math.max(2, cur - side)
  let end = Math.min(total - 1, cur + side)

  if (cur - 1 <= side) {
    end = max - 2
  }
  if (total - cur <= side) {
    start = total - (max - 3)
  }

  const result = [1]
  if (start > 2) result.push('...')
  for (let i = start; i <= end; i++) result.push(i)
  if (end < total - 1) result.push('...')
  result.push(total)
  return result
})

function goTo(page) {
  if (page === '...') return
  if (page < 1 || page > props.totalPages) return
  if (page === props.currentPage) return
  emit('change', page)
}

function prev() { if (props.currentPage > 1) emit('change', props.currentPage - 1) }
function next() { if (props.currentPage < props.totalPages) emit('change', props.currentPage + 1) }
</script>

<template>
  <nav v-if="totalPages > 1" class="pagination" aria-label="페이지 네비게이션">
    <button
      type="button"
      class="page-btn nav"
      :disabled="currentPage <= 1"
      @click="prev"
      aria-label="이전"
    >‹</button>

    <button
      v-for="(p, i) in pages"
      :key="i"
      type="button"
      class="page-btn"
      :class="{ active: p === currentPage, dots: p === '...' }"
      :disabled="p === '...'"
      @click="goTo(p)"
    >{{ p }}</button>

    <button
      type="button"
      class="page-btn nav"
      :disabled="currentPage >= totalPages"
      @click="next"
      aria-label="다음"
    >›</button>
  </nav>
</template>

<style scoped>
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 4px;
  margin: 1.5rem 0 0.5rem;
  flex-wrap: wrap;
}

.page-btn {
  min-width: 34px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text2);
  font-size: 0.85rem;
  font-weight: 600;
  border-radius: 8px;
  cursor: pointer;
  font-family: 'Pretendard', sans-serif;
  transition: all 0.15s;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.page-btn:hover:not(:disabled):not(.dots) {
  border-color: var(--accent);
  color: var(--accent);
}

.page-btn.active {
  background: var(--gradient);
  border-color: transparent;
  color: #fff;
}

.page-btn.nav {
  font-size: 1.1rem;
  font-weight: 700;
}

.page-btn.dots {
  border-color: transparent;
  background: transparent;
  cursor: default;
}

.page-btn:disabled:not(.active):not(.dots) {
  opacity: 0.35;
  cursor: not-allowed;
}
</style>
