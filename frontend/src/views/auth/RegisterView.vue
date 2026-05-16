<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import api from '@/api'

const router = useRouter()
const authStore = useAuthStore()
const notifyStore = useNotificationStore()

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
    // silent
  }
}

onMounted(fetchGuides)

const step = ref('choice') // choice, fan, streamer
const loading = ref(false)
const errorMsg = ref('')

const fieldErrors = ref({
  nickname: '',
  email: '',
  password: '',
  passwordConfirm: '',
  terms: '',
  platform: ''
})

function clearFieldErrors() {
  Object.keys(fieldErrors.value).forEach(k => fieldErrors.value[k] = '')
}

function validateFanForm() {
  clearFieldErrors()
  let valid = true
  const f = fanForm.value
  if (!f.nickname) { fieldErrors.value.nickname = '닉네임을 입력해주세요'; valid = false }
  if (!f.email) { fieldErrors.value.email = '이메일을 입력해주세요'; valid = false }
  if (!f.password) { fieldErrors.value.password = '비밀번호를 입력해주세요'; valid = false }
  else if (f.password.length < 8) { fieldErrors.value.password = '비밀번호는 8자 이상이어야 합니다'; valid = false }
  if (f.password !== f.passwordConfirm) { fieldErrors.value.passwordConfirm = '비밀번호가 일치하지 않습니다'; valid = false }
  if (!f.agreeTerms || !f.agreePrivacy) { fieldErrors.value.terms = '필수 약관에 동의해주세요'; valid = false }
  return valid
}

function validateStreamerForm() {
  clearFieldErrors()
  let valid = true
  const f = streamerForm.value
  if (!f.nickname) { fieldErrors.value.nickname = '닉네임을 입력해주세요'; valid = false }
  if (!f.email) { fieldErrors.value.email = '이메일을 입력해주세요'; valid = false }
  if (!f.password) { fieldErrors.value.password = '비밀번호를 입력해주세요'; valid = false }
  else if (f.password.length < 8) { fieldErrors.value.password = '비밀번호는 8자 이상이어야 합니다'; valid = false }
  if (f.password !== f.passwordConfirm) { fieldErrors.value.passwordConfirm = '비밀번호가 일치하지 않습니다'; valid = false }
  if (!f.platforms.youtube && !f.platforms.chzzk && !f.platforms.soop) { fieldErrors.value.platform = '하나 이상의 플랫폼을 선택해주세요'; valid = false }
  if (!f.agreeTerms || !f.agreePrivacy || !f.agreeCommunity) { fieldErrors.value.terms = '필수 약관에 동의해주세요'; valid = false }
  return valid
}

// Fan form
const fanForm = ref({ nickname: '', email: '', password: '', passwordConfirm: '', agreeTerms: false, agreePrivacy: false, agreeMarketing: false })
const allAgreed = ref(false)

// Streamer form
const streamerForm = ref({
  nickname: '', category: 'GAME', email: '', password: '', passwordConfirm: '',
  platforms: { youtube: false, chzzk: false, soop: false },
  screenshots: { youtube: null, chzzk: null, soop: null },
  agreeTerms: false, agreePrivacy: false, agreeCommunity: false, agreeMarketing: false,
})

function pickScreenshot(platformKey, event) {
  const file = event.target.files?.[0]
  streamerForm.value.screenshots[platformKey] = file || null
}

function toggleAllAgree(formType) {
  if (formType === 'fan') {
    const val = !allAgreed.value
    allAgreed.value = val
    fanForm.value.agreeTerms = val
    fanForm.value.agreePrivacy = val
    fanForm.value.agreeMarketing = val
  }
}

async function submitFan() {
  if (!validateFanForm()) return

  const f = fanForm.value
  loading.value = true
  errorMsg.value = ''
  try {
    await authStore.register({ nickname: f.nickname, email: f.email, password: f.password, role: 'FAN' })
    notifyStore.success('회원가입이 완료되었습니다!')
    router.push('/login')
  } catch (e) {
    errorMsg.value = e.response?.data?.message || '회원가입에 실패했습니다.'
  }
  loading.value = false
}

