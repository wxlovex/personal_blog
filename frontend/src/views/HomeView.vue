<script setup>
import { ref, computed, onMounted } from 'vue'
import { articleList, articleDelete } from '@/api'
import ArticleCard from '@/components/ArticleCard.vue'

const articles = ref([])
const loading = ref(false)
const error = ref('')

const currentPage = ref(1)
const pageSize = 10
const total = ref(0)
const totalPages = ref(0)

const hasPrev = computed(() => currentPage.value > 1)
const hasNext = computed(() => currentPage.value < totalPages.value)

// Generate visible page numbers with ellipsis
const visiblePages = computed(() => {
  const pages = totalPages.value
  if (pages <= 7) {
    return Array.from({ length: pages }, (_, i) => i + 1)
  }
  const cur = currentPage.value
  if (cur <= 4) return [1, 2, 3, 4, 5, '...', pages]
  if (cur >= pages - 3) return [1, '...', pages - 4, pages - 3, pages - 2, pages - 1, pages]
  return [1, '...', cur - 1, cur, cur + 1, '...', pages]
})

async function fetchArticles() {
  loading.value = true
  error.value = ''
  try {
    const res = await articleList(currentPage.value, pageSize)
    const page = res.data.data
    articles.value = page.records
    total.value = page.total
    totalPages.value = page.pages
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Failed to load articles'
  } finally {
    loading.value = false
  }
}

function goToPage(p) {
  if (p === '...') return
  currentPage.value = p
  fetchArticles()
}

function prevPage() {
  if (hasPrev.value) {
    currentPage.value--
    fetchArticles()
  }
}

function nextPage() {
  if (hasNext.value) {
    currentPage.value++
    fetchArticles()
  }
}

async function handleDelete(id) {
  if (!confirm('Are you sure you want to delete this article?')) return
  try {
    await articleDelete(id)
    // If it was the last item on this page (and not the first page), go back one
    if (articles.value.length === 1 && hasPrev.value) {
      currentPage.value--
    }
    fetchArticles()
  } catch (e) {
    alert(e.response?.data?.message || e.message || 'Failed to delete')
  }
}

onMounted(fetchArticles)
</script>

<template>
  <div class="home-page">
    <h1>Articles</h1>

    <div v-if="loading" class="loading">Loading...</div>
    <p v-if="error" class="error-msg">{{ error }}</p>

    <div v-if="!loading && !error && articles.length === 0" class="empty text-muted">
      No articles yet. Be the first to <router-link to="/publish">write one</router-link>!
    </div>

    <div v-if="articles.length > 0" class="article-list">
      <ArticleCard
        v-for="article in articles"
        :key="article.id"
        :article="article"
        @delete="handleDelete"
      />
    </div>

    <div v-if="totalPages.value > 1" class="pagination">
      <button class="btn btn-secondary" :disabled="!hasPrev" @click="prevPage">
        ← Previous
      </button>

      <button
        v-for="p in visiblePages"
        :key="p"
        class="btn page-btn"
        :class="{ active: p === currentPage, ellipsis: p === '...' }"
        :disabled="p === '...'"
        @click="goToPage(p)"
      >
        {{ p }}
      </button>

      <button class="btn btn-secondary" :disabled="!hasNext" @click="nextPage">
        Next →
      </button>

      <span class="page-info text-muted">{{ total }} articles in total</span>
    </div>
  </div>
</template>

<style scoped>
.home-page h1 {
  margin-bottom: 24px;
}
.article-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.loading {
  text-align: center;
  padding: 40px 0;
  color: var(--text-muted);
}
.empty {
  text-align: center;
  padding: 60px 0;
}
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}
.page-btn {
  min-width: 38px;
  padding: 6px 10px;
  font-size: 0.85rem;
  border-radius: 6px;
  background: var(--bg);
  color: var(--text);
  border: 1px solid var(--border);
}
.page-btn:hover:not(:disabled):not(.ellipsis) {
  background: var(--accent-bg);
  border-color: var(--accent);
  color: var(--accent);
}
.page-btn.active {
  background: var(--accent);
  color: #fff;
  border-color: var(--accent);
}
.page-btn.ellipsis {
  border-color: transparent;
  background: transparent;
  cursor: default;
}
.page-info {
  width: 100%;
  text-align: center;
  font-size: 0.8rem;
  margin-top: 4px;
}
</style>
