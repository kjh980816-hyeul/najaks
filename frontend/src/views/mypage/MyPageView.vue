<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { useRouter, useRoute } from 'vue-router'
import api from '@/api'
import Pagination from '@/components/Pagination.vue'
import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'

const authStore = useAuthStore()
const notify = useNotificationStore()
const router = useRouter()
const route = useRoute()

// 치지직 라이브 연동
const chzzkStatus = ref({ connected: false, chatAnalysisEnabled: false, expiresAt: null, scope: null })
const chzzkBusy = ref(false)

async function fetchChzzkStatus() {
  try {
    const { data } = await api.get('/chzzk/oauth/status')
    chzzkStatus.value = data || { connected: false }
  } catch (e) {
    chzzkStatus.value = { connected: false }
  }
}

async function connectChzzk() {
  if (chzzkBusy.value) return
  chzzkBusy.value = true
  try {
    const { data } = await api.post('/chzzk/oauth/authorize-url')
    if (data && data.authorizeUrl) {
      window.location.href = data.authorizeUrl
    } else {
      notify.error('치지직 연동 URL을 받지 못했습니다')
    }
  } catch (e) {
    notify.error(e.response?.data?.error || '치지직 연동 URL 요청 실패')
  } finally {
    chzzkBusy.value = false
  }
}

async function disconnectChzzk() {
  if (!confirm('치지직 연동을 해제하시겠어요? 방송 채팅 수집과 이메일 리포트가 중단됩니다.')) return
  try {
    await api.delete('/chzzk/oauth/connection')
    notify.success('치지직 연동이 해제되었습니다')
    await fetchChzzkStatus()
  } catch (e) {
    notify.error('연동 해제 실패')
  }
}

const profile = ref(null)
const isLoading = ref(true)
const isEditing = ref(false)
const isSaving = ref(false)

const editForm = ref({
  nickname: '',
  profileImage: ''
})

const streamerEditForm = ref({
  coverImage: '',
  bio: '',
  broadcastSchedule: '',
  youtubeUrl: '',
  chzzkUrl: '',
  soopUrl: '',
  scheduleImageUrl: '',
  category: ''
})

const application = ref(null)

// 클립/방송예고
const myClips = ref([])
const mySchedules = ref([])

const pageSize = 10
const clipPage = ref(1)
const schedulePage = ref(1)
const pagedClips = computed(() => {
  const s = (clipPage.value - 1) * pageSize
  return myClips.value.slice(s, s + pageSize)
})
const pagedSchedules = computed(() => {
  const s = (schedulePage.value - 1) * pageSize
  return mySchedules.value.slice(s, s + pageSize)
})
const clipTotalPages = computed(() => Math.max(Math.ceil(myClips.value.length / pageSize), 1))
const scheduleTotalPages = computed(() => Math.max(Math.ceil(mySchedules.value.length / pageSize), 1))
const clipForm = ref({ title: '', url: '' })
const scheduleForm = ref({ title: '', description: '', scheduledAt: '' })
const clipSubmitting = ref(false)
const scheduleSubmitting = ref(false)

// 일정표 이미지
const scheduleImageFile = ref(null)
const scheduleImagePreview = ref('')
const scheduleUploading = ref(false)

function handleScheduleImageSelect(event) {
  const file = event.target.files[0]
  if (!file) return
  scheduleImageFile.value = file
  scheduleImagePreview.value = URL.createObjectURL(file)
}

function cancelScheduleImage() {
  scheduleImageFile.value = null
  scheduleImagePreview.value = ''
}

async function submitScheduleImage() {
  if (!scheduleImageFile.value) return
  scheduleUploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', scheduleImageFile.value)
    formData.append('directory', 'schedule-images')
    const { data } = await api.post('/files/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    streamerEditForm.value.scheduleImageUrl = data.data
    await api.put('/streamers/profile', streamerEditForm.value)
    notify.success('일정표가 업로드되었습니다')
    scheduleImageFile.value = null
    scheduleImagePreview.value = ''
    fetchProfile()
  } catch { notify.error('일정표 업로드에 실패했습니다') }
  scheduleUploading.value = false
}

async function removeScheduleImage() {
  if (!confirm('일정표를 삭제하시겠습니까?')) return
  streamerEditForm.value.scheduleImageUrl = ''
  try {
    await api.put('/streamers/profile', streamerEditForm.value)
    notify.success('일정표가 삭제되었습니다')
    fetchProfile()
  } catch { notify.error('삭제 실패') }
}

const isStreamer = computed(() => authStore.user?.role === 'STREAMER')
const isFan = computed(() => authStore.user?.role === 'FAN')
const showApplicationSection = computed(() => isFan.value)

