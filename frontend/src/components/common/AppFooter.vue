<script setup>
const currentYear = new Date().getFullYear()
</script>

<template>
  <footer class="app-footer">
    <div class="footer-container">
      <div class="footer-grid">
        <div class="footer-section brand">
          <div class="footer-logo">NAJAKS</div>
          <p class="footer-desc">스트리머와 팬이 함께 만드는 커뮤니티 플랫폼</p>
        </div>

        <div class="footer-section">
          <h4>플랫폼</h4>
          <ul>
            <li><router-link to="/streamers">스트리머</router-link></li>
            <li><router-link :to="{ path: '/community', query: { group: 'community' } }">커뮤니티</router-link></li>
            <li><router-link :to="{ path: '/community', query: { group: 'pride' } }">나작스 자랑</router-link></li>
            <li><router-link to="/creator">구인/구직</router-link></li>
            <li><router-link to="/contents">대회·컨텐츠 홍보</router-link></li>
          </ul>
        </div>

        <div class="footer-section">
          <h4>지원</h4>
          <ul>
            <li><router-link to="/support">고객센터</router-link></li>
            <li><router-link to="/info/contact">문의하기</router-link></li>
            <li><router-link to="/info/report-info">신고 안내</router-link></li>
            <li><router-link to="/resources">스트리머 도구 모음</router-link></li>
          </ul>
        </div>

        <div class="footer-section">
          <h4>정책</h4>
          <ul>
            <li><router-link to="/info/terms">이용약관</router-link></li>
            <li><router-link to="/info/privacy">개인정보처리방침</router-link></li>
            <li><router-link to="/info/community-guide">커뮤니티 가이드</router-link></li>
          </ul>
        </div>
      </div>

      <div class="footer-bottom">
        <p>&copy; {{ currentYear }} NAJAKS. All rights reserved.</p>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.app-footer {
  background: var(--bg2);
  border-top: 1px solid var(--border);
  margin-top: 0;
}

.footer-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 48px 40px 24px;
}

.footer-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr;
  gap: 2rem;
  margin-bottom: 2rem;
}

.footer-logo {
  font-size: 20px;
  font-weight: 900;
  background: var(--gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 8px;
}

.footer-desc {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text3);
}

.footer-section h4 {
  color: var(--text);
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.footer-section ul {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.footer-section a {
  color: var(--text3);
  text-decoration: none;
  font-size: 13px;
  transition: color 0.2s;
}

.footer-section a:hover {
  color: var(--text);
}

.footer-bottom {
  border-top: 1px solid var(--border);
  padding-top: 16px;
  text-align: center;
  font-size: 12px;
  color: var(--text3);
}

@media (max-width: 768px) {
  .footer-grid { grid-template-columns: 1fr 1fr; }
  .footer-container { padding: 32px 24px 20px; }
}

@media (max-width: 480px) {
  .footer-grid { grid-template-columns: 1fr; }
}
</style>
