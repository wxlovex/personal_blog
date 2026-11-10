<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { plazaDetail } from '@/api'
import { formatTime, utcTimestamp } from '@/utils/time'
import BlogIcon from '@/components/BlogIcon.vue'
const route = useRoute()
const article = ref(null)
const loading = ref(true)
const error = ref('')
const unavailable = ref(false)
const paragraphs = computed(() => (article.value?.content || '').split(/\n\s*\n/).filter(part => part.trim()))
let requestId = 0
async function fetchArticle() {
  const id = ++requestId
  loading.value = true; error.value = ''; article.value = null; unavailable.value = false
  try { const { data } = await plazaDetail(route.params.id); if (id === requestId) article.value = data.data }
  catch (e) { if (id === requestId) { unavailable.value = e.response?.status === 404; error.value = unavailable.value ? '这篇文章尚未公开、已被撤回，或已经删除。' : '暂时未能取回文章，请重试。' } }
  finally { if (id === requestId) loading.value = false }
}
onMounted(fetchArticle)
onBeforeUnmount(() => { requestId++ })
</script>
<template>
  <div class="public-detail page-enter">
    <router-link :to="{ path: '/plaza', query: route.query }" class="text-link back-link"><BlogIcon name="arrow-left" />返回广场</router-link>
    <p v-if="loading" class="status-panel text-muted" role="status">正在打开文章…</p>
    <div v-else-if="error" class="status-panel" role="alert"><BlogIcon name="book" /><h1>{{ unavailable ? '这篇文章暂不可见' : '文章暂时没有打开' }}</h1><p>{{ error }}</p><button v-if="!unavailable" class="btn" @click="fetchArticle">重新加载</button><router-link v-else to="/plaza" class="btn">看看其他文章</router-link></div>
    <article v-else-if="article"><header class="public-header"><p class="eyebrow">{{ article.category }} · 广场</p><h1>{{ article.title }}</h1><div class="public-meta"><span>{{ article.authorName }}</span><time :datetime="utcTimestamp(article.publishedTime)">{{ formatTime(article.publishedTime) }} 发布</time><span><BlogIcon name="globe" />公开文章</span></div></header><div class="public-body"><p v-for="(paragraph, index) in paragraphs" :key="index">{{ paragraph }}</p></div><div class="public-ending"><span>阅</span><p>一页文字，一次相遇。</p></div><p class="public-note">本文由作者主动分享到广场，个人补记与私人分类不会公开。</p></article>
  </div>
</template>
<style scoped>
.public-detail { max-width: 740px; margin-inline: auto; }
.back-link { margin-bottom: 36px; color: var(--text-muted); }
.public-header { padding-bottom: 28px; border-bottom: 1px solid var(--border); margin-bottom: 32px; }
.public-header h1 { margin-top: 18px; }
.public-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 10px 24px; color: var(--text-muted); font-size: .75rem; margin-top: 22px; }
.public-meta > span:last-child { display: inline-flex; align-items: center; gap: 6px; }
.public-meta .icon { width: 14px; height: 14px; }
.public-body { font-size: 1.0625rem; line-height: 2.05; overflow-wrap: anywhere; }
.public-body p { white-space: pre-wrap; margin-bottom: 1.5em; }
.public-ending { text-align: center; color: var(--text-muted); padding-block: 35px; }
.public-ending span { font: 22px/1.6 var(--serif); color: var(--accent); }
.public-ending p { font-size: .75rem; margin-top: 10px; }
.public-note { border-top: 1px solid var(--border); padding-top: 20px; color: var(--text-muted); font-size: .75rem; }
.status-panel h1 { font-size: 1.5rem; margin-bottom: 14px; }
@media (max-width: 600px) { .public-body { font-size: 1rem; } }
</style>
