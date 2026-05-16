<script setup>
import { ref, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'
import api from '@/api'
import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'

const router = useRouter()
const notify = useNotificationStore()

const isLoading = ref(true)
const isSaving = ref(false)

const form = ref({
  coverImage: '',
  avatar: '',
  bio: '',
  broadcastSchedule: '',
  youtubeUrl: '',
  chzzkUrl: '',
  soopUrl: ''
})

// ── 커버 크롭 ──
const cropModalOpen = ref(false)
const cropSrc = ref('')
const cropImgRef = ref(null)
const coverUploading = ref(false)
let cropperInstance = null

function handleCoverSelect(event) {
  const file = event.target.files[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = async (e) => {
    cropSrc.value = e.target.result
    cropModalOpen.value = true
    await nextTick()
    if (cropperInstance) { cropperInstance.destroy(); cropperInstance = null }
    if (cropImgRef.value) {
      cropperInstance = new Cropper(cropImgRef.value, {
        aspectRatio: 1136 / 230,
        viewMode: 1,
        dragMode: 'move',
        autoCropArea: 0.9,
        background: false,
        movable: true,
        zoomable: true,
        zoomOnWheel: true,
        scalable: false,
        rotatable: false,
        responsive: true,
        cropBoxMovable: true,
        cropBoxResizable: true,
      })
    }
  }
  reader.readAsDataURL(file)
  event.target.value = ''
}

function cancelCrop() {
  cropModalOpen.value = false
  cropSrc.value = ''
  if (cropperInstance) { cropperInstance.destroy(); cropperInstance = null }
}

async function applyCrop() {
  if (!cropperInstance) return
  const canvas = cropperInstance.getCroppedCanvas({
    maxWidth: 1600,
    imageSmoothingQuality: 'high'
  })
  if (!canvas) { notify.error('크롭 처리에 실패했습니다'); return }
  coverUploading.value = true
  canvas.toBlob(async (blob) => {
    if (!blob) { coverUploading.value = false; return }
    const formData = new FormData()
    formData.append('file', blob, 'cover.jpg')
    formData.append('directory', 'streamer-profiles')
    try {
      const { data } = await api.post('/files/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      form.value.coverImage = data.data
      notify.success('커버 이미지가 업로드되었습니다')
      cancelCrop()
    } catch {
      notify.error('이미지 업로드에 실패했습니다')
    } finally {
      coverUploading.value = false
    }
  }, 'image/jpeg', 0.9)
}

onBeforeUnmount(() => {
  if (cropperInstance) { cropperInstance.destroy(); cropperInstance = null }
})

async function fetchProfile() {
  try {
    const { data } = await api.get('/users/me')
    const sp = data.data.streamerProfile
    if (sp) {
      form.value = {
        coverImage: sp.coverImage || '',
        avatar: sp.avatar || '',
        bio: sp.bio || '',
        broadcastSchedule: sp.broadcastSchedule || '',
        youtubeUrl: sp.youtubeUrl || '',
        chzzkUrl: sp.chzzkUrl || '',
        soopUrl: sp.soopUrl || ''
      }
    }
  } catch {
    notify.error('프로필을 불러올 수 없습니다')
  } finally {
    isLoading.value = false
  }
}

async function handleImageUpload(event, field) {
  const file = event.target.files[0]
  if (!file) return

  const formData = new FormData()
  formData.append('file', file)
  formData.append('directory', 'streamer-profiles')

  try {
    const { data } = await api.post('/files/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    form.value[field] = data.data
    notify.success('이미지가 업로드되었습니다')
  } catch {
    notify.error('이미지 업로드에 실패했습니다')
  }
}

async function handleSave() {
  isSaving.value = true
  try {
    await api.put('/streamers/profile', form.value)
    notify.success('스트리머 프로필이 업데이트되었습니다')
    router.push('/mypage')
  } catch (error) {
    notify.error(error.response?.data?.message || '저장에 실패했습니다')
  } finally {
    isSaving.value = false
  }
}

onMounted(fetchProfile)
</script>

<template>
  <div class="edit-profile-page">
    <div class="edit-card">
      <div class="edit-header">
        <h1>스트리머 프로필 편집</h1>
        <p>프로필 정보를 입력하여 팬들에게 보여줄 프로필을 완성하세요.</p>
      </div>

      <div v-if="isLoading" class="loading">
        <div class="spinner"></div>
      </div>

      <form v-else @submit.prevent="handleSave" class="edit-form">
        <!-- 커버 이미지 -->
        <div class="form-section">
          <h3 class="section-title">이미지</h3>

          <div class="form-group">
            <label class="form-label">커버 이미지</label>
            <div class="cover-preview" v-if="form.coverImage">
              <img :src="form.coverImage" alt="커버 이미지" />
            </div>
            <span class="cover-hint">업로드 시 원하는 영역을 직접 잘라서 커버로 사용할 수 있습니다 (권장: 가로가 긴 배너)</span>
            <label class="file-upload-btn">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="17 8 12 3 7 8"/>
                <line x1="12" y1="3" x2="12" y2="15"/>
              </svg>
              이미지 업로드 (영역 선택)
              <input type="file" accept="image/*" @change="handleCoverSelect" hidden />
            </label>
          </div>

          <!-- 아바타 -->
          <div class="form-group">
            <label class="form-label">아바타</label>
            <div class="avatar-preview" v-if="form.avatar">
              <img :src="form.avatar" alt="아바타" />
            </div>
            <label class="file-upload-btn">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="17 8 12 3 7 8"/>
                <line x1="12" y1="3" x2="12" y2="15"/>
              </svg>
              이미지 업로드
              <input type="file" accept="image/*" @change="handleImageUpload($event, 'avatar')" hidden />
            </label>
          </div>
        </div>

        <!-- 자기소개 -->
        <div class="form-section">
          <h3 class="section-title">소개</h3>

          <div class="form-group">
            <label class="form-label" for="bio">자기소개</label>
            <textarea
              id="bio"
              v-model="form.bio"
              rows="4"
              class="form-input"
              placeholder="스트리머 활동에 대해 소개해주세요"
            ></textarea>
          </div>

          <div class="form-group">
            <label class="form-label" for="schedule">방송 시간표</label>
            <textarea
              id="schedule"
              v-model="form.broadcastSchedule"
              rows="3"
              class="form-input"
              placeholder="예: 월·수·금 20:00~23:00"
            ></textarea>
          </div>
        </div>

        <!-- 플랫폼 링크 -->
        <div class="form-section">
          <h3 class="section-title">플랫폼 링크</h3>

          <div class="form-group">
            <label class="form-label" for="youtube">
              <span class="platform-icon">▶</span>
              YouTube
            </label>
            <input
              id="youtube"
              v-model="form.youtubeUrl"
              type="url"
              class="form-input"
              placeholder="https://youtube.com/@채널명"
            />
          </div>

          <div class="form-group">
            <label class="form-label" for="chzzk">
              <span class="platform-icon">●</span>
              치지직
            </label>
            <input
              id="chzzk"
              v-model="form.chzzkUrl"
              type="url"
              class="form-input"
              placeholder="https://chzzk.naver.com/채널ID"
            />
          </div>

          <div class="form-group">
            <label class="form-label" for="soop">
              <span class="platform-icon">◆</span>
              숲(SOOP)
            </label>
            <input
              id="soop"
              v-model="form.soopUrl"
              type="url"
              class="form-input"
              placeholder="https://www.sooplive.co.kr/채널명"
            />
          </div>
        </div>

        <div class="form-actions">
          <button type="submit" class="btn btn-primary" :disabled="isSaving">
            {{ isSaving ? '저장 중...' : '저장하기' }}
          </button>
          <router-link to="/mypage" class="btn btn-outline">취소</router-link>
        </div>
      </form>
    </div>

    <!-- 커버 이미지 크롭 모달 -->
    <div v-if="cropModalOpen" class="crop-modal" @click.self="cancelCrop">
      <div class="crop-modal-card">
        <div class="crop-modal-head">
          <h3>커버로 사용할 영역을 선택해주세요</h3>
          <p>이미지를 드래그해서 위치를 조정하고, 박스 모서리를 잡아 크기를 조절할 수 있어요. 마우스 휠로 확대/축소도 가능해요.</p>
        </div>
        <div class="crop-stage">
          <img ref="cropImgRef" :src="cropSrc" alt="크롭 대상" />
        </div>
        <div class="crop-modal-actions">
          <button type="button" class="btn btn-outline" @click="cancelCrop" :disabled="coverUploading">취소</button>
          <button type="button" class="btn btn-primary" @click="applyCrop" :disabled="coverUploading">
            {{ coverUploading ? '업로드 중...' : '이 영역으로 자르기' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.edit-profile-page {
  max-width: 700px;
  margin: 0 auto;
  padding: 2rem 1rem;
  background: var(--bg, #0a0a0f);
  min-height: 100vh;
}

.edit-card {
  background: var(--bg2, #12121a);
  border: 1px solid var(--border, rgba(255,255,255,0.07));
  border-radius: 16px;
  padding: 2.5rem;
}

.edit-header {
  margin-bottom: 2rem;
}

.edit-header h1 {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text, #f0f0f8);
  margin-bottom: 0.5rem;
}

.edit-header p {
  color: var(--text2, #9999bb);
  font-size: 0.95rem;
}

.loading {
  text-align: center;
  padding: 3rem 0;
}

.spinner {
  width: 36px;
  height: 36px;
  border: 3px solid var(--bg3, #1a1a26);
  border-top-color: var(--accent, #6c63ff);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.edit-form {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  padding: 1.5rem;
  background: var(--card, rgba(255,255,255,0.04));
  border: 1px solid var(--border, rgba(255,255,255,0.07));
  border-radius: 12px;
}

.section-title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--text, #f0f0f8);
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--border, rgba(255,255,255,0.07));
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-label {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text2, #9999bb);
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.platform-icon {
  font-size: 0.7rem;
  color: var(--accent, #6c63ff);
}

.form-input {
  padding: 0.75rem 1rem;
  background: var(--bg3, #1a1a26);
  border: 1.5px solid var(--border, rgba(255,255,255,0.07));
  border-radius: 10px;
  font-size: 1rem;
  color: var(--text, #f0f0f8);
  outline: none;
  transition: border-color 0.2s;
  width: 100%;
  box-sizing: border-box;
  font-family: inherit;
}

.form-input::placeholder {
  color: var(--text3, #555577);
}

.form-input:focus {
  border-color: var(--accent, #6c63ff);
  box-shadow: 0 0 0 3px rgba(108, 99, 255, 0.1);
}

.file-upload-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 1.2rem;
  background: var(--bg3, #1a1a26);
  border: 1.5px solid var(--border, rgba(255,255,255,0.07));
  border-radius: 10px;
  color: var(--text2, #9999bb);
  font-size: 0.85rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  width: fit-content;
}

.file-upload-btn:hover {
  border-color: var(--accent, #6c63ff);
  color: var(--accent, #6c63ff);
  background: rgba(108, 99, 255, 0.05);
}

.cover-preview {
  width: 100%;
  max-height: 200px;
  overflow: hidden;
  border-radius: 10px;
  border: 1px solid var(--border, rgba(255,255,255,0.07));
}

.cover-preview img {
  width: 100%;
  height: auto;
  object-fit: cover;
}

.avatar-preview {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  border: 2px solid var(--border, rgba(255,255,255,0.07));
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.form-actions {
  display: flex;
  gap: 0.75rem;
  padding-top: 0.5rem;
}

.btn {
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 10px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  text-decoration: none;
  text-align: center;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: var(--gradient, linear-gradient(135deg, #6c63ff, #ff6b9d));
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  opacity: 0.9;
  transform: translateY(-1px);
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
  background: rgba(108, 99, 255, 0.05);
}

.cover-hint {
  font-size: 0.78rem;
  color: var(--text3, #555577);
  margin-top: 0.25rem;
  line-height: 1.5;
}

/* 크롭 모달 */
.crop-modal {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.85);
  display: flex; align-items: center; justify-content: center;
  z-index: 9999; padding: 1rem;
}
.crop-modal-card {
  width: 100%;
  max-width: 900px;
  background: var(--bg2, #12121a);
  border: 1px solid var(--border, rgba(255,255,255,0.08));
  border-radius: 14px;
  padding: 1.5rem;
  display: flex; flex-direction: column; gap: 1rem;
  max-height: 90vh;
}
.crop-modal-head h3 {
  font-size: 1.05rem; font-weight: 700;
  color: var(--text); margin: 0 0 6px;
}
.crop-modal-head p {
  font-size: 0.82rem; color: var(--text2);
  margin: 0; line-height: 1.5;
}
.crop-stage {
  flex: 1;
  min-height: 300px;
  max-height: 60vh;
  background: #000;
  border-radius: 10px;
  overflow: hidden;
}
.crop-stage img {
  display: block;
  max-width: 100%;
}
.crop-modal-actions {
  display: flex; gap: 0.5rem; justify-content: flex-end;
}
</style>
