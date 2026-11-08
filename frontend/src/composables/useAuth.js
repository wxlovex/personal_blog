import { ref, computed } from 'vue'

const TOKEN_KEY = 'blog_token'

// Shared reactive state (singleton across all component uses)
const token = ref(localStorage.getItem(TOKEN_KEY) || '')
const currentUser = ref(null)

export function useAuth() {
  const isLoggedIn = computed(() => !!token.value)

  function setToken(newToken) {
    currentUser.value = null
    token.value = newToken
    localStorage.setItem(TOKEN_KEY, newToken)
  }

  function login(newToken) {
    setToken(newToken)
  }

  function logout() {
    token.value = ''
    currentUser.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  return {
    token,
    currentUser,
    isLoggedIn,
    login,
    logout,
  }
}
