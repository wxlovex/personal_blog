<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { userLogin } from '@/api'
import { useAuth } from '@/composables/useAuth'

const router = useRouter()
const { login } = useAuth()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function handleSubmit() {
  error.value = ''
  if (!username.value || !password.value) {
    error.value = 'Please fill in all fields'
    return
  }

  loading.value = true
  try {
    const res = await userLogin({
      username: username.value,
      password: password.value,
    })
    // Backend returns Result<T> wrapper: { code, message, data }
    login(res.data.data)
    router.push('/')
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Login failed'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card card">
      <h1>Login</h1>
      <form class="auth-form" @submit.prevent="handleSubmit">
        <div class="form-group">
          <label class="form-label" for="login-username">Username</label>
          <input
            id="login-username"
            v-model="username"
            class="form-input"
            type="text"
            placeholder="Enter username"
            autocomplete="username"
          />
        </div>
        <div class="form-group">
          <label class="form-label" for="login-password">Password</label>
          <input
            id="login-password"
            v-model="password"
            class="form-input"
            type="password"
            placeholder="Enter password"
            autocomplete="current-password"
          />
        </div>
        <p v-if="error" class="error-msg">{{ error }}</p>
        <button type="submit" class="btn btn-primary" :disabled="loading">
          {{ loading ? 'Logging in...' : 'Login' }}
        </button>
      </form>
      <p class="auth-footer text-muted">
        Don't have an account?
        <router-link to="/register">Register</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  padding-top: 40px;
}
.auth-card {
  width: 100%;
  max-width: 400px;
}
.auth-card h1 {
  text-align: center;
  margin-bottom: 24px;
}
.auth-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.auth-form .btn {
  margin-top: 4px;
}
.auth-footer {
  text-align: center;
  margin-top: 20px;
}
</style>
