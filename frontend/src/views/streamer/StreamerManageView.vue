<script setup>
import { ref, onMounted } from 'vue'
import { useNotificationStore } from '@/stores/notification'
import api from '@/api'

const notify = useNotificationStore()

const activeTab = ref('clips')

// 클립
const clips = ref([])
const clipLoading = ref(false)
const clipForm = ref({ title: '', url: '' })
const clipSubmitting = ref(false)

// 방송예고
const schedules = ref([])
const scheduleLoading = ref(false)
const scheduleForm = ref({ title: '', description: '', scheduledAt: '', image: null })
const scheduleImagePreview = ref(null)

function onScheduleImageChange(e) {
  const file = e.target.files[0]
  if (file) {
    scheduleForm.value.image = file
    scheduleImagePreview.value = URL.createObjectURL(file)
  }
}
const scheduleSubmitting = ref(false)

async function fetchClips() {
  clipLoading.value = true
  try {
    const { data } = await api.get('/public/clips')
    clips.value = data.data || []
  } catch { clips.value = [] }
  clipLoading.value = false
}

async function submitClip() {
  if (!clipForm.value.title || !clipForm.value.url) {
    notify.warning('제목과 URL을 입력해주세요')
    return
  }
  clipSubmitting.value = true
  try {
    const formData = new FormData()
    formData.append('title', clipForm.value.title)
    formData.append('url', clipForm.value.url)
    await api.post('/clips', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    notify.success('클립이 등록되었습니다')
    clipForm.value = { title: '', url: '' }
    fetchClips()
  } catch (e) {
    notify.error(e.response?.data?.message || '클립 등록에 실패했습니다')
  }
  clipSubmitting.value = false
}

async function deleteClip(id) {
  if (!confirm('이 클립을 삭제하시겠습니까?')) return
  try {
    await api.delete(`/clips/${id}`)
    notify.success('클립이 삭제되었습니다')
    fetchClips()
  } catch { notify.error('삭제에 실패했습니다') }
}

async function fetchSchedules() {
  scheduleLoading.value = true
  try {
    const { data } = await api.get('/public/schedules')
    schedules.value = data.data || []
  } catch { schedules.value = [] }
  scheduleLoading.value = false
}

async function submitSchedule() {
  if (!scheduleForm.value.title || !scheduleForm.value.scheduledAt) {
    notify.warning('제목과 방송 예정 시간을 입력해주세요')
    return
  }
  scheduleSubmitting.value = true
  try {
    const formData = new FormData()
    formData.append('title', scheduleForm.value.title)
    if (scheduleForm.value.description) formData.append('description', scheduleForm.value.description)
    formData.append('scheduledAt', scheduleForm.value.scheduledAt + ':00')
    if (scheduleForm.value.image) formData.append('image', scheduleForm.value.image)
    await api.post('/schedules', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    notify.success('방송 예고가 등록되었습니다')
    scheduleForm.value = { title: '', description: '', scheduledAt: '', image: null }
    scheduleImagePreview.value = null
    fetchSchedules()
  } catch (e) {
    notify.error(e.response?.data?.message || '등록에 실패했습니다')
  }
  scheduleSubmitting.value = false
}

async function deleteSchedule(id) {
  if (!confirm('이 방송 예고를 삭제하시겠습니까?')) return
  try {
    await api.delete(`/schedules/${id}`)
    notify.success('삭제되었습니다')
    fetchSchedules()
  } catch { notify.error('삭제에 실패했습니다') }
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

onMounted(() => { fetchClips(); fetchSchedules() })
</script>

<template>
  <div class="manage-page">
    <h1>스트리머 관리</h1>

    <div class="tabs">
      <button :class="['tab', { active: activeTab === 'clips' }]" @click="activeTab = 'clips'">클립 관리</button>
      <button :class="['tab', { active: activeTab === 'schedules' }]" @click="activeTab = 'schedules'">방송 예고</button>
    </div>

    <!-- 클립 관리 -->
    <div v-if="activeTab === 'clips'" class="tab-content">
      <div class="form-card">
        <h3>클립 등록</h3>
        <form @submit.prevent="submitClip" class="inline-form">
          <input v-model="clipForm.title" type="text" placeholder="클립 제목" class="form-input" />
          <input v-model="clipForm.url" type="url" placeholder="클립 URL (유튜브, 치지직 등)" class="form-input" />
          <button type="submit" class="btn btn-primary" :disabled="clipSubmitting">
            {{ clipSubmitting ? '등록 중...' : '등록' }}
          </button>
        </form>
      </div>

      <div v-if="clipLoading" class="loading"><div class="spinner"></div></div>
      <div v-else-if="clips.length === 0" class="empty">등록된 클립이 없습니다.</div>
      <div v-else class="item-list">
        <div v-for="clip in clips" :key="clip.id" class="item-row">
          <div class="item-info">
            <strong>{{ clip.title }}</strong>
            <a :href="clip.url" target="_blank" class="item-link">{{ clip.url }}</a>
            <span class="item-meta">{{ clip.streamerNickname }} · 조회 {{ clip.viewCount || 0 }}</span>
          </div>
          <button class="btn btn-danger btn-sm" @click="deleteClip(clip.id)">삭제</button>
        </div>
      </div>
    </div>

    <!-- 방송 예고 -->
    <div v-if="activeTab === 'schedules'" class="tab-content">
      <div class="form-card">
        <h3>방송 예고 등록</h3>
        <form @submit.prevent="submitSchedule" class="inline-form">
          <input v-model="scheduleForm.title" type="text" placeholder="방송 제목" class="form-input" />
          <input v-model="scheduleForm.description" type="text" placeholder="설명 (선택)" class="form-input" />
          <input v-model="scheduleForm.scheduledAt" type="datetime-local" class="form-input" />
          <div class="image-upload-row">
            <label class="btn btn-outline btn-sm upload-label">
              📷 이미지 첨부
              <input type="file" accept="image/*" @change="onScheduleImageChange" hidden />
            </label>
            <span v-if="scheduleForm.image" class="upload-filename">{{ scheduleForm.image.name }}</span>
          </div>
          <div v-if="scheduleImagePreview" class="image-preview">
            <img :src="scheduleImagePreview" alt="미리보기" />
          </div>
          <button type="submit" class="btn btn-primary" :disabled="scheduleSubmitting">
            {{ scheduleSubmitting ? '등록 중...' : '등록' }}
          </button>
        </form>
      </div>

      <div v-if="scheduleLoading" class="loading"><div class="spinner"></div></div>
      <div v-else-if="schedules.length === 0" class="empty">등록된 방송 예고가 없습니다.</div>
      <div v-else class="item-list">
        <div v-for="s in schedules" :key="s.id" class="item-row">
          <img v-if="s.imageUrl" :src="s.imageUrl" alt="" class="item-thumb" />
          <div class="item-info">
            <strong>{{ s.title }}</strong>
            <span class="item-meta">{{ s.streamerNickname }} · {{ formatDate(s.scheduledAt) }}</span>
            <span v-if="s.description" class="item-desc">{{ s.description }}</span>
          </div>
          <button class="btn btn-danger btn-sm" @click="deleteSchedule(s.id)">삭제</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.manage-page { max-width: 800px; margin: 0 auto; padding: 2rem 1rem; font-family: 'Pretendard', sans-serif; }
.manage-page h1 { font-size: 1.5rem; font-weight: 700; color: var(--text); margin-bottom: 1.5rem; }

.tabs { display: flex; gap: 0; border-bottom: 2px solid var(--border); margin-bottom: 1.5rem; }
.tab {
  padding: 0.75rem 1.25rem; border: none; background: none; font-size: 0.95rem; font-weight: 600;
  color: var(--text3); cursor: pointer; border-bottom: 2px solid transparent; margin-bottom: -2px;
  transition: color 0.2s, border-color 0.2s; font-family: 'Pretendard', sans-serif;
}
.tab.active { color: var(--accent); border-bottom-color: var(--accent); }

.form-card {
  background: var(--card); border: 1px solid var(--border); border-radius: 14px;
  padding: 1.5rem; margin-bottom: 1.5rem;
}
.form-card h3 { font-size: 1rem; font-weight: 700; color: var(--text); margin-bottom: 1rem; }

.inline-form { display: flex; flex-direction: column; gap: 0.75rem; }

.form-input {
  padding: 0.65rem 0.85rem; background: var(--bg3); border: 1.5px solid var(--border);
  border-radius: 8px; color: var(--text); font-size: 0.88rem; outline: none;
  font-family: 'Pretendard', sans-serif;
}
.form-input:focus { border-color: var(--accent); }

.btn { padding: 0.6rem 1.25rem; border: none; border-radius: 8px; font-size: 0.9rem; font-weight: 600; cursor: pointer; transition: all 0.2s; font-family: 'Pretendard', sans-serif; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-primary { background: var(--gradient); color: #fff; }
.btn-danger { background: rgba(239,68,68,0.15); color: #ef4444; border: 1px solid rgba(239,68,68,0.25); }
.btn-sm { padding: 0.35rem 0.75rem; font-size: 0.8rem; }

.loading { text-align: center; padding: 2rem; }
.spinner { width: 32px; height: 32px; border: 3px solid var(--border); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto; }
@keyframes spin { to { transform: rotate(360deg); } }

.empty { text-align: center; padding: 2rem; color: var(--text3); background: var(--card); border: 1px solid var(--border); border-radius: 12px; }

.item-list { display: flex; flex-direction: column; gap: 0.5rem; }
.item-row {
  display: flex; align-items: center; justify-content: space-between; gap: 1rem;
  padding: 1rem 1.25rem; background: var(--card); border: 1px solid var(--border); border-radius: 12px;
}
.item-info { display: flex; flex-direction: column; gap: 0.25rem; flex: 1; min-width: 0; }
.item-info strong { font-size: 0.95rem; color: var(--text); }
.item-link { font-size: 0.8rem; color: var(--accent); text-decoration: none; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-meta { font-size: 0.78rem; color: var(--text3); }
.item-desc { font-size: 0.82rem; color: var(--text2); }

.btn-outline { background: transparent; border: 1.5px solid var(--border); color: var(--text2); }
.btn-outline:hover { border-color: var(--accent); color: var(--accent); }
.image-upload-row { display: flex; align-items: center; gap: 0.75rem; }
.upload-label { cursor: pointer; }
.upload-filename { font-size: 0.8rem; color: var(--text2); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.image-preview { border-radius: 8px; overflow: hidden; border: 1px solid var(--border); max-height: 150px; }
.image-preview img { width: 100%; max-height: 150px; object-fit: cover; display: block; }
.item-thumb { width: 60px; height: 60px; border-radius: 8px; object-fit: cover; flex-shrink: 0; border: 1px solid var(--border); }

@media (max-width: 480px) { .manage-page { padding: 1rem; } }
</style>
