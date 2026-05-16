<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'
import api from '@/api'

const route = useRoute()
const router = useRouter()
const notify = useNotificationStore()

const token = ref('')
const newPassword = ref('')
const newPasswordConfirm = ref('')
const submitting = ref(false)
const errorMsg = ref('')
const checking = ref(true)
const tokenValid = ref(false)

onMounted(async () => {
  token.value = route.query.token || ''
  if (!token.value) {
    checking.value = false
    tokenValid.value = false
    return
  }
  try {
    const { data } = await api.get('/auth/reset-password/validate', { params: { token: token.value } })
    tokenValid.value = !!data.valid
  } catch {
    tokenValid.value = false
  } finally {
    checking.value = false
  }
})

async function submit() {
  errorMsg.value = ''
  if (newPassword.value.length < 8) {
    errorMsg.value = '비밀번호는 8자 이상이어야 합니다.'
    return
  }
  if (newPassword.value !== newPasswordConfirm.value) {
    errorMsg.value = '비밀번호가 일치하지 않습니다.'
    return
  }
  submitting.value = true
  try {
    await api.post('/auth/reset-password', {
      token: token.value,
      newPassword: newPassword.value
    })
    notify.success('비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요.')
    router.push('/login')
  } catch (e) {
    errorMsg.value = e.response?.data?.message || '비밀번호 변경에 실패했습니다.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="reset-page">
    <div class="reset-container">
      <div class="reset-logo">
        <div class="logo-text">NAJAKS</div>
        <p>비밀번호 재설정</p>
      </div>

      <div class="reset-card">
        <div v-if="checking" class="reset-status">
          <div class="spinner"></div>
          <p>토큰을 확인하는 중입니다...</p>
        </div>

        <div v-else-if="!tokenValid" class="reset-status reset-error">
          <h3>유효하지 않은 링크입니다</h3>
          <p>토큰이 만료되었거나 이미 사용되었습니다.<br />비밀번호 재설정 링크를 다시 요청해주세요.</p>
          <router-link to="/login" class="submit-btn">로그인 페이지로</router-link>
        </div>

        <form v-else @submit.prevent="submit" class="reset-form">
          <div class="form-group">
            <label class="form-label">새 비밀번호</label>
            <input
              v-model="newPassword"
              type="password"
              placeholder="8자 이상"
              class="form-input"
              autocomplete="new-password"
            />
          </div>
          <div class="form-group">
            <label class="form-label">새 비밀번호 확인</label>
            <input
              v-model="newPasswordConfirm"
              type="password"
              placeholder="비밀번호 재입력"
              class="form-input"
              autocomplete="new-password"
            />
          </div>

          <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

          <button type="submit" class="submit-btn" :disabled="submitting">
            {{ submitting ? '변경 중...' : '비밀번호 변경' }}
          </button>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.reset-page {
  min-height: calc(100vh - 65px);
  display: flex; align-items: center; justify-content: center;
  padding: 48px 20px;
  background: radial-gradient(ellipse at 30% 40%, rgba(108,99,255,0.12), transparent 60%),
              radial-gradient(ellipse at 70% 70%, rgba(255,107,157,0.08), transparent 55%);
}
.reset-container { width: 100%; max-width: 420px; }
.reset-logo { text-align: center; margin-bottom: 36px; }
.logo-text {
  font-size: 30px; font-weight: 900;
  background: var(--gradient);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
  margin-bottom: 8px;
}
.reset-logo p { font-size: 14px; color: var(--text2); }
.reset-card {
  background: var(--bg2); border: 1px solid var(--border);
  border-radius: 20px; padding: 32px;
}
.reset-form { display: flex; flex-direction: column; gap: 12px; }
.form-group { display: flex; flex-direction: column; }
.reset-status { text-align: center; padding: 1rem 0; }
.reset-status p { color: var(--text2); line-height: 1.6; margin: 12px 0 20px; font-size: 14px; }
.reset-error h3 {
  color: var(--text); font-size: 18px; margin-bottom: 8px;
}
.spinner {
  width: 36px; height: 36px;
  border: 3px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto;
}
@keyframes spin { to { transform: rotate(360deg); } }
.error-msg {
  font-size: 13px; color: var(--accent2);
  text-align: center; padding: 8px;
  background: rgba(255,107,157,0.1); border-radius: 8px;
}
.submit-btn {
  display: inline-block;
  width: 100%; padding: 14px;
  background: var(--gradient); border: none; border-radius: 12px;
  font-size: 15px; font-weight: 700; color: white;
  cursor: pointer; text-align: center; text-decoration: none;
}
.submit-btn:hover { opacity: 0.85; }
.submit-btn:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
