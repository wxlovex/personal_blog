<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { plazaList, plazaCategories } from '@/api'
import { formatDate, utcTimestamp } from '@/utils/time'
import BlogIcon from '@/components/BlogIcon.vue'
const route = useRoute()
const router = useRouter()
const keyword = typeof route.query.keyword === 'string' ? route.query.keyword.slice(0, 100) : ''
const search = ref(keyword)
const category = ref(typeof route.query.category === 'string' ? route.query.category : '')
const currentPage = ref(Math.max(1, Number.parseInt(route.query.page, 10) || 1))
const categories = ref([])
const articles = ref([])
const total = ref(0)
const pages = ref(0)
const loading = ref(true)
const error = ref('')
const categoryError = ref('')
const filtered = computed(() => !!keyword || !!category.value)
let requestId = 0
function query(page = 1, applySearch = false) {
  const term = applySearch ? search.value.trim() : keyword
  return { ...(term && { keyword: term }), ...(category.value && { category: category.value }), ...(page > 1 && { page }) }
}
async function fetchArticles() {
  const id = ++requestId
  loading.value = true; error.value = ''
  try {
    const { data } = await plazaList(currentPage.value, 10, { keyword, category: category.value })
    if (id !== requestId) return
    if (currentPage.value > Math.max(1, data.data.pages)) { navigate(Math.max(1, data.data.pages)); return }
    articles.value = data.data.records; total.value = data.data.total; pages.value = data.data.pages
  } catch (e) { if (id === requestId) error.value = e.response?.data?.message || '广场暂时没有打开，请稍后重试。' }
  finally { if (id === requestId) loading.value = false }
}
async function loadCategories() {
  categoryError.value = ''
  try { categories.value = (await plazaCategories()).data.data }
  catch { categoryError.value = '广场分类加载失败，请重试。' }
}
function navigate(page = 1, applySearch = false) {
  const target = { path: '/plaza', query: query(page, applySearch) }
  if (router.resolve(target).fullPath === route.fullPath) { currentPage.value = page; fetchArticles() }
  else router.replace(target)
}
function clearFilters() { search.value = ''; category.value = ''; navigate(1, true) }
function chooseCategory(name) { category.value = name; navigate(1, true) }
onMounted(() => { fetchArticles(); loadCategories() })
onBeforeUnmount(() => { requestId++ })
</script>
<template>
  <div class="plaza-page page-enter">
    <header class="plaza-intro"><div><p class="eyebrow">PAGES WE CHOOSE TO SHARE</p><h1>在行间，<br>遇见别人的一页。</h1><p class="page-description">技术里的发现，日常里的微光。<br>这里只收录作者主动公开的文字。</p></div><div class="plaza-mark" aria-hidden="true"><BlogIcon name="globe" /><span>文字相遇的地方</span></div></header>
    <div class="plaza-toolbar"><form class="plaza-search" role="search" @submit.prevent="navigate(1, true)"><label for="plaza-search" class="sr-only">搜索广场文章标题</label><input id="plaza-search" v-model="search" class="form-input" type="search" maxlength="100" placeholder="搜索广场文章标题" /><button class="btn" type="submit">搜索</button></form><router-link to="/" class="text-link">管理我的文章<BlogIcon name="arrow-right" /></router-link></div>
    <nav class="category-tabs" aria-label="广场分类"><button class="category-tab" :class="{ active: !category }" :aria-pressed="!category" @click="chooseCategory('')">全部</button><button v-for="name in categories" :key="name" class="category-tab" :class="{ active: category === name }" :aria-pressed="category === name" @click="chooseCategory(name)">{{ name }}</button></nav>
    <p v-if="categoryError" class="form-hint" role="alert">{{ categoryError }} <button class="btn btn-sm" @click="loadCategories">重试分类</button></p>
    <section aria-labelledby="plaza-heading" :aria-busy="loading">
      <div class="section-label"><h2 id="plaza-heading">{{ category || '广场文章' }}</h2><span v-if="!loading && !error" aria-live="polite">{{ total }} 篇公开文章 · 最新发布在前</span></div>
      <p v-if="loading" class="status-panel text-muted" role="status">正在翻阅广场…</p>
      <div v-else-if="error" class="status-panel" role="alert"><BlogIcon name="alert" /><h2>广场暂时无法打开</h2><p>{{ error }}</p><button class="btn" @click="fetchArticles">重新加载</button></div>
      <div v-else-if="!articles.length" class="status-panel"><BlogIcon name="book" /><h2>{{ filtered ? '没有找到符合条件的文章' : '广场正等着第一篇文字' }}</h2><p>{{ filtered ? '换个标题关键词或分类，再找找看。' : '你可以在自己的日记旁点击“发布到广场”，让文字与更多人相遇。' }}</p><button v-if="filtered" class="btn" @click="clearFilters">查看全部文章</button><router-link v-else to="/" class="btn">回到我的日记</router-link></div>
      <div v-else class="plaza-articles"><article v-for="article in articles" :key="article.id" class="plaza-card"><div class="plaza-card-top"><span class="public-category">{{ article.category }}</span><span class="public-author">{{ article.authorName }}</span></div><router-link :to="{ path: `/plaza/${article.id}`, query: route.query }" class="public-title"><h2>{{ article.title }}<BlogIcon name="arrow-up-right" /></h2></router-link><p class="public-summary">{{ article.summary }}</p><time :datetime="utcTimestamp(article.publishedTime)">{{ formatDate(article.publishedTime) }} 发布</time></article></div>
      <nav v-if="!loading && !error && pages > 1" class="plaza-pagination" aria-label="广场分页"><button class="btn btn-sm" :disabled="currentPage <= 1" @click="navigate(currentPage - 1)">上一页</button><span>第 {{ currentPage }} / {{ pages }} 页</span><button class="btn btn-sm" :disabled="currentPage >= pages" @click="navigate(currentPage + 1)">下一页</button></nav>
    </section>
    <p class="plaza-footnote"><BlogIcon name="lock" />私密文章不会出现在这里。分享与否，由作者自己决定。</p>
  </div>