async function submitStreamer() {
  if (!validateStreamerForm()) return

  const f = streamerForm.value

  // 선택한 플랫폼 각각 스크린샷 첨부 확인
  const missingShots = []
  if (f.platforms.youtube && !f.screenshots.youtube) missingShots.push('유튜브')
  if (f.platforms.chzzk && !f.screenshots.chzzk) missingShots.push('치지직')
  if (f.platforms.soop && !f.screenshots.soop) missingShots.push('숲(SOOP)')
  if (missingShots.length > 0) {
    errorMsg.value = `${missingShots.join(', ')} 플랫폼의 스크린샷을 첨부해주세요.`
    return
  }

  if (!f.agreeTerms || !f.agreePrivacy || !f.agreeCommunity) { errorMsg.value = '필수 약관에 동의해주세요.'; return }

  loading.value = true
  errorMsg.value = ''
  try {
    // 1. 회원가입 (가입 시점에 자동 로그인 → 토큰 세팅됨)
    await authStore.register({
      nickname: f.nickname, email: f.email, password: f.password,
      role: 'STREAMER', category: f.category,
      platformYoutube: f.platforms.youtube,
      platformChzzk: f.platforms.chzzk,
      platformSoop: f.platforms.soop,
    })

    // 2. 스트리머 인증 신청 (multipart, 선택된 플랫폼 스크린샷 모두 전송)
    const formData = new FormData()
    if (f.platforms.youtube && f.screenshots.youtube) formData.append('screenshots', f.screenshots.youtube)
    if (f.platforms.chzzk && f.screenshots.chzzk) formData.append('screenshots', f.screenshots.chzzk)
    if (f.platforms.soop && f.screenshots.soop) formData.append('screenshots', f.screenshots.soop)

    try {
      await api.post('/streamer-applications', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      notifyStore.success('스트리머 신청이 완료되었습니다! 승인을 기다려주세요.')
    } catch {
      notifyStore.warning('가입은 완료되었지만 스크린샷 신청에 실패했습니다. 마이페이지에서 다시 신청해주세요.')
    }
    router.push('/mypage')
  } catch (e) {
    errorMsg.value = e.response?.data?.message || '가입에 실패했습니다.'
  }
  loading.value = false
}

</script>

<template>
  <div class="register-page">
    <!-- Step: Choice -->
    <div v-if="step === 'choice'" class="register-container choice">
      <div class="reg-logo">
        <div class="logo-text">NAJAKS</div>
        <h2>어떤 계정으로 가입할까요?</h2>
        <p>목적에 맞는 계정 유형을 선택해주세요.</p>
      </div>

      <div class="choice-grid">
        <div class="choice-card" @click="step = 'fan'">
          <div class="choice-icon" style="background:rgba(108,99,255,0.1);border-color:rgba(108,99,255,0.3);">🧑</div>
          <div class="choice-name">시청자</div>
          <div class="choice-desc">이벤트 참가, 커뮤니티 활동,<br>방송 예고 확인 등 팬 활동</div>
          <ul class="choice-features">
            <li><span class="check" style="color:var(--accent);">✓</span> 이벤트 참가 신청</li>
            <li><span class="check" style="color:var(--accent);">✓</span> 나작스 게시판 활동</li>
            <li><span class="check" style="color:var(--accent);">✓</span> 방송 예고 알림</li>
            <li><span class="check disabled">✗</span> <span class="disabled">스트리머 전용 공간</span></li>
          </ul>
          <div class="choice-btn" style="background:rgba(108,99,255,0.1);color:var(--accent);">시청자로 가입하기 →</div>
        </div>

        <div class="choice-card" @click="step = 'streamer'">
          <div class="approval-badge">승인 필요</div>
          <div class="choice-icon" style="background:rgba(255,107,157,0.1);border-color:rgba(255,107,157,0.3);">🎥</div>
          <div class="choice-name">스트리머</div>
          <div class="choice-desc">컨텐츠 등록, 이벤트 홍보,<br>스트리머 전용 커뮤니티 활동</div>
          <ul class="choice-features">
            <li><span class="check" style="color:var(--accent2);">✓</span> 컨텐츠·클립 등록</li>
            <li><span class="check" style="color:var(--accent2);">✓</span> 스트리머 전용 커뮤니티</li>
            <li><span class="check" style="color:var(--accent2);">✓</span> 스트리머 프로필 페이지</li>
            <li><span class="check" style="color:var(--accent2);">✓</span> 방송 예고 등록</li>
          </ul>
          <div class="choice-btn" style="background:rgba(255,107,157,0.1);color:var(--accent2);">스트리머로 신청하기 →</div>
        </div>
      </div>

      <p class="switch-link">이미 계정이 있으신가요? <router-link to="/login">로그인</router-link></p>
    </div>

    <!-- Step: Fan Registration -->
    <div v-if="step === 'fan'" class="register-container form-view">
      <div class="form-header">
        <span class="back-link" @click="step = 'choice'">← 뒤로</span>
        <div class="form-title-badge">
          <div class="title-icon" style="background:rgba(108,99,255,0.2);">🧑</div>
          <span>시청자 회원가입</span>
        </div>
      </div>

      <div class="step-indicator">
        <div class="step active"><div class="step-num">1</div><span>계정 정보</span></div>
        <div class="step-line"></div>
        <div class="step"><div class="step-num inactive">2</div><span class="inactive">완료</span></div>
      </div>

      <div class="form-card">
        <form @submit.prevent="submitFan" class="reg-form">
          <div class="form-group">
            <label class="form-label">닉네임 <span class="form-required">*</span></label>
            <input v-model="fanForm.nickname" type="text" placeholder="사용할 닉네임 입력 (2~12자)" :class="['form-input', { 'has-error': fieldErrors.nickname }]" />
            <span v-if="fieldErrors.nickname" class="field-error">{{ fieldErrors.nickname }}</span>
          </div>
          <div class="form-group">
            <label class="form-label">이메일 <span class="form-required">*</span></label>
            <input v-model="fanForm.email" type="email" placeholder="example@email.com" :class="['form-input', { 'has-error': fieldErrors.email }]" />
            <span v-if="fieldErrors.email" class="field-error">{{ fieldErrors.email }}</span>
          </div>
          <div class="form-group">
            <label class="form-label">비밀번호 <span class="form-required">*</span></label>
            <input v-model="fanForm.password" type="password" placeholder="8자 이상, 영문+숫자 포함" :class="['form-input', { 'has-error': fieldErrors.password }]" />
            <span v-if="fieldErrors.password" class="field-error">{{ fieldErrors.password }}</span>
          </div>
          <div class="form-group">
            <label class="form-label">비밀번호 확인 <span class="form-required">*</span></label>
            <input v-model="fanForm.passwordConfirm" type="password" placeholder="비밀번호 재입력" :class="['form-input', { 'has-error': fieldErrors.passwordConfirm }]" />
            <span v-if="fieldErrors.passwordConfirm" class="field-error">{{ fieldErrors.passwordConfirm }}</span>
          </div>

          <div class="agree-box">
            <label class="agree-item main" @click="toggleAllAgree('fan')">
              <input type="checkbox" v-model="allAgreed" />
              <span>전체 동의</span>
            </label>
            <div class="agree-divider"></div>
            <label class="agree-item"><input type="checkbox" v-model="fanForm.agreeTerms" /><span>[필수] 이용약관 동의</span><a href="/info/terms" target="_blank" class="terms-link" @click.stop>보기</a></label>
            <label class="agree-item"><input type="checkbox" v-model="fanForm.agreePrivacy" /><span>[필수] 개인정보처리방침 동의</span><a href="/info/privacy" target="_blank" class="terms-link" @click.stop>보기</a></label>
            <label class="agree-item"><input type="checkbox" v-model="fanForm.agreeMarketing" /><span>[선택] 마케팅 수신 동의</span></label>
          </div>
          <span v-if="fieldErrors.terms" class="field-error">{{ fieldErrors.terms }}</span>

          <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
          <button type="submit" class="submit-btn" :disabled="loading">{{ loading ? '처리 중...' : '가입 완료' }}</button>
        </form>
      </div>
    </div>

    <!-- Step: Streamer Registration -->
    <div v-if="step === 'streamer'" class="register-container form-view streamer">
      <div class="form-header">
        <span class="back-link" @click="step = 'choice'">← 뒤로</span>
        <div class="form-title-badge">
          <div class="title-icon" style="background:rgba(255,107,157,0.2);">🎥</div>
          <span>스트리머 가입 신청</span>
        </div>
      </div>

      <div class="step-indicator">
        <div class="step active pink"><div class="step-num pink">1</div><span>기본 정보</span></div>
        <div class="step-line"></div>
        <div class="step"><div class="step-num inactive">2</div><span class="inactive">플랫폼 정보</span></div>
        <div class="step-line"></div>
        <div class="step"><div class="step-num inactive">3</div><span class="inactive">승인 대기</span></div>
      </div>

      <div class="info-banner">
        <span>ℹ️</span>
        <div>스트리머 신청은 관리자 검토 후 승인됩니다. 승인까지 <strong>1~3일</strong> 정도 소요될 수 있어요.</div>
      </div>

      <div class="form-card">
        <div class="form-section-title">기본 정보</div>
        <form @submit.prevent="submitStreamer" class="reg-form">
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">닉네임 <span class="form-required">*</span></label>
              <input v-model="streamerForm.nickname" type="text" placeholder="방송 닉네임" :class="['form-input', { 'has-error': fieldErrors.nickname }]" />
              <span v-if="fieldErrors.nickname" class="field-error">{{ fieldErrors.nickname }}</span>
            </div>
            <div class="form-group">
              <label class="form-label">카테고리 <span class="form-required">*</span></label>
              <select v-model="streamerForm.category" class="form-input">
                <option value="GAME">게임</option>
                <option value="MUSIC">음악</option>
                <option value="MUKBANG">먹방</option>
                <option value="TALK">일상/토크</option>
                <option value="SPORTS">스포츠</option>
                <option value="ETC">기타</option>
              </select>
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">이메일 <span class="form-required">*</span></label>
            <input v-model="streamerForm.email" type="email" placeholder="example@email.com" :class="['form-input', { 'has-error': fieldErrors.email }]" />
            <span v-if="fieldErrors.email" class="field-error">{{ fieldErrors.email }}</span>
          </div>
          <div class="form-group">
            <label class="form-label">비밀번호 <span class="form-required">*</span></label>
            <input v-model="streamerForm.password" type="password" placeholder="8자 이상, 영문+숫자 포함" :class="['form-input', { 'has-error': fieldErrors.password }]" />
            <span v-if="fieldErrors.password" class="field-error">{{ fieldErrors.password }}</span>
          </div>
          <div class="form-group">
            <label class="form-label">비밀번호 확인 <span class="form-required">*</span></label>
            <input v-model="streamerForm.passwordConfirm" type="password" placeholder="비밀번호 재입력" :class="['form-input', { 'has-error': fieldErrors.passwordConfirm }]" />
            <span v-if="fieldErrors.passwordConfirm" class="field-error">{{ fieldErrors.passwordConfirm }}</span>
          </div>

          <div class="platform-section">
            <div class="form-section-title">활동 중인 플랫폼 <span class="hint">(하나 이상 필수)</span></div>
            <p class="platform-hint">선택한 플랫폼마다 채널 관리 화면 스크린샷을 첨부해야 해요.</p>

            <div class="platform-list">
              <div class="platform-row" :class="{ 'has-guide': streamerForm.platforms.youtube && guideImages.youtube }">
                <div class="platform-row-main">
                  <label class="platform-item">
                    <input type="checkbox" v-model="streamerForm.platforms.youtube" />
                    <div class="platform-icon" style="background:#FF0000;">▶</div>
                    <span class="platform-name">유튜브</span>
                    <span class="platform-desc">YouTube Studio 화면 캡처</span>
                  </label>
                  <div v-if="streamerForm.platforms.youtube" class="screenshot-upload">
                    <label class="screenshot-btn">
                      📎 {{ streamerForm.screenshots.youtube ? streamerForm.screenshots.youtube.name : '스크린샷 첨부' }}
                      <input type="file" accept="image/*" @change="pickScreenshot('youtube', $event)" hidden />
                    </label>
                  </div>
                </div>
                <aside v-if="streamerForm.platforms.youtube && guideImages.youtube" class="platform-guide-mini">
                  <div class="guide-mini-title">📷 이런 화면을 캡처해주세요</div>
                  <div class="guide-mini-thumb" @click="openGuideLightbox(guideImages.youtube)">
                    <img :src="guideImages.youtube.imageUrl" alt="유튜브 예시" />
                  </div>
                  <p v-if="guideImages.youtube.description" class="guide-mini-desc">{{ guideImages.youtube.description }}</p>
                </aside>
              </div>

              <div class="platform-row" :class="{ 'has-guide': streamerForm.platforms.chzzk && guideImages.chzzk }">
                <div class="platform-row-main">
                  <label class="platform-item">
                    <input type="checkbox" v-model="streamerForm.platforms.chzzk" />
                    <div class="platform-icon" style="background:#000;padding:2px;"><img src="/chzzk-logo.png" alt="치지직" style="width:24px;height:24px;border-radius:5px;" /></div>
                    <span class="platform-name">치지직</span>
                    <span class="platform-desc">채널 관리 버튼이 보이는 화면</span>
                  </label>
                  <div v-if="streamerForm.platforms.chzzk" class="screenshot-upload">
                    <label class="screenshot-btn">
                      📎 {{ streamerForm.screenshots.chzzk ? streamerForm.screenshots.chzzk.name : '스크린샷 첨부' }}
                      <input type="file" accept="image/*" @change="pickScreenshot('chzzk', $event)" hidden />
                    </label>
                  </div>
                </div>
                <aside v-if="streamerForm.platforms.chzzk && guideImages.chzzk" class="platform-guide-mini">
                  <div class="guide-mini-title">📷 이런 화면을 캡처해주세요</div>
                  <div class="guide-mini-thumb" @click="openGuideLightbox(guideImages.chzzk)">
                    <img :src="guideImages.chzzk.imageUrl" alt="치지직 예시" />
                  </div>
                  <p v-if="guideImages.chzzk.description" class="guide-mini-desc">{{ guideImages.chzzk.description }}</p>
                </aside>
              </div>

              <div class="platform-row" :class="{ 'has-guide': streamerForm.platforms.soop && guideImages.soop }">
                <div class="platform-row-main">
                  <label class="platform-item">
                    <input type="checkbox" v-model="streamerForm.platforms.soop" />
                    <div class="platform-icon" style="background:#0078ff;font-weight:900;color:white;font-size:13px;">S</div>
                    <span class="platform-name">숲 (SOOP)</span>
                    <span class="platform-desc">방송국 관리 화면 캡처</span>
                  </label>
                  <div v-if="streamerForm.platforms.soop" class="screenshot-upload">
                    <label class="screenshot-btn">
                      📎 {{ streamerForm.screenshots.soop ? streamerForm.screenshots.soop.name : '스크린샷 첨부' }}
                      <input type="file" accept="image/*" @change="pickScreenshot('soop', $event)" hidden />
                    </label>
                  </div>
                </div>
                <aside v-if="streamerForm.platforms.soop && guideImages.soop" class="platform-guide-mini">
                  <div class="guide-mini-title">📷 이런 화면을 캡처해주세요</div>
                  <div class="guide-mini-thumb" @click="openGuideLightbox(guideImages.soop)">
                    <img :src="guideImages.soop.imageUrl" alt="숲 예시" />
                  </div>
                  <p v-if="guideImages.soop.description" class="guide-mini-desc">{{ guideImages.soop.description }}</p>
                </aside>
              </div>
            </div>
            <span v-if="fieldErrors.platform" class="field-error">{{ fieldErrors.platform }}</span>
          </div>

          <div class="agree-box">
            <label class="agree-item main"><input type="checkbox" @change="streamerForm.agreeTerms = $event.target.checked; streamerForm.agreePrivacy = $event.target.checked; streamerForm.agreeCommunity = $event.target.checked; streamerForm.agreeMarketing = $event.target.checked" /><span>전체 동의</span></label>
            <div class="agree-divider"></div>
            <label class="agree-item"><input type="checkbox" v-model="streamerForm.agreeTerms" /><span>[필수] 이용약관 동의</span><a href="/info/terms" target="_blank" class="terms-link" @click.stop>보기</a></label>
            <label class="agree-item"><input type="checkbox" v-model="streamerForm.agreePrivacy" /><span>[필수] 개인정보처리방침 동의</span><a href="/info/privacy" target="_blank" class="terms-link" @click.stop>보기</a></label>
            <label class="agree-item"><input type="checkbox" v-model="streamerForm.agreeCommunity" /><span>[필수] 스트리머 커뮤니티 이용 규칙 동의</span><a href="/info/community-guide" target="_blank" class="terms-link" @click.stop>보기</a></label>
            <label class="agree-item"><input type="checkbox" v-model="streamerForm.agreeMarketing" /><span>[선택] 마케팅 수신 동의</span></label>
          </div>
          <span v-if="fieldErrors.terms" class="field-error">{{ fieldErrors.terms }}</span>

          <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
          <button type="submit" class="submit-btn streamer-submit" :disabled="loading">{{ loading ? '처리 중...' : '신청서 제출 — 승인 대기' }}</button>
        </form>
      </div>
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
.register-page {
  min-height: calc(100vh - 65px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 20px;
  background: radial-gradient(ellipse at 20% 50%, rgba(108,99,255,0.12), transparent 55%),
              radial-gradient(ellipse at 80% 30%, rgba(255,107,157,0.08), transparent 55%);
}

.register-container { width: 100%; }
.register-container.choice { max-width: 560px; }
.register-container.form-view { max-width: 440px; }
.register-container.streamer { max-width: 900px; }

/* Logo */
.reg-logo { text-align: center; margin-bottom: 40px; }
.logo-text {
  font-size: 30px; font-weight: 900;
  background: var(--gradient); -webkit-background-clip: text; -webkit-text-fill-color: transparent;
  margin-bottom: 8px;
}
.reg-logo h2 { font-size: 22px; font-weight: 800; margin-bottom: 8px; }
.reg-logo p { font-size: 14px; color: var(--text2); }

/* Choice Grid */
.choice-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 24px;
}

.choice-card {
  background: var(--bg2); border: 2px solid var(--border);
  border-radius: 20px; padding: 32px 24px; cursor: pointer;
  transition: all 0.25s; text-align: center; position: relative; overflow: hidden;
}

.choice-card:hover {
  border-color: var(--accent); background: rgba(108,99,255,0.07); transform: translateY(-4px);
}

.choice-card:last-child:hover {
  border-color: var(--accent2); background: rgba(255,107,157,0.07);
}

.approval-badge {
  position: absolute; top: 14px; right: 14px;
  background: var(--gradient); border-radius: 6px; padding: 3px 10px;
  font-size: 10px; font-weight: 700; color: white;
}

.choice-icon {
  width: 64px; height: 64px; border-radius: 18px;
  border: 1px solid; display: flex; align-items: center; justify-content: center;
  font-size: 30px; margin: 0 auto 18px;
}

.choice-name { font-size: 18px; font-weight: 800; margin-bottom: 8px; }
.choice-desc { font-size: 13px; color: var(--text2); line-height: 1.6; margin-bottom: 16px; }

.choice-features {
  text-align: left; list-style: none; padding: 0;
  display: flex; flex-direction: column; gap: 6px; font-size: 12px; color: var(--text2);
}

.choice-features li { display: flex; align-items: center; gap: 6px; }
.check { font-weight: 700; }
.disabled { color: var(--text3); }

.choice-btn {
  margin-top: 20px; padding: 10px; border-radius: 10px;
  font-size: 13px; font-weight: 700;
}

/* Form Header */
.form-header {
  display: flex; align-items: center; gap: 12px; margin-bottom: 28px;
}

.back-link { cursor: pointer; color: var(--text2); font-size: 13px; }
.back-link:hover { color: var(--text); }

.form-title-badge { display: flex; align-items: center; gap: 8px; }

.title-icon {
  width: 28px; height: 28px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center; font-size: 14px;
}

.form-title-badge span { font-size: 15px; font-weight: 700; }

/* Step Indicator */
.step-indicator { display: flex; align-items: center; margin-bottom: 32px; }

.step { display: flex; align-items: center; gap: 8px; }
.step-num {
  width: 28px; height: 28px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; color: white; background: var(--accent);
}

.step-num.pink { background: var(--accent2); }
.step-num.inactive { background: var(--bg3); border: 1px solid var(--border); color: var(--text3); }

.step span { font-size: 12px; font-weight: 600; color: var(--accent); }
.step span.inactive { color: var(--text3); }
.step.active.pink span { color: var(--accent2); }

.step-line { flex: 1; height: 1px; background: var(--border); margin: 0 12px; }

/* Info Banner */
.info-banner {
  background: rgba(255,107,157,0.08); border: 1px solid rgba(255,107,157,0.25);
  border-radius: 12px; padding: 14px 16px; margin-bottom: 20px;
  display: flex; gap: 10px; font-size: 12px; color: var(--text2); line-height: 1.6;
}

.info-banner strong { color: var(--text); }

/* Form Card */
.form-card {
  background: var(--bg2); border: 1px solid var(--border);
  border-radius: 20px; padding: 32px;
}

.form-section-title {
  font-size: 13px; font-weight: 700; color: var(--text2);
  margin-bottom: 18px; text-transform: uppercase; letter-spacing: 0.5px;
}

.form-section-title .hint { font-size: 11px; font-weight: 400; color: var(--text3); }

.reg-form { display: flex; flex-direction: column; gap: 14px; }

.form-group { display: flex; flex-direction: column; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }

/* Platform Section */
.platform-section { border-top: 1px solid var(--border); padding-top: 18px; margin-top: 4px; }
.platform-hint { font-size: 12px; color: var(--text3); margin-bottom: 14px; }

.platform-list { display: flex; flex-direction: column; gap: 12px; }

.platform-item {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 16px; cursor: pointer; background: var(--bg3);
  border: 1px solid var(--border); border-radius: 12px; transition: border-color 0.2s;
}

.platform-item:hover { border-color: var(--accent); }

.platform-item input[type="checkbox"] { width: 16px; height: 16px; flex-shrink: 0; accent-color: var(--accent); }

.platform-icon {
  width: 28px; height: 28px; border-radius: 7px;
  display: flex; align-items: center; justify-content: center; font-size: 14px; flex-shrink: 0;
}

.platform-name { font-size: 14px; font-weight: 700; }
.platform-desc { font-size: 12px; color: var(--text3); margin-left: auto; }

.platform-row {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20px;
  align-items: start;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: rgba(255,255,255,0.02);
  transition: grid-template-columns 0.2s;
}
.platform-row.has-guide {
  grid-template-columns: minmax(360px, 1fr) 380px;
}
.platform-row-main { display: flex; flex-direction: column; gap: 8px; min-width: 0; }

.platform-guide-mini {
  background: rgba(108,99,255,0.08);
  border: 1px solid rgba(108,99,255,0.2);
  border-radius: 10px;
  padding: 12px;
}
.guide-mini-title {
  font-size: 12px; font-weight: 700;
  color: var(--accent, #6c63ff);
  margin-bottom: 8px;
}
.guide-mini-thumb {
  width: 100%; border-radius: 8px; overflow: hidden;
  border: 1px solid var(--border);
  cursor: zoom-in; background: var(--bg3);
}
.guide-mini-thumb:hover { border-color: var(--accent, #6c63ff); }
.guide-mini-thumb img { width: 100%; height: auto; display: block; }
.guide-mini-desc {
  font-size: 12px; color: var(--text2);
  line-height: 1.5; margin: 8px 0 0;
}

/* 라이트박스 */
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

@media (max-width: 900px) {
  .register-container.streamer { max-width: 540px; }
  .platform-row { grid-template-columns: 1fr; }
}

.screenshot-upload {
  padding-left: 40px;
  display: flex;
  align-items: center;
}

.screenshot-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: rgba(108,99,255,0.08);
  border: 1.5px dashed var(--accent);
  border-radius: 10px;
  color: var(--accent);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.screenshot-btn:hover { background: rgba(108,99,255,0.15); }

/* Agree Box */
.agree-box {
  background: var(--bg3); border-radius: 12px; padding: 16px;
  display: flex; flex-direction: column; gap: 10px; margin-top: 4px;
}

.agree-item {
  display: flex; align-items: center; gap: 10px; cursor: pointer;
}

.agree-item.main span { font-size: 13px; font-weight: 600; }
.agree-item span { font-size: 12px; color: var(--text2); }

.agree-item input[type="checkbox"] { width: 14px; height: 14px; accent-color: var(--accent); }
.agree-divider { height: 1px; background: var(--border); }

.terms-link {
  margin-left: auto;
  font-size: 11px;
  color: var(--accent);
  text-decoration: none;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(108,99,255,0.08);
}
.terms-link:hover {
  background: rgba(108,99,255,0.15);
}

.field-error {
  font-size: 11px;
  color: #ff6b6b;
  margin-top: 2px;
}

.form-input.has-error {
  border-color: #ff6b6b;
}

.error-msg {
  font-size: 13px; color: var(--accent2); text-align: center;
  padding: 8px; background: rgba(255,107,157,0.1); border-radius: 8px;
}

.submit-btn {
  width: 100%; padding: 14px; background: var(--gradient);
  border: none; border-radius: 12px; font-size: 15px; font-weight: 700;
  color: white; cursor: pointer; margin-top: 6px; transition: opacity 0.2s;
}

.submit-btn:hover { opacity: 0.85; }
.submit-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.streamer-submit {
  background: linear-gradient(135deg, #ff6b9d, #ffb400);
}

.switch-link { text-align: center; font-size: 13px; color: var(--text2); }
.switch-link a { color: var(--accent); font-weight: 600; text-decoration: none; }

@media (max-width: 480px) {
  .choice-grid { grid-template-columns: 1fr; }
  .form-row { grid-template-columns: 1fr; }
  .platform-desc { display: none; }
}
</style>
