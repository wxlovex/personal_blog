import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '@/composables/useAuth'
import { userMe } from '@/api'

import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import HomeView from '@/views/HomeView.vue'
import DetailView from '@/views/DetailView.vue'
import PublishView from '@/views/PublishView.vue'

const routes = [
  { path: '/', name: 'home', component: HomeView, meta: { requiresAuth: true } },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/register', name: 'register', component: RegisterView },
  { path: '/article/:id', name: 'detail', component: DetailView, meta: { requiresAuth: true } },
  { path: '/publish', name: 'publish', component: PublishView, meta: { requiresAuth: true } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// Navigation guard — redirect to /login if not authenticated
router.beforeEach(async (to) => {
  const { isLoggedIn, currentUser } = useAuth()
  if (!to.meta.requiresAuth) return
  if (!isLoggedIn.value) return '/login'
  if (!currentUser.value) {
    try {
      currentUser.value = (await userMe()).data.data
    } catch {
      return '/login'
    }
  }
})

export default router
