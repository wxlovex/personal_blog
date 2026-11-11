import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '@/composables/useAuth'
import { useProfile } from '@/composables/useProfile'

import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import HomeView from '@/views/HomeView.vue'
import DetailView from '@/views/DetailView.vue'
import PublishView from '@/views/PublishView.vue'
import AboutView from '@/views/AboutView.vue'
import PlazaView from '@/views/PlazaView.vue'
import PlazaDetailView from '@/views/PlazaDetailView.vue'
import SecurityView from '@/views/SecurityView.vue'
import RecoveryView from '@/views/RecoveryView.vue'

const routes = [
  { path: '/account/security', name: 'security', component: SecurityView, meta: { requiresAuth: true, title: '账号安全 · 行间' } },
  { path: '/account/verify-email', name: 'verify-email', component: RecoveryView, meta: { title: '验证邮箱 · 行间' } },
  { path: '/forgot-password', name: 'forgot-password', component: RecoveryView, meta: { title: '找回密码 · 行间' } },
  { path: '/reset-password', name: 'reset-password', component: RecoveryView, meta: { title: '重置密码 · 行间' } },
  { path: '/', name: 'home', component: HomeView, meta: { requiresAuth: true, title: '我的日记 · 行间' } },
  { path: '/about', name: 'about', component: AboutView, meta: { title: '关于 · 行间' } },
  { path: '/plaza', name: 'plaza', component: PlazaView, meta: { title: '广场 · 行间' } },
  { path: '/plaza/:id', name: 'plaza-detail', component: PlazaDetailView, meta: { title: '广场文章 · 行间' } },
  { path: '/login', name: 'login', component: LoginView, meta: { title: '登录 · 行间' } },
  { path: '/register', name: 'register', component: RegisterView, meta: { title: '注册 · 行间' } },
  { path: '/article/:id', name: 'detail', component: DetailView, meta: { requiresAuth: true, title: '翻开日记 · 行间' } },
  { path: '/article/:id/edit', name: 'edit', component: PublishView, meta: { requiresAuth: true, title: '修改日记 · 行间' } },
  { path: '/publish', name: 'publish', component: PublishView, meta: { requiresAuth: true, title: '写一页 · 行间' } },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    return savedPosition || { top: 0 }
  },
})

// API endpoints enforce authorization; transient profile failures keep the page recoverable.
router.beforeEach(async (to) => {
  const { isLoggedIn, currentUser } = useAuth()
  if (to.name === 'login' || to.name === 'register') return
  if (to.meta.requiresAuth && isLoggedIn.value && !currentUser.value) {
    try {
      await useProfile().refreshProfile()
    } catch (error) {
      if (error.response?.status === 401 && !isLoggedIn.value) return false
    }
  }
  if (to.meta.requiresAuth && !isLoggedIn.value) return { path: '/login', query: { redirect: to.fullPath } }
})

router.afterEach(to => { document.title = to.meta.title || '行间' })

export default router