async function fetchProfile() {
  try {
    const { data } = await api.get('/users/me')
    const payload = data?.data || data
    if (!payload || !payload.role) {
      throw new Error('Invalid profile payload')
    }
    profile.value = payload
    editForm.value.nickname = payload.nickname || ''
    editForm.value.profileImage = payload.profileImage || ''

    if (payload.streamerProfile) {
      const sp = payload.streamerProfile
      streamerEditForm.value = {
        coverImage: sp.coverImage || '',
        bio: sp.bio || '',
        broadcastSchedule: sp.broadcastSchedule || '',
        youtubeUrl: sp.youtubeUrl || '',
        chzzkUrl: sp.chzzkUrl || '',
        soopUrl: sp.soopUrl || '',
        scheduleImageUrl: sp.scheduleImageUrl || '',
        category: sp.category || ''
      }
    }
  } catch (error) {
    if (error?.response?.status === 401) {
      authStore.logout()
      router.replace({ name: 'Login', query: { redirect: '/mypage' } })
      return
    }
    notify.error('프로필을 불러올 수 없습니다. 다시 로그인해주세요.')
    authStore.logout()
    router.replace({ name: 'Login', query: { redirect: '/mypage' } })
  } finally {
    isLoading.value = false
  }
}

async function fetchApplication() {
  try {
    const { data } = await api.get('/streamer-applications/me')
    application.value = data.data
  } catch {
    // no application
  }
}

async function handleSaveProfile() {
  isSaving.value = true
  try {
    // 기본 프로필 저장
    const { data } = await api.put('/users/me', {
      nickname: editForm.value.nickname,
      profileImage: editForm.value.profileImage || null
    })
    profile.value = { ...profile.value, ...data.data }
    authStore.user.nickname = data.data.nickname
    authStore.user.profileImage = data.data.profileImage
    localStorage.setItem('user', JSON.stringify(authStore.user))

    // 스트리머면 스트리머 프로필도 같이 저장
    if (isStreamer.value) {
      await api.put('/streamers/profile', streamerEditForm.value)
    }

    isEditing.value = false
    notify.success('프로필이 업데이트되었습니다')
    fetchProfile()
  } catch (error) {
    notify.error(error.response?.data?.message || '프로필 업데이트에 실패했습니다')
  } finally {
    isSaving.value = false
  }
}

async function handleProfileImageUpload(event) {
  const file = event.target.files[0]
  if (!file) return

  const formData = new FormData()
  formData.append('file', file)
  formData.append('directory', 'profile-images')

  try {
    const { data } = await api.post('/files/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    editForm.value.profileImage = data.data
    notify.success('이미지가 업로드되었습니다')
  } catch {
    notify.error('이미지 업로드에 실패했습니다')
  }
}

// ── 커버 이미지 크롭 ──
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
      streamerEditForm.value.coverImage = data.data
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

function getStatusText(status) {
  switch (status) {
    case 'PENDING': return '심사 중'
    case 'APPROVED': return '승인됨'
    case 'REJECTED': return '반려됨'
    default: return status
  }
}

function getStatusClass(status) {
  switch (status) {
    case 'PENDING': return 'status-pending'
    case 'APPROVED': return 'status-approved'
    case 'REJECTED': return 'status-rejected'
    default: return ''
  }
}

function getClipThumbnail(url) {
  if (!url) return null
  const ytMatch = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/|youtube\.com\/shorts\/)([a-zA-Z0-9_-]{11})/)
  if (ytMatch) return `https://img.youtube.com/vi/${ytMatch[1]}/hqdefault.jpg`
  if (url.includes('chzzk.naver.com')) return '/chzzk-logo.png'
  return null
}

