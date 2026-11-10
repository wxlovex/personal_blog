import { ref, computed } from 'vue'

const TOKEN_KEY = 'blog_token'

// Shared reactive state (singleton across all component uses)
const token = ref(localStorage.getItem(TOKEN_KEY) || '')
const currentUser = ref(null)

// A session change in another tab must discard the previous account's page state.
window.addEventListener('storage', (event) => {
  if (event.key === TOKEN_KEY || event.key === null) {
    token.value = localStorage.getItem(TOKEN_KEY) || ''
    currentUser.value = null
    window.location.reload()
  }
})
window.addEventListener('pageshow', () => {
  if (token.value !== (localStorage.getItem(TOKEN_KEY) || '')) {
    token.value = ''
    currentUser.value = null
    window.location.reload()
  }
})

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

  function clearAccount(userId) {
    // UI cleanup only. Authorization remains entirely on the server.
    try {
      const payload = JSON.parse(atob(token.value.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
      if (String(payload.userId) === String(userId)) logout()
    } catch { /* An unrelated or malformed local credential is handled by the API guard. */ }
  }

  return {
    token,
    currentUser,
    isLoggedIn,
    login,
    logout,
    clearAccount,
  }
}
