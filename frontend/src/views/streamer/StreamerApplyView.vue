<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'
import api from '@/api'

const router = useRouter()
const notify = useNotificationStore()

const platforms = ref({
  youtube: { selected: false, file: null, preview: '' },
  chzzk: { selected: false, file: null, preview: '' },
  soop: { selected: false, file: null, preview: '' },
})

const guideImages = ref({})
const guideLightboxOpen = ref(false)
const guideLightboxImage = ref(null)

function openGuideLightbox(guide) {
  guideLightboxImage.value = guide
  guideLightboxOpen.value = true
  document.body.style.overflow = 'hidden'
}

function closeGuideLightbox() {
  guideLightboxOpen.value = false
  guideLightboxImage.value = null
  document.body.style.overflow = ''
}

async function fetchGuides() {
  try {
    const { data } = await api.get('/public/application-guides')
    const map = {}
    ;(data.data || []).forEach(g => { map[g.platform] = g })
    guideImages.value = map
  } catch {
    // 가이드 이미지가 없어도 폼은 정상 동작해야 함
  }
}

onMounted(fetchGuides)

const platformMeta = {
  youtube: { label: '유튜브', desc: 'YouTube Studio 화면 캡처', color: '#FF0000', icon: '▶' },
  chzzk: { label: '치지직', desc: '치지직 채널 관리 화면', color: '#03C75A', icon: '치' },
  soop: { label: '숲(SOOP)', desc: '방송국 관리 화면', color: '#0078ff', icon: 'S' },
}

const isSubmitting = ref(false)

const attachedCount = computed(() => Object.values(platforms.value).filter(p => p.selected && p.file).length)

function pickFile(key, event) {
  const file = event.target.files?.[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    notify.warning('이미지 파일만 업로드할 수 있습니다')
    return
  }
  if (file.size > 20 * 1024 * 1024) {
    notify.warning('파일 크기는 20MB 이하여야 합니다')
    return
  }

  platforms.value[key].file = file
  platforms.value[key].preview = URL.createObjectURL(file)
}

function clearFile(key) {
  platforms.value[key].file = null
  platforms.value[key].preview = ''
}

