import axios from 'axios'
import { useAuth } from '@/composables/useAuth'

const api = axios.create({
  baseURL: '/api',
  timeout: 20000,
})

// Request interceptor — attach JWT token
api.interceptors.request.use(
  (config) => {
    const { token } = useAuth()
    if (token.value) {
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
    if (error.response?.status === 401 && error.config?.headers?.Authorization
        && !['/user/login', '/user/register'].includes(error.config?.url)) {
      const { logout } = useAuth()
      logout()
      // Redirect to login — use window.location as a fallback outside Vue context
      window.location.href = '/login'
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

// ── Article APIs ──

export function articleList(page, size) {
  return api.get('/article/list', { params: { page, size } })
}

export function articleDetail(id) {
  return api.get(`/article/detail/${id}`)
}

export function articlePublish(data) {
  return api.post('/article/publish', data)
}

export function articleDelete(id) {
  return api.delete(`/article/${id}`)
}

// ── Comment APIs ──

export function commentList(articleId, page = 1, size = 10) {
  return api.get(`/comment/${articleId}`, { params: { page, size } })
}

export function commentPublish(data) {
  return api.post('/comment/publish', data)
}

export default api
