<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import api from '@/api'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const notify = useNotificationStore()

const isSubmitting = ref(false)
const imageFile = ref(null)
const imagePreview = ref('')
const existingImageUrl = ref('')
const clipUrl = ref('')
const editId = computed(() => route.query.edit ? Number(route.query.edit) : null)

const CLIP_URL_REGEX = /(https?:\/\/(?:www\.)?(?:youtube\.com\/(?:watch\?v=|shorts\/|embed\/)[a-zA-Z0-9_-]{11}\S*|youtu\.be\/[a-zA-Z0-9_-]{11}\S*|chzzk\.naver\.com\/\S+))/i

function extractClipThumbnail(url) {
  if (!url) return ''
  const yt = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/shorts\/|youtube\.com\/embed\/)([a-zA-Z0-9_-]{11})/)
  if (yt) return `https://img.youtube.com/vi/${yt[1]}/hqdefault.jpg`
  if (/chzzk\.naver\.com/.test(url)) return '/chzzk-logo.png'
  return ''
}

const clipThumbnail = computed(() => extractClipThumbnail(clipUrl.value))
const isClipBoard = computed(() => form.value?.category === 'CLIP_SHOWCASE')
const isEditMode = computed(() => editId.value != null)
const streamerBoardId = computed(() => route.query.streamerBoard ? Number(route.query.streamerBoard) : null)
const isStreamerBoardMode = computed(() => streamerBoardId.value != null)
const streamerBoardNickname = ref('')

function handleImageUpload(event) {
  const file = event.target.files[0]
  if (!file) return
  imageFile.value = file
  imagePreview.value = URL.createObjectURL(file)
  existingImageUrl.value = ''
}

function removeImage() {
  imageFile.value = null
  imagePreview.value = ''
  existingImageUrl.value = ''
}

const form = ref({
  title: '',
  content: '',
  category: route.query.category || 'FREE'
})

async function loadStreamerInfo() {
  if (!isStreamerBoardMode.value) return
  try {
    const { data } = await api.get(`/public/streamers/${streamerBoardId.value}`)
    streamerBoardNickname.value = data.data?.nickname || ''
  } catch {
    /* silent */
  }
}

async function loadExistingPost() {
  if (!isEditMode.value) return
  try {
    const { data } = await api.get(`/posts/${editId.value}`)
    const p = data.data
    if (!p) throw new Error('not found')
    if (authStore.user?.id !== p.authorId && authStore.user?.role !== 'ADMIN') {
      notify.error('수정 권한이 없습니다')
      router.replace(`/community/${editId.value}`)
      return
    }
    form.value.title = p.title || ''
    form.value.content = p.content || ''
    form.value.category = p.category || 'FREE'
    if (p.imageUrl) {
      existingImageUrl.value = p.imageUrl
      imagePreview.value = p.imageUrl
    }
    // 클립자랑 편집 시 content에서 URL을 추출해서 필드로 분리
    if (form.value.category === 'CLIP_SHOWCASE') {
      const m = (p.content || '').match(CLIP_URL_REGEX)
      if (m) {
        clipUrl.value = m[0]
        form.value.content = (p.content || '').replace(m[0], '').replace(/^\s+/, '').trim()
      }
    }
  } catch {
    notify.error('게시글을 불러오지 못했습니다')
    router.replace('/community')
  }
}

onMounted(() => {
  loadExistingPost()
  loadStreamerInfo()
})

const isStreamer = authStore.user?.role === 'STREAMER' || authStore.user?.role === 'ADMIN'
const isAdmin = authStore.user?.role === 'ADMIN'

const categoryOptions = [
  { value: 'FREE', label: '자유게시판', icon: '💬' },
  { value: 'QUESTION', label: '질문게시판', icon: '❓' },
  { value: 'HUMOR', label: '유머게시판', icon: '😂' },
  { value: 'TIP', label: '팁/정보', icon: '💡' },
  { value: 'INQUIRY', label: '문의 게시판', icon: '📨' },
  { value: 'ARTWORK', label: '작품 공유', icon: '🎨' },
  { value: 'FANART', label: '팬아트', icon: '🖼️' },
  { value: 'CLIP_SHOWCASE', label: '클립 자랑', icon: '🎬' },
  { value: 'GOODS', label: '굿즈 자랑', icon: '🎁' },
  { value: 'PORTFOLIO', label: '포트폴리오', icon: '🗂️' },
  { value: 'WORK_REVIEW', label: '작업 후기', icon: '⭐' },
  { value: 'JOB_SEEKING', label: '구직', icon: '🔍' },
]

