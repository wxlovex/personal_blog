<script setup>
import { ref, computed, watch } from 'vue'
import { articleList, articleDelete, categoryList } from '@/api'
import { useAuth } from '@/composables/useAuth'
import ArticleCard from '@/components/ArticleCard.vue'
import BlogIcon from '@/components/BlogIcon.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import CategoryManager from '@/components/CategoryManager.vue'
import ArticleCategoryDialog from '@/components/ArticleCategoryDialog.vue'
import ArticlePublicationDialog from '@/components/ArticlePublicationDialog.vue'

const { token } = useAuth()
const articles = ref([])
const loading = ref(true)
const error = ref('')
const currentPage = ref(1)
const total = ref(0)
const totalPages = ref(0)
const listHeading = ref(null)
const articleToDelete = ref(null)
const articleToCategorize = ref(null)
const articleToPublish = ref(null)
const deleting = ref(false)
const deleteError = ref('')
const searchInput = ref('')
const keyword = ref('')
const selectedCategory = ref('')
const categories = ref([])
const categoryError = ref('')
const showCategories = ref(false)
const filtered = computed(() => !!keyword.value || !!selectedCategory.value)
let requestId = 0
let categoryRequestId = 0
const visiblePages = computed(() => {
  const pages = totalPages.value
  if (pages <= 7) return Array.from({ length: pages }, (_, i) => i + 1)
  const cur = currentPage.value
  if (cur <= 4) return [1, 2, 3, 4, 5, '…', pages]
  if (cur >= pages - 3) return [1, '…', pages - 4, pages - 3, pages - 2, pages - 1, pages]
  return [1, '…', cur - 1, cur, cur + 1, '…', pages]
})

