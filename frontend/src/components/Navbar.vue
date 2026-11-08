<script setup>
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '@/composables/useAuth'

const router = useRouter()
const route = useRoute()
const { isLoggedIn, logout } = useAuth()

function handleLogout() {
  logout()
  router.push('/login')
}
</script>

<template>
  <header class="navbar">
    <div class="navbar-inner">
      <router-link to="/" class="navbar-brand">📝 My Blog</router-link>

      <nav class="navbar-links">
        <template v-if="isLoggedIn">
          <router-link to="/publish" class="btn btn-primary btn-sm">Write</router-link>
          <button class="btn btn-secondary btn-sm" @click="handleLogout">Logout</button>
        </template>
        <template v-else>
          <router-link
            v-if="route.path !== '/login'"
            to="/login"
            class="btn btn-secondary btn-sm"
          >Login</router-link>
          <router-link
            v-if="route.path !== '/register'"
            to="/register"
            class="btn btn-primary btn-sm"
          >Register</router-link>
        </template>
      </nav>
    </div>
  </header>
</template>

<style scoped>
.navbar {
  border-bottom: 1px solid var(--border);
  background: var(--bg);
  position: sticky;
  top: 0;
  z-index: 100;
}
.navbar-inner {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
}
.navbar-brand {
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--text-h);
  text-decoration: none;
}
.navbar-brand:hover {
  color: var(--accent);
}
.navbar-links {
  display: flex;
  align-items: center;
  gap: 10px;
}
.btn-sm {
  padding: 5px 14px;
  font-size: 0.825rem;
}
</style>