async function fetchMyClips() {
  try {
    const { data } = await api.get('/public/clips')
    const uid = Number(authStore.user?.id)
    myClips.value = (data.data || []).filter(c => Number(c.streamerId) === uid)
  } catch { myClips.value = [] }
}
async function fetchMySchedules() {
  try {
    const { data } = await api.get('/public/schedules')
    const uid = Number(authStore.user?.id)
    mySchedules.value = (data.data || []).filter(s => Number(s.streamerId) === uid)
  } catch { mySchedules.value = [] }
}
async function submitClip() {
  if (!clipForm.value.title || !clipForm.value.url) { notify.warning('제목과 URL을 입력해주세요'); return }
  clipSubmitting.value = true
  try {
    const fd = new FormData(); fd.append('title', clipForm.value.title); fd.append('url', clipForm.value.url)
    await api.post('/clips', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    notify.success('클립이 등록되었습니다'); clipForm.value = { title: '', url: '' }; fetchMyClips()
  } catch (e) { notify.error(e.response?.data?.message || '등록 실패') }
  clipSubmitting.value = false
}
async function deleteClip(id) {
  if (!confirm('삭제하시겠습니까?')) return
  try { await api.delete(`/clips/${id}`); notify.success('삭제됨'); fetchMyClips() } catch { notify.error('삭제 실패') }
}
async function submitSchedule() {
  if (!scheduleForm.value.title || !scheduleForm.value.scheduledAt) { notify.warning('제목과 시간을 입력해주세요'); return }
  scheduleSubmitting.value = true
  try {
    const fd = new FormData()
    fd.append('title', scheduleForm.value.title)
    if (scheduleForm.value.description) fd.append('description', scheduleForm.value.description)
    fd.append('scheduledAt', scheduleForm.value.scheduledAt + ':00')
    await api.post('/schedules', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    notify.success('방송 예고가 등록되었습니다'); scheduleForm.value = { title: '', description: '', scheduledAt: '' }; fetchMySchedules()
  } catch (e) { notify.error(e.response?.data?.message || '등록 실패') }
  scheduleSubmitting.value = false
}
async function deleteSchedule(id) {
  if (!confirm('삭제하시겠습니까?')) return
  try { await api.delete(`/schedules/${id}`); notify.success('삭제됨'); fetchMySchedules() } catch { notify.error('삭제 실패') }
}

onMounted(() => {
  fetchProfile()
  fetchApplication()
  if (authStore.user?.role === 'STREAMER' || authStore.user?.role === 'ADMIN') {
    fetchMyClips()
    fetchMySchedules()
    fetchChzzkStatus()
  }

  // OAuth 콜백 결과 토스트
  const chzzkQuery = route.query.chzzk
  if (chzzkQuery) {
    if (chzzkQuery === 'connected') notify.success('치지직 연동 완료! 이제 라이브 감지 및 채팅 분석이 활성화됩니다')
    else if (chzzkQuery === 'denied') notify.error('치지직 연동이 거부되었습니다')
    else if (chzzkQuery === 'expired') notify.error('인증 세션이 만료되었습니다. 다시 시도해주세요')
    else if (chzzkQuery === 'error') notify.error('치지직 연동 중 오류가 발생했습니다')
    // URL 쿼리 제거
    router.replace({ query: { ...route.query, chzzk: undefined } })
  }
})
</script>

<template>
  <div class="mypage">
    <div class="page-header">
      <h1>마이페이지</h1>
    </div>

    <div v-if="isLoading" class="loading">
      <div class="spinner"></div>
      <p>로딩 중...</p>
    </div>

    <div v-else-if="profile" class="profile-content">
      <!-- 프로필 카드 -->
      <div class="profile-card">
        <div class="profile-avatar-section">
          <div class="avatar">
            <img v-if="profile.profileImage" :src="profile.profileImage" alt="프로필" />
            <div v-else class="avatar-placeholder">
              {{ profile.nickname?.charAt(0)?.toUpperCase() }}
            </div>
          </div>
          <div class="profile-info">
            <h2>{{ profile.nickname }}</h2>
            <p class="email">{{ profile.email }}</p>
            <div class="profile-badges">
              <span class="role-badge" :class="'role-' + (profile.role || '').toLowerCase()">
                {{ profile.role }}
              </span>
              <!-- 플랫폼 마크 -->
              <template v-if="isStreamer && profile.streamerProfile">
                <a v-if="profile.streamerProfile.youtubeUrl" :href="profile.streamerProfile.youtubeUrl" target="_blank" class="platform-badge youtube" title="YouTube">
                  <svg width="14" height="10" viewBox="0 0 28 20"><path d="M27.4 3.1s-.3-1.9-1.1-2.7C25.1-.8 23.7-.8 23-.9 19.2-1.2 14-1.2 14-1.2s-5.2 0-9 .3c-.7.1-2.1.1-3.3 1.2C.9 1.2.6 3.1.6 3.1S.3 5.3.3 7.5v2c0 2.2.3 4.4.3 4.4s.3 1.9 1.1 2.7c1.2 1.2 2.7 1.1 3.4 1.3 2.5.2 10.9.3 10.9.3s5.2 0 9-.3c.7-.1 2.1-.1 3.3-1.2.8-.8 1.1-2.7 1.1-2.7s.3-2.2.3-4.4v-2c0-2.2-.3-4.5-.3-4.5z" fill="#fff"/><path d="M11 13.5V5l7.5 4.3L11 13.5z" fill="#FF0000"/></svg>
                </a>
                <a v-if="profile.streamerProfile.chzzkUrl" :href="profile.streamerProfile.chzzkUrl" target="_blank" class="platform-badge chzzk" title="치지직">
                  <img src="/chzzk-logo.png" alt="치지직" style="width: 16px; height: 16px; border-radius: 3px;" />
                </a>
                <a v-if="profile.streamerProfile.soopUrl" :href="profile.streamerProfile.soopUrl" target="_blank" class="platform-badge soop" title="SOOP">
                  <svg width="12" height="12" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" fill="none" stroke="#fff" stroke-width="2.5"/><circle cx="12" cy="12" r="4" fill="#fff"/></svg>
                </a>
              </template>
            </div>
          </div>
        </div>

        <button v-if="!isEditing" class="btn btn-outline" @click="isEditing = true">
          프로필 편집
        </button>
      </div>

      <!-- 통합 프로필 편집 폼 -->
      <div v-if="isEditing" class="edit-section">
        <h3>프로필 편집</h3>
        <form @submit.prevent="handleSaveProfile" class="edit-form">
          <!-- 기본 정보 -->
          <div class="edit-subsection">
            <div class="subsection-title">기본 정보</div>
            <div class="form-group">
              <label>프로필 이미지</label>
              <div class="image-upload">
                <div class="preview">
                  <img v-if="editForm.profileImage" :src="editForm.profileImage" alt="미리보기" />
                  <div v-else class="preview-placeholder">이미지 없음</div>
                </div>
                <input type="file" accept="image/*" @change="handleProfileImageUpload" />
              </div>
            </div>

            <div class="form-group">
              <label for="nickname">닉네임</label>
              <input id="nickname" v-model="editForm.nickname" type="text" placeholder="닉네임 입력" required />
            </div>
          </div>

          <!-- 스트리머 전용 -->
          <template v-if="isStreamer">
            <div class="edit-subsection">
              <div class="subsection-title">스트리머 프로필</div>

              <div class="form-group">
                <label>커버 이미지 <span style="font-size:11px;color:#888;">(스트리머 상세 페이지 상단 배경)</span></label>
                <div class="cover-preview" v-if="streamerEditForm.coverImage">
                  <img :src="streamerEditForm.coverImage" alt="커버" />
                </div>
                <input type="file" accept="image/*" @change="handleCoverSelect" />
                <span class="cover-upload-hint">업로드 시 영역을 직접 선택할 수 있어요 (가로형 배너)</span>
              </div>

              <div class="form-group">
                <label for="category">카테고리</label>
                <select id="category" v-model="streamerEditForm.category" class="manage-input">
                  <option value="">선택 안 함</option>
                  <option value="GAME">게임</option>
                  <option value="MUSIC">음악</option>
                  <option value="MUKBANG">먹방</option>
                  <option value="TALK">일상/토크</option>
                  <option value="SPORTS">스포츠</option>
                  <option value="ETC">기타</option>
                </select>
              </div>

              <div class="form-group">
                <label for="bio">자기소개</label>
                <textarea id="bio" v-model="streamerEditForm.bio" rows="3" placeholder="스트리머 활동에 대해 소개해주세요"></textarea>
              </div>

              <div class="form-group">
                <label for="schedule">방송 시간표</label>
                <textarea id="schedule" v-model="streamerEditForm.broadcastSchedule" rows="2" placeholder="예: 월·수·금 20:00~23:00"></textarea>
              </div>
            </div>

            <div class="edit-subsection">
              <div class="subsection-title">플랫폼 링크</div>

              <div class="form-group">
                <label for="youtube">
                  <span class="platform-icon-label youtube-icon">▶</span> YouTube
                </label>
                <input id="youtube" v-model="streamerEditForm.youtubeUrl" type="url" placeholder="https://youtube.com/@채널명" />
              </div>

              <div class="form-group">
                <label for="chzzk">
                  <span class="platform-icon-label chzzk-icon">치</span> 치지직
                </label>
                <input id="chzzk" v-model="streamerEditForm.chzzkUrl" type="url" placeholder="https://chzzk.naver.com/채널ID" />
              </div>

              <div class="form-group">
                <label for="soop">
                  <span class="platform-icon-label soop-icon">S</span> SOOP
                </label>
                <input id="soop" v-model="streamerEditForm.soopUrl" type="url" placeholder="https://www.sooplive.co.kr/채널명" />
              </div>
            </div>
          </template>

          <div class="form-actions">
            <button type="submit" class="btn btn-primary" :disabled="isSaving">
              {{ isSaving ? '저장 중...' : '저장' }}
            </button>
            <button type="button" class="btn btn-outline" @click="isEditing = false">취소</button>
          </div>
        </form>
      </div>

      <!-- 스트리머 프로필 정보 (보기 모드) -->
      <div v-if="isStreamer && profile.streamerProfile && !isEditing" class="streamer-section">
        <div class="section-header">
          <h3>스트리머 프로필</h3>
        </div>

        <div class="streamer-info-grid">
          <div class="info-item">
            <span class="label">인증 상태</span>
            <span class="verified-badge" v-if="profile.streamerProfile.verified">인증됨 ✓</span>
            <span v-else class="unverified">미인증</span>
          </div>
          <div class="info-item" v-if="profile.streamerProfile.bio">
            <span class="label">자기소개</span>
            <span>{{ profile.streamerProfile.bio }}</span>
          </div>
          <div class="info-item" v-if="profile.streamerProfile.youtubeUrl">
            <span class="label"><span class="platform-icon-label youtube-icon">▶</span> YouTube</span>
            <a :href="profile.streamerProfile.youtubeUrl" target="_blank">{{ profile.streamerProfile.youtubeUrl }}</a>
          </div>
          <div class="info-item" v-if="profile.streamerProfile.chzzkUrl">
            <span class="label"><span class="platform-icon-label chzzk-icon">치</span> 치지직</span>
            <a :href="profile.streamerProfile.chzzkUrl" target="_blank">{{ profile.streamerProfile.chzzkUrl }}</a>
          </div>
          <div class="info-item" v-if="profile.streamerProfile.soopUrl">
            <span class="label"><span class="platform-icon-label soop-icon">S</span> SOOP</span>
            <a :href="profile.streamerProfile.soopUrl" target="_blank">{{ profile.streamerProfile.soopUrl }}</a>
          </div>
        </div>
      </div>

      <!-- 치지직 라이브 연동 -->
      <div v-if="isStreamer && profile.streamerProfile?.verified" class="streamer-section chzzk-connect-section">
        <div class="section-header">
          <h3><span class="platform-icon-label chzzk-icon">치</span> 치지직 라이브 연동</h3>
        </div>

        <div v-if="!chzzkStatus.connected" class="chzzk-not-connected">
          <div class="chzzk-notice">
            <div class="chzzk-notice-title">📢 지금 연동하면 이렇게 바뀝니다</div>
            <div class="chzzk-notice-body">
              치지직 계정을 연동한 뒤 방송을 시작하면
              <b>나작스 메인 화면 상단 "지금 라이브" 섹션</b>에 내 방송이 자동으로 노출됩니다.
              연동하지 않은 스트리머는 메인 화면에 뜨지 않으니 꼭 연결해 주세요.
            </div>
          </div>
          <p class="chzzk-desc">
            치지직 계정을 연동하면:
          </p>
          <ul class="chzzk-benefits">
            <li>🔴 방송 시작 시 나작스 <b>메인 "지금 라이브" 섹션</b>에 자동 노출</li>
            <li>📊 방송 종료 시 <b>AI 채팅 분석 리포트</b>를 이메일로 자동 발송 (프리미엄)</li>
            <li>💬 채팅 급상승 순간을 <b>하이라이트</b>로 자동 수집</li>
          </ul>
          <button @click="connectChzzk" class="btn btn-primary chzzk-connect-btn" :disabled="chzzkBusy">
            {{ chzzkBusy ? '이동 중...' : '치지직 계정 연동하기' }}
          </button>
          <p class="chzzk-hint">치지직 로그인 → 권한 동의 → 자동 복귀</p>
        </div>

        <div v-else class="chzzk-connected">
          <div class="chzzk-status-row">
            <span class="chzzk-badge-connected">✅ 연동됨</span>
            <span v-if="chzzkStatus.chatAnalysisEnabled" class="chzzk-badge-premium">프리미엄 활성</span>
            <span v-else class="chzzk-badge-free">기본 플랜</span>
          </div>
          <p class="chzzk-hint" v-if="chzzkStatus.expiresAt">
            토큰 만료: {{ new Date(chzzkStatus.expiresAt).toLocaleString('ko-KR') }} (자동 갱신)
          </p>
          <button @click="disconnectChzzk" class="btn btn-secondary btn-sm">연동 해제</button>
        </div>
      </div>

      <!-- 클립 관리 -->
      <div v-if="isStreamer && profile.streamerProfile?.verified" class="streamer-section">
        <div class="section-header"><h3>클립 관리</h3></div>
        <form @submit.prevent="submitClip" class="manage-form">
          <input v-model="clipForm.title" type="text" placeholder="클립 제목" class="manage-input" />
          <input v-model="clipForm.url" type="url" placeholder="클립 URL (유튜브, 치지직 등)" class="manage-input" />
          <button type="submit" class="btn btn-primary btn-sm" :disabled="clipSubmitting">{{ clipSubmitting ? '등록 중...' : '클립 등록' }}</button>
        </form>
        <div v-if="myClips.length > 0" class="manage-list">
          <div v-for="c in pagedClips" :key="c.id" class="manage-item">
            <a v-if="getClipThumbnail(c.url)" :href="c.url" target="_blank" class="clip-thumb-sm">
              <img :src="getClipThumbnail(c.url)" alt="미리보기" />
              <span class="clip-play-sm">▶</span>
            </a>
            <div class="manage-item-info"><strong>{{ c.title }}</strong><a :href="c.url" target="_blank" class="manage-link">{{ c.url }}</a></div>
            <button class="btn-del" @click="deleteClip(c.id)">삭제</button>
          </div>
        </div>
        <Pagination v-if="myClips.length > 0" :current-page="clipPage" :total-pages="clipTotalPages" @change="p => clipPage = p" />
      </div>

      <!-- 일정표 관리 -->
      <div v-if="isStreamer && profile.streamerProfile?.verified" class="streamer-section">
        <div class="section-header"><h3>📅 일정표 관리</h3></div>

        <!-- 현재 등록된 일정표 -->
        <div v-if="profile.streamerProfile?.scheduleImageUrl && !scheduleImagePreview" class="schedule-image-preview">
          <img :src="profile.streamerProfile.scheduleImageUrl" alt="일정표" />
          <div class="schedule-actions" style="margin-top:0.5rem; display:flex; gap:0.5rem;">
            <label class="btn btn-outline btn-sm" style="cursor:pointer;">
              이미지 변경
              <input type="file" accept="image/*" @change="handleScheduleImageSelect" hidden />
            </label>
            <button class="btn-del" @click="removeScheduleImage">삭제</button>
          </div>
        </div>

        <!-- 새 이미지 미리보기 (선택 후 업로드 전) -->
        <div v-else-if="scheduleImagePreview" class="schedule-image-preview">
          <p style="font-size:0.8rem; color:var(--accent); margin-bottom:0.5rem; font-weight:600;">새 일정표 미리보기</p>
          <img :src="scheduleImagePreview" alt="미리보기" />
          <div class="schedule-actions" style="margin-top:0.75rem; display:flex; gap:0.5rem;">
            <button class="btn btn-primary btn-sm" :disabled="scheduleUploading" @click="submitScheduleImage">
              {{ scheduleUploading ? '업로드 중...' : '일정표 업로드' }}
            </button>
            <button class="btn btn-outline btn-sm" @click="cancelScheduleImage">취소</button>
          </div>
        </div>

        <!-- 일정표 없을 때 -->
        <div v-else class="schedule-image-upload">
          <label class="upload-area">
            📅 일정표 이미지를 선택하세요 (방송 시간표 등)
            <input type="file" accept="image/*" @change="handleScheduleImageSelect" hidden />
          </label>
        </div>
      </div>

      <!-- 방송 예고 관리 -->
      <div v-if="isStreamer && profile.streamerProfile?.verified" class="streamer-section">
        <div class="section-header"><h3>방송 예고</h3></div>
        <form @submit.prevent="submitSchedule" class="manage-form">
          <input v-model="scheduleForm.title" type="text" placeholder="방송 제목" class="manage-input" />
          <input v-model="scheduleForm.description" type="text" placeholder="설명 (선택)" class="manage-input" />
          <input v-model="scheduleForm.scheduledAt" type="datetime-local" class="manage-input" />
          <button type="submit" class="btn btn-primary btn-sm" :disabled="scheduleSubmitting">{{ scheduleSubmitting ? '등록 중...' : '예고 등록' }}</button>
        </form>
        <div v-if="mySchedules.length > 0" class="manage-list">
          <div v-for="s in pagedSchedules" :key="s.id" class="manage-item">
            <div class="manage-item-info"><strong>{{ s.title }}</strong><span class="manage-meta">{{ new Date(s.scheduledAt).toLocaleString('ko-KR') }}</span></div>
            <button class="btn-del" @click="deleteSchedule(s.id)">삭제</button>
          </div>
        </div>
        <Pagination v-if="mySchedules.length > 0" :current-page="schedulePage" :total-pages="scheduleTotalPages" @change="p => schedulePage = p" />
      </div>

      <!-- 스트리머 인증 신청 (팬 또는 미인증 스트리머) -->
      <div v-if="showApplicationSection" class="application-section">
        <h3>스트리머 인증</h3>

        <div v-if="application" class="application-status">
          <p>
            신청 상태:
            <span :class="getStatusClass(application.status)">{{ getStatusText(application.status) }}</span>
          </p>
          <p class="application-date">신청일: {{ new Date(application.createdAt).toLocaleDateString('ko-KR') }}</p>
          <div v-if="application.status === 'REJECTED'" class="rejected-box">
            <p class="rejected-notice">반려된 신청입니다. 사유를 확인하고 다시 신청해주세요.</p>
            <div v-if="application.rejectionReason" class="rejected-reason">
              <strong>거절 사유</strong>
              <p>{{ application.rejectionReason }}</p>
            </div>
          </div>
        </div>

        <div v-if="isStreamer && !application && (!profile.streamerProfile || !profile.streamerProfile.verified)" class="application-status" style="margin-bottom:1rem;">
          <p>스트리머로 가입했지만 인증 신청이 완료되지 않았습니다. 아래 버튼을 눌러 신청해주세요.</p>
        </div>

        <router-link v-if="!application || application.status === 'REJECTED'" to="/streamer/apply" class="btn btn-primary">
          스트리머 인증 신청하기
        </router-link>
      </div>
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
.mypage {
  max-width: 800px;
  margin: 0 auto;
  padding: 2rem 1rem;
  font-family: 'Pretendard', sans-serif;
}

.page-header h1 {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 1.5rem;
}

.loading { text-align: center; padding: 4rem 0; color: var(--text3); }

.spinner {
  width: 40px; height: 40px;
  border: 3px solid var(--border); border-top-color: var(--accent);
  border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto 1rem;
}

@keyframes spin { to { transform: rotate(360deg); } }

.profile-card {
  background: var(--card); border: 1px solid var(--border); border-radius: 16px;
  padding: 2rem; display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 1.5rem; backdrop-filter: blur(12px);
}

.profile-avatar-section { display: flex; align-items: center; gap: 1.25rem; }

.avatar {
  width: 72px; height: 72px; border-radius: 50%; overflow: hidden;
  flex-shrink: 0; border: 2px solid var(--border);
}

.avatar img { width: 100%; height: 100%; object-fit: cover; }

.avatar-placeholder {
  width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;
  background: var(--gradient); color: #fff; font-size: 1.5rem; font-weight: 700;
}

.profile-info h2 { font-size: 1.25rem; font-weight: 700; color: var(--text); margin-bottom: 0.25rem; }
.email { color: var(--text3); font-size: 0.9rem; margin-bottom: 0.5rem; }

.profile-badges { display: flex; flex-wrap: wrap; gap: 0.4rem; align-items: center; }

.role-badge {
  display: inline-block; padding: 0.2rem 0.7rem; border-radius: 20px;
  font-size: 0.75rem; font-weight: 600; text-transform: uppercase;
}

.role-fan { background: rgba(108, 99, 255, 0.15); color: var(--accent); }
.role-streamer { background: rgba(0, 212, 170, 0.15); color: var(--accent3); }
.role-admin { background: rgba(255, 107, 157, 0.15); color: var(--accent2); }

.platform-badge {
  display: inline-flex; align-items: center; justify-content: center;
  width: 22px; height: 22px; border-radius: 5px;
  text-decoration: none; transition: transform 0.15s;
}
.platform-badge:hover { transform: scale(1.15); }
.platform-badge.youtube { background: #FF0000; }
.platform-badge.chzzk { background: #03C75A; }
.platform-badge.soop { background: #0078ff; }

.btn {
  padding: 0.6rem 1.25rem; border: none; border-radius: 8px; font-size: 0.9rem;
  font-weight: 600; cursor: pointer; transition: all 0.2s; text-decoration: none;
  display: inline-block; text-align: center; font-family: 'Pretendard', sans-serif;
}

.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-primary { background: var(--gradient); color: #fff; }
.btn-primary:hover:not(:disabled) { opacity: 0.9; transform: translateY(-1px); }
.btn-outline { background: transparent; color: var(--accent); border: 1.5px solid var(--accent); }
.btn-outline:hover { background: rgba(108, 99, 255, 0.1); }
.btn-sm { padding: 0.4rem 0.8rem; font-size: 0.8rem; }

.edit-section, .streamer-section, .application-section {
  background: var(--card); border: 1px solid var(--border); border-radius: 16px;
  padding: 2rem; margin-bottom: 1.5rem; backdrop-filter: blur(12px);
}

/* 치지직 연동 섹션 */
.chzzk-connect-section { border-left: 4px solid #00ffa3; }
.chzzk-desc { margin: 0 0 0.75rem; color: var(--text); }
.chzzk-benefits { margin: 0 0 1.25rem; padding-left: 1.25rem; color: var(--text-muted); line-height: 1.9; }
.chzzk-notice {
  background: linear-gradient(135deg, rgba(0,255,163,0.12), rgba(108,99,255,0.10));
  border: 1px solid rgba(0,255,163,0.35);
  border-radius: 10px;
  padding: 0.9rem 1rem;
  margin-bottom: 1rem;
}
.chzzk-notice-title {
  font-weight: 800; color: var(--text); margin-bottom: 0.3rem; font-size: 0.95rem;
}
.chzzk-notice-body {
  color: var(--text); line-height: 1.65; font-size: 0.88rem;
}
.chzzk-benefits li { margin-bottom: 0.25rem; }
.chzzk-connect-btn {
  background: linear-gradient(135deg, #00ffa3, #00c6ff);
  color: #0b1f1a; font-weight: 700; border: none; padding: 0.75rem 1.5rem; border-radius: 10px;
}
.chzzk-connect-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.chzzk-hint { font-size: 0.85rem; color: var(--text-muted); margin: 0.75rem 0 0; }
.chzzk-status-row { display: flex; gap: 0.5rem; margin-bottom: 0.75rem; flex-wrap: wrap; }
.chzzk-badge-connected, .chzzk-badge-premium, .chzzk-badge-free {
  padding: 0.25rem 0.75rem; border-radius: 20px; font-size: 0.85rem; font-weight: 600;
}
.chzzk-badge-connected { background: #00ffa322; color: #00c477; border: 1px solid #00ffa366; }
.chzzk-badge-premium { background: #ffd70022; color: #ffb000; border: 1px solid #ffd70066; }
.chzzk-badge-free { background: var(--border); color: var(--text-muted); }

.edit-section h3, .streamer-section h3, .application-section h3 {
  font-size: 1.1rem; font-weight: 700; color: var(--text); margin-bottom: 1.25rem;
}

.section-header {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem;
}
.section-header h3 { margin-bottom: 0; }

.edit-form { display: flex; flex-direction: column; gap: 1.25rem; }

.edit-subsection {
  background: rgba(255,255,255,0.02); border: 1px solid var(--border);
  border-radius: 12px; padding: 1.25rem; display: flex; flex-direction: column; gap: 1rem;
}

.subsection-title {
  font-size: 0.85rem; font-weight: 700; color: var(--accent);
  text-transform: uppercase; letter-spacing: 0.5px;
  padding-bottom: 0.5rem; border-bottom: 1px solid var(--border);
}

.form-group { display: flex; flex-direction: column; gap: 0.4rem; }

.form-group label {
  font-size: 0.9rem; font-weight: 600; color: var(--text2);
  display: flex; align-items: center; gap: 0.4rem;
}

.form-group input[type="text"],
.form-group input[type="url"],
.form-group textarea {
  padding: 0.75rem 1rem; border: 1.5px solid var(--border); border-radius: 8px;
  font-size: 1rem; outline: none; transition: border-color 0.2s;
  width: 100%; box-sizing: border-box; background: var(--bg2); color: var(--text);
  font-family: 'Pretendard', sans-serif;
}

.form-group input::placeholder, .form-group textarea::placeholder { color: var(--text3); }
.form-group input:focus, .form-group textarea:focus { border-color: var(--accent); }
.form-group input[type="file"] { color: var(--text2); }

.image-upload { display: flex; align-items: center; gap: 1rem; }

.preview {
  width: 80px; height: 80px; border-radius: 8px; overflow: hidden;
  border: 1.5px solid var(--border);
}

.avatar-preview-sm { border-radius: 50%; }

.preview img { width: 100%; height: 100%; object-fit: cover; }

.preview-placeholder {
  width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;
  color: var(--text3); font-size: 0.75rem; background: var(--bg3);
}

.cover-preview {
  width: 100%; max-height: 160px; overflow: hidden; border-radius: 8px;
  border: 1px solid var(--border); margin-bottom: 0.5rem;
}
.cover-preview img { width: 100%; height: auto; object-fit: cover; }

.form-actions { display: flex; gap: 0.75rem; }

.platform-icon-label {
  display: inline-flex; align-items: center; justify-content: center;
  width: 16px; height: 16px; border-radius: 3px; font-size: 9px;
  font-weight: 900; color: white;
}

.youtube-icon { background: #FF0000; }
.chzzk-icon { background: #03C75A; }
.soop-icon { background: #0078ff; }

.streamer-info-grid { display: flex; flex-direction: column; gap: 1rem; }

.info-item { display: flex; flex-direction: column; gap: 0.25rem; color: var(--text2); }

.info-item .label {
  font-size: 0.8rem; font-weight: 600; color: var(--text3);
  display: flex; align-items: center; gap: 0.4rem;
}

.info-item a { color: var(--accent); text-decoration: none; }
.info-item a:hover { text-decoration: underline; }

.verified-badge { color: var(--accent3); font-weight: 600; }
.unverified { color: #fbbf24; font-weight: 600; }

.application-status {
  background: var(--bg3); border: 1px solid var(--border); border-radius: 8px;
  padding: 1rem; margin-bottom: 1rem; color: var(--text2);
}

.status-pending { color: #fbbf24; font-weight: 600; }
.status-approved { color: var(--accent3); font-weight: 600; }
.status-rejected { color: #ef4444; font-weight: 600; }

.application-date { font-size: 0.85rem; color: var(--text3); margin-top: 0.25rem; }
.rejected-box { margin-top: 0.75rem; }
.rejected-notice { color: #ef4444; font-size: 0.85rem; margin-bottom: 0.75rem; }
.rejected-reason {
  background: rgba(239,68,68,0.08);
  border: 1px solid rgba(239,68,68,0.25);
  border-left: 3px solid #ef4444;
  border-radius: 10px;
  padding: 0.85rem 1rem;
}
.rejected-reason strong {
  display: block;
  color: #ef4444;
  font-size: 0.78rem;
  font-weight: 700;
  margin-bottom: 0.35rem;
  letter-spacing: 0.3px;
}
.rejected-reason p {
  color: var(--text);
  font-size: 0.9rem;
  line-height: 1.55;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.manage-form { display: flex; flex-direction: column; gap: 0.5rem; margin-bottom: 1rem; }
.manage-input {
  padding: 0.6rem 0.85rem; background: var(--bg2); border: 1.5px solid var(--border);
  border-radius: 8px; color: var(--text); font-size: 0.88rem; outline: none; font-family: 'Pretendard', sans-serif;
}
.manage-input:focus { border-color: var(--accent); }
.manage-list { display: flex; flex-direction: column; gap: 0.4rem; }
.manage-item {
  display: flex; align-items: center; justify-content: space-between; gap: 0.75rem;
  padding: 0.75rem 1rem; background: var(--bg2); border: 1px solid var(--border); border-radius: 8px;
}
.manage-item-info { display: flex; flex-direction: column; gap: 0.15rem; flex: 1; min-width: 0; }
.manage-item-info strong { font-size: 0.9rem; color: var(--text); }
.manage-link { font-size: 0.78rem; color: var(--accent); text-decoration: none; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.manage-meta { font-size: 0.78rem; color: var(--text3); }
.btn-del {
  padding: 0.25rem 0.6rem; border: 1px solid rgba(239,68,68,0.3); border-radius: 6px;
  background: rgba(239,68,68,0.1); color: #ef4444; font-size: 0.75rem; cursor: pointer;
  font-family: 'Pretendard', sans-serif;
}

.schedule-image-section { margin-bottom: 1.5rem; }
.schedule-image-preview img { width: 100%; max-height: 300px; object-fit: contain; border-radius: 8px; border: 1px solid var(--border); }
.schedule-image-upload .upload-area {
  display: flex; align-items: center; justify-content: center; padding: 2rem;
  border: 2px dashed var(--border); border-radius: 10px; cursor: pointer;
  color: var(--text2); font-size: 0.85rem; transition: background 0.2s;
}
.schedule-image-upload .upload-area:hover { background: rgba(108,99,255,0.05); }

.clip-thumb-sm {
  position: relative; flex-shrink: 0; width: 80px; height: 50px;
  border-radius: 6px; overflow: hidden; display: block;
}
.clip-thumb-sm img { width: 100%; height: 100%; object-fit: cover; }
.clip-play-sm {
  position: absolute; top: 50%; left: 50%; transform: translate(-50%,-50%);
  width: 24px; height: 24px; border-radius: 50%; background: rgba(0,0,0,0.6);
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 10px;
}

@media (max-width: 768px) {
  .profile-card { flex-direction: column; align-items: flex-start; gap: 1rem; }
  .form-actions { flex-direction: column; }
}

.cover-upload-hint {
  display: block;
  font-size: 11px;
  color: var(--text3);
  margin-top: 4px;
}

/* 커버 이미지 크롭 모달 */
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