const streamerCategories = [
  { value: 'STREAMER_ONLY', label: '스트리머 전용', icon: '🔒' },
  { value: 'RECRUITMENT', label: '구인', icon: '📢' },
]

const adminCategories = [
  { value: 'NOTICE', label: '공지사항', icon: '📢' },
]

const allCategories = (() => {
  let list = [...categoryOptions]
  if (isAdmin) list = [...adminCategories, ...list]
  if (isStreamer) list = [...list, ...streamerCategories]
  return list
})()

async function handleSubmit() {
  if (!form.value.title.trim()) {
    notify.warning('제목을 입력해주세요')
    return
  }
  if (isClipBoard.value) {
    if (!clipUrl.value.trim()) {
      notify.warning('클립 URL을 입력해주세요 (유튜브 / 치지직)')
      return
    }
    if (!extractClipThumbnail(clipUrl.value)) {
      notify.warning('지원하는 클립 URL은 유튜브, 유튜브 쇼츠, 치지직입니다')
      return
    }
  } else if (!form.value.content.trim()) {
    notify.warning('내용을 입력해주세요')
    return
  }

  isSubmitting.value = true
  try {
    let imageUrl = existingImageUrl.value || null
    let finalContent = form.value.content

    // 클립자랑: URL을 본문 맨 앞에 포함, 썸네일을 imageUrl로 저장
    if (isClipBoard.value) {
      finalContent = `${clipUrl.value.trim()}\n\n${form.value.content || ''}`.trim()
      const thumb = extractClipThumbnail(clipUrl.value)
      if (thumb) imageUrl = thumb
    }

    if (imageFile.value) {
      try {
        const formData = new FormData()
        formData.append('file', imageFile.value)
        formData.append('directory', 'post-images')
        const uploadRes = await api.post('/files/upload', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        })
        imageUrl = uploadRes.data.data
      } catch (uploadErr) {
        notify.error('이미지 업로드에 실패했습니다: ' + (uploadErr.response?.data?.message || uploadErr.message))
        isSubmitting.value = false
        return
      }
    }

    if (isEditMode.value) {
      const { data } = await api.put(`/posts/${editId.value}`, { ...form.value, content: finalContent, imageUrl })
      notify.success('게시글이 수정되었습니다')
      router.push(`/community/${data.data.id}`)
    } else {
      const payload = { ...form.value, content: finalContent, imageUrl }
      if (isStreamerBoardMode.value) {
        payload.targetStreamerId = streamerBoardId.value
      }
      const { data } = await api.post('/posts', payload)
      notify.success('게시글이 작성되었습니다')
      if (isStreamerBoardMode.value) {
        router.push(`/streamers/${streamerBoardId.value}`)
      } else {
        router.push(`/community/${data.data.id}`)
      }
    }
  } catch (e) {
    const msg = e.response?.data?.message || (isEditMode.value ? '게시글 수정에 실패했습니다' : '게시글 작성에 실패했습니다')
    notify.error(msg)
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="write-page">
    <div class="write-card">
      <div class="write-header">
        <h1>
          {{ isEditMode ? '글 수정' : (isStreamerBoardMode ? `${streamerBoardNickname || '스트리머'} 팬 게시판 글쓰기` : '글쓰기') }}
        </h1>
        <button class="btn btn-outline" @click="router.back()">← 뒤로</button>
      </div>

      <form @submit.prevent="handleSubmit" class="write-form">
        <div class="form-group">
          <label>카테고리</label>
          <select v-model="form.category" class="form-input">
            <option v-for="cat in allCategories" :key="cat.value" :value="cat.value">
              {{ cat.icon }} {{ cat.label }}
            </option>
          </select>
        </div>

        <div class="form-group">
          <label>제목</label>
          <input
            v-model="form.title"
            type="text"
            class="form-input"
            placeholder="제목을 입력하세요"
            maxlength="100"
          />
        </div>

        <!-- 클립자랑 전용: URL 필드 -->
        <div v-if="isClipBoard" class="form-group">
          <label>클립 URL <span class="required">*</span></label>
          <input
            v-model="clipUrl"
            type="url"
            class="form-input"
            placeholder="유튜브 / 유튜브 쇼츠 / 치지직 URL을 붙여넣으세요"
          />
          <div v-if="clipThumbnail" class="clip-preview-wrap">
            <img :src="clipThumbnail" alt="클립 미리보기" class="clip-preview-thumb" />
            <div class="clip-preview-note">썸네일이 자동으로 적용됩니다</div>
          </div>
        </div>

        <div class="form-group">
          <label>{{ isClipBoard ? '추가 설명 (선택)' : '내용' }}</label>
          <textarea
            v-model="form.content"
            class="form-input content-input"
            :placeholder="isClipBoard ? '클립 설명이나 감상을 자유롭게 남겨보세요 (선택)' : '내용을 입력하세요'"
            rows="12"
          ></textarea>
        </div>

        <div v-if="!isClipBoard" class="form-group">
          <label>이미지 첨부</label>
          <div class="image-upload-area">
            <div v-if="imagePreview" class="image-preview">
              <img :src="imagePreview" alt="미리보기" />
              <button type="button" class="remove-image-btn" @click="removeImage">✕</button>
            </div>
            <label v-else class="upload-label">
              📷 이미지를 선택하세요
              <input type="file" accept="image/*" @change="handleImageUpload" hidden />
            </label>
          </div>
        </div>

        <div class="form-actions">
          <button type="submit" class="btn btn-primary" :disabled="isSubmitting">
            {{ isSubmitting ? (isEditMode ? '수정 중...' : '등록 중...') : (isEditMode ? '수정 완료' : '게시글 등록') }}
          </button>
          <button type="button" class="btn btn-outline" @click="router.back()">취소</button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.write-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 2rem 1rem;
  font-family: 'Pretendard', sans-serif;
}