async function fetchArticles() {
  const id = ++requestId
  loading.value = true
  error.value = ''
  try {
    const { data } = await articleList(currentPage.value, 10, { keyword: keyword.value, categoryId: selectedCategory.value })
    if (id !== requestId) return
    if (currentPage.value > Math.max(1, data.data.pages)) {
      currentPage.value = Math.max(1, data.data.pages)
      return fetchArticles()
    }
    articles.value = data.data.records
    total.value = data.data.total
    totalPages.value = data.data.pages
  } catch (e) {
    if (id === requestId) error.value = e.response?.data?.message || '暂时没能取回日记，请稍后再试。'
  } finally {
    if (id === requestId) loading.value = false
  }
}
async function fetchCategories() {
  const id = ++categoryRequestId
  categoryError.value = ''
  try {
    const { data } = await categoryList()
    if (id !== categoryRequestId) return
    categories.value = data.data
    if (selectedCategory.value && !categories.value.some(item => String(item.id) === selectedCategory.value)) {
      selectedCategory.value = ''
      applyFilters()
    }
  } catch (e) { if (id === categoryRequestId) categoryError.value = e.response?.data?.message || '暂时没能取回分类。' }
}
function applyFilters() {
  keyword.value = searchInput.value.trim()
  currentPage.value = 1
  fetchArticles()
}
function clearFilters() { searchInput.value = ''; selectedCategory.value = ''; applyFilters() }
function categoriesChanged() { fetchCategories(); fetchArticles() }
function articleCategorySaved() {
  articleToCategorize.value = null
  fetchArticles()
  fetchCategories()
}
function publicationSaved() { articleToPublish.value = null; fetchArticles() }
function goToPage(page) {
  if (typeof page !== 'number' || page < 1 || page > totalPages.value || page === currentPage.value || loading.value) return
  currentPage.value = page
  fetchArticles()
  listHeading.value?.scrollIntoView({ block: 'start', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' })
}
function confirmDelete(article) { articleToDelete.value = article; deleteError.value = '' }
async function handleDelete() {
  if (deleting.value || !articleToDelete.value) return
  deleting.value = true
  try {
    await articleDelete(articleToDelete.value.id)
    articleToDelete.value = null
    if (articles.value.length === 1 && currentPage.value > 1) currentPage.value--
    await fetchArticles()
    fetchCategories()
  } catch (e) { deleteError.value = e.response?.data?.message || '删除失败，请稍后再试。' }
  finally { deleting.value = false }
}
watch(token, () => {
  requestId++; categoryRequestId++
  currentPage.value = 1; articles.value = []; categories.value = []
  articleToCategorize.value = null
  articleToPublish.value = null
  searchInput.value = ''; keyword.value = ''; selectedCategory.value = ''; showCategories.value = false
  if (token.value) { fetchArticles(); fetchCategories() }
}, { immediate: true })
</script>

<template>
  <div class="home-page page-enter">
    <section class="home-intro" aria-labelledby="home-title">
      <div>
        <p class="eyebrow">A LITTLE SPACE FOR MYSELF</p>
        <h1 id="home-title">写给自己，<br>不必向谁交代。</h1>
        <p class="home-description">日常、念头、没想明白的事，都可以留在这里。<br class="desktop-break">记录默认私密，愿意分享时，再把这一页带到广场。</p>
        <p class="privacy-note"><BlogIcon name="lock" />默认私密 · 由你决定分享</p>
      </div>
      <div class="intro-aside" aria-hidden="true"><span class="aside-line"></span><span>此刻 · 日常 · 自己</span><span class="aside-note">JUST FOR YOU.</span></div>
    </section>

    <section aria-labelledby="recent-heading" :aria-busy="loading">
      <div ref="listHeading" class="section-label list-heading">
        <h2 id="recent-heading">我的日记<span class="heading-dot" aria-hidden="true"></span></h2>
        <span v-if="!loading && !error" aria-live="polite">{{ filtered ? `找到 ${total} 篇记录` : total ? `共 ${total} 篇记录` : '每一次记录，都是新的开始' }} · 最新在前</span>
      </div>
      <div class="journal-tools">
        <form class="search-form" role="search" @submit.prevent="applyFilters">
          <label for="title-search" class="sr-only">按标题搜索我的日记</label>
          <input id="title-search" v-model="searchInput" class="form-input" type="search" maxlength="100" placeholder="搜索日记标题" />
          <button class="btn" type="submit">搜索</button>
        </form>
        <label class="category-filter"><span class="sr-only">按分类筛选</span><select v-model="selectedCategory" class="form-input" @change="applyFilters"><option value="">全部分类</option><option v-for="category in categories" :key="category.id" :value="String(category.id)">{{ category.name }}（{{ category.articleCount || 0 }}）</option></select></label>
        <button class="btn btn-quiet" :aria-expanded="showCategories" aria-controls="category-panel" @click="showCategories = !showCategories">{{ showCategories ? '收起分类' : '管理分类' }}</button>
        <button v-if="filtered" class="btn btn-quiet" @click="clearFilters">清除筛选</button>
      </div>
      <p v-if="categoryError" class="category-error form-hint" role="alert">{{ categoryError }} <button class="btn btn-sm" @click="fetchCategories">重试分类</button></p>
      <CategoryManager v-if="showCategories" id="category-panel" :categories="categories" @changed="categoriesChanged" />
      <div v-if="loading" class="article-skeletons" role="status" aria-label="正在加载日记">
        <div v-for="n in 3" :key="n" class="article-skeleton" aria-hidden="true"><span></span><span></span><span></span></div>
        <span class="sr-only">正在加载日记…</span>
      </div>
      <div v-else-if="error" class="status-panel" role="alert"><BlogIcon name="alert" /><h2>日记暂时没有取回</h2><p>{{ error }}</p><button class="btn" @click="fetchArticles">重新加载</button></div>
      <div v-else-if="!articles.length && filtered" class="status-panel"><BlogIcon name="book" /><h2>没有找到符合条件的日记</h2><p>试试其他标题关键词，或换一个分类。</p><button class="btn" @click="clearFilters">查看全部日记</button></div>
      <div v-else-if="!articles.length" class="status-panel"><BlogIcon name="book" /><h2>书页正等着第一行字</h2><p>不必取一个好标题，也不必写得完整。记下一句话，就是开始。</p><router-link to="/publish" class="btn btn-primary"><BlogIcon name="pen" />写下第一页</router-link></div>
      <div v-else class="article-list"><ArticleCard v-for="article in articles" :key="article.id" :article="article" @delete="confirmDelete(article)" @category="articleToCategorize = article" @publication="articleToPublish = article" /></div>
      <nav v-if="!loading && !error && totalPages > 1" class="pagination" aria-label="日记分页">
        <button class="btn btn-sm pagination-prev" :disabled="currentPage <= 1" aria-label="上一页" @click="goToPage(currentPage - 1)"><BlogIcon name="chevron-left" /><span>上一页</span></button>
        <div class="page-numbers"><template v-for="(page, index) in visiblePages" :key="index"><span v-if="page === '…'" class="page-ellipsis" aria-hidden="true">…</span><button v-else class="page-btn" :class="{ active: page === currentPage }" :aria-label="`第 ${page} 页`" :aria-current="page === currentPage ? 'page' : undefined" @click="goToPage(page)">{{ page }}</button></template></div>
        <button class="btn btn-sm pagination-next" :disabled="currentPage >= totalPages" aria-label="下一页" @click="goToPage(currentPage + 1)"><span>下一页</span><BlogIcon name="chevron-right" /></button>
      </nav>
    </section>
    <ArticleCategoryDialog :article="articleToCategorize" @cancel="articleToCategorize = null" @saved="articleCategorySaved" @categories-changed="fetchCategories" />
    <ArticlePublicationDialog :article="articleToPublish" @cancel="articleToPublish = null" @saved="publicationSaved" />
    <ConfirmDialog :open="!!articleToDelete" :busy="deleting" title="删除这篇日记？" :description="`《${articleToDelete?.title || ''}》及其补记将被删除，此操作无法撤销。`" :error="deleteError" @cancel="articleToDelete = null" @confirm="handleDelete" />
  </div>
</template>

<style scoped>
.home-intro { display: flex; align-items: center; justify-content: space-between; gap: 40px; padding-block: 19px 60px; }
.home-intro .eyebrow { margin-bottom: 21px; }
.home-intro h1 { font-size: clamp(2rem, 4.4vw, 3.05rem); line-height: 1.6; letter-spacing: .025em; }
.home-description { margin-top: 24px; color: var(--text-muted); font-size: .925rem; line-height: 1.95; }
.privacy-note { display: inline-flex; align-items: center; gap: 6px; margin-top: 18px; color: var(--accent); font-size: .75rem; }
.privacy-note .icon { width: 14px; height: 14px; }
.intro-aside { display: flex; align-items: center; gap: 14px; writing-mode: vertical-rl; font: .8rem/1.6 var(--serif); letter-spacing: .2em; color: var(--accent); padding-right: 25px; }
.aside-line { width: 1px; height: 50px; background: var(--accent); }
.aside-note { font: 9px/1.5 var(--mono); color: var(--text-muted); letter-spacing: .12em; }
.list-heading { scroll-margin-top: 25px; }
.list-heading h2 { display: flex; align-items: center; gap: 9px; }
.heading-dot { width: 5px; height: 5px; background: var(--accent); border-radius: 50%; }
.journal-tools { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; padding-block: 20px; }
.search-form { display: flex; flex: 1 1 260px; gap: 8px; }
.search-form .form-input { flex: 1; width: 0; }
.search-form .btn { flex-shrink: 0; white-space: nowrap; }
.search-form .form-input, .category-filter .form-input { font-size: .85rem; padding: 10px 12px; }
.category-filter { flex: 0 1 210px; min-width: 0; }
.category-error { margin-bottom: 20px; }
.article-skeleton { padding-block: 32px; border-bottom: 1px solid var(--border); display: flex; flex-direction: column; gap: 14px; }
.article-skeleton span { display: block; background: var(--bg-secondary); border-radius: 3px; height: 14px; width: 75%; }
.article-skeleton span:first-child { height: 24px; width: 55%; }
.article-skeleton span:last-child { width: 30%; height: 11px; }
.pagination { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 20px; padding-top: 30px; }
.page-numbers { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; }
.page-btn { display: inline-grid; place-items: center; min-width: 34px; height: 36px; border: 1px solid transparent; border-radius: 4px; font: .8rem/1 var(--mono); color: var(--text-muted); background: transparent; }
.page-btn:hover { background: var(--bg-secondary); color: var(--accent); }
.page-btn.active { color: var(--on-accent); background: var(--accent); }
.page-ellipsis { padding-inline: 5px; color: var(--text-muted); }
@media (max-width: 600px) {
  .home-intro { padding-block: 10px 40px; }
  .home-intro .eyebrow { font-size: 10px; margin-bottom: 18px; }
  .home-intro h1 { font-size: clamp(1.8rem, 6.8vw, 2.3rem); }
  .intro-aside { display: none; }
  .home-description { margin-top: 18px; font-size: .85rem; }
  .desktop-break { display: none; }
  .pagination { gap: 15px; }
  .page-numbers { order: 3; width: 100%; justify-content: center; }
  .search-form { flex-basis: 100%; }
  .category-filter { flex: 1; }
}
@media (pointer: coarse) { .page-btn { min-width: 40px; min-height: 44px; } }
</style>
