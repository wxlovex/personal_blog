import { ref } from 'vue'
import { userMe } from '@/api'
import { useAuth } from './useAuth'

const profileError = ref('')
const loadingProfile = ref(false)
let pending = null
let pendingToken = ''

export function useProfile() {
  const { token, currentUser } = useAuth()

  function refreshProfile() {
    if (!token.value) return Promise.resolve()
    if (pending && pendingToken === token.value) return pending
    const requestedToken = token.value
    pendingToken = requestedToken
    loadingProfile.value = true
    profileError.value = ''
    const request = userMe().then(({ data }) => {
      if (token.value === requestedToken) currentUser.value = data.data
    }).catch(error => {
      if (token.value === requestedToken && error.response?.status !== 401) {
        profileError.value = '账号信息暂时无法加载，你可以继续浏览或重试。'
      }
      throw error
    }).finally(() => {
      if (pending === request) {
        pending = null
        loadingProfile.value = false
      }
    })
    pending = request
    return request
  }

  return { profileError, loadingProfile, refreshProfile }
}
