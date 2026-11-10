<script setup>
import Navbar from './components/Navbar.vue'
import { useAuth } from '@/composables/useAuth'
const { isLoggedIn, token } = useAuth()
</script>

<template>
  <Navbar />
  <main id="main-content" class="page-shell site-main">
    <router-view v-slot="{ Component, route }"><component :is="Component" v-if="!route.meta.requiresAuth || isLoggedIn" :key="route.fullPath + (route.meta.requiresAuth ? token : '')" /></router-view>
  </main>
  <footer class="page-shell site-footer">
    <router-link to="/" class="footer-brand">行间 <span>/ 只属于你的日记本</span></router-link>
    <span>文字是时间的书签。<span class="footer-year">© {{ new Date().getFullYear() }}</span></span>
  </footer>
</template>

<style scoped>
.site-footer { border-top: 1px solid var(--border); padding-block: 25px 30px; display: flex; align-items: center; justify-content: space-between; gap: 14px; color: var(--text-muted); font-size: .7rem; }
.footer-brand { color: var(--text); font-family: var(--serif); font-size: .85rem; }
.footer-brand span { font-family: var(--sans); font-size: .7rem; margin-left: 6px; color: var(--text-muted); }
.footer-year { margin-left: 16px; font-family: var(--mono); }
@media (max-width: 600px) { .site-footer { flex-direction: column; align-items: flex-start; gap: 9px; } }
</style>
