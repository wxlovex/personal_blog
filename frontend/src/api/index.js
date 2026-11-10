import axios from 'axios'
import { useAuth } from '@/composables/useAuth'

const api = axios.create({
  baseURL: '/api',
  timeout: 20000,
})

const publicPaths = ['/user/login', '/user/register', '/user/security-options', '/user/email/confirm', '/user/password/forgot', '/user/password/reset']

// Request interceptor — attach JWT token
api.interceptors.request.use(
  (config) => {
    const { token } = useAuth()
    if (token.value && !publicPaths.includes(config.url) && !config.url.startsWith('/plaza/')) {
      config.headers.Authorization = `Bearer ${token.value}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor — handle 401
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401
        && !publicPaths.includes(error.config?.url)) {
      const { token, logout } = useAuth()
      // A late response from the previous account must not clear a newer login.
      if (error.config?.headers?.Authorization !== `Bearer ${token.value}`) return Promise.reject(error)
      logout()
      // Clear private page memory as well as credentials after a session expires.
      if (!['/login', '/register'].includes(window.location.pathname)) {
        window.location.replace('/login?redirect=' + encodeURIComponent(window.location.pathname))
      }
    }
    return Promise.reject(error)
  }
)

// ── User APIs ──

export function userRegister(data) {
  return api.post('/user/register', data)
}

export function userLogin(data) {
  return api.post('/user/login', data)
}

export function userMe() {
  return api.get('/user/me')
}

export function userLogout() {
  return api.post('/user/logout')
}

export function userSecurity() { return api.get('/user/security') }
export function securityOptions() { return api.get('/user/security-options') }
export function userPasswordChange(data) { return api.post('/user/password', data) }
export function userLogoutAll(data) { return api.post('/user/logout-all', data) }
export function userEmailVerification(data) { return api.post('/user/email/verification', data) }
export function userEmailConfirm(token) { return api.post('/user/email/confirm', { token }) }
export function userForgotPassword(email) { return api.post('/user/password/forgot', { email }) }
export function userResetPassword(data) { return api.post('/user/password/reset', data) }

// ── Article APIs ──

export function articleList(page, size, { keyword, categoryId } = {}) {
  return api.get('/article/list', { params: { page, size, keyword: keyword || undefined, categoryId: categoryId || undefined } })
}

export function articleDetail(id) {
  return api.get(`/article/detail/${id}`)
}

export function articlePublish(data) {
  return api.post('/article/publish', data)
}

export function articleUpdate(id, data) {
  return api.put(`/article/${id}`, data)
}

export function articleUpdateCategory(id, categoryId) {
  return api.patch(`/article/${id}/category`, { categoryId })
}

export function articlePublication(id, published, category) {
  return api.put(`/article/${id}/publication`, { published, category: published ? category : undefined })
}
export function plazaCategories() { return api.get('/plaza/categories') }
export function plazaList(page = 1, size = 10, { keyword, category } = {}) {
  return api.get('/plaza/articles', { params: { page, size, keyword: keyword || undefined, category: category || undefined } })
}
export function plazaDetail(id) { return api.get(`/plaza/articles/${id}`) }

export function articleDelete(id) {
  return api.delete(`/article/${id}`)
}

export function categoryList() { return api.get('/category/list') }
export function categoryCreate(data) { return api.post('/category', data) }
export function categoryUpdate(id, data) { return api.put(`/category/${id}`, data) }
export function categoryDelete(id) { return api.delete(`/category/${id}`) }

// ── Comment APIs ──

export function commentList(articleId, page = 1, size = 10) {
  return api.get(`/comment/${articleId}`, { params: { page, size } })
}

export function commentPublish(data) {
  return api.post('/comment/publish', data)
}

export default api