</template>
<style scoped>
.plaza-intro { display: flex; align-items: center; justify-content: space-between; gap: 30px; padding-block: 20px 45px; }
.plaza-intro h1 { margin-top: 18px; line-height: 1.65; }
.plaza-mark { display: flex; align-items: center; gap: 18px; writing-mode: vertical-rl; color: var(--accent); font: .8rem/1.8 var(--serif); letter-spacing: .15em; padding-inline: 20px; }
.plaza-mark .icon { width: 48px; height: 48px; stroke-width: .8; }
.plaza-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 20px; justify-content: space-between; }
.plaza-search { display: flex; flex: 0 1 500px; gap: 10px; }
.plaza-search input { width: 0; flex: 1; font-size: .875rem; }
.plaza-search .btn { flex-shrink: 0; white-space: nowrap; }
.category-tabs { display: flex; flex-wrap: wrap; gap: 8px; padding-block: 24px 30px; }
.category-tab { border: 1px solid transparent; border-radius: 4px; padding: 7px 18px; background: transparent; color: var(--text-muted); font-size: .85rem; }
.category-tab.active { background: var(--accent); color: var(--on-accent); }
.category-tab:hover:not(.active) { background: var(--accent-bg); color: var(--accent); }
.plaza-card { padding-block: 28px; border-bottom: 1px solid var(--border); }
.plaza-card-top { display: flex; gap: 18px; align-items: center; margin-bottom: 10px; font-size: .75rem; }
.public-category { color: var(--accent); }
.public-author { color: var(--text-muted); overflow-wrap: anywhere; }
.public-title { color: var(--text-h); }
.public-title h2 { display: flex; align-items: baseline; gap: 12px; font: 600 1.35rem/1.7 var(--serif); }
.public-title .icon { width: 15px; height: 15px; color: var(--accent); flex-shrink: 0; }
.public-summary { margin-block: 12px 16px; color: var(--text-muted); font-size: .875rem; line-height: 1.9; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; overflow-wrap: anywhere; }
.plaza-card time { color: var(--text-muted); font: .7rem/1.5 var(--mono); }
.plaza-pagination { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 28px; }
.plaza-pagination span { color: var(--text-muted); font-size: .8rem; }
.plaza-footnote { display: flex; align-items: flex-start; gap: 8px; color: var(--text-muted); font-size: .75rem; margin-top: 36px; }
.plaza-footnote .icon { flex-shrink: 0; width: 14px; margin-top: 2px; }
@media (max-width: 600px) { .plaza-intro { padding-block: 5px 32px; } .plaza-mark { display: none; } .plaza-search { flex-basis: 100%; } .category-tabs { gap: 4px; } .category-tab { padding: 8px 13px; } .public-title h2 { font-size: 1.2rem; } }
</style>
