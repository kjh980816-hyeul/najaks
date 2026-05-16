<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'
import api from '@/api'

const router = useRouter()
const route = useRoute()
const notify = useNotificationStore()

const isSubmitting = ref(false)
const imageFiles = ref([])
const imagePreviews = ref([])
const existingImageUrls = ref([])

const editId = computed(() => route.query.edit ? Number(route.query.edit) : null)
const isEditMode = computed(() => editId.value != null)

const categories = [
  { value: 'GAME', label: '게임' },
  { value: 'MUSIC', label: '음악' },
  { value: 'MUKBANG', label: '먹방' },
  { value: 'TALK', label: '토크' },
  { value: 'ETC', label: '기타' }
]

const contactMethods = [
  { value: '', label: '선택 안 함' },
  { value: 'discord', label: '디스코드' },
  { value: 'kakao', label: '카카오톡' },
  { value: 'email', label: '이메일' },
  { value: 'twitter', label: '트위터/X' },
  { value: 'etc', label: '기타' }
]

const form = ref({
  title: '',
  description: '',
  applyLink: '',
  startDate: '',
  endDate: '',
  category: 'GAME',
  tags: [],
  requirements: '',
  prize: '',
  recruitCount: '',
  followerCount: '',
  followerUnlimited: false,
  contactMethod: '',
  contactInfo: '',
  hostName: ''
})

const maxImages = 6

function handleImages(event) {
  const files = Array.from(event.target.files || [])
  if (!files.length) return
  const available = maxImages - imageFiles.value.length
  const toAdd = files.slice(0, available)
  for (const file of toAdd) {
    imageFiles.value.push(file)
    imagePreviews.value.push(URL.createObjectURL(file))
  }
  if (files.length > available) {
    notify.warning(`이미지는 최대 ${maxImages}장까지 업로드할 수 있습니다`)
  }
  event.target.value = ''
}

function removeImage(idx) {
  const total = existingImageUrls.value.length
  if (idx < total) {
    existingImageUrls.value.splice(idx, 1)
    imagePreviews.value.splice(idx, 1)
  } else {
    const fileIdx = idx - total
    URL.revokeObjectURL(imagePreviews.value[idx])
    imageFiles.value.splice(fileIdx, 1)
    imagePreviews.value.splice(idx, 1)
  }
}

async function loadExistingContent() {
  if (!isEditMode.value) return
  try {
    const { data } = await api.get(`/public/contents/${editId.value}`)
    const c = data.data
    if (!c) throw new Error('not found')
    form.value.title = c.title || ''
    form.value.description = c.description || ''
    form.value.applyLink = c.applyLink || ''
    form.value.startDate = c.startDate ? String(c.startDate).slice(0, 16) : ''
    form.value.endDate = c.endDate ? String(c.endDate).slice(0, 16) : ''
    form.value.category = c.category || 'GAME'
    form.value.tags = c.tags || []
    form.value.requirements = c.requirements || ''
    form.value.prize = c.prize || ''
    form.value.recruitCount = c.recruitCount || ''
    form.value.followerCount = c.followerCount != null ? String(c.followerCount) : ''
    form.value.followerUnlimited = !!c.followerUnlimited
    form.value.contactMethod = c.contactMethod || ''
    form.value.contactInfo = c.contactInfo || ''
    form.value.hostName = c.hostName || ''
    const urls = c.imageUrls && c.imageUrls.length > 0
      ? c.imageUrls
      : (c.thumbnailUrl ? [c.thumbnailUrl] : [])
    existingImageUrls.value = [...urls]
    imagePreviews.value = [...urls]
  } catch {
    notify.error('컨텐츠를 불러오지 못했습니다')
    router.replace('/contents')
  }
}

onMounted(loadExistingContent)

function toggleTag(val) {
  const i = form.value.tags.indexOf(val)
  if (i >= 0) form.value.tags.splice(i, 1)
  else form.value.tags.push(val)
}

const isTagSelected = (val) => form.value.tags.includes(val)

