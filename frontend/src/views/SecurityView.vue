<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { userSecurity, userPasswordChange, userLogoutAll, userEmailVerification } from '@/api'
import { useAuth } from '@/composables/useAuth'
import BlogIcon from '@/components/BlogIcon.vue'

const router = useRouter()
const { token, currentUser, logout } = useAuth()
const status = ref(null)
const error = ref('')
const busy = ref('')
const change = ref({ currentPassword: '', newPassword: '', confirmPassword: '' })
const email = ref({ email: '', currentPassword: '' })
const endPassword = ref('')
const changeError = ref('')
const emailError = ref('')
const endError = ref('')
const emailMessage = ref('')
const formatBytes = value => Number(value) < 1024 ? `${value} B` : Number(value) < 1048576 ? `${(value / 1024).toFixed(1)} KiB` : `${(value / 1048576).toFixed(1)} MiB`
onMounted(async () => {
  try {
    status.value = (await userSecurity()).data.data
    email.value.email = status.value.verifiedEmail || currentUser.value?.email || ''
  } catch (failure) { error.value = failure.response?.data?.message || '账号信息暂时无法加载，请刷新重试。' }
})
function finish(notice, previousToken) {
  if (token.value !== previousToken) return
  logout()
  router.replace('/login?notice=' + notice)
}
async function changePassword() {
  if (busy.value) return
  changeError.value = ''
  if (change.value.newPassword !== change.value.confirmPassword) { changeError.value = '两次输入的新密码不一致。'; return }
  if (change.value.newPassword.length < 8 || new TextEncoder().encode(change.value.newPassword).length > 72) { changeError.value = '新密码至少 8 个字符，且不超过 72 个 UTF-8 字节。'; return }
  busy.value = 'password'
  const previousToken = token.value
  try {
    await userPasswordChange({ currentPassword: change.value.currentPassword, newPassword: change.value.newPassword })
    change.value = { currentPassword: '', newPassword: '', confirmPassword: '' }
    finish('password-changed', previousToken)
  } catch (failure) { changeError.value = failure.response?.data?.message || '修改未完成，请稍后重试。' }
  finally { busy.value = '' }
}
async function verifyEmail() {
  if (busy.value) return
  busy.value = 'email'; emailError.value = ''; emailMessage.value = ''
  try {
    await userEmailVerification({ email: email.value.email.trim(), currentPassword: email.value.currentPassword })
    email.value.currentPassword = ''
    emailMessage.value = '验证请求已受理，请检查邮箱。链接 30 分钟内有效；确认后需要重新登录。'
  } catch (failure) { emailError.value = failure.response?.data?.message || '验证邮件暂未发送，请稍后重试。' }
  finally { busy.value = '' }
}
async function endAll() {
  if (busy.value) return
  busy.value = 'sessions'; endError.value = ''
  const previousToken = token.value
  try { await userLogoutAll({ currentPassword: endPassword.value }); endPassword.value = ''; finish('all-sessions-ended', previousToken) }
  catch (failure) { endError.value = failure.response?.data?.message || '操作未完成，请稍后重试。' }
  finally { busy.value = '' }
}
</script>

