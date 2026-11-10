<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { securityOptions, userEmailConfirm, userForgotPassword, userResetPassword } from '@/api'
import { useAuth } from '@/composables/useAuth'
const route = useRoute()
const { clearAccount } = useAuth()
const mode = computed(() => route.name)
const title = computed(() => ({ 'forgot-password': '找回密码', 'reset-password': '设置新密码', 'verify-email': '验证邮箱' })[mode.value])
const email = ref('')
const newPassword = ref('')
const confirmation = ref('')
const secret = ref('')
const busy = ref(false)
const loading = ref(true)
const enabled = ref(false)
const error = ref('')
const message = ref('')
const done = ref(false)
onMounted(async () => {
  if (mode.value !== 'forgot-password') {
    secret.value = new URLSearchParams(route.hash.slice(1)).get('token') || ''
    // Remove the one-time credential from the visible URL and browser history entry.
    window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search)
  }
  try {
    enabled.value = (await securityOptions()).data.data.mailEnabled
    if (enabled.value && mode.value !== 'forgot-password' && !/^[A-Za-z0-9_-]{43}$/.test(secret.value)) {
      enabled.value = false
      error.value = '链接无效，请从邮件重新打开，或重新申请。'
    }
  } catch { error.value = '服务暂时不可用，请稍后重试。' }
  finally { loading.value = false }
})
async function submit() {
  if (busy.value || !enabled.value || done.value) return
  error.value = ''; message.value = ''
  if (mode.value === 'reset-password') {
    if (newPassword.value !== confirmation.value) { error.value = '两次输入的新密码不一致。'; return }
    if (newPassword.value.length < 8 || new TextEncoder().encode(newPassword.value).length > 72) { error.value = '密码至少 8 个字符，且不超过 72 个 UTF-8 字节。'; return }
  }
  busy.value = true
  try {
    if (mode.value === 'forgot-password') {
      await userForgotPassword(email.value.trim())
      message.value = '如果该邮箱已验证，将收到重置链接。请检查收件箱及垃圾邮件。'
    } else if (mode.value === 'reset-password') {
      const result = await userResetPassword({ token: secret.value, newPassword: newPassword.value })
      clearAccount(result.data.data)
      message.value = '密码已重置，所有设备已退出。请用新密码登录。'
      secret.value = ''; newPassword.value = ''; confirmation.value = ''; done.value = true
    } else {
      const result = await userEmailConfirm(secret.value)
      clearAccount(result.data.data)
      message.value = '邮箱已验证，所有设备已退出。请重新登录。'
      secret.value = ''; done.value = true
    }
  } catch (failure) { error.value = failure.response?.data?.message || '操作暂未完成，请稍后重试。' }
  finally { busy.value = false }
}
</script>

<template>
  <section class="recovery-page page-enter">
    <p class="eyebrow">A WAY BACK TO YOUR PAGES</p><h1>{{ title }}</h1>
    <p class="page-description">{{ mode === 'forgot-password' ? '通过已验证的邮箱，找回回到书桌前的钥匙。' : '这一步，只为照看好你的账号。' }}</p>
    <div class="security-card recovery-card">
      <p v-if="message" class="account-notice" role="status">{{ message }}</p>
      <p v-if="error" class="error-msg" role="alert">{{ error }}</p>
      <p v-if="!enabled && !error" class="text-muted" role="status">{{ loading ? '正在查看服务状态…' : '邮箱验证与邮件找回密码暂未启用。' }}</p>
      <form v-if="!done && enabled" class="account-form" @submit.prevent="submit">
        <div v-if="mode === 'forgot-password'" class="form-group"><label for="recovery-email" class="form-label">已验证的邮箱</label><input id="recovery-email" v-model="email" type="email" class="form-input" autocomplete="email" maxlength="254" required :disabled="busy || !enabled" /></div>
        <template v-if="mode === 'reset-password'">
          <div class="form-group"><label for="recovery-password" class="form-label">新密码</label><input id="recovery-password" v-model="newPassword" type="password" class="form-input" autocomplete="new-password" minlength="8" required :disabled="busy || !enabled" /></div>
          <div class="form-group"><label for="recovery-confirm" class="form-label">再次输入新密码</label><input id="recovery-confirm" v-model="confirmation" type="password" class="form-input" autocomplete="new-password" minlength="8" required :disabled="busy || !enabled" /></div>
        </template>
        <p v-if="mode === 'verify-email'" class="security-description">确认后，该邮箱将用于账号恢复。请仅确认由你本人申请的验证邮件。</p>
        <button class="btn btn-primary" :disabled="busy || !enabled">{{ busy ? '正在处理…' : mode === 'forgot-password' ? '发送重置邮件' : mode === 'reset-password' ? '重置密码' : '确认验证邮箱' }}</button>
      </form>
      <router-link to="/login" class="text-link recovery-back">返回登录</router-link>
    </div>
  </section>
</template>
