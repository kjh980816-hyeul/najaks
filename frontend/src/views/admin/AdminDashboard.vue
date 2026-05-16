<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useNotificationStore } from '@/stores/notification'
import api from '@/api'
import Pagination from '@/components/Pagination.vue'

const notify = useNotificationStore()

const activeTab = ref('applications')
const applications = ref([])
const pendingContents = ref([])
const reports = ref([])
const banners = ref([])
const isLoading = ref(true)
const contentLoading = ref(false)
const reportLoading = ref(false)
const bannerLoading = ref(false)
const filter = ref('PENDING')
const reportFilter = ref('PENDING')
const contentFilter = ref('PENDING')

const newBanner = ref({
  title: '',
  linkUrl: '',
  startDate: '',
  endDate: '',
  imageFile: null,
  imageUrl: '',
})
const bannerSubmitting = ref(false)
const editingBannerId = ref(null)
const editBanner = ref({
  title: '',
  linkUrl: '',
  startDate: '',
  endDate: '',
  active: true,
  imageFile: null,
  imageUrl: '',
})
const bannerUpdating = ref(false)

function formatDatetimeLocal(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function startEditBanner(banner) {
  editingBannerId.value = banner.id
  editBanner.value = {
    title: banner.title,
    linkUrl: banner.linkUrl,
    startDate: formatDatetimeLocal(banner.startDate),
    endDate: formatDatetimeLocal(banner.endDate),
    active: banner.active,
    imageFile: null,
    imageUrl: '',
  }
}

function cancelEditBanner() {
  editingBannerId.value = null
  editBanner.value = { title: '', linkUrl: '', startDate: '', endDate: '', active: true, imageFile: null, imageUrl: '' }
}

function pickEditBannerImage(event) {
  editBanner.value.imageFile = event.target.files?.[0] || null
}

async function updateBanner(id) {
  const b = editBanner.value
  if (!b.title || !b.linkUrl || !b.startDate || !b.endDate) {
    notify.warning('제목, 링크, 시작일, 종료일을 모두 입력해주세요')
    return
  }
  bannerUpdating.value = true
  try {
    const formData = new FormData()
    formData.append('title', b.title)
    formData.append('linkUrl', b.linkUrl)
    formData.append('startDate', b.startDate + ':00')
    formData.append('endDate', b.endDate + ':00')
    formData.append('active', b.active)
    if (b.imageFile) formData.append('image', b.imageFile)
    if (b.imageUrl) formData.append('imageUrl', b.imageUrl)
    await api.put(`/admin/banners/${id}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    notify.success('배너가 수정되었습니다')
    cancelEditBanner()
    fetchBanners()
  } catch (error) {
    notify.error(error.response?.data?.message || '배너 수정에 실패했습니다')
  } finally {
    bannerUpdating.value = false
  }
}
const expandedContentId = ref(null)

function toggleContentDetail(id) {
  expandedContentId.value = expandedContentId.value === id ? null : id
}

// ── 회원 관리 ──
const users = ref([])
const userLoading = ref(false)
const userSearchQuery = ref('')
const userRoleFilter = ref('ALL')

const filteredUsers = computed(() => {
  let list = users.value
  if (userRoleFilter.value !== 'ALL') {
    list = list.filter(u => u.role === userRoleFilter.value)
  }
  if (!userSearchQuery.value.trim()) return list
  const q = userSearchQuery.value.toLowerCase()
  return list.filter(u =>
    (u.nickname || '').toLowerCase().includes(q) ||
    (u.email || '').toLowerCase().includes(q)
  )
})

// ── 리소스 링크 관리 ──
const resources = ref([])
const resourceLoading = ref(false)
const newResource = ref({ name: '', url: '', description: '', category: '방송 도구' })
const resourceSubmitting = ref(false)

const resourceCategories = ['방송 도구', '디자인/편집', '음악/효과음', '채팅/커뮤니티', '수익화/분석', '기타']

async function fetchResources() {
  resourceLoading.value = true
  try {
    const { data } = await api.get('/public/resources')
    resources.value = data.data || []
  } catch { notify.error('리소스 목록을 불러올 수 없습니다') }
  resourceLoading.value = false
}

async function submitResource() {
  const r = newResource.value
  if (!r.name || !r.url || !r.category) { notify.warning('이름, URL, 카테고리를 입력해주세요'); return }
  resourceSubmitting.value = true
  try {
    await api.post('/admin/resources', r)
    notify.success('리소스가 추가되었습니다')
    newResource.value = { name: '', url: '', description: '', category: '방송 도구' }
    fetchResources()
  } catch { notify.error('추가에 실패했습니다') }
  resourceSubmitting.value = false
}

async function deleteResource(id) {
  if (!confirm('이 리소스를 삭제하시겠습니까?')) return
  try {
    await api.delete(`/admin/resources/${id}`)
    notify.success('삭제되었습니다')
    fetchResources()
  } catch { notify.error('삭제에 실패했습니다') }
}

// ── 스트리머 신청 가이드 이미지 ──
const guidePlatforms = [
  { key: 'youtube', label: '유튜브', color: '#FF0000', defaultDesc: 'YouTube Studio 채널 대시보드 화면을 캡처해주세요.' },
  { key: 'chzzk', label: '치지직', color: '#03C75A', defaultDesc: '치지직 스튜디오 → 채널 관리 화면을 캡처해주세요.' },
  { key: 'soop', label: '숲(SOOP)', color: '#0078ff', defaultDesc: '방송국 관리 → 내 정보 페이지를 캡처해주세요.' },
]
const guideImages = ref({})
const guideLoading = ref(false)
const uploadingGuide = ref(null)
const guideDescDrafts = ref({ youtube: '', chzzk: '', soop: '' })

async function fetchGuideImages() {
  guideLoading.value = true
  try {
    const { data } = await api.get('/admin/application-guides')
    const map = {}
    ;(data.data || []).forEach(g => { map[g.platform] = g })
    guideImages.value = map
    guidePlatforms.forEach(p => {
      guideDescDrafts.value[p.key] = map[p.key]?.description || ''
    })
  } catch {
    notify.error('가이드 이미지를 불러올 수 없습니다')
  } finally {
    guideLoading.value = false
  }
}

async function uploadGuideImage(platform, event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) { notify.warning('이미지 파일만 업로드할 수 있습니다'); return }
  uploadingGuide.value = platform
  try {
    const formData = new FormData()
    formData.append('image', file)
    formData.append('description', guideDescDrafts.value[platform] ?? '')
    const { data } = await api.post(`/admin/application-guides/${platform}`, formData)
    notify.success('가이드 이미지가 저장되었습니다')
    if (data?.data) {
      guideImages.value = { ...guideImages.value, [platform]: data.data }
      guideDescDrafts.value[platform] = data.data.description || ''
    } else {
      fetchGuideImages()
    }
  } catch (e) {
    notify.error(e.response?.data?.message || e.message || '업로드에 실패했습니다')
  } finally {
    uploadingGuide.value = null
    event.target.value = ''
  }
}

async function saveGuideDescription(platform) {
  const current = guideImages.value[platform]
  if (!current) { notify.warning('먼저 이미지를 업로드해주세요'); return }
  try {
    const formData = new FormData()
    formData.append('imageUrl', current.imageUrl)
    formData.append('description', guideDescDrafts.value[platform] ?? '')
    const { data } = await api.post(`/admin/application-guides/${platform}`, formData)
    notify.success('저장되었습니다')
    if (data?.data) {
      guideImages.value = { ...guideImages.value, [platform]: data.data }
      guideDescDrafts.value[platform] = data.data.description || ''
    } else {
      fetchGuideImages()
    }
  } catch (e) {
    notify.error(e.response?.data?.message || e.message || '저장에 실패했습니다')
  }
}

async function removeGuideImage(platform) {
  if (!confirm('이 가이드 이미지를 삭제하시겠습니까?')) return
  try {
    await api.delete(`/admin/application-guides/${platform}`)
    notify.success('가이드 이미지가 삭제되었습니다')
    fetchGuideImages()
  } catch {
    notify.error('삭제에 실패했습니다')
  }
}

// ── 정적 페이지 관리 ──
const staticPages = ref([])
const staticPageLoading = ref(false)
const editingPage = ref(null)
const editForm = ref({ title: '', sections: [] })
const pageSaving = ref(false)

// 히스토리 모달
const historyModalPage = ref(null)
const historyList = ref([])
const historyLoading = ref(false)
const historySelected = ref(null)
const historyRestoring = ref(false)

const filteredApplications = computed(() => {
  if (filter.value === 'ALL') return applications.value
  return applications.value.filter(app => app.status === filter.value)
})

const filteredContents = computed(() => {
  if (contentFilter.value === 'ALL') return pendingContents.value
  if (contentFilter.value === 'APPROVED') {
    return pendingContents.value.filter(c => c.status === 'APPROVED' || c.status === 'ONGOING' || c.status === 'CLOSED')
  }
  return pendingContents.value.filter(c => c.status === contentFilter.value)
})

const contentStatusCount = computed(() => {
  const all = pendingContents.value
  return {
    PENDING: all.filter(c => c.status === 'PENDING').length,
    APPROVED: all.filter(c => c.status === 'APPROVED' || c.status === 'ONGOING' || c.status === 'CLOSED').length,
    REJECTED: all.filter(c => c.status === 'REJECTED').length,
    ALL: all.length,
  }
})

async function fetchApplications() {
  isLoading.value = true
  try {
    const { data } = await api.get('/admin/streamer-applications')
    applications.value = data.data || []
  } catch {
    notify.error('신청 목록을 불러올 수 없습니다')
  } finally {
    isLoading.value = false
  }
}

async function handleApprove(id) {
  try {
    await api.post(`/admin/streamer-applications/${id}/approve`)
    notify.success('스트리머 인증이 승인되었습니다')
    fetchApplications()
  } catch {
    notify.error('승인 처리에 실패했습니다')
  }
}

async function handleReject(id) {
  const reason = prompt('거절 사유를 입력해주세요 (신청자에게 전달됩니다)')
  if (reason === null) return
  if (!reason.trim()) {
    notify.warning('거절 사유를 입력해주세요')
    return
  }
  try {
    await api.post(`/admin/streamer-applications/${id}/reject`, { reason: reason.trim() })
    notify.success('스트리머 인증이 반려되었습니다')
    fetchApplications()
  } catch {
    notify.error('반려 처리에 실패했습니다')
  }
}

function formatDate(dateStr) {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

function getStatusText(status) {
  switch (status) {
    case 'PENDING': return '심사 중'
    case 'APPROVED': return '승인됨'
    case 'ONGOING': return '진행중'
    case 'CLOSED': return '마감'
    case 'REJECTED': return '반려됨'
    default: return status
  }
}

async function fetchPendingContents() {
  contentLoading.value = true
  try {
    const { data } = await api.get('/admin/contents')
    pendingContents.value = (data.data || []).sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
  } catch {
    notify.error('컨텐츠 목록을 불러올 수 없습니다')
  } finally {
    contentLoading.value = false
  }
}

async function handleApproveContent(id) {
  try {
    await api.post(`/admin/contents/${id}/approve`)
    notify.success('컨텐츠가 승인되었습니다')
    fetchPendingContents()
  } catch {
    notify.error('승인에 실패했습니다')
  }
}

async function handleRejectContent(id) {
  try {
    await api.post(`/admin/contents/${id}/reject`)
    notify.success('컨텐츠가 반려되었습니다')
    fetchPendingContents()
  } catch {
    notify.error('반려에 실패했습니다')
  }
}

async function handleDeleteContent(id) {
  if (!confirm('이 컨텐츠를 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.')) return
  try {
    await api.delete(`/admin/contents/${id}`)
    notify.success('컨텐츠가 삭제되었습니다')
    fetchPendingContents()
  } catch {
    notify.error('삭제에 실패했습니다')
  }
}

const categoryMap = { GAME: '게임', MUSIC: '음악', MUKBANG: '먹방', TALK: '토크', ETC: '기타' }

const reportStatusMap = {
  PENDING: '대기',
  DISMISSED: '기각',
  DELETED: '삭제 처리',
  SUSPENDED: '정지 처리'
}

const reportTargetMap = { POST: '게시글', COMMENT: '댓글', USER: '사용자' }

async function fetchReports() {
  reportLoading.value = true
  try {
    const url = reportFilter.value === 'ALL'
      ? '/admin/reports'
      : `/admin/reports?status=${reportFilter.value}`
    const { data } = await api.get(url)
    reports.value = data.data || []
  } catch {
    notify.error('신고 목록을 불러올 수 없습니다')
  } finally {
    reportLoading.value = false
  }
}

async function handleProcessReport(id, action) {
  const labels = { DISMISSED: '기각', DELETED: '콘텐츠 삭제', SUSPENDED: '사용자 정지' }
  if (!confirm(`이 신고를 "${labels[action]}"(으)로 처리하시겠습니까?`)) return
  try {
    await api.post(`/admin/reports/${id}/process`, { action })
    notify.success('신고가 처리되었습니다')
    fetchReports()
  } catch {
    notify.error('신고 처리에 실패했습니다')
  }
}

function selectReportFilter(f) {
  reportFilter.value = f
  fetchReports()
}

// ── 광고 배너 관리 ──

async function fetchBanners() {
  bannerLoading.value = true
  try {
    const { data } = await api.get('/admin/banners')
    banners.value = data.data || []
  } catch {
    notify.error('배너 목록을 불러올 수 없습니다')
  } finally {
    bannerLoading.value = false
  }
}

function pickBannerImage(event) {
  newBanner.value.imageFile = event.target.files?.[0] || null
}

async function submitBanner() {
  const b = newBanner.value
  if (!b.title || !b.linkUrl || !b.startDate || !b.endDate) {
    notify.warning('제목, 링크, 시작일, 종료일을 모두 입력해주세요')
    return
  }
  if (!b.imageFile && !b.imageUrl) {
    notify.warning('이미지 파일을 선택하거나 이미지 URL을 입력해주세요')
    return
  }
  bannerSubmitting.value = true
  try {
    const formData = new FormData()
    formData.append('title', b.title)
    formData.append('linkUrl', b.linkUrl)
    formData.append('startDate', b.startDate + ':00')
    formData.append('endDate', b.endDate + ':00')
    if (b.imageFile) {
      formData.append('image', b.imageFile)
    }
    if (b.imageUrl) {
      formData.append('imageUrl', b.imageUrl)
    }
    await api.post('/admin/banners', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    notify.success('배너가 등록되었습니다')
    newBanner.value = { title: '', linkUrl: '', startDate: '', endDate: '', imageFile: null, imageUrl: '' }
    fetchBanners()
  } catch (error) {
    notify.error(error.response?.data?.message || '배너 등록에 실패했습니다')
  } finally {
    bannerSubmitting.value = false
  }
}

async function deleteBanner(id) {
  if (!confirm('이 배너를 삭제하시겠습니까?')) return
  try {
    await api.delete(`/admin/banners/${id}`)
    notify.success('배너가 삭제되었습니다')
    fetchBanners()
  } catch {
    notify.error('배너 삭제에 실패했습니다')
  }
}

// ── 스트리머 Notion 일괄 동기화 ──
const notionSyncBusy = ref(false)
async function syncStreamersToNotion() {
  if (notionSyncBusy.value) return
  if (!confirm('verified 스트리머 전체를 Notion 스트리머 DB에 동기화합니다. 계속하시겠습니까?\n(Notion DB에 "치지직 연동" checkbox 속성이 있어야 합니다)')) return
  notionSyncBusy.value = true
  try {
    // Notion API 40+ 페이지 upsert 는 30~60초 소요 — 기본 timeout(10s) 초과하므로 개별 연장
    const { data } = await api.post('/admin/streamers/sync-notion', null, { timeout: 180000 })
    const r = data.data || data
    notify.success(`동기화 완료: 신규 ${r.created ?? 0} / 업데이트 ${r.updated ?? 0} / 스킵 ${r.skipped ?? 0} / 실패 ${r.failed ?? 0}`)
  } catch (e) {
    notify.error(e.response?.data?.message || 'Notion 동기화 실패')
  } finally {
    notionSyncBusy.value = false
  }
}

// ── 프리미엄 관리 ──
const premiumList = ref([])
const premiumLoading = ref(false)
const premiumEmailEdit = ref({})  // streamerNo → editing email

async function fetchPremiumList() {
  premiumLoading.value = true
  try {
    const { data } = await api.get('/admin/premium')
    premiumList.value = Array.isArray(data) ? data : []
  } catch {
    notify.error('프리미엄 목록을 불러올 수 없습니다')
  } finally {
    premiumLoading.value = false
  }
}

async function togglePremium(row, enabled) {
  try {
    const days = enabled ? (prompt('활성화 기간(일). 비워두면 무기한:', '30') || '') : ''
    const params = new URLSearchParams({ enabled: String(enabled) })
    if (days && !isNaN(parseInt(days))) params.append('days', days)
    await api.post(`/admin/premium/${row.streamerNo}/toggle-chat-analysis?${params.toString()}`)
    notify.success(enabled ? '프리미엄이 활성화되었습니다' : '프리미엄이 비활성화되었습니다')
    fetchPremiumList()
  } catch {
    notify.error('상태 변경 실패')
  }
}

async function saveReportEmail(row) {
  const email = (premiumEmailEdit.value[row.streamerNo] ?? '').trim()
  try {
    await api.post(`/admin/premium/${row.streamerNo}/report-email`, { email })
    notify.success(email ? '리포트 이메일이 저장되었습니다' : '리포트 이메일이 삭제되었습니다')
    premiumEmailEdit.value[row.streamerNo] = ''
    await fetchPremiumList()
  } catch {
    notify.error('이메일 저장 실패')
  }
}

async function toggleEmailReport(row, enabled) {
  try {
    await api.post(`/admin/premium/${row.streamerNo}/toggle-email?enabled=${enabled}`)
    notify.success(enabled ? '이메일 발송이 활성화되었습니다' : '이메일 발송이 비활성화되었습니다')
    fetchPremiumList()
  } catch {
    notify.error('이메일 상태 변경 실패')
  }
}

// ── 회원 관리 함수들 ──

async function fetchUsers() {
  userLoading.value = true
  try {
    const { data } = await api.get('/admin/users')
    users.value = data.data || []
  } catch {
    notify.error('회원 목록을 불러올 수 없습니다')
  } finally {
    userLoading.value = false
  }
}

const roleMap = { FAN: '시청자', STREAMER: '스트리머', ADMIN: '관리자' }

async function handleDeleteUser(id, nickname) {
  if (!confirm(`"${nickname}" 회원을 정말 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.`)) return
  try {
    await api.delete(`/admin/users/${id}`)
    notify.success('회원이 삭제되었습니다')
    fetchUsers()
  } catch (e) {
    notify.error(e.response?.data?.message || '회원 삭제에 실패했습니다')
  }
}

// ── 정적 페이지 관리 함수들 ──

async function fetchStaticPages() {
  staticPageLoading.value = true
  try {
    const { data } = await api.get('/admin/pages')
    staticPages.value = data.data || []
  } catch {
    notify.error('정적 페이지 목록을 불러올 수 없습니다')
  } finally {
    staticPageLoading.value = false
  }
}

function startEditPage(page) {
  editingPage.value = page
  let sections = []
  try { sections = JSON.parse(page.content) } catch { sections = [] }
  editForm.value = {
    title: page.title,
    sections: sections.map(s => ({ ...s }))
  }
}

function cancelEditPage() {
  editingPage.value = null
  editForm.value = { title: '', sections: [] }
}

function addSection() {
  editForm.value.sections.push({ heading: '', body: '' })
}

function removeSection(idx) {
  editForm.value.sections.splice(idx, 1)
}

async function saveStaticPage() {
  if (!editForm.value.title.trim()) {
    notify.warning('제목을 입력해주세요')
    return
  }
  pageSaving.value = true
  try {
    await api.put(`/admin/pages/${editingPage.value.slug}`, {
      title: editForm.value.title,
      content: JSON.stringify(editForm.value.sections)
    })
    notify.success('페이지가 수정되었습니다')
    cancelEditPage()
    fetchStaticPages()
  } catch {
    notify.error('페이지 수정에 실패했습니다')
  } finally {
    pageSaving.value = false
  }
}

function parseHistorySections(raw) {
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

async function openHistoryModal(page) {
  historyModalPage.value = page
  historySelected.value = null
  historyList.value = []
  historyLoading.value = true
  try {
    const { data } = await api.get(`/admin/pages/${page.slug}/history`)
    historyList.value = data.data || []
    if (historyList.value.length > 0) {
      historySelected.value = historyList.value[0]
    }
  } catch {
    notify.error('히스토리를 불러올 수 없습니다')
  } finally {
    historyLoading.value = false
  }
}

function closeHistoryModal() {
  historyModalPage.value = null
  historyList.value = []
  historySelected.value = null
}

function selectHistoryVersion(entry) {
  historySelected.value = entry
}

async function restoreHistoryVersion(entry) {
  if (!historyModalPage.value) return
  if (!confirm(`v${entry.version} 버전으로 복구하시겠습니까? 현재 내용은 새 버전으로 히스토리에 기록됩니다.`)) return
  historyRestoring.value = true
  try {
    await api.post(`/admin/pages/${historyModalPage.value.slug}/restore/${entry.version}`)
    notify.success(`v${entry.version} 버전으로 복구되었습니다`)
    closeHistoryModal()
    fetchStaticPages()
  } catch {
    notify.error('복구에 실패했습니다')
  } finally {
    historyRestoring.value = false
  }
}

// ── 공통 페이징 ──
const ADMIN_PAGE_SIZE = 10
const applicationPage = ref(1)
const contentPage = ref(1)
const reportPage = ref(1)
const staticPagePage = ref(1)
const userPage = ref(1)
const resourcePage = ref(1)
const bannerPage = ref(1)

function slicePage(list, page) {
  const start = (page - 1) * ADMIN_PAGE_SIZE
  return list.slice(start, start + ADMIN_PAGE_SIZE)
}
function totalOf(list) {
  return Math.max(Math.ceil(list.length / ADMIN_PAGE_SIZE), 1)
}

const pagedApplications = computed(() => slicePage(filteredApplications.value, applicationPage.value))
const pagedAdminContents = computed(() => slicePage(filteredContents.value, contentPage.value))
const pagedReports = computed(() => slicePage(reports.value, reportPage.value))
const pagedStaticPages = computed(() => slicePage(staticPages.value, staticPagePage.value))
const pagedAdminUsers = computed(() => slicePage(filteredUsers.value, userPage.value))
const pagedResources = computed(() => slicePage(resources.value, resourcePage.value))
const pagedBanners = computed(() => slicePage(banners.value, bannerPage.value))

const applicationTotalPages = computed(() => totalOf(filteredApplications.value))
const contentTotalPages = computed(() => totalOf(filteredContents.value))
const reportTotalPages = computed(() => totalOf(reports.value))
const staticPageTotalPages = computed(() => totalOf(staticPages.value))
const userTotalPages = computed(() => totalOf(filteredUsers.value))
const resourceTotalPages = computed(() => totalOf(resources.value))
const bannerTotalPages = computed(() => totalOf(banners.value))

watch(filter, () => { applicationPage.value = 1 })
watch(contentFilter, () => { contentPage.value = 1 })
watch(reportFilter, () => { reportPage.value = 1 })
watch(userSearchQuery, () => { userPage.value = 1 })

onMounted(() => {
  fetchApplications()
  fetchPendingContents()
  fetchReports()
  fetchBanners()
  fetchStaticPages()
  fetchUsers()
  fetchResources()
  fetchGuideImages()
})
</script>

<template>
  <div class="admin-page">
    <div class="page-header">
      <h1>관리자 대시보드</h1>
    </div>

    <div class="tabs">
      <button
        :class="['tab', { active: activeTab === 'applications' }]"
        @click="activeTab = 'applications'"
      >
        스트리머 인증 관리
      </button>
      <button
        :class="['tab', { active: activeTab === 'contents' }]"
        @click="activeTab = 'contents'"
      >
        컨텐츠 승인
      </button>
      <button
        :class="['tab', { active: activeTab === 'reports' }]"
        @click="activeTab = 'reports'"
      >
        신고 관리
      </button>
      <button
        :class="['tab', { active: activeTab === 'banners' }]"
        @click="activeTab = 'banners'"
      >
        광고 배너 관리
      </button>
      <button
        :class="['tab', { active: activeTab === 'pages' }]"
        @click="activeTab = 'pages'"
      >
        정적 페이지 관리
      </button>
      <button
        :class="['tab', { active: activeTab === 'users' }]"
        @click="activeTab = 'users'"
      >
        회원 관리
      </button>
      <button
        :class="['tab', { active: activeTab === 'resources' }]"
        @click="activeTab = 'resources'"
      >
        도구 모음 관리
      </button>
      <button
        :class="['tab', { active: activeTab === 'apply-guides' }]"
        @click="activeTab = 'apply-guides'"
      >
        스트리머 신청 가이드
      </button>
      <button
        :class="['tab', { active: activeTab === 'premium' }]"
        @click="activeTab = 'premium'; fetchPremiumList()"
      >
        프리미엄 관리
      </button>
    </div>

    <div v-if="activeTab === 'applications'" class="tab-content">
      <div class="filter-bar">
        <button
          v-for="f in ['PENDING', 'APPROVED', 'REJECTED', 'ALL']"
          :key="f"
          :class="['filter-btn', { active: filter === f }]"
          @click="filter = f"
        >
          {{ f === 'ALL' ? '전체' : getStatusText(f) }}
        </button>
      </div>

      <div v-if="isLoading" class="loading">
        <div class="spinner"></div>
      </div>

      <div v-else-if="filteredApplications.length === 0" class="empty">
        해당하는 신청이 없습니다.
      </div>

      <div v-else class="application-list">
        <div
          v-for="app in pagedApplications"
          :key="app.id"
          class="application-card"
        >
          <div class="app-info">
            <div class="app-user">
              <strong>{{ app.userNickname }}</strong>
              <span class="app-email">{{ app.userEmail }}</span>
            </div>
            <div class="app-meta">
              <span :class="['status-badge', 'status-' + app.status.toLowerCase()]">
                {{ getStatusText(app.status) }}
              </span>
              <span class="app-date">{{ formatDate(app.createdAt) }}</span>
            </div>
          </div>

          <div class="app-screenshots">
            <a
              v-for="(url, idx) in (app.screenshotUrls && app.screenshotUrls.length ? app.screenshotUrls : [app.screenshotUrl].filter(Boolean))"
              :key="idx"
              :href="url"
              target="_blank"
              class="screenshot-thumb"
            >
              <img :src="url" :alt="`스크린샷 ${idx + 1}`" />
            </a>
          </div>

          <div v-if="app.status === 'PENDING'" class="app-actions">
            <button class="btn btn-approve" @click="handleApprove(app.id)">승인</button>
            <button class="btn btn-reject" @click="handleReject(app.id)">반려</button>
          </div>

          <div v-else class="app-review-info">
            <span v-if="app.reviewedByNickname">처리자: {{ app.reviewedByNickname }}</span>
            <span v-if="app.reviewedAt">처리일: {{ formatDate(app.reviewedAt) }}</span>
            <div v-if="app.status === 'REJECTED' && app.rejectionReason" class="rejection-reason">
              <strong>거절 사유:</strong> {{ app.rejectionReason }}
            </div>
          </div>
        </div>
      </div>
      <Pagination
        v-if="filteredApplications.length > 0"
        :current-page="applicationPage"
        :total-pages="applicationTotalPages"
        @change="p => applicationPage = p"
      />
    </div>

    <!-- 컨텐츠 승인 관리 -->
    <div v-if="activeTab === 'contents'" class="tab-content">
      <div class="filter-bar">
        <button
          v-for="f in ['PENDING', 'APPROVED', 'REJECTED', 'ALL']"
          :key="f"
          :class="['filter-btn', { active: contentFilter === f }]"
          @click="contentFilter = f"
        >
          {{ f === 'ALL' ? '전체' : f === 'APPROVED' ? '승인됨' : f === 'PENDING' ? '심사 중' : '반려됨' }}
          <span class="filter-count">{{ contentStatusCount[f] }}</span>
        </button>
      </div>

      <div v-if="contentLoading" class="loading"><div class="spinner"></div></div>

      <div v-else-if="filteredContents.length === 0" class="empty">해당하는 컨텐츠가 없습니다.</div>

      <div v-else class="application-list">
        <div v-for="content in pagedAdminContents" :key="content.id" class="application-card">
          <div class="app-info" @click="toggleContentDetail(content.id)" style="cursor:pointer;">
            <div class="app-user">
              <strong>{{ content.title }}</strong>
              <span class="app-email">{{ content.streamerNickname }} · {{ categoryMap[content.category] || content.category }}</span>
            </div>
            <div class="app-meta">
              <span :class="['status-badge', 'status-' + content.status.toLowerCase()]">
                {{ getStatusText(content.status) }}
              </span>
              <span class="app-date">{{ formatDate(content.createdAt) }}</span>
              <span class="expand-icon">{{ expandedContentId === content.id ? '▲' : '▼' }}</span>
            </div>
          </div>

          <!-- 상세 내용 (펼치기) -->
          <div v-if="expandedContentId === content.id" class="content-detail-box">
            <div v-if="content.imageUrls && content.imageUrls.length > 0" class="admin-image-grid">
              <img v-for="(src, i) in content.imageUrls" :key="i" :src="src" alt="이미지" />
            </div>
            <div v-else-if="content.thumbnailUrl" class="app-screenshot">
              <img :src="content.thumbnailUrl" alt="썸네일" />
            </div>

            <div class="content-detail-grid">
              <div class="detail-item" v-if="content.description">
                <label>내용</label>
                <div class="detail-value detail-desc">{{ content.description }}</div>
              </div>
              <div class="detail-item" v-if="content.hostName">
                <label>주최자</label>
                <span class="detail-value">{{ content.hostName }}</span>
              </div>
              <div class="detail-item" v-if="content.tags && content.tags.length">
                <label>태그</label>
                <span class="detail-value">{{ content.tags.join(', ') }}</span>
              </div>
              <div class="detail-item" v-if="content.requirements">
                <label>참가 조건</label>
                <div class="detail-value detail-desc">{{ content.requirements }}</div>
              </div>
              <div class="detail-item" v-if="content.prize">
                <label>상금/혜택</label>
                <div class="detail-value detail-desc">{{ content.prize }}</div>
              </div>
              <div class="detail-item" v-if="content.recruitCount">
                <label>모집 인원</label>
                <span class="detail-value">{{ content.recruitCount }}</span>
              </div>
              <div class="detail-item" v-if="content.followerUnlimited || content.followerCount != null">
                <label>팔로워 조건</label>
                <span class="detail-value">{{ content.followerUnlimited ? '제한 없음' : `${content.followerCount}명 이상` }}</span>
              </div>
              <div class="detail-item" v-if="content.contactMethod || content.contactInfo">
                <label>연락처</label>
                <span class="detail-value">{{ content.contactMethod || '' }} {{ content.contactInfo || '' }}</span>
              </div>
              <div class="detail-item" v-if="content.applyLink">
                <label>신청 링크</label>
                <a :href="content.applyLink" target="_blank" class="detail-link">{{ content.applyLink }}</a>
              </div>
              <div class="detail-item" v-if="content.startDate">
                <label>시작일</label>
                <span class="detail-value">{{ formatDate(content.startDate) }}</span>
              </div>
              <div class="detail-item" v-if="content.endDate">
                <label>종료일</label>
                <span class="detail-value">{{ formatDate(content.endDate) }}</span>
              </div>
            </div>
          </div>

          <div v-if="content.status === 'PENDING'" class="app-actions">
            <button class="btn btn-approve" @click="handleApproveContent(content.id)">승인</button>
            <button class="btn btn-reject" @click="handleRejectContent(content.id)">반려</button>
          </div>
          <div class="app-actions" style="margin-top: 0.5rem;">
            <button class="btn btn-edit" @click="$router.push({ path: '/contents/create', query: { edit: content.id } })">수정</button>
            <button class="btn btn-danger" @click="handleDeleteContent(content.id)">삭제</button>
          </div>
        </div>
      </div>
      <Pagination
        v-if="filteredContents.length > 0"
        :current-page="contentPage"
        :total-pages="contentTotalPages"
        @change="p => contentPage = p"
      />
    </div>

    <!-- 신고 관리 탭 -->
    <div v-if="activeTab === 'reports'" class="tab-content">
      <div class="filter-bar">
        <button
          v-for="f in ['PENDING', 'DISMISSED', 'DELETED', 'SUSPENDED', 'ALL']"
          :key="f"
          :class="['filter-btn', { active: reportFilter === f }]"
          @click="selectReportFilter(f)"
        >
          {{ f === 'ALL' ? '전체' : reportStatusMap[f] }}
        </button>
      </div>

      <div v-if="reportLoading" class="loading"><div class="spinner"></div></div>

      <div v-else-if="reports.length === 0" class="empty">
        해당하는 신고가 없습니다.
      </div>

      <div v-else class="application-list">
        <div v-for="report in pagedReports" :key="report.id" class="application-card">
          <div class="app-info">
            <div class="app-user">
              <strong>{{ reportTargetMap[report.targetType] }} 신고</strong>
              <span class="app-email">신고자: {{ report.reporterNickname }}</span>
            </div>
            <div class="app-meta">
              <span :class="['status-badge', 'status-' + report.status.toLowerCase()]">
                {{ reportStatusMap[report.status] }}
              </span>
              <span class="app-date">{{ formatDate(report.createdAt) }}</span>
            </div>
          </div>

          <div class="report-target">
            <span class="report-target-label">대상</span>
            <span class="report-target-text">{{ report.targetPreview }}</span>
          </div>

          <div class="report-reason">
            <span class="report-target-label">사유</span>
            <p>{{ report.reason }}</p>
          </div>

          <div v-if="report.status === 'PENDING'" class="app-actions">
            <button class="btn btn-approve" @click="handleProcessReport(report.id, 'DISMISSED')">기각</button>
            <button
              v-if="report.targetType !== 'USER'"
              class="btn btn-reject"
              @click="handleProcessReport(report.id, 'DELETED')"
            >콘텐츠 삭제</button>
            <button
              v-if="report.targetType === 'USER'"
              class="btn btn-reject"
              @click="handleProcessReport(report.id, 'SUSPENDED')"
            >사용자 정지</button>
          </div>

          <div v-else class="app-review-info">
            <span v-if="report.processedByNickname">처리자: {{ report.processedByNickname }}</span>
            <span v-if="report.processedAt">처리일: {{ formatDate(report.processedAt) }}</span>
          </div>
        </div>
      </div>
      <Pagination
        v-if="reports.length > 0"
        :current-page="reportPage"
        :total-pages="reportTotalPages"
        @change="p => reportPage = p"
      />
    </div>

    <!-- 정적 페이지 관리 탭 -->
    <div v-if="activeTab === 'pages'" class="tab-content">
      <div v-if="staticPageLoading" class="loading"><div class="spinner"></div></div>

      <div v-else-if="editingPage" class="page-edit-card">
        <h3 class="banner-section-title">{{ editingPage.title }} 수정</h3>
        <form @submit.prevent="saveStaticPage" class="page-edit-form">
          <div class="banner-field">
            <label>페이지 제목</label>
            <input v-model="editForm.title" type="text" placeholder="페이지 제목" />
          </div>

          <div class="sections-editor">
            <div v-for="(section, idx) in editForm.sections" :key="idx" class="section-item">
              <div class="section-header">
                <span class="section-number">섹션 {{ idx + 1 }}</span>
                <button type="button" class="btn-remove" @click="removeSection(idx)">삭제</button>
              </div>
              <div class="banner-field">
                <label>제목</label>
                <input v-model="section.heading" type="text" placeholder="섹션 제목" />
              </div>
              <div class="banner-field">
                <label>내용</label>
                <textarea v-model="section.body" rows="4" placeholder="섹션 내용"></textarea>
              </div>
            </div>
          </div>

          <button type="button" class="btn btn-add-section" @click="addSection">+ 섹션 추가</button>

          <div class="page-edit-actions">
            <button type="submit" class="btn btn-approve" :disabled="pageSaving">
              {{ pageSaving ? '저장 중...' : '저장' }}
            </button>
            <button type="button" class="btn btn-reject" @click="cancelEditPage">취소</button>
          </div>
        </form>
      </div>

      <div v-else>
        <div v-if="staticPages.length === 0" class="empty">등록된 정적 페이지가 없습니다.</div>
        <div v-else class="application-list">
          <div v-for="page in pagedStaticPages" :key="page.id" class="application-card">
            <div class="app-info">
              <div class="app-user">
                <strong>{{ page.title }}</strong>
                <span class="app-email">slug: {{ page.slug }}</span>
              </div>
              <div class="app-meta">
                <span class="app-date">{{ page.updatedAt ? formatDate(page.updatedAt) : '' }}</span>
              </div>
            </div>
            <div class="app-actions">
              <button class="btn btn-approve" @click="startEditPage(page)">수정</button>
              <button class="btn btn-reject" @click="openHistoryModal(page)">이전 버전</button>
            </div>
          </div>
        </div>
        <Pagination
          v-if="staticPages.length > 0"
          :current-page="staticPagePage"
          :total-pages="staticPageTotalPages"
          @change="p => staticPagePage = p"
        />
      </div>
    </div>

    <!-- 정적 페이지 히스토리 모달 -->
    <div v-if="historyModalPage" class="history-overlay" @click.self="closeHistoryModal">
      <div class="history-modal">
        <div class="history-header">
          <h3>{{ historyModalPage.title }} — 수정 이력</h3>
          <button class="history-close" @click="closeHistoryModal">×</button>
        </div>
        <div v-if="historyLoading" class="loading"><div class="spinner"></div></div>
        <div v-else-if="historyList.length === 0" class="empty">저장된 이력이 없습니다.</div>
        <div v-else class="history-body">
          <ul class="history-list">
            <li
              v-for="entry in historyList"
              :key="entry.id"
              :class="['history-item', { active: historySelected && historySelected.id === entry.id }]"
              @click="selectHistoryVersion(entry)"
            >
              <div class="history-version">v{{ entry.version }}</div>
              <div class="history-meta">
                <div class="history-date">{{ formatDate(entry.editedAt) }}</div>
                <div class="history-editor">{{ entry.editedByEmail || '시스템' }}</div>
              </div>
            </li>
          </ul>
          <div class="history-detail">
            <template v-if="historySelected">
              <div class="history-detail-head">
                <div>
                  <strong>v{{ historySelected.version }}</strong>
                  <span class="history-title-preview">— {{ historySelected.title }}</span>
                </div>
                <button
                  class="btn btn-approve"
                  :disabled="historyRestoring"
                  @click="restoreHistoryVersion(historySelected)"
                >
                  {{ historyRestoring ? '복구 중...' : '이 버전으로 복구' }}
                </button>
              </div>
              <div class="history-sections">
                <div
                  v-for="(section, idx) in parseHistorySections(historySelected.content)"
                  :key="idx"
                  class="history-section"
                >
                  <h4>{{ section.heading }}</h4>
                  <p>{{ section.body }}</p>
                </div>
              </div>
            </template>
            <div v-else class="empty">좌측에서 버전을 선택하세요.</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 회원 관리 탭 -->
    <div v-if="activeTab === 'users'" class="tab-content">
      <div v-if="userLoading" class="loading"><div class="spinner"></div></div>
      <div v-else-if="users.length === 0" class="empty">등록된 회원이 없습니다.</div>
      <div v-else>
        <div v-if="userRoleFilter === 'STREAMER' || userRoleFilter === 'ALL'" class="notion-sync-bar">
          <button class="btn btn-primary btn-sm" :disabled="notionSyncBusy" @click="syncStreamersToNotion">
            {{ notionSyncBusy ? '동기화 중...' : '📋 스트리머 목록 Notion 동기화' }}
          </button>
          <span class="notion-sync-hint">verified 스트리머 전원을 Notion 스트리머 DB에 일괄 upsert (치지직 연동 여부 포함)</span>
        </div>
        <div class="role-sub-tabs">
          <button :class="['role-tab', { active: userRoleFilter === 'ALL' }]" @click="userRoleFilter = 'ALL'; userPage = 1">
            전체 <span class="role-count">({{ users.length }})</span>
          </button>
          <button :class="['role-tab', { active: userRoleFilter === 'FAN' }]" @click="userRoleFilter = 'FAN'; userPage = 1">
            시청자 <span class="role-count">({{ users.filter(u => u.role === 'FAN').length }})</span>
          </button>
          <button :class="['role-tab', { active: userRoleFilter === 'STREAMER' }]" @click="userRoleFilter = 'STREAMER'; userPage = 1">
            스트리머 <span class="role-count">({{ users.filter(u => u.role === 'STREAMER').length }})</span>
          </button>
          <button :class="['role-tab', { active: userRoleFilter === 'ADMIN' }]" @click="userRoleFilter = 'ADMIN'; userPage = 1">
            관리자 <span class="role-count">({{ users.filter(u => u.role === 'ADMIN').length }})</span>
          </button>
        </div>
        <div class="user-search-bar">
          <input v-model="userSearchQuery" type="text" placeholder="닉네임 또는 이메일로 검색..." class="user-search-input" />
        </div>
        <div class="user-table">
          <div class="user-table-header">
            <span class="user-col-id">ID</span>
            <span class="user-col-nick">닉네임</span>
            <span class="user-col-email">이메일</span>
            <span class="user-col-role">역할</span>
            <span class="user-col-date">가입일</span>
            <span class="user-col-action">관리</span>
          </div>
          <div v-for="u in pagedAdminUsers" :key="u.id" class="user-table-row">
            <span class="user-col-id">{{ u.id }}</span>
            <span class="user-col-nick">
              <img v-if="u.profileImage" :src="u.profileImage" class="user-mini-avatar" />
              <span v-else class="user-mini-placeholder">{{ (u.nickname || '?').charAt(0) }}</span>
              {{ u.nickname }}
            </span>
            <span class="user-col-email">{{ u.email }}</span>
            <span class="user-col-role">
              <span :class="['status-badge', 'role-badge-' + (u.role || '').toLowerCase()]">{{ roleMap[u.role] || u.role }}</span>
            </span>
            <span class="user-col-date">{{ u.createdAt ? formatDate(u.createdAt) : '' }}</span>
            <span class="user-col-action">
              <button v-if="u.role !== 'ADMIN'" class="btn btn-reject btn-xs" @click="handleDeleteUser(u.id, u.nickname)">삭제</button>
            </span>
          </div>
        </div>
        <Pagination
          v-if="filteredUsers.length > 0"
          :current-page="userPage"
          :total-pages="userTotalPages"
          @change="p => userPage = p"
        />
      </div>
    </div>

    <!-- 도구 모음 관리 탭 -->
    <div v-if="activeTab === 'resources'" class="tab-content">
      <div class="banner-create-card">
        <h3 class="banner-section-title">새 리소스 추가</h3>
        <form @submit.prevent="submitResource" class="banner-form">
          <div class="banner-field">
            <label>이름</label>
            <input v-model="newResource.name" type="text" placeholder="사이트 이름" />
          </div>
          <div class="banner-field">
            <label>URL</label>
            <input v-model="newResource.url" type="url" placeholder="https://..." />
          </div>
          <div class="banner-field">
            <label>설명</label>
            <input v-model="newResource.description" type="text" placeholder="간단한 설명" />
          </div>
          <div class="banner-field">
            <label>카테고리</label>
            <select v-model="newResource.category">
              <option v-for="cat in resourceCategories" :key="cat" :value="cat">{{ cat }}</option>
            </select>
          </div>
          <button type="submit" class="btn btn-approve" :disabled="resourceSubmitting">
            {{ resourceSubmitting ? '추가 중...' : '리소스 추가' }}
          </button>
        </form>
      </div>

      <div v-if="resourceLoading" class="loading"><div class="spinner"></div></div>
      <div v-else-if="resources.length === 0" class="empty">관리자가 추가한 리소스가 없습니다.</div>
      <div v-else class="application-list">
        <div v-for="r in pagedResources" :key="r.id" class="application-card">
          <div class="app-info">
            <div class="app-user">
              <strong>{{ r.name }}</strong>
              <span class="app-email">{{ r.category }} · {{ r.url }}</span>
            </div>
            <div class="app-meta">
              <span class="app-date">{{ r.description }}</span>
            </div>
          </div>
          <div class="app-actions">
            <button class="btn btn-reject" @click="deleteResource(r.id)">삭제</button>
          </div>
        </div>
      </div>
      <Pagination
        v-if="resources.length > 0"
        :current-page="resourcePage"
        :total-pages="resourceTotalPages"
        @change="p => resourcePage = p"
      />
    </div>

    <!-- 스트리머 신청 가이드 이미지 관리 -->
    <div v-if="activeTab === 'apply-guides'" class="tab-content">
      <div class="streamer-covers-hint">
        스트리머 인증 신청 시 <strong>어떤 화면을 캡처해서 올려야 하는지</strong> 예시로 보여줄 이미지를 플랫폼별로 업로드할 수 있습니다.
        여기 등록한 이미지는 스트리머 신청 폼(/streamer/apply)의 각 플랫폼 블록 안에 자동으로 노출됩니다.
      </div>
      <div v-if="guideLoading" class="loading"><div class="spinner"></div></div>
      <div v-else class="guide-list">
        <div v-for="p in guidePlatforms" :key="p.key" class="guide-card">
          <div class="guide-card-head">
            <div class="guide-platform-tag" :style="{ background: p.color }">{{ p.label }}</div>
            <span class="guide-platform-key">{{ p.key }}</span>
          </div>

          <div class="guide-preview">
            <img v-if="guideImages[p.key]" :src="guideImages[p.key].imageUrl" :alt="`${p.label} 예시`" />
            <div v-else class="guide-preview-empty">
              아직 예시 이미지가 없습니다.<br />아래에서 업로드해주세요.
            </div>
          </div>

          <div class="guide-field">
            <label>설명 문구 (신청 폼에 함께 표시됨)</label>
            <input
              v-model="guideDescDrafts[p.key]"
              type="text"
              :placeholder="p.defaultDesc"
              class="guide-desc-input"
            />
          </div>

          <div class="guide-actions">
            <label class="btn btn-approve" :class="{ disabled: uploadingGuide === p.key }">
              {{ uploadingGuide === p.key ? '업로드 중...' : (guideImages[p.key] ? '이미지 교체' : '이미지 업로드') }}
              <input
                type="file"
                accept="image/*"
                hidden
                @change="uploadGuideImage(p.key, $event)"
                :disabled="uploadingGuide === p.key"
              />
            </label>
            <button
              v-if="guideImages[p.key]"
              type="button"
              class="btn btn-edit"
              @click="saveGuideDescription(p.key)"
            >저장</button>
            <button
              v-if="guideImages[p.key]"
              type="button"
              class="btn btn-reject"
              @click="removeGuideImage(p.key)"
            >삭제</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 프리미엄 관리 탭 -->
    <div v-if="activeTab === 'premium'" class="tab-content">
      <div class="premium-header">
        <h3 style="margin:0 0 0.5rem;">프리미엄 회원 관리</h3>
        <p style="margin:0;color:var(--text-muted);font-size:0.9rem;">
          방송 종료 시 AI 분석 리포트를 이메일로 자동 발송할 스트리머를 관리합니다.
          스트리머가 치지직 연동까지 마친 경우에만 실제 채팅 기반 리포트가 생성됩니다.
        </p>
      </div>

      <div v-if="premiumLoading" class="loading"><div class="spinner"></div></div>
      <div v-else-if="premiumList.length === 0" class="empty">
        등록된 프리미엄 스트리머가 없습니다. 관리자 페이지에서 스트리머별로 직접 활성화할 수 있습니다.
      </div>
      <div v-else class="premium-list">
        <div v-for="row in premiumList" :key="row.streamerNo" class="premium-card">
          <div class="premium-card-header">
            <div class="premium-nick">
              <strong>#{{ row.streamerNo }} {{ row.nickname || '(알 수 없음)' }}</strong>
              <span class="premium-email">{{ row.email }}</span>
            </div>
            <div class="premium-badges">
              <span :class="['prem-badge', row.chatAnalysisEnabled ? 'on' : 'off']">
                채팅 분석 {{ row.chatAnalysisEnabled ? 'ON' : 'OFF' }}
              </span>
              <span :class="['prem-badge', row.chzzkConnected ? 'on' : 'off']">
                치지직 연동 {{ row.chzzkConnected ? '✓' : '✗' }}
              </span>
              <span :class="['prem-badge', row.emailEnabled ? 'on' : 'off']">
                이메일 {{ row.emailEnabled ? 'ON' : 'OFF' }}
              </span>
            </div>
          </div>

          <div class="premium-actions">
            <button v-if="!row.chatAnalysisEnabled" class="btn btn-primary btn-sm"
                    @click="togglePremium(row, true)">프리미엄 활성화</button>
            <button v-else class="btn btn-outline btn-sm"
                    @click="togglePremium(row, false)">프리미엄 해제</button>

            <button v-if="row.emailEnabled" class="btn btn-outline btn-sm"
                    @click="toggleEmailReport(row, false)">이메일 발송 중지</button>
            <button v-else class="btn btn-primary btn-sm"
                    @click="toggleEmailReport(row, true)">이메일 발송 활성화</button>
          </div>

          <div class="premium-email-row">
            <label>리포트 수신 이메일 (암호화 저장):</label>
            <div class="premium-email-input-row">
              <input type="email"
                     v-model="premiumEmailEdit[row.streamerNo]"
                     :placeholder="row.hasReportEmail ? '등록됨 (새로 입력 시 교체)' : (row.email || '이메일 입력')" />
              <button class="btn btn-primary btn-sm" @click="saveReportEmail(row)">저장</button>
            </div>
          </div>

          <div v-if="row.expiresAt || row.lastReportSentAt || row.emailFailureCount > 0" class="premium-meta">
            <span v-if="row.expiresAt">만료: {{ new Date(row.expiresAt).toLocaleString('ko-KR') }}</span>
            <span v-if="row.lastReportSentAt">· 마지막 발송: {{ new Date(row.lastReportSentAt).toLocaleString('ko-KR') }}</span>
            <span v-if="row.emailFailureCount > 0" style="color:#e75555;">· 연속 실패 {{ row.emailFailureCount }}회</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 광고 배너 관리 탭 -->
    <div v-if="activeTab === 'banners'" class="tab-content">
      <div class="banner-create-card">
        <h3 class="banner-section-title">새 배너 등록</h3>
        <form @submit.prevent="submitBanner" class="banner-form">
          <div class="banner-form-grid">
            <div class="banner-field">
              <label>제목</label>
              <input v-model="newBanner.title" type="text" placeholder="배너 제목" />
            </div>
            <div class="banner-field">
              <label>링크 URL</label>
              <input v-model="newBanner.linkUrl" type="url" placeholder="https://..." />
            </div>
            <div class="banner-field">
              <label>시작일시</label>
              <input v-model="newBanner.startDate" type="datetime-local" />
            </div>
            <div class="banner-field">
              <label>종료일시</label>
              <input v-model="newBanner.endDate" type="datetime-local" />
            </div>
            <div class="banner-field banner-field-full">
              <label>이미지 파일</label>
              <input type="file" accept="image/*" @change="pickBannerImage" />
              <span v-if="newBanner.imageFile" class="banner-file-name">{{ newBanner.imageFile.name }}</span>
              <span class="banner-hint">권장: 1200 x 200px (가로로 넓은 형태). 어떤 크기든 자동 조정되지만, 비율이 다르면 일부가 잘릴 수 있습니다.</span>
            </div>
            <div class="banner-field banner-field-full">
              <label>또는 이미지 URL 직접 입력</label>
              <input v-model="newBanner.imageUrl" type="url" placeholder="https://example.com/image.jpg" />
            </div>
          </div>
          <button type="submit" class="btn btn-approve" :disabled="bannerSubmitting">
            {{ bannerSubmitting ? '등록 중...' : '배너 등록' }}
          </button>
        </form>
      </div>

      <h3 class="banner-section-title">등록된 배너</h3>
      <div v-if="bannerLoading" class="loading"><div class="spinner"></div></div>
      <div v-else-if="banners.length === 0" class="empty">등록된 배너가 없습니다.</div>
      <div v-else class="banner-list">
        <template v-for="banner in pagedBanners" :key="banner.id">
          <!-- 일반 표시 모드 -->
          <div v-if="editingBannerId !== banner.id" class="banner-card">
            <img :src="banner.imageUrl" :alt="banner.title" class="banner-image" />
            <div class="banner-info">
              <strong>{{ banner.title }}</strong>
              <a :href="banner.linkUrl" target="_blank" class="banner-link">{{ banner.linkUrl }}</a>
              <div class="banner-meta">
                <span>{{ formatDate(banner.startDate) }} ~ {{ formatDate(banner.endDate) }}</span>
                <span class="banner-clicks">클릭: {{ banner.clickCount || 0 }}</span>
                <span :class="['status-badge', banner.active ? 'status-approved' : 'status-rejected']">
                  {{ banner.active ? '활성' : '비활성' }}
                </span>
              </div>
            </div>
            <div class="banner-actions">
              <button class="btn btn-edit" @click="startEditBanner(banner)">수정</button>
              <button class="btn btn-reject" @click="deleteBanner(banner.id)">삭제</button>
            </div>
          </div>

          <!-- 편집 모드 -->
          <div v-else class="banner-edit-card">
            <h4 class="banner-edit-title">배너 수정</h4>
            <form @submit.prevent="updateBanner(banner.id)" class="banner-form">
              <div class="banner-form-grid">
                <div class="banner-field">
                  <label>제목</label>
                  <input v-model="editBanner.title" type="text" />
                </div>
                <div class="banner-field">
                  <label>링크 URL</label>
                  <input v-model="editBanner.linkUrl" type="url" />
                </div>
                <div class="banner-field">
                  <label>시작일시</label>
                  <input v-model="editBanner.startDate" type="datetime-local" />
                </div>
                <div class="banner-field">
                  <label>종료일시</label>
                  <input v-model="editBanner.endDate" type="datetime-local" />
                </div>
                <div class="banner-field banner-field-full">
                  <label class="checkbox-label">
                    <input v-model="editBanner.active" type="checkbox" />
                    <span>활성화 (체크 해제 시 사이트에 노출되지 않음)</span>
                  </label>
                </div>
                <div class="banner-field banner-field-full">
                  <label>이미지 교체 (선택)</label>
                  <input type="file" accept="image/*" @change="pickEditBannerImage" />
                  <span v-if="editBanner.imageFile" class="banner-file-name">{{ editBanner.imageFile.name }}</span>
                  <span class="banner-hint">이미지를 변경하지 않으면 기존 이미지가 유지됩니다.</span>
                </div>
                <div class="banner-field banner-field-full">
                  <label>또는 이미지 URL 직접 입력</label>
                  <input v-model="editBanner.imageUrl" type="url" :placeholder="banner.imageUrl" />
                </div>
              </div>
              <div class="banner-edit-actions">
                <button type="submit" class="btn btn-approve" :disabled="bannerUpdating">
                  {{ bannerUpdating ? '수정 중...' : '저장' }}
                </button>
                <button type="button" class="btn btn-reject" @click="cancelEditBanner">취소</button>
              </div>
            </form>
          </div>
        </template>
      </div>
      <Pagination
        v-if="banners.length > 0"
        :current-page="bannerPage"
        :total-pages="bannerTotalPages"
        @change="p => bannerPage = p"
      />
    </div>
  </div>
</template>

<style scoped>
.admin-page {
  max-width: 1000px;
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

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
  border-bottom: 2px solid var(--border);
  margin-bottom: 1.5rem;
}

.tab {
  padding: 0.6rem 0.95rem;
  border: none;
  background: none;
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--text3);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: color 0.2s, border-color 0.2s, background 0.2s;
  font-family: 'Pretendard', sans-serif;
  white-space: nowrap;
  border-radius: 6px 6px 0 0;
}

.tab:hover:not(.active) { background: rgba(255,255,255,0.03); }

.tab.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
}

.tab:hover { color: var(--text2); }

.filter-bar {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}

.filter-btn {
  padding: 0.4rem 1rem;
  border: 1.5px solid var(--border);
  border-radius: 20px;
  background: transparent;
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--text2);
  cursor: pointer;
  transition: all 0.2s;
  font-family: 'Pretendard', sans-serif;
}

.filter-btn.active {
  background: var(--gradient);
  color: #fff;
  border-color: transparent;
}

.filter-count {
  display: inline-block;
  margin-left: 0.35rem;
  padding: 0 0.4rem;
  background: rgba(255,255,255,0.18);
  border-radius: 10px;
  font-size: 0.72rem;
  font-weight: 700;
}

.filter-btn:not(.active) .filter-count {
  background: rgba(108,99,255,0.12);
  color: var(--accent);
}

.filter-btn:hover:not(.active) {
  border-color: var(--accent);
  color: var(--accent);
}

.loading { text-align: center; padding: 3rem 0; }

.spinner {
  width: 36px;
  height: 36px;
  border: 3px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto;
}

@keyframes spin { to { transform: rotate(360deg); } }

.empty {
  text-align: center;
  padding: 3rem 0;
  color: var(--text3);
}

.application-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.application-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 1.5rem;
  backdrop-filter: blur(12px);
  transition: background 0.2s;
}

.application-card:hover {
  background: var(--card-hover);
}

.app-info {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1rem;
}

.app-user strong {
  display: block;
  font-size: 1.05rem;
  color: var(--text);
}

.app-email { font-size: 0.85rem; color: var(--text3); }

.app-meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.status-badge {
  padding: 0.2rem 0.6rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 600;
}

.status-pending { background: rgba(251, 191, 36, 0.15); color: #fbbf24; }
.status-approved { background: rgba(0, 212, 170, 0.15); color: var(--accent3); }
.status-rejected { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
.status-dismissed { background: rgba(148, 163, 184, 0.15); color: #94a3b8; }
.status-deleted { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
.status-suspended { background: rgba(244, 114, 182, 0.15); color: #f472b6; }

.report-target,
.report-reason {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.75rem 1rem;
  margin-bottom: 0.75rem;
}

.report-target-label {
  display: inline-block;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--text3);
  margin-right: 0.5rem;
  text-transform: uppercase;
}

.report-target-text {
  font-size: 0.9rem;
  color: var(--text);
}

.report-reason p {
  margin: 0.25rem 0 0;
  font-size: 0.9rem;
  color: var(--text2);
  white-space: pre-wrap;
  word-break: break-word;
}

/* Banner management */
.banner-create-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 1.5rem;
  margin-bottom: 2rem;
  backdrop-filter: blur(12px);
}

.banner-section-title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--text);
  margin: 0 0 1rem;
}

.banner-form { display: flex; flex-direction: column; gap: 1rem; }

.banner-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.85rem;
}

.banner-field { display: flex; flex-direction: column; gap: 0.35rem; }
.banner-field-full { grid-column: 1 / -1; }

.banner-field label {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--text3);
  text-transform: uppercase;
}

.banner-field input[type="text"],
.banner-field input[type="url"],
.banner-field input[type="datetime-local"] {
  padding: 0.6rem 0.85rem;
  background: var(--bg3);
  border: 1.5px solid var(--border);
  border-radius: 8px;
  color: var(--text);
  font-size: 0.88rem;
  font-family: 'Pretendard', sans-serif;
  outline: none;
}

.banner-field input:focus { border-color: var(--accent); }

.banner-field input[type="file"] {
  font-size: 0.85rem;
  color: var(--text2);
}

.banner-file-name {
  font-size: 0.78rem;
  color: var(--accent);
  margin-top: 0.25rem;
}

.banner-hint {
  font-size: 0.72rem;
  color: var(--text3);
  margin-top: 0.25rem;
}

.banner-list {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.banner-card {
  display: grid;
  grid-template-columns: 160px 1fr auto;
  gap: 1rem;
  padding: 1rem;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  align-items: center;
}

.banner-image {
  width: 160px;
  height: 90px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid var(--border);
}

.banner-info {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  min-width: 0;
}

.banner-info strong {
  font-size: 0.95rem;
  color: var(--text);
}

.banner-link {
  font-size: 0.78rem;
  color: var(--accent);
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.banner-meta {
  display: flex;
  gap: 0.85rem;
  align-items: center;
  flex-wrap: wrap;
  font-size: 0.78rem;
  color: var(--text3);
}

.banner-clicks { font-weight: 600; }

.banner-actions {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.banner-edit-card {
  background: rgba(108,99,255,0.04);
  border: 1.5px solid rgba(108,99,255,0.3);
  border-radius: 12px;
  padding: 1.25rem;
}

.banner-edit-title {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--accent);
  margin: 0 0 1rem;
}

.banner-edit-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.75rem;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
  text-transform: none;
  font-weight: 500;
  color: var(--text2);
  font-size: 0.85rem;
}

.checkbox-label input[type="checkbox"] {
  width: 16px;
  height: 16px;
  accent-color: var(--accent);
}

@media (max-width: 768px) {
  .banner-form-grid { grid-template-columns: 1fr; }
  .banner-card { grid-template-columns: 1fr; }
  .banner-image { width: 100%; height: 180px; }
}

.app-date { font-size: 0.8rem; color: var(--text3); }

.app-screenshot {
  margin-bottom: 1rem;
  border-radius: 8px;
  overflow: hidden;
  max-height: 300px;
  border: 1px solid var(--border);
}

.app-screenshot img {
  width: 100%;
  height: auto;
  object-fit: contain;
  cursor: pointer;
}

.app-screenshots {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.screenshot-thumb {
  flex: 0 0 auto;
  display: block;
  width: 200px;
  height: 140px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--border);
  cursor: pointer;
  transition: border-color 0.2s, transform 0.2s;
}

.screenshot-thumb:hover {
  border-color: var(--accent);
  transform: scale(1.02);
}

.screenshot-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.content-desc {
  color: var(--text2);
  font-size: 0.9rem;
  margin-bottom: 1rem;
}

.expand-icon {
  font-size: 0.7rem;
  color: var(--text3);
  margin-left: 0.5rem;
}

.content-detail-box {
  margin: 1rem 0;
  padding: 1rem;
  background: rgba(108,99,255,0.04);
  border: 1px solid rgba(108,99,255,0.15);
  border-radius: 10px;
}

.content-detail-grid {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.detail-item label {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--accent);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.detail-value {
  font-size: 0.9rem;
  color: var(--text);
}

.detail-desc {
  white-space: pre-wrap;
  line-height: 1.6;
  max-height: 300px;
  overflow-y: auto;
}

.detail-link {
  color: var(--accent);
  font-size: 0.85rem;
  word-break: break-all;
}

.app-actions { display: flex; gap: 0.75rem; }

.btn {
  padding: 0.6rem 1.25rem;
  border: none;
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  font-family: 'Pretendard', sans-serif;
}

.btn-approve { background: rgba(0, 212, 170, 0.2); color: var(--accent3); border: 1px solid rgba(0, 212, 170, 0.3); }
.btn-approve:hover { background: rgba(0, 212, 170, 0.3); }
.btn-reject { background: rgba(239, 68, 68, 0.15); color: #ef4444; border: 1px solid rgba(239, 68, 68, 0.25); }
.btn-reject:hover { background: rgba(239, 68, 68, 0.25); }
.btn-edit { background: rgba(108, 99, 255, 0.15); color: var(--accent); border: 1px solid rgba(108, 99, 255, 0.3); }
.btn-edit:hover { background: rgba(108, 99, 255, 0.28); }
.btn-danger { background: rgba(239, 68, 68, 0.15); color: #ef4444; border: 1px solid rgba(239, 68, 68, 0.25); }
.btn-danger:hover { background: rgba(239, 68, 68, 0.25); }

.app-review-info {
  display: flex;
  gap: 1rem;
  font-size: 0.85rem;
  color: var(--text3);
}

.placeholder {
  text-align: center;
  padding: 4rem 0;
  color: var(--text3);
}

/* Notion 동기화 바 */
.notion-sync-bar {
  display: flex; align-items: center; gap: 0.75rem; margin-bottom: 1rem;
  padding: 0.75rem 1rem; background: var(--card); border: 1px solid var(--border);
  border-radius: 10px; flex-wrap: wrap;
}
.notion-sync-hint { color: var(--text-muted); font-size: 0.85rem; }

/* 회원 role 서브 탭 */
.role-sub-tabs {
  display: flex; gap: 0.5rem; margin-bottom: 1rem; flex-wrap: wrap;
}
.role-tab {
  background: var(--card); border: 1px solid var(--border); color: var(--text-muted);
  padding: 0.55rem 1rem; border-radius: 8px; font-size: 0.9rem; cursor: pointer;
  transition: all 0.2s;
}
.role-tab:hover { border-color: var(--primary); color: var(--text); }
.role-tab.active { background: var(--primary); color: #fff; border-color: var(--primary); }
.role-count { opacity: 0.7; margin-left: 0.25rem; font-size: 0.82rem; }

/* 프리미엄 관리 */
.premium-header { margin-bottom: 1.25rem; }
.premium-list { display: flex; flex-direction: column; gap: 1rem; }
.premium-card {
  background: var(--card); border: 1px solid var(--border); border-radius: 12px;
  padding: 1.25rem; display: flex; flex-direction: column; gap: 0.75rem;
}
.premium-card-header {
  display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; flex-wrap: wrap;
}
.premium-nick strong { display: block; margin-bottom: 0.25rem; }
.premium-email { color: var(--text-muted); font-size: 0.88rem; }
.premium-badges { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.prem-badge {
  padding: 0.2rem 0.6rem; border-radius: 12px; font-size: 0.78rem; font-weight: 600;
}
.prem-badge.on { background: rgba(0, 255, 163, 0.15); color: #00c477; border: 1px solid rgba(0, 255, 163, 0.4); }
.prem-badge.off { background: var(--border); color: var(--text-muted); }
.premium-actions { display: flex; gap: 0.5rem; flex-wrap: wrap; }
.premium-email-row { font-size: 0.88rem; color: var(--text-muted); }
.premium-email-row label { display: block; margin-bottom: 0.4rem; }
.premium-email-input-row { display: flex; gap: 0.5rem; }
.premium-email-input-row input {
  flex: 1; padding: 0.5rem 0.75rem; border: 1px solid var(--border); border-radius: 6px;
  background: var(--bg); color: var(--text);
}
.premium-meta { color: var(--text-muted); font-size: 0.82rem; display: flex; gap: 0.5rem; flex-wrap: wrap; }

/* User search */
.user-search-bar {
  margin-bottom: 1rem;
}

.user-search-input {
  width: 100%;
  padding: 0.6rem 1rem;
  background: var(--bg3);
  border: 1.5px solid var(--border);
  border-radius: 8px;
  color: var(--text);
  font-size: 0.88rem;
  font-family: 'Pretendard', sans-serif;
  outline: none;
  box-sizing: border-box;
}

.user-search-input::placeholder { color: var(--text3); }
.user-search-input:focus { border-color: var(--accent); }

/* User table */
.user-table {
  border: 1px solid var(--border); border-radius: 12px; overflow: hidden;
}

.user-table-header, .user-table-row {
  display: grid; grid-template-columns: 50px 1fr 1.5fr 90px 100px 60px;
  gap: 0.5rem; padding: 0.75rem 1rem; align-items: center;
}

.user-table-header {
  background: var(--bg3); font-size: 0.75rem; font-weight: 700;
  color: var(--text3); text-transform: uppercase; letter-spacing: 0.5px;
}

.user-table-row {
  background: var(--card); border-top: 1px solid var(--border);
  font-size: 0.85rem; color: var(--text2);
}

.user-table-row:hover { background: var(--card-hover); }

.user-col-nick { display: flex; align-items: center; gap: 0.5rem; font-weight: 600; color: var(--text); }

.user-mini-avatar { width: 24px; height: 24px; border-radius: 6px; object-fit: cover; }

.user-mini-placeholder {
  width: 24px; height: 24px; border-radius: 6px; background: var(--gradient);
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 10px; font-weight: 700; color: white;
}

.btn-xs { padding: 0.2rem 0.5rem; font-size: 0.7rem; border-radius: 5px; }

.role-badge-fan { background: rgba(108,99,255,0.15); color: var(--accent); }
.role-badge-streamer { background: rgba(0,212,170,0.15); color: var(--accent3); }
.role-badge-admin { background: rgba(255,107,157,0.15); color: var(--accent2); }

/* 스트리머 신청 가이드 이미지 관리 */
.streamer-covers-hint {
  background: rgba(108,99,255,0.08);
  border: 1px solid rgba(108,99,255,0.2);
  border-radius: 10px;
  padding: 0.85rem 1rem;
  font-size: 0.85rem;
  color: var(--text2);
  margin-bottom: 1.25rem;
  line-height: 1.5;
}

.guide-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1rem;
}

.guide-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.guide-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.guide-platform-tag {
  display: inline-block;
  padding: 0.3rem 0.75rem;
  border-radius: 8px;
  color: #fff;
  font-size: 0.8rem;
  font-weight: 700;
}

.guide-platform-key {
  font-size: 0.72rem;
  color: var(--text3);
  font-family: monospace;
}

.guide-preview {
  width: 100%;
  aspect-ratio: 16 / 10;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--border);
  background: var(--bg3);
  display: flex;
  align-items: center;
  justify-content: center;
}

.guide-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.guide-preview-empty {
  font-size: 0.78rem;
  color: var(--text3);
  text-align: center;
  padding: 0.5rem;
  line-height: 1.5;
}

.guide-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.guide-field label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text3);
  text-transform: uppercase;
}

.guide-desc-input {
  padding: 0.55rem 0.75rem;
  background: var(--bg3);
  border: 1.5px solid var(--border);
  border-radius: 8px;
  color: var(--text);
  font-size: 0.85rem;
  font-family: 'Pretendard', sans-serif;
  outline: none;
}

.guide-desc-input:focus { border-color: var(--accent); }

.guide-actions {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.guide-actions label.btn {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
}

.guide-actions label.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Static page editor */
.page-edit-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 1.5rem;
  backdrop-filter: blur(12px);
}

.page-edit-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.page-edit-form textarea {
  padding: 0.6rem 0.85rem;
  background: var(--bg3);
  border: 1.5px solid var(--border);
  border-radius: 8px;
  color: var(--text);
  font-size: 0.88rem;
  font-family: 'Pretendard', sans-serif;
  outline: none;
  resize: vertical;
  min-height: 80px;
}

.page-edit-form textarea:focus { border-color: var(--accent); }

.page-edit-form input[type="text"] {
  padding: 0.6rem 0.85rem;
  background: var(--bg3);
  border: 1.5px solid var(--border);
  border-radius: 8px;
  color: var(--text);
  font-size: 0.88rem;
  font-family: 'Pretendard', sans-serif;
  outline: none;
}

.page-edit-form input[type="text"]:focus { border-color: var(--accent); }

.sections-editor {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.section-item {
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-number {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--accent);
  text-transform: uppercase;
}

.btn-remove {
  padding: 0.25rem 0.6rem;
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: 6px;
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  font-size: 0.75rem;
  font-weight: 600;
  cursor: pointer;
  font-family: 'Pretendard', sans-serif;
}

.btn-remove:hover { background: rgba(239, 68, 68, 0.2); }

.btn-add-section {
  padding: 0.6rem 1.25rem;
  border: 1.5px dashed var(--border);
  border-radius: 8px;
  background: transparent;
  color: var(--text2);
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  font-family: 'Pretendard', sans-serif;
}

.btn-add-section:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.page-edit-actions {
  display: flex;
  gap: 0.75rem;
  margin-top: 0.5rem;
}

@media (max-width: 768px) {
  .app-info { flex-direction: column; gap: 0.5rem; }
  .filter-bar { flex-wrap: wrap; }
}

/* 정적 페이지 히스토리 모달 */
.history-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 1rem;
}

.history-modal {
  width: min(1000px, 100%);
  max-height: 85vh;
  background: var(--surface, #fff);
  color: var(--text);
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.45);
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid var(--border);
}
.history-header h3 { margin: 0; font-size: 1.1rem; }
.history-close {
  background: transparent;
  border: none;
  color: var(--text2);
  font-size: 1.5rem;
  cursor: pointer;
  line-height: 1;
}
.history-close:hover { color: var(--text); }

.history-body {
  display: grid;
  grid-template-columns: 260px 1fr;
  flex: 1;
  min-height: 0;
}

.history-list {
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  border-right: 1px solid var(--border);
  max-height: 70vh;
}
.history-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  cursor: pointer;
  border-bottom: 1px solid var(--border);
}
.history-item:hover { background: var(--hover, rgba(127, 127, 127, 0.08)); }
.history-item.active {
  background: var(--hover, rgba(127, 127, 127, 0.12));
  border-left: 3px solid var(--accent);
  padding-left: calc(1rem - 3px);
}
.history-version {
  font-weight: 700;
  color: var(--accent);
  min-width: 40px;
}
.history-meta { font-size: 0.85rem; }
.history-date { color: var(--text2); }
.history-editor { color: var(--text3); font-size: 0.8rem; }

.history-detail {
  overflow-y: auto;
  padding: 1.25rem;
  max-height: 70vh;
}
.history-detail-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--border);
}
.history-title-preview { color: var(--text2); margin-left: 0.5rem; }
.history-sections { display: flex; flex-direction: column; gap: 1rem; }
.history-section h4 { margin: 0 0 0.4rem 0; font-size: 1rem; }
.history-section p { margin: 0; white-space: pre-wrap; color: var(--text2); line-height: 1.6; }

@media (max-width: 768px) {
  .history-body { grid-template-columns: 1fr; }
  .history-list { max-height: 30vh; border-right: none; border-bottom: 1px solid var(--border); }
  .history-detail { max-height: 40vh; }
}
</style>
