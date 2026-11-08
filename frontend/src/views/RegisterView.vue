<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { userRegister } from '@/api'

const router = useRouter()

const username = ref('')
const password = ref('')
const email = ref('')
const error = ref('')
const loading = ref(false)

async function handleSubmit() {
  error.value = ''
  if (!username.value || !password.value || !email.value) {
    error.value = 'Please fill in all fields'
    return
  }

  loading.value = true
  try {
    await userRegister({
      username: username.value,
      password: password.value,
      email: email.value,
    })
    router.push('/login')
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Registration failed'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card card">
      <h1>Register</h1>
      <form class="auth-form" @submit.prevent="handleSubmit">
        <div class="form-group">
          <label class="form-label" for="reg-username">Username</label>
          <input
            id="reg-username"
            v-model="username"
            class="form-input"
            type="text"
            placeholder="Enter username"
            autocomplete="username"
          />
        </div>
        <div class="form-group">
          <label class="form-label" for="reg-email">Email</label>
          <input
            id="reg-email"
            v-model="email"
            class="form-input"
            type="email"
            placeholder="Enter email"
            autocomplete="email"
          />
        </div>
        <div class="form-group">
          <label class="form-label" for="reg-password">Password</label>
          <input
            id="reg-password"
            v-model="password"
            class="form-input"
            type="password"
            placeholder="Enter password"
            autocomplete="new-password"
          />
        </div>
        <p v-if="error" class="error-msg">{{ error }}</p>
        <button type="submit" class="btn btn-primary" :disabled="loading">
          {{ loading ? 'Registering...' : 'Register' }}
        </button>
      </form>
      <p class="auth-footer text-muted">
        Already have an account?
        <router-link to="/login">Login</router-link>
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
