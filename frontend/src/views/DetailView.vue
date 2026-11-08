<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { articleDetail, articleDelete } from '@/api'
import CommentSection from '@/components/CommentSection.vue'
import { useAuth } from '@/composables/useAuth'

const { currentUser } = useAuth()

const route = useRoute()
const router = useRouter()

const article = ref(null)
const loading = ref(false)
const error = ref('')

async function fetchDetail() {
  loading.value = true
  error.value = ''
  try {
    const res = await articleDetail(route.params.id)
    article.value = res.data.data
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Failed to load article'
  } finally {
    loading.value = false
  }
}

async function handleDelete() {
  if (!confirm('Delete this article?')) return
  try {
    await articleDelete(article.value.id)
    router.push('/')
  } catch (e) {
    alert(e.response?.data?.message || e.message || 'Failed to delete')
  }
}

function goBack() {
  router.push('/')
}

function formatTime(isoString) {
  if (!isoString) return ''
  const d = new Date(isoString)
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}`
}

onMounted(fetchDetail)
</script>

<template>
  <div class="detail-page">
    <button class="btn btn-secondary btn-back" @click="goBack">← Back</button>

    <div v-if="loading" class="loading">Loading...</div>
    <p v-if="error" class="error-msg">{{ error }}</p>

    <article v-if="!loading && article" class="article-detail">
      <header>
        <h1>{{ article.title }}</h1>
        <div class="article-detail-meta text-muted">
          <span v-if="article.authorName" class="article-author">{{ article.authorName }}</span>
          <span v-if="article.createTime">{{ formatTime(article.createTime) }}</span>
          <span>👁 {{ article.viewCount ?? 0 }} views</span>
          <span>❤ {{ article.likeCount ?? 0 }} likes</span>
        </div>
      </header>

      <div class="article-content">
        <p>{{ article.content }}</p>
      </div>

      <CommentSection :article-id="article.id" />

      <footer v-if="currentUser?.id === article.userId" class="article-detail-actions">
        <button class="btn btn-danger" @click="handleDelete">Delete</button>
      </footer>
    </article>
  </div>
</template>

<style scoped>
.detail-page {
  max-width: 720px;
}
.btn-back {
  margin-bottom: 20px;
}
.loading {
  text-align: center;
  padding: 40px 0;
  color: var(--text-muted);
}
.article-detail header {
  margin-bottom: 28px;
}
.article-detail h1 {
  margin-bottom: 10px;
}
.article-detail-meta {
  display: flex;
  gap: 16px;
  font-size: 0.85rem;
}
.article-author {
  font-weight: 500;
  color: var(--text-h);
}
.article-content {
  line-height: 1.8;
  font-size: 1.05rem;
  white-space: pre-wrap;
  word-wrap: break-word;
}
.article-detail-actions {
  margin-top: 40px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}
</style>
