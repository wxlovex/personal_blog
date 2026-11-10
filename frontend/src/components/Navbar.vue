<script setup>
import { useRouter } from 'vue-router'
import { ref } from 'vue'
import { userLogout } from '@/api'
import { useAuth } from '@/composables/useAuth'
import BlogIcon from './BlogIcon.vue'
const router = useRouter()
const { isLoggedIn, currentUser, token, logout } = useAuth()
const loggingOut = ref(false)
const logoutError = ref('')
async function handleLogout() {
  if (loggingOut.value) return
  loggingOut.value = true
  logoutError.value = ''
  const previousToken = token.value
  try {
    await userLogout()
    if (token.value !== previousToken) return
    logout()
    router.replace('/login')
  } catch (error) {
    if (token.value && token.value !== previousToken) return
    if (error.response?.status === 401) {
      logout()
      router.replace('/login')
    } else {
      logoutError.value = error.response?.data?.message || '退出暂未完成，请检查网络后重试。'
    }
  } finally { loggingOut.value = false }
}
</script>
<template>
  <header class="navbar">
    <div class="page-shell navbar-inner">
      <router-link to="/" class="navbar-brand" aria-label="行间，返回首页">
        <span class="brand-seal" aria-hidden="true">记</span>
        <span class="brand-name">行间<span class="brand-caption">只属于你的日记本</span></span>
      </router-link>
      <nav class="navbar-links" aria-label="主导航">
        <router-link to="/" class="nav-link" exact-active-class="is-active">日记</router-link>
        <router-link to="/plaza" class="nav-link" active-class="is-active">广场</router-link>
        <router-link to="/about" class="nav-link" active-class="is-active">关于</router-link>
        <span class="nav-divider" aria-hidden="true"></span>
        <template v-if="isLoggedIn">
          <router-link to="/account/security" class="nav-link" active-class="is-active">账号安全</router-link>
          <router-link to="/publish" class="btn btn-primary btn-sm"><BlogIcon name="pen" /><span>写一页</span></router-link>
          <button class="logout-btn" :disabled="loggingOut" :aria-label="`退出登录${currentUser?.username ? '，' + currentUser.username : ''}`" @click="handleLogout"><BlogIcon name="logout" /><span class="logout-text">{{ loggingOut ? '正在退出…' : '退出' }}</span></button>
        </template>
        <template v-else>
          <router-link to="/login" class="nav-link">登录</router-link>
          <router-link to="/register" class="btn btn-primary btn-sm">注册</router-link>
        </template>
      </nav>
    </div>
    <p v-if="logoutError" class="page-shell error-msg" role="alert">{{ logoutError }}</p>
  </header>
</template>
<style scoped>
.navbar { border-bottom: 1px solid var(--border); }
.navbar-inner { min-height: 94px; display: flex; align-items: center; justify-content: space-between; gap: 24px; }
.navbar-brand { display: inline-flex; align-items: center; gap: 12px; color: var(--text-h); flex-shrink: 0; }
.brand-seal { display: grid; place-items: center; width: 35px; height: 39px; background: var(--accent); color: var(--on-accent); border-radius: 3px; font: 22px/1 var(--serif); }
.brand-name { font: 600 21px/1.5 var(--serif); letter-spacing: .14em; }
.brand-caption { display: block; font: 10px/1.8 var(--sans); letter-spacing: .09em; color: var(--text-muted); }
.navbar-links { display: flex; align-items: center; gap: 24px; }
.nav-link { display: inline-flex; align-items: center; min-height: 40px; font-size: .825rem; color: var(--text-muted); }
.nav-link:hover, .is-active { color: var(--accent); }
.is-active { text-decoration: underline; text-underline-offset: 8px; text-decoration-thickness: 1px; }
.nav-divider { width: 1px; height: 15px; background: var(--border); }
.logout-btn { display: inline-flex; align-items: center; gap: 6px; min-height: 40px; border: 0; padding: 0; background: transparent; color: var(--text-muted); font-size: .8rem; }
.logout-btn:hover { color: var(--accent); }
@media (max-width: 600px) {
  .navbar-inner { min-height: 80px; gap: 12px; }
  .brand-caption, .nav-divider, .logout-text { display: none; }
  .navbar-links { gap: 15px; }
  .brand-name { font-size: 19px; }
  .brand-seal { width: 29px; height: 33px; font-size: 19px; }
  .navbar-brand { gap: 8px; }
  .navbar-links .btn { padding-inline: 11px; }
}
@media (max-width: 700px) {
  .navbar-inner { flex-wrap: wrap; justify-content: center; padding-block: 16px 10px; gap: 10px 16px; }
  .navbar-brand { margin-right: auto; }
  .navbar-links { width: 100%; justify-content: space-between; gap: 10px; }
}
@media (pointer: coarse) { .nav-link, .logout-btn { min-height: 44px; } .logout-btn { min-width: 44px; justify-content: center; } }
@media (max-width: 420px) { .navbar-links { gap: 7px; } }
</style>
