<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { userRegister } from '@/api'
import BlogIcon from '@/components/BlogIcon.vue'
const router = useRouter()
const username = ref('')
const password = ref('')
const email = ref('')
const error = ref('')
const loading = ref(false)
async function handleSubmit() {
  if (loading.value) return
  error.value = ''
  if (!username.value.trim() || !password.value) { error.value = '请填写用户名和密码。'; return }
  if (password.value.length < 8 || new TextEncoder().encode(password.value).length > 72) { error.value = '密码至少 8 个字符，且不超过 72 个 UTF-8 字节。'; return }
  loading.value = true
  try {
    await userRegister({ username: username.value.trim(), password: password.value, email: email.value.trim() || undefined })
    router.push('/login')
  } catch (e) { error.value = e.response?.data?.message || '注册暂未成功，请稍后重试。' }
  finally { loading.value = false }
}
</script>
<template>
  <div class="auth-page page-enter">
    <section class="auth-intro"><p class="eyebrow">YOUR OWN PRIVATE JOURNAL</p><h1>留一个角落，<br>先写给自己。</h1><p>创建账号，拥有独立的日记本。记录默认私密，只有你主动发布的文章才会出现在广场。</p><span class="auth-note">不必等到准备好，<br>第一行字，就是开始。</span></section>
    <section class="auth-card" aria-labelledby="register-heading">
      <h2 id="register-heading">加入这间书房</h2><p class="auth-subtitle">从一个属于你的账号开始。</p>
      <form class="auth-form" @submit.prevent="handleSubmit">
        <div class="form-group"><label class="form-label" for="reg-username">用户名</label><input id="reg-username" v-model="username" class="form-input" type="text" placeholder="给自己取个名字" autocomplete="username" minlength="3" maxlength="32" required :disabled="loading" aria-describedby="username-hint" /><p id="username-hint" class="form-hint">3–32 位字母、数字、下划线或短横线</p></div>
        <div class="form-group"><label class="form-label" for="reg-email">邮箱<span class="optional">选填</span></label><input id="reg-email" v-model="email" class="form-input" type="email" placeholder="you@example.com" autocomplete="email" :disabled="loading" /><p class="form-hint">选填，不影响注册和登录；请妥善保存密码。</p></div>
        <div class="form-group"><label class="form-label" for="reg-password">密码</label><input id="reg-password" v-model="password" class="form-input" type="password" placeholder="设置一个安全的密码" autocomplete="new-password" minlength="8" required :disabled="loading" aria-describedby="password-hint" /><p id="password-hint" class="form-hint">至少 8 个字符</p></div>
        <p v-if="error" class="error-msg" role="alert">{{ error }}</p>
        <button type="submit" class="btn btn-primary" :disabled="loading">{{ loading ? '正在创建账号…' : '开启第一章' }}<BlogIcon v-if="!loading" name="arrow-right" /></button>
      </form>
      <p class="auth-footer">已经有账号？<router-link to="/login">去登录</router-link></p>
    </section>
  </div>
</template>