async function handleSubmit() {
  if (!form.value.title.trim()) {
    notify.warning('제목을 입력해주세요')
    return
  }

  isSubmitting.value = true
  try {
    const formData = new FormData()
    formData.append('title', form.value.title)
    formData.append('category', form.value.category)
    if (form.value.description) formData.append('description', form.value.description)
    if (form.value.applyLink) formData.append('applyLink', form.value.applyLink)
    if (form.value.startDate) formData.append('startDate', form.value.startDate + ':00')
    if (form.value.endDate) formData.append('endDate', form.value.endDate + ':00')
    for (const t of form.value.tags) formData.append('tags', t)
    if (form.value.requirements) formData.append('requirements', form.value.requirements)
    if (form.value.prize) formData.append('prize', form.value.prize)
    if (form.value.recruitCount) formData.append('recruitCount', form.value.recruitCount)
    if (form.value.followerUnlimited) {
      formData.append('followerUnlimited', 'true')
    } else if (form.value.followerCount !== '' && form.value.followerCount !== null) {
      formData.append('followerCount', String(form.value.followerCount))
    }
    if (form.value.contactMethod) formData.append('contactMethod', form.value.contactMethod)
    if (form.value.contactInfo) formData.append('contactInfo', form.value.contactInfo)
    if (form.value.hostName) formData.append('hostName', form.value.hostName)
    for (const url of existingImageUrls.value) formData.append('existingImageUrls', url)
    for (const f of imageFiles.value) formData.append('images', f)

    if (isEditMode.value) {
      await api.put(`/contents/${editId.value}`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      notify.success('컨텐츠가 수정되었습니다')
      router.push(`/contents/${editId.value}`)
    } else {
      await api.post('/contents', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      notify.success('컨텐츠가 등록되었습니다! 관리자 승인 후 노출됩니다.')
      router.push('/contents')
    }
  } catch (error) {
    notify.error(error.response?.data?.message || (isEditMode.value ? '수정에 실패했습니다' : '등록에 실패했습니다'))
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="create-page">
    <div class="create-card">
      <div class="create-header">
        <h1>{{ isEditMode ? '컨텐츠 수정' : '컨텐츠 등록' }}</h1>
        <p>{{ isEditMode ? '내용을 수정하면 즉시 반영됩니다.' : '이벤트, 컨텐츠를 등록하면 관리자 승인 후 메인에 노출됩니다.' }}</p>
      </div>

      <form @submit.prevent="handleSubmit" class="create-form">
        <div class="form-group">
          <label for="title">제목 <span class="form-required">*</span></label>
          <input
            id="title"
            v-model="form.title"
            type="text"
            placeholder="컨텐츠 제목을 입력하세요"
            required
          />
        </div>

        <div class="form-group">
          <label for="hostName">주최자</label>
          <input
            id="hostName"
            v-model="form.hostName"
            type="text"
            placeholder="이벤트/컨텐츠를 주최하는 사람 또는 팀명"
          />
        </div>

        <div class="form-group">
          <label for="category">대표 카테고리 <span class="form-required">*</span></label>
          <select id="category" v-model="form.category">
            <option v-for="cat in categories" :key="cat.value" :value="cat.value">
              {{ cat.label }}
            </option>
          </select>
        </div>

        <div class="form-group">
          <label>추가 태그 (복수 선택 가능)</label>
          <div class="tag-chips">
            <button
              v-for="cat in categories"
              :key="cat.value"
              type="button"
              class="tag-chip"
              :class="{ selected: isTagSelected(cat.value) }"
              @click="toggleTag(cat.value)"
            >{{ cat.label }}</button>
          </div>
        </div>

        <div class="form-group">
          <label for="description">설명</label>
          <textarea
            id="description"
            v-model="form.description"
            rows="5"
            placeholder="이벤트 내용, 참가 조건 등을 입력하세요"
          ></textarea>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="startDate">시작일</label>
            <input id="startDate" v-model="form.startDate" type="datetime-local" />
          </div>
          <div class="form-group">
            <label for="endDate">마감일</label>
            <input id="endDate" v-model="form.endDate" type="datetime-local" />
          </div>
        </div>

        <div class="form-group">
          <label>이미지 (최대 {{ maxImages }}장, 첫 번째가 썸네일로 사용)</label>
          <div class="image-grid">
            <div v-for="(src, idx) in imagePreviews" :key="idx" class="image-slot">
              <img :src="src" alt="" />
              <button type="button" class="image-remove" @click="removeImage(idx)">×</button>
              <span v-if="idx === 0" class="image-thumb-badge">대표</span>
            </div>
            <div
              v-if="imageFiles.length < maxImages"
              class="image-slot image-add"
              @click="$refs.imgInput.click()"
            >
              <span>＋</span>
              <small>이미지 추가</small>
            </div>
          </div>
          <input ref="imgInput" type="file" accept="image/*" multiple @change="handleImages" hidden />
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="recruitCount">모집 인원</label>
            <input id="recruitCount" v-model="form.recruitCount" type="text" placeholder="예: 50명, 2~4팀, 제한없음" />
          </div>
          <div class="form-group">
            <label for="followerCount">팔로워 수 조건</label>
            <div class="follower-row">
              <input
                id="followerCount"
                v-model="form.followerCount"
                type="number"
                min="0"
                placeholder="예: 1000"
                :disabled="form.followerUnlimited"
              />
              <label class="inline-check">
                <input type="checkbox" v-model="form.followerUnlimited" />
                제한없음
              </label>
            </div>
          </div>
        </div>

        <div class="form-group">
          <label for="requirements">참가 조건</label>
          <textarea
            id="requirements"
            v-model="form.requirements"
            rows="3"
            placeholder="예: 치지직 스트리머, 성인 인증 필수"
          ></textarea>
        </div>

        <div class="form-group">
          <label for="prize">상금 / 혜택</label>
          <textarea
            id="prize"
            v-model="form.prize"
            rows="2"
            placeholder="예: 우승 상금 30만원, 클립 홍보 지원"
          ></textarea>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="contactMethod">연락 수단</label>
            <select id="contactMethod" v-model="form.contactMethod">
              <option v-for="m in contactMethods" :key="m.value" :value="m.value">{{ m.label }}</option>
            </select>
          </div>
          <div class="form-group">
            <label for="contactInfo">연락처</label>
            <input id="contactInfo" v-model="form.contactInfo" type="text" placeholder="디스코드 ID, 이메일 등" />
          </div>
        </div>

        <div class="form-group">
          <label for="applyLink">신청 링크 (선택)</label>
          <input
            id="applyLink"
            v-model="form.applyLink"
            type="url"
            placeholder="https://..."
          />
          <span class="input-hint">참가 신청을 받을 URL을 입력하세요 (네이버 폼, 구글 폼, 디스코드 초대 링크 등)</span>
        </div>

        <div class="form-actions">
          <button type="submit" class="btn btn-primary" :disabled="isSubmitting">
            {{ isSubmitting
              ? (isEditMode ? '수정 중...' : '등록 중...')
              : (isEditMode ? '수정 완료' : '컨텐츠 등록') }}
          </button>
          <router-link to="/contents" class="btn btn-outline">취소</router-link>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.create-page {
  max-width: 760px;
  margin: 0 auto;
  padding: 2rem 1rem;
  font-family: 'Pretendard', sans-serif;
}

.create-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 2.5rem;
  backdrop-filter: blur(12px);
}

.create-header { margin-bottom: 2rem; }
.create-header h1 { font-size: 1.5rem; font-weight: 700; color: var(--text); margin-bottom: 0.5rem; }
.create-header p { color: var(--text2); }

.create-form { display: flex; flex-direction: column; gap: 1.5rem; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.form-group { display: flex; flex-direction: column; gap: 0.4rem; }
.form-group label { font-size: 0.9rem; font-weight: 600; color: var(--text2); }
.form-required { color: var(--accent2); }

.form-group input,
.form-group textarea,
.form-group select {
  padding: 0.75rem 1rem;
  border: 1.5px solid var(--border);
  border-radius: 8px;
  font-size: 1rem;
  outline: none;
  transition: border-color 0.2s;
  width: 100%;
  box-sizing: border-box;
  font-family: 'Pretendard', sans-serif;
  background: var(--bg2);
  color: var(--text);
}

.form-group input:disabled { opacity: 0.5; cursor: not-allowed; }
.form-group input::placeholder,
.form-group textarea::placeholder { color: var(--text3); }
.form-group input:focus,
.form-group textarea:focus,
.form-group select:focus { border-color: var(--accent); box-shadow: 0 0 0 3px rgba(108,99,255,0.15); }
.form-group select option { background: var(--bg2); color: var(--text); }

/* datetime-local — 달력 아이콘 / 피커 색상 조정 */
.form-group input[type="datetime-local"] {
  color-scheme: dark;
  position: relative;
  cursor: pointer;
  padding-right: 2.75rem;
  background:
    url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='white' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><rect x='3' y='4' width='18' height='18' rx='2' ry='2'/><line x1='16' y1='2' x2='16' y2='6'/><line x1='8' y1='2' x2='8' y2='6'/><line x1='3' y1='10' x2='21' y2='10'/></svg>")
    no-repeat right 0.85rem center / 20px 20px,
    var(--bg2);
}
.form-group input[type="datetime-local"]::-webkit-calendar-picker-indicator {
  position: absolute;
  right: 0;
  top: 0;
  width: 100%;
  height: 100%;
  padding: 0;
  margin: 0;
  cursor: pointer;
  opacity: 0;
}
.form-group input[type="datetime-local"]::-webkit-datetime-edit { color: var(--text); }
.form-group input[type="datetime-local"]::-webkit-datetime-edit-fields-wrapper { color: var(--text); }
.form-group input[type="datetime-local"]::-webkit-datetime-edit-text { color: var(--text2); padding: 0 2px; }
.form-group input[type="datetime-local"]::-webkit-datetime-edit-year-field,
.form-group input[type="datetime-local"]::-webkit-datetime-edit-month-field,
.form-group input[type="datetime-local"]::-webkit-datetime-edit-day-field,
.form-group input[type="datetime-local"]::-webkit-datetime-edit-hour-field,
.form-group input[type="datetime-local"]::-webkit-datetime-edit-minute-field {
  color: var(--text);
  font-weight: 600;
}
.form-group input[type="datetime-local"]:focus::-webkit-datetime-edit-year-field:focus,
.form-group input[type="datetime-local"]:focus::-webkit-datetime-edit-month-field:focus,
.form-group input[type="datetime-local"]:focus::-webkit-datetime-edit-day-field:focus,
.form-group input[type="datetime-local"]:focus::-webkit-datetime-edit-hour-field:focus,
.form-group input[type="datetime-local"]:focus::-webkit-datetime-edit-minute-field:focus {
  background: var(--accent);
  color: #fff;
  border-radius: 3px;
}

.input-hint { font-size: 0.8rem; color: var(--text3); }

.tag-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.tag-chip {
  padding: 6px 14px;
  border-radius: 999px;
  border: 1.5px solid var(--border);
  background: var(--bg2);
  color: var(--text2);
  font-size: 0.85rem;
  cursor: pointer;
  font-family: 'Pretendard', sans-serif;
  transition: all 0.15s;
}
.tag-chip:hover { border-color: var(--accent); }
.tag-chip.selected { background: var(--accent); border-color: var(--accent); color: #fff; font-weight: 700; }

.image-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.image-slot {
  aspect-ratio: 1;
  border: 2px dashed var(--border);
  border-radius: 12px;
  position: relative;
  overflow: hidden;
  background: var(--bg2);
}
.image-slot img { width: 100%; height: 100%; object-fit: cover; }
.image-add {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  cursor: pointer; color: var(--text3); font-size: 28px;
}
.image-add small { font-size: 11px; margin-top: 4px; }
.image-add:hover { border-color: var(--accent); color: var(--accent); }
.image-remove {
  position: absolute; top: 6px; right: 6px;
  width: 24px; height: 24px; border-radius: 50%; border: none;
  background: rgba(0,0,0,0.7); color: #fff; font-size: 16px;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
}
.image-thumb-badge {
  position: absolute; bottom: 6px; left: 6px;
  padding: 2px 8px; background: var(--accent); color: #fff;
  font-size: 10px; font-weight: 700; border-radius: 4px;
}

.follower-row { display: flex; align-items: center; gap: 10px; }
.follower-row input { flex: 1; }
.inline-check { display: flex; align-items: center; gap: 6px; font-size: 0.85rem; color: var(--text2); white-space: nowrap; cursor: pointer; }
.inline-check input { width: auto; }

.form-actions { display: flex; gap: 0.75rem; padding-top: 0.5rem; }
.btn { padding: 0.75rem 1.5rem; border: none; border-radius: 8px; font-size: 1rem; font-weight: 600; cursor: pointer; text-decoration: none; text-align: center; transition: all 0.2s; font-family: 'Pretendard', sans-serif; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-primary { background: var(--gradient); color: #fff; }
.btn-primary:hover:not(:disabled) { opacity: 0.9; transform: translateY(-1px); }
.btn-outline { background: transparent; color: var(--accent); border: 1.5px solid var(--accent); }
.btn-outline:hover { background: rgba(108, 99, 255, 0.1); }

@media (max-width: 640px) {
  .form-row { grid-template-columns: 1fr; }
  .image-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