<template>
  <section class="security-page page-enter">
    <header class="page-heading"><p class="eyebrow">KEEP YOUR PAGES SAFE</p><h1>账号安全</h1><p class="page-description">照看好钥匙，也照看好只属于你的书页。</p></header>
    <p v-if="error" class="error-msg" role="alert">{{ error }}</p>
    <p v-else-if="!status" class="text-muted" role="status">正在查看账号信息…</p>
    <div v-else class="security-grid">
      <section class="security-card security-storage" aria-labelledby="storage-heading">
        <div class="security-card-heading"><div><p class="eyebrow">01 / YOUR SHELF</p><h2 id="storage-heading">我的书架</h2></div><BlogIcon name="folder" /></div>
        <p class="storage-amount">{{ formatBytes(status.storage.usedBytes) }} <span>/ {{ formatBytes(status.maxStorageBytes) }}</span></p>
        <meter :value="status.storage.usedBytes" :max="status.maxStorageBytes" min="0" aria-label="已用存储空间" />
        <dl class="storage-counts"><div><dt>日记</dt><dd>{{ status.storage.articles }} / {{ status.maxArticles }}</dd></div><div><dt>补记</dt><dd>{{ status.storage.comments }} / {{ status.maxComments }}</dd></div><div><dt>分类</dt><dd>{{ status.storage.categories }} / {{ status.maxCategories }}</dd></div></dl>
        <p class="form-hint">按标题、正文、补记及分类的 UTF-8 字节统计。删除内容会释放空间。</p>
      </section>
      <section v-if="status.mailEnabled" class="security-card" aria-labelledby="email-heading">
        <div class="security-card-heading"><div><p class="eyebrow">02 / A WAY BACK</p><h2 id="email-heading">验证邮箱</h2></div><span class="security-badge">{{ status.emailVerified ? '已验证' : '待验证' }}</span></div>
        <p class="security-description">用于找回密码。{{ status.verifiedEmailRequiredForPublication ? '发布广场或修改已公开正文前，也需要验证邮箱。' : '未验证的邮箱不能用于重置密码。' }}</p>
        <p v-if="status.verifiedEmail" class="verified-address">{{ status.verifiedEmail }}</p>
        <form class="account-form" @submit.prevent="verifyEmail">
          <div class="form-group"><label for="security-email" class="form-label">验证邮箱地址</label><input id="security-email" v-model="email.email" class="form-input" type="email" maxlength="254" autocomplete="email" required :disabled="!!busy" /></div>
          <div class="form-group"><label for="email-current-password" class="form-label">验证邮箱时的当前密码</label><input id="email-current-password" v-model="email.currentPassword" class="form-input" type="password" autocomplete="current-password" required :disabled="!!busy" /></div>
          <p v-if="emailError" class="error-msg" role="alert">{{ emailError }}</p><p v-if="emailMessage" class="account-notice" role="status">{{ emailMessage }}</p>
          <button class="btn" :disabled="!!busy">{{ busy === 'email' ? '正在申请…' : '发送验证邮件' }}</button>
        </form>
      </section>
      <section class="security-card" aria-labelledby="password-heading">
        <div class="security-card-heading"><div><p class="eyebrow">{{ status.mailEnabled ? '03' : '02' }} / A NEW KEY</p><h2 id="password-heading">修改密码</h2></div><BlogIcon name="lock" /></div>
        <p class="security-description">修改成功后，所有设备都会退出。请用新密码重新登录。</p>
        <form class="account-form" @submit.prevent="changePassword">
          <div class="form-group"><label for="change-current-password" class="form-label">当前密码</label><input id="change-current-password" v-model="change.currentPassword" type="password" class="form-input" autocomplete="current-password" required :disabled="!!busy" /></div>
          <div class="form-group"><label for="change-new-password" class="form-label">新密码</label><input id="change-new-password" v-model="change.newPassword" type="password" class="form-input" autocomplete="new-password" minlength="8" required :disabled="!!busy" /><p class="form-hint">至少 8 个字符，不超过 72 个 UTF-8 字节。</p></div>
          <div class="form-group"><label for="change-confirm-password" class="form-label">再次输入新密码</label><input id="change-confirm-password" v-model="change.confirmPassword" type="password" class="form-input" autocomplete="new-password" minlength="8" required :disabled="!!busy" /></div>
          <p v-if="changeError" class="error-msg" role="alert">{{ changeError }}</p>
          <button class="btn btn-primary" :disabled="!!busy">{{ busy === 'password' ? '正在修改…' : '修改密码并退出所有设备' }}</button>
        </form>
      </section>
      <section class="security-card" aria-labelledby="sessions-heading">
        <div class="security-card-heading"><div><p class="eyebrow">{{ status.mailEnabled ? '04' : '03' }} / CLOSE THE DOOR</p><h2 id="sessions-heading">登录会话</h2></div><BlogIcon name="logout" /></div>
        <p class="security-description">当前有 {{ status.activeSessions }} 个有效登录会话。如果在公共设备上忘记退出，可以在这里结束所有会话。</p>
        <form class="account-form" @submit.prevent="endAll">
          <div class="form-group"><label for="end-all-password" class="form-label">退出所有设备时的当前密码</label><input id="end-all-password" v-model="endPassword" type="password" class="form-input" autocomplete="current-password" required :disabled="!!busy" /></div>
          <p v-if="endError" class="error-msg" role="alert">{{ endError }}</p><button class="btn btn-danger" :disabled="!!busy">{{ busy === 'sessions' ? '正在退出…' : '退出所有设备' }}</button>
        </form>
      </section>
    </div>
  </section>
</template>
