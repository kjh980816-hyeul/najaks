<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api'

const page = ref(null)
const isLoading = ref(true)
const faqOpen = ref({})

async function fetchSupport() {
  try {
    const { data } = await api.get('/public/search?type=page&slug=support')
    // Try fetching static page directly
    const res = await fetch('/api/public/pages/support')
    if (res.ok) {
      const json = await res.json()
      page.value = json.data
    }
  } catch {
    // fallback
  }
  isLoading.value = false
}

// Hardcoded support content as fallback
const supportSections = ref([
  { heading: '문의 방법', body: '이메일: Admin.Najaks@gmail.com\n운영시간: 평일 10:00 ~ 18:00 (주말/공휴일 휴무)', icon: '📧' },
  { heading: '답변 기한', body: '문의 접수 후 영업일 기준 1~3일 이내에 답변드립니다.', icon: '⏰' },
  { heading: '자주 묻는 질문', body: '', icon: '❓' },
])

const faqs = ref([
  { q: '스트리머 인증은 얼마나 걸리나요?', a: '신청 후 영업일 기준 1~3일 정도 소요됩니다.' },
  { q: '비밀번호를 잊어버렸어요', a: '현재 비밀번호 재설정 기능은 준비 중입니다. 고객센터로 문의해주세요.' },
  { q: '부적절한 게시물을 발견했어요', a: '게시물 옆 신고 버튼을 사용해주세요. 5회 이상 신고된 게시물은 자동으로 숨김 처리됩니다.' },
  { q: '계정을 삭제하고 싶어요', a: '마이페이지에서 계정 삭제를 요청하거나, 고객센터 이메일로 문의해주세요.' },
  { q: '스트리머 전용 게시판은 어떻게 접근하나요?', a: '스트리머 인증이 완료된 후 커뮤니티에서 "스트리머 전용" 메뉴를 이용할 수 있습니다.' },
])

function toggleFaq(idx) {
  faqOpen.value[idx] = !faqOpen.value[idx]
}

onMounted(fetchSupport)
</script>

<template>
  <div class="support-page">
    <div class="support-header">
      <h1>고객센터</h1>
      <p>궁금한 점이 있으시면 언제든 문의해주세요.</p>
    </div>

    <div class="support-grid">
      <a href="mailto:Admin.Najaks@gmail.com" class="support-card">
        <div class="support-card-icon">📧</div>
        <h3>이메일 문의</h3>
        <p>Admin.Najaks@gmail.com</p>
        <span class="support-card-sub">평일 10:00 ~ 18:00</span>
      </a>
      <router-link :to="{ path: '/community', query: { group: 'community', board: 'INQUIRY' } }" class="support-card">
        <div class="support-card-icon">📨</div>
        <h3>문의 게시판</h3>
        <p>글로 남기고 답변 받기</p>
        <span class="support-card-sub">로그인 후 글쓰기</span>
      </router-link>
      <router-link to="/info/report-info" class="support-card">
        <div class="support-card-icon">📋</div>
        <h3>신고 안내</h3>
        <p>부적절한 콘텐츠 신고</p>
        <span class="support-card-sub">자세히 보기 →</span>
      </router-link>
    </div>

    <div class="faq-section">
      <h2>자주 묻는 질문</h2>
      <div class="faq-list">
        <div v-for="(faq, idx) in faqs" :key="idx" class="faq-item" :class="{ open: faqOpen[idx] }">
          <div class="faq-question" @click="toggleFaq(idx)">
            <span>{{ faq.q }}</span>
            <span class="faq-arrow">{{ faqOpen[idx] ? '−' : '+' }}</span>
          </div>
          <div v-if="faqOpen[idx]" class="faq-answer">
            {{ faq.a }}
          </div>
        </div>
      </div>
    </div>

    <div class="support-links">
      <h2>관련 안내</h2>
      <div class="link-grid">
        <router-link to="/info/terms" class="link-card">📄 이용약관</router-link>
        <router-link to="/info/privacy" class="link-card">🔒 개인정보처리방침</router-link>
        <router-link to="/info/community-guide" class="link-card">📝 커뮤니티 가이드</router-link>
        <router-link to="/info/contact" class="link-card">💬 문의하기</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.support-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 2.5rem 1.5rem;
}

.support-header {
  text-align: center;
  margin-bottom: 2.5rem;
}
.support-header h1 { font-size: 2rem; font-weight: 800; margin-bottom: 0.5rem; }
.support-header p { color: var(--text2); font-size: 1rem; }

.support-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin-bottom: 3rem;
}

.support-card {
  background: var(--card, rgba(255,255,255,0.04));
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 1.5rem;
  text-align: center;
  text-decoration: none;
  color: inherit;
  display: block;
  cursor: pointer;
  transition: all 0.2s;
}
.support-card:hover {
  border-color: var(--accent);
  background: rgba(108,99,255,0.05);
  transform: translateY(-2px);
}
.support-card-icon { font-size: 2rem; margin-bottom: 0.75rem; }
.support-card h3 { font-size: 1rem; font-weight: 700; margin-bottom: 0.5rem; }
.support-card p { color: var(--text2); font-size: 0.9rem; margin-bottom: 0.25rem; }
.support-card-sub { font-size: 0.8rem; color: var(--text3, #555577); }
.support-card-link { font-size: 0.8rem; color: var(--accent); text-decoration: none; font-weight: 600; }

.faq-section { margin-bottom: 3rem; }
.faq-section h2 { font-size: 1.25rem; font-weight: 700; margin-bottom: 1rem; }

.faq-list { display: flex; flex-direction: column; gap: 0.5rem; }

.faq-item {
  background: var(--card, rgba(255,255,255,0.04));
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
}

.faq-question {
  display: flex; justify-content: space-between; align-items: center;
  padding: 1rem 1.25rem; cursor: pointer; font-weight: 600; font-size: 0.95rem;
  transition: background 0.2s;
}
.faq-question:hover { background: rgba(108,99,255,0.05); }
.faq-arrow { font-size: 1.25rem; color: var(--accent); font-weight: 700; }

.faq-answer {
  padding: 0 1.25rem 1rem;
  color: var(--text2); font-size: 0.9rem; line-height: 1.6;
  border-top: 1px solid var(--border);
  padding-top: 1rem;
}

.support-links h2 { font-size: 1.25rem; font-weight: 700; margin-bottom: 1rem; }

.link-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.75rem;
}

.link-card {
  display: flex; align-items: center; justify-content: center;
  padding: 1rem; background: var(--card, rgba(255,255,255,0.04));
  border: 1px solid var(--border); border-radius: 12px;
  color: var(--text); text-decoration: none; font-size: 0.9rem; font-weight: 600;
  transition: all 0.2s;
}
.link-card:hover { border-color: var(--accent); background: rgba(108,99,255,0.05); }

@media (max-width: 768px) {
  .support-grid { grid-template-columns: 1fr; }
  .link-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 480px) {
  .link-grid { grid-template-columns: 1fr; }
}
</style>