.write-card {
  background: var(--bg2, #12121a);
  border: 1px solid var(--border, rgba(255,255,255,0.07));
  border-radius: 16px;
  padding: 2rem;
}

.write-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.write-header h1 {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text, #f0f0f8);
}

.write-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.form-group label {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text2, #9999bb);
}

.form-input {
  padding: 0.75rem 1rem;
  background: var(--bg3, #1a1a26);
  border: 1.5px solid var(--border, rgba(255,255,255,0.07));
  border-radius: 10px;
  font-size: 0.95rem;
  color: var(--text, #f0f0f8);
  outline: none;
  transition: border-color 0.2s;
  font-family: 'Pretendard', sans-serif;
  width: 100%;
  box-sizing: border-box;
}

.form-input::placeholder {
  color: var(--text3, #555577);
}

.form-input:focus {
  border-color: var(--accent, #6c63ff);
}

select.form-input {
  cursor: pointer;
}

.content-input {
  resize: vertical;
  min-height: 200px;
  line-height: 1.7;
}

.form-actions {
  display: flex;
  gap: 0.75rem;
  padding-top: 0.5rem;
}

.btn {
  padding: 0.7rem 1.5rem;
  border: none;
  border-radius: 10px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  text-decoration: none;
  font-family: 'Pretendard', sans-serif;
}

.btn:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-primary {
  background: var(--gradient, linear-gradient(135deg, #6c63ff, #ff6b9d));
  color: #fff;
}

.btn-primary:hover:not(:disabled) { opacity: 0.9; }

.btn-outline {
  background: transparent;
  color: var(--text2, #9999bb);
  border: 1.5px solid var(--border, rgba(255,255,255,0.07));
}

.btn-outline:hover {
  border-color: var(--accent, #6c63ff);
  color: var(--accent, #6c63ff);
}

.image-upload-area {
  border: 2px dashed var(--border, rgba(255,255,255,0.07));
  border-radius: 10px;
  overflow: hidden;
}

.upload-label {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  cursor: pointer;
  color: var(--text2, #9999bb);
  font-size: 0.9rem;
  transition: background 0.2s;
}

.upload-label:hover {
  background: rgba(108,99,255,0.05);
}

.image-preview {
  position: relative;
}

.image-preview img {
  width: 100%;
  max-height: 300px;
  object-fit: contain;
  display: block;
}

.remove-image-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: none;
  background: rgba(0,0,0,0.7);
  color: white;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.required { color: #ef4444; margin-left: 2px; }

.clip-preview-wrap {
  margin-top: 10px;
  padding: 10px;
  background: rgba(108,99,255,0.06);
  border: 1px solid rgba(108,99,255,0.2);
  border-radius: 10px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.clip-preview-thumb {
  width: 140px;
  height: 80px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: #000;
}
.clip-preview-note {
  font-size: 0.78rem;
  color: var(--accent, #6c63ff);
  font-weight: 600;
}

@media (max-width: 480px) {
  .write-card { padding: 1.25rem; }
  .form-actions { flex-direction: column; }
  .clip-preview-wrap { flex-direction: column; align-items: stretch; }
  .clip-preview-thumb { width: 100%; height: 140px; }
}
</style>
