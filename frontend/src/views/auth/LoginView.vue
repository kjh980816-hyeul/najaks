<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import api from '@/api'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const notifyStore = useNotificationStore()

const email = ref('')
const password = ref('')
const loading = ref(false)
const errorMsg = ref('')

const forgotOpen = ref(false)
const forgotEmail = ref('')
const forgotLoading = ref(false)
const forgotMessage = ref('')
const forgotError = ref('')

function openForgot() {
  forgotEmail.value = email.value
  forgotMessage.value = ''
  forgotError.value = ''
  forgotOpen.value = true
}

function closeForgot() {
  forgotOpen.value = false
}

async function submitForgot() {
  if (!forgotEmail.value.trim()) {
    forgotError.value = '이메일을 입력해주세요.'
    return
  }
  forgotLoading.value = true
  forgotError.value = ''
  forgotMessage.value = ''
  try {
    const { data } = await api.post('/auth/forgot-password', { email: forgotEmail.value.trim() })
    forgotMessage.value = data.message || '메일을 발송했습니다. 메일함(스팸함 포함)을 확인해주세요.'
  } catch (e) {
    forgotError.value = e.response?.data?.message || '메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.'
  } finally {
    forgotLoading.value = false
  }
}

async function handleLogin() {
  if (!email.value || !password.value) {
    errorMsg.value = '이메일과 비밀번호를 입력해주세요.'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    await authStore.login(email.value, password.value)
    notifyStore.success('로그인 성공!')
    const redirect = route.query.redirect
    router.push(redirect && typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/')
  } catch (e) {
    errorMsg.value = e.response?.data?.message || '로그인에 실패했습니다.'
  }
  loading.value = false
}

</script>

<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-logo">
        <div class="logo-text">NAJAKS</div>
        <p>다시 만나서 반가워요 👋</p>
      </div>

      <div class="login-card">
        <form @submit.prevent="handleLogin" class="login-form">
          <div class="form-group">
            <label class="form-label">이메일</label>
            <input v-model="email" type="email" placeholder="example@email.com" class="form-input" />
          </div>
          <div class="form-group">
            <label class="form-label">비밀번호</label>
            <input v-model="password" type="password" placeholder="비밀번호 입력" class="form-input" />
          </div>

          <div class="forgot-link">
            <button type="button" class="forgot-btn" @click="openForgot">비밀번호를 잊으셨나요?</button>
          </div>

          <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

          <button type="submit" class="submit-btn" :disabled="loading">
            {{ loading ? '로그인 중...' : '로그인' }}
          </button>
        </form>

        <p class="switch-link">
          아직 계정이 없으신가요?
          <router-link to="/register">회원가입</router-link>
        </p>
      </div>
    </div>

    <!-- 비밀번호 찾기 모달 -->
    <div v-if="forgotOpen" class="modal-overlay" @click.self="closeForgot">
      <div class="modal-card">
        <div class="modal-head">
          <h3>비밀번호 찾기</h3>
          <button type="button" class="modal-close" @click="closeForgot" aria-label="닫기">✕</button>
        </div>
        <p class="modal-desc">가입하신 이메일을 입력하시면 비밀번호 재설정 링크를 보내드립니다.</p>
        <form @submit.prevent="submitForgot" class="modal-form">
          <input
            v-model="forgotEmail"
            type="email"
            placeholder="example@email.com"
            class="form-input"
            autocomplete="email"
          />
          <div v-if="forgotError" class="error-msg">{{ forgotError }}</div>
          <div v-if="forgotMessage" class="success-msg">{{ forgotMessage }}</div>
          <button type="submit" class="submit-btn" :disabled="forgotLoading">
            {{ forgotLoading ? '발송 중...' : '재설정 링크 받기' }}
          </button>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: calc(100vh - 65px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 20px;
  background: radial-gradient(ellipse at 30% 40%, rgba(108,99,255,0.12), transparent 60%),
              radial-gradient(ellipse at 70% 70%, rgba(255,107,157,0.08), transparent 55%);
}

.login-container {
  width: 100%;
  max-width: 420px;
}

.login-logo {
  text-align: center;
  margin-bottom: 36px;
}

.logo-text {
  font-size: 30px;
  font-weight: 900;
  background: var(--gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin-bottom: 8px;
}

.login-logo p {
  font-size: 14px;
  color: var(--text2);
}

.login-card {
  background: var(--bg2);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 32px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.form-group {
  display: flex;
  flex-direction: column;
}

.forgot-link {
  text-align: right;
  margin-bottom: 4px;
}

.forgot-link span,
.forgot-btn {
  font-size: 12px;
  color: var(--accent);
  cursor: pointer;
  background: none;
  border: none;
  padding: 0;
  font-family: inherit;
}
.forgot-btn:hover { text-decoration: underline; }

.error-msg {
  font-size: 13px;
  color: var(--accent2);
  text-align: center;
  padding: 8px;
  background: rgba(255,107,157,0.1);
  border-radius: 8px;
}

.submit-btn {
  width: 100%;
  padding: 14px;
  background: var(--gradient);
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 700;
  color: white;
  cursor: pointer;
  transition: opacity 0.2s;
}

.submit-btn:hover { opacity: 0.85; }
.submit-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.switch-link {
  text-align: center;
  margin-top: 20px;
  font-size: 13px;
  color: var(--text2);
}

.switch-link a {
  color: var(--accent);
  font-weight: 600;
  text-decoration: none;
}

/* 비밀번호 찾기 모달 */
.modal-overlay {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.65);
  display: flex; align-items: center; justify-content: center;
  z-index: 9999; padding: 1rem;
}
.modal-card {
  width: 100%; max-width: 420px;
  background: var(--bg2);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 24px;
}
.modal-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 8px;
}
.modal-head h3 {
  font-size: 17px; font-weight: 700; color: var(--text); margin: 0;
}
.modal-close {
  background: none; border: none; color: var(--text3);
  font-size: 18px; cursor: pointer; padding: 4px 8px;
}
.modal-close:hover { color: var(--text); }
.modal-desc {
  font-size: 13px; color: var(--text2);
  line-height: 1.6; margin: 0 0 16px;
}
.modal-form { display: flex; flex-direction: column; gap: 12px; }
.success-msg {
  font-size: 13px;
  color: var(--accent3, #00d4aa);
  text-align: center;
  padding: 8px;
  background: rgba(0,212,170,0.1);
  border-radius: 8px;
  line-height: 1.5;
}
</style>
