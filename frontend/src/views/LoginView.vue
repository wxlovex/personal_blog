<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userLogin, securityOptions } from '@/api'
import { useAuth } from '@/composables/useAuth'
import BlogIcon from '@/components/BlogIcon.vue'
const router = useRouter()
const route = useRoute()
const { login } = useAuth()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)
const mailEnabled = ref(false)
onMounted(async () => {
  try { mailEnabled.value = (await securityOptions()).data.data.mailEnabled }
  catch { mailEnabled.value = false }
})
const notice = computed(() => ({ 'password-changed': '密码已修改，所有设备已退出，请使用新密码登录。', 'all-sessions-ended': '所有设备已退出，请重新登录。' })[route.query.notice] || '')
async function handleSubmit() {
  if (loading.value) return
  error.value = ''
  if (!username.value.trim() || !password.value) { error.value = '请填写用户名和密码。'; return }
  loading.value = true
  try {
    const res = await userLogin({ username: username.value.trim(), password: password.value })
    login(res.data.data)
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' && (['/publish', '/account/security'].includes(redirect) || /^\/article\/\d+(\/edit)?$/.test(redirect)) ? redirect : '/')
  } catch (e) { error.value = e.response?.data?.message || '登录暂未成功，请稍后重试。' }
  finally { loading.value = false }
}
</script>
<template>
  <div class="auth-page page-enter">
    <section class="auth-intro"><p class="eyebrow">A LITTLE SPACE FOR MYSELF</p><h1>欢迎回来，<br>这里的每一页都属于你。</h1><p>登录后，翻开自己的日记。记录默认私密，愿意分享时，再带到广场。</p><span class="auth-note">不必完整，不必漂亮，<br>真实就很好。</span></section>
    <section class="auth-card" aria-labelledby="login-heading">
      <h2 id="login-heading">登录行间</h2><p class="auth-subtitle">回到熟悉的书桌前。</p>
      <p v-if="notice" class="account-notice" role="status">{{ notice }}</p>
      <form class="auth-form" @submit.prevent="handleSubmit">
        <div class="form-group"><label class="form-label" for="login-username">用户名</label><input id="login-username" v-model="username" class="form-input" type="text" placeholder="你的用户名" autocomplete="username" required :disabled="loading" /></div>
        <div class="form-group"><label class="form-label" for="login-password">密码</label><input id="login-password" v-model="password" class="form-input" type="password" placeholder="输入密码" autocomplete="current-password" required :disabled="loading" /></div>
        <p v-if="error" class="error-msg" role="alert">{{ error }}</p>
        <button type="submit" class="btn btn-primary" :disabled="loading">{{ loading ? '正在登录…' : '回到书房' }}<BlogIcon v-if="!loading" name="arrow-right" /></button>
        <router-link v-if="mailEnabled" to="/forgot-password" class="text-link">忘记密码？</router-link>
      </form>
      <p class="auth-footer">还没有账号？<router-link to="/register">注册一个</router-link></p>
    </section>
  </div>
</template>
