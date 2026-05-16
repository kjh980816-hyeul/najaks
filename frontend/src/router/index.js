import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomeView.vue'),
    meta: { title: 'NaJacks - Home' }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { title: 'Login', guest: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { title: 'Register', guest: true }
  },
  {
    path: '/reset-password',
    name: 'ResetPassword',
    component: () => import('@/views/auth/ResetPasswordView.vue'),
    meta: { title: '비밀번호 재설정', guest: true }
  },
  {
    path: '/streamers',
    name: 'Streamers',
    component: () => import('@/views/streamer/StreamerListView.vue'),
    meta: { title: 'Streamers' }
  },
  {
    path: '/streamers/:id',
    name: 'StreamerDetail',
    component: () => import('@/views/streamer/StreamerDetailView.vue'),
    meta: { title: 'Streamer' }
  },
  {
    path: '/streamers/:id/board',
    name: 'StreamerBoard',
    component: () => import('@/views/streamer/StreamerBoardView.vue'),
    meta: { title: '팬 게시판' }
  },
  {
    path: '/contents',
    name: 'Contents',
    component: () => import('@/views/content/ContentListView.vue'),
    meta: { title: '컨텐츠 홍보' }
  },
  {
    path: '/contents/create',
    name: 'ContentCreate',
    component: () => import('@/views/content/ContentCreateView.vue'),
    meta: { title: '컨텐츠 등록', requiresAuth: true, role: 'STREAMER' }
  },
  {
    path: '/contents/:id',
    name: 'ContentDetail',
    component: () => import('@/views/content/ContentDetailView.vue'),
    meta: { title: '컨텐츠 상세' }
  },
  {
    path: '/mypage',
    name: 'MyPage',
    component: () => import('@/views/mypage/MyPageView.vue'),
    meta: { title: '마이페이지', requiresAuth: true }
  },
  {
    path: '/streamer/apply',
    name: 'StreamerApply',
    component: () => import('@/views/streamer/StreamerApplyView.vue'),
    meta: { title: '스트리머 인증 신청', requiresAuth: true }
  },
  {
    path: '/streamer/manage',
    name: 'StreamerManage',
    component: () => import('@/views/streamer/StreamerManageView.vue'),
    meta: { title: '스트리머 관리', requiresAuth: true, role: 'STREAMER' }
  },
  {
    path: '/streamer/edit-profile',
    name: 'StreamerEditProfile',
    component: () => import('@/views/streamer/StreamerEditProfileView.vue'),
    meta: { title: '스트리머 프로필 편집', requiresAuth: true, role: 'STREAMER' }
  },
  {
    path: '/community',
    name: 'Community',
    component: () => import('@/views/community/CommunityView.vue'),
    meta: { title: '커뮤니티', requiresAuth: true }
  },
  {
    path: '/community/write',
    name: 'PostWrite',
    component: () => import('@/views/community/PostWriteView.vue'),
    meta: { title: '글쓰기', requiresAuth: true }
  },
  {
    path: '/community/:id',
    name: 'PostDetail',
    component: () => import('@/views/community/PostDetailView.vue'),
    meta: { title: '게시글', requiresAuth: true }
  },
  {
    path: '/creator',
    name: 'Creator',
    component: () => import('@/views/creator/CreatorDashboard.vue'),
    meta: { title: '크리에이터 게시판', requiresAuth: true }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/admin/AdminDashboard.vue'),
    meta: { title: 'Admin Dashboard', requiresAuth: true, role: 'ADMIN' }
  },
  {
    path: '/search',
    name: 'Search',
    component: () => import('@/views/search/SearchResultView.vue'),
    meta: { title: '검색 결과' }
  },
  {
    path: '/support',
    name: 'Support',
    component: () => import('@/views/support/SupportView.vue'),
    meta: { title: '고객센터' }
  },
  {
    path: '/resources',
    name: 'StreamerResources',
    component: () => import('@/views/resources/StreamerResourcesView.vue'),
    meta: { title: '스트리머 도구 모음' }
  },
  {
    path: '/info/:slug',
    name: 'StaticPage',
    component: () => import('@/views/static/StaticPageView.vue'),
    meta: { title: '안내' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

const SITE_NAME = 'NAJAKS - 스트리머 · 팬을 위한 커뮤니티 사이트'

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} | NAJAKS` : SITE_NAME

  const authStore = useAuthStore()

  // Guest-only routes (login, register) - redirect if already authenticated
  if (to.meta.guest && authStore.isAuthenticated) {
    return next({ name: 'Home' })
  }

  // Protected routes
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return next({ name: 'Login', query: { redirect: to.fullPath } })
  }

  // Role-based access
  if (to.meta.role) {
    const userRole = authStore.user?.role
    if (to.meta.role === 'ADMIN' && userRole !== 'ADMIN') {
      return next({ name: 'Home' })
    }
    if (to.meta.role === 'STREAMER' && userRole !== 'STREAMER' && userRole !== 'ADMIN') {
      return next({ name: 'Home' })
    }
  }

  next()
})

export default router