async function handleSubmit() {
  const selectedKeys = Object.keys(platforms.value).filter(k => platforms.value[k].selected)
  if (selectedKeys.length === 0) {
    notify.warning('인증할 플랫폼을 1개 이상 선택해주세요')
    return
  }

  const missing = selectedKeys.filter(k => !platforms.value[k].file).map(k => platformMeta[k].label)
  if (missing.length > 0) {
    notify.warning(`${missing.join(', ')} 스크린샷을 첨부해주세요`)
    return
  }

  isSubmitting.value = true
  try {
    const formData = new FormData()
    selectedKeys.forEach(k => {
      formData.append('screenshots', platforms.value[k].file)
    })

    await api.post('/streamer-applications', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    notify.success('스트리머 인증 신청이 완료되었습니다! 관리자 검토 후 승인됩니다.')
    router.push('/mypage')
  } catch (error) {
    notify.error(error.response?.data?.message || '신청에 실패했습니다. 다시 시도해주세요.')
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="apply-page">
    <div class="apply-card">
      <div class="apply-header">
        <h1>스트리머 인증 신청</h1>
        <p>
          유튜브, 치지직, 숲(SOOP) 등에서 활동하고 계신 스트리머라면
          인증 신청을 통해 스트리머 전용 기능을 이용할 수 있습니다.
        </p>
      </div>

      <div class="instructions">
        <h3>📋 신청 방법</h3>
        <ol>
          <li>활동 중인 플랫폼을 모두 선택합니다 (1개 이상)</li>
          <li>각 플랫폼의 <strong>채널 관리 화면</strong>을 캡처합니다</li>
          <li>선택한 플랫폼마다 스크린샷을 첨부합니다</li>
          <li>관리자가 확인 후 승인하면 스트리머 권한이 부여됩니다</li>
        </ol>
      </div>

      <form @submit.prevent="handleSubmit" class="apply-form">
        <div class="platform-grid">
          <div
            v-for="(meta, key) in platformMeta"
            :key="key"
            class="platform-block"
            :class="{ active: platforms[key].selected }"
          >
            <div class="platform-main">
              <label class="platform-header">
                <input type="checkbox" v-model="platforms[key].selected" />
                <div class="platform-icon" :style="{ background: meta.color }">{{ meta.icon }}</div>
                <div>
                  <div class="platform-name">{{ meta.label }}</div>
                  <div class="platform-desc">{{ meta.desc }}</div>
                </div>
              </label>

              <div v-if="platforms[key].selected" class="screenshot-area">
                <div v-if="platforms[key].preview" class="preview-wrap">
                  <img :src="platforms[key].preview" alt="미리보기" />
                  <button type="button" class="remove-btn" @click="clearFile(key)">✕ 제거</button>
                </div>
                <label v-else class="upload-btn">
                  <span>📎 {{ meta.label }} 스크린샷 업로드</span>
                  <input type="file" accept="image/*" @change="pickFile(key, $event)" hidden />
                </label>
              </div>
            </div>

            <aside v-if="guideImages[key]" class="platform-guide">
              <div class="guide-example-head">
                <span class="guide-example-title">📷 예시 — 이런 화면을 캡처해주세요</span>
                <button type="button" class="guide-zoom-btn" @click="openGuideLightbox(guideImages[key])">확대</button>
              </div>
              <div class="guide-example-thumb" @click="openGuideLightbox(guideImages[key])">
                <img :src="guideImages[key].imageUrl" :alt="`${meta.label} 예시`" />
              </div>
              <p v-if="guideImages[key].description" class="guide-example-desc">
                {{ guideImages[key].description }}
              </p>
            </aside>
          </div>
        </div>

        <div class="form-actions">
          <button type="submit" class="btn btn-primary" :disabled="isSubmitting || attachedCount === 0">
            {{ isSubmitting ? '신청 중...' : `인증 신청하기 (${attachedCount}장 첨부)` }}
          </button>
          <router-link to="/mypage" class="btn btn-outline">취소</router-link>
        </div>
      </form>
    </div>

    <!-- 가이드 이미지 라이트박스 -->
    <div v-if="guideLightboxOpen" class="guide-lightbox" @click.self="closeGuideLightbox">
      <button class="guide-lightbox-close" @click="closeGuideLightbox" aria-label="닫기">✕</button>
      <div class="guide-lightbox-inner" @click.stop>
        <img :src="guideLightboxImage?.imageUrl" alt="가이드 이미지" />
        <p v-if="guideLightboxImage?.description" class="guide-lightbox-desc">
          {{ guideLightboxImage.description }}
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.apply-page {
  min-height: calc(100vh - 200px);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 2rem;
  background: var(--bg, #0a0a0f);
}

.apply-card {
  width: 100%;
  max-width: 900px;
  background: var(--bg2, #12121a);
  border: 1px solid var(--border, rgba(255,255,255,0.07));
  border-radius: 16px;
  padding: 2.5rem;
}

.apply-header { margin-bottom: 1.75rem; }
.apply-header h1 {
  font-size: 1.5rem; font-weight: 700;
  color: var(--text, #f0f0f8); margin-bottom: 0.75rem;
}
.apply-header p {
  color: var(--text2, #9999bb); line-height: 1.6; font-size: 0.95rem;
}

.instructions {
  background: var(--bg3, #1a1a26);
  border: 1px solid var(--border, rgba(255,255,255,0.07));
  border-radius: 12px;
  padding: 1.25rem 1.5rem;
  margin-bottom: 1.75rem;
}
.instructions h3 {
  font-size: 0.95rem; font-weight: 700;
  color: var(--accent, #6c63ff); margin-bottom: 0.5rem;
}
.instructions ol {
  padding-left: 1.25rem; color: var(--text2, #9999bb);
  line-height: 1.85; font-size: 0.88rem;
}
.instructions ol strong { color: var(--text, #f0f0f8); }

.apply-form { display: flex; flex-direction: column; gap: 1.5rem; }

.platform-grid { display: flex; flex-direction: column; gap: 0.85rem; }

.platform-block {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  align-items: start;
  border: 1.5px solid var(--border, rgba(255,255,255,0.07));
  border-radius: 12px;
  padding: 16px;
  background: var(--bg3, #1a1a26);
  transition: border-color 0.2s, background 0.2s;
}

.platform-block.active {
  border-color: var(--accent, #6c63ff);
  background: rgba(108,99,255,0.05);
}

.platform-main { min-width: 0; }

.platform-guide {
  background: rgba(108,99,255,0.06);
  border: 1px solid rgba(108,99,255,0.18);
  border-radius: 10px;
  padding: 12px;
  align-self: stretch;
}

@media (max-width: 700px) {
  .platform-block { grid-template-columns: 1fr; }
}

.platform-header {
  display: flex; align-items: center; gap: 12px; cursor: pointer;
}

.platform-header input[type="checkbox"] {
  width: 17px; height: 17px; accent-color: var(--accent, #6c63ff); flex-shrink: 0;
}

.platform-icon {
  width: 32px; height: 32px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 900; color: white; flex-shrink: 0;
}

.platform-name { font-size: 14px; font-weight: 700; color: var(--text); }
.platform-desc { font-size: 11px; color: var(--text3); margin-top: 2px; }

.screenshot-area { margin-top: 12px; padding-left: 44px; }

@media (max-width: 700px) {
  .screenshot-area { padding-left: 0; }
}

.upload-btn {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 10px 16px;
  background: rgba(108,99,255,0.08);
  border: 1.5px dashed var(--accent, #6c63ff);
  border-radius: 10px;
  color: var(--accent, #6c63ff);
  font-size: 13px; font-weight: 600; cursor: pointer;
  transition: background 0.2s;
}
.upload-btn:hover { background: rgba(108,99,255,0.15); }

.preview-wrap {
  display: flex; align-items: flex-start; gap: 10px;
}

.preview-wrap img {
  max-height: 160px; max-width: 280px;
  border-radius: 8px; border: 1px solid var(--border);
  object-fit: contain;
}

.remove-btn {
  background: rgba(239,68,68,0.1);
  border: 1px solid rgba(239,68,68,0.3);
  color: #ef4444;
  padding: 5px 10px; border-radius: 6px; cursor: pointer;
  font-size: 11px; font-weight: 600;
}
.remove-btn:hover { background: rgba(239,68,68,0.2); }

.form-actions { display: flex; gap: 0.75rem; padding-top: 0.5rem; }

.btn {
  padding: 0.75rem 1.5rem; border: none; border-radius: 10px;
  font-size: 0.95rem; font-weight: 600; cursor: pointer;
  transition: all 0.2s; text-decoration: none; text-align: center;
}
.btn:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-primary {
  background: var(--gradient, linear-gradient(135deg, #6c63ff, #ff6b9d));
  color: #fff;
}
.btn-primary:hover:not(:disabled) {
  opacity: 0.9; transform: translateY(-1px);
  box-shadow: 0 4px 20px rgba(108, 99, 255, 0.3);
}

.btn-outline {
  background: transparent;
  color: var(--text2, #9999bb);
  border: 1.5px solid var(--border, rgba(255,255,255,0.07));
}
.btn-outline:hover {
  border-color: var(--accent, #6c63ff);
  color: var(--accent, #6c63ff);
}

/* 가이드 이미지 섹션 */
.guide-example-head {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 8px;
}
.guide-example-title {
  font-size: 12px; font-weight: 700;
  color: var(--accent, #6c63ff);
}
.guide-zoom-btn {
  background: rgba(108,99,255,0.12);
  border: 1px solid rgba(108,99,255,0.3);
  color: var(--accent, #6c63ff);
  padding: 3px 10px; border-radius: 6px;
  font-size: 11px; font-weight: 600; cursor: pointer;
  font-family: 'Pretendard', sans-serif;
}
.guide-zoom-btn:hover { background: rgba(108,99,255,0.2); }
.guide-example-thumb {
  width: 100%; border-radius: 8px; overflow: hidden;
  border: 1px solid var(--border); cursor: zoom-in;
  background: var(--bg);
}
.guide-example-thumb img { width: 100%; height: auto; display: block; }
.guide-example-thumb:hover { border-color: var(--accent, #6c63ff); }
.guide-example-thumb:hover { border-color: var(--accent, #6c63ff); }
.guide-example-desc {
  font-size: 12px; color: var(--text2);
  line-height: 1.5; margin: 8px 0 0;
}

/* 가이드 라이트박스 */
.guide-lightbox {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.9);
  display: flex; align-items: center; justify-content: center;
  z-index: 9999; padding: 2rem;
}
.guide-lightbox-close {
  position: absolute; top: 20px; right: 24px;
  width: 40px; height: 40px; border-radius: 50%;
  background: rgba(255,255,255,0.15); border: none;
  color: #fff; font-size: 18px; cursor: pointer;
}
.guide-lightbox-inner {
  max-width: 92vw; max-height: 90vh;
  display: flex; flex-direction: column; align-items: center; gap: 1rem;
}
.guide-lightbox-inner img {
  max-width: 100%; max-height: 80vh;
  object-fit: contain; border-radius: 8px;
}
.guide-lightbox-desc {
  color: #fff; font-size: 14px;
  background: rgba(0,0,0,0.5);
  padding: 10px 16px; border-radius: 8px;
  text-align: center; max-width: 600px;
  line-height: 1.6; margin: 0;
}
</style>
