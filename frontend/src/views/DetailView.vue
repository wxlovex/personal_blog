<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { articleDetail, articleDelete } from '@/api'
import { useAuth } from '@/composables/useAuth'
import { formatTime, utcTimestamp } from '@/utils/time'
import CommentSection from '@/components/CommentSection.vue'
import BlogIcon from '@/components/BlogIcon.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import ArticleCategoryDialog from '@/components/ArticleCategoryDialog.vue'
import ArticlePublicationDialog from '@/components/ArticlePublicationDialog.vue'
const { currentUser } = useAuth()
const route = useRoute()
const router = useRouter()
const article = ref(null)
const loading = ref(true)
const error = ref('')
const showDelete = ref(false)
const showCategory = ref(false)
const showPublication = ref(false)
const deleting = ref(false)
const deleteError = ref('')
let requestId = 0
const paragraphs = computed(() => (article.value?.content || '').split(/\n\s*\n/).filter(part => part.trim()))
const readingMinutes = computed(() => {
  const text = article.value?.content || ''
  const chinese = (text.match(/[\u3400-\u9fff]/g) || []).length
  const words = (text.replace(/[\u3400-\u9fff]/g, ' ').match(/\S+/g) || []).length
  return Math.max(1, Math.ceil(chinese / 400 + words / 200))
})
async function fetchDetail() {
  const id = ++requestId
  loading.value = true
  error.value = ''
  article.value = null
  showCategory.value = false
  showPublication.value = false
  try {
    const { data } = await articleDetail(route.params.id)
    if (id === requestId) article.value = data.data
  } catch (e) { if (id === requestId) error.value = e.response?.data?.message || '暂时没能打开这篇日记，请稍后重试。' }
  finally { if (id === requestId) loading.value = false }
}
async function handleDelete() {
  if (deleting.value) return
  deleting.value = true
  deleteError.value = ''
  try { await articleDelete(article.value.id); showDelete.value = false; router.push('/') }
  catch (e) { deleteError.value = e.response?.data?.message || '删除失败，请稍后再试。' }
  finally { deleting.value = false }
}
function categorySaved(updated) { article.value = updated; showCategory.value = false }
function publicationSaved(updated) { article.value = updated; showPublication.value = false }
watch(() => route.params.id, fetchDetail, { immediate: true })
</script>

<template>
  <div class="detail-page page-enter">
    <router-link to="/" class="text-link back-link"><BlogIcon name="arrow-left" />返回我的日记</router-link>
    <div v-if="loading" class="detail-loading" role="status"><span></span><span></span><span></span><span class="sr-only">正在打开日记…</span></div>
    <div v-else-if="error" class="status-panel" role="alert"><BlogIcon name="alert" /><h1>这页暂时无法打开</h1><p>{{ error }}</p><button class="btn" @click="fetchDetail">重新加载</button></div>
    <article v-else-if="article" class="article-detail">
      <header class="article-header">
        <p class="eyebrow">{{ article.categoryName || '未分类' }}</p>
        <h1>{{ article.title }}</h1>
        <div class="article-detail-meta">
          <time v-if="article.createTime" :datetime="utcTimestamp(article.createTime)">{{ formatTime(article.createTime) }}</time>
          <span class="meta-item"><BlogIcon name="clock" />约 {{ readingMinutes }} 分钟</span>
          <span class="meta-item"><BlogIcon :name="article.published ? 'globe' : 'lock'" />{{ article.published ? `已公开 · ${article.plazaCategory}` : '仅自己可见' }}</span>
          <button v-if="currentUser?.id === article.userId" class="detail-category-button" @click="showCategory = true"><BlogIcon name="folder" />修改分类</button>
        </div>
        <div v-if="currentUser?.id === article.userId" class="publication-tools"><button class="btn btn-sm" @click="showPublication = true"><BlogIcon name="globe" />{{ article.published ? '广场设置' : '发布到广场' }}</button><router-link v-if="article.published" :to="`/plaza/${article.id}`" class="text-link">查看广场文章<BlogIcon name="arrow-up-right" /></router-link><span v-else class="form-hint">默认私密，发布后其他人才能看见正文。</span></div>
      </header>
      <div class="article-content"><p v-for="(paragraph, index) in paragraphs" :key="index">{{ paragraph }}</p></div>
      <div class="article-ending"><span class="ending-mark" aria-hidden="true">记</span><p>写到这里，留给下一次思考。</p></div>
      <div class="article-footnote">
        <span class="last-edited">{{ article.updateTime !== article.createTime ? `修改于 ${formatTime(article.updateTime)}` : '这一页，留给当时的自己。' }}</span>
        <div v-if="currentUser?.id === article.userId" class="diary-actions"><router-link :to="`/article/${article.id}/edit`" class="btn btn-sm"><BlogIcon name="pen" />修改这一页</router-link><button class="btn btn-quiet btn-sm" @click="showDelete = true; deleteError = ''"><BlogIcon name="trash" />删除日记</button></div>
      </div>
      <CommentSection :key="article.id" :article-id="article.id" />
    </article>
    <ArticleCategoryDialog :article="showCategory ? article : null" @cancel="showCategory = false" @saved="categorySaved" />
    <ArticlePublicationDialog :article="showPublication ? article : null" @cancel="showPublication = false" @saved="publicationSaved" />
    <ConfirmDialog :open="showDelete" :busy="deleting" title="删除这篇日记？" description="日记及其补记将被删除，此操作无法撤销。" :error="deleteError" @cancel="showDelete = false" @confirm="handleDelete" />
  </div>
</template>

<style scoped>
.detail-page { max-width: 740px; margin-inline: auto; }
.back-link { color: var(--text-muted); margin-bottom: 40px; min-height: 32px; }
.article-header { padding-bottom: 30px; margin-bottom: 36px; border-bottom: 1px solid var(--border); }
.article-header .eyebrow { margin-bottom: 18px; }
.article-header h1 { font-size: clamp(1.85rem, 4vw, 2.6rem); line-height: 1.55; }
.article-detail-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 12px 24px; color: var(--text-muted); font-size: .75rem; margin-top: 26px; }
.author-avatar { display: grid; place-items: center; width: 26px; height: 26px; background: var(--accent-bg); border-radius: 50%; color: var(--accent); font: .75rem/1 var(--serif); }
.meta-item { display: inline-flex; align-items: center; gap: 6px; }
.meta-item .icon { width: 14px; height: 14px; }
.detail-category-button { display: inline-flex; align-items: center; gap: 6px; padding: 5px 0; color: var(--accent); font-size: .75rem; background: none; border: 0; }
.detail-category-button .icon { width: 14px; height: 14px; }
.publication-tools { display: flex; align-items: center; flex-wrap: wrap; gap: 12px 18px; margin-top: 20px; }
.article-content { color: var(--text); font-size: 1.0625rem; line-height: 2.05; overflow-wrap: anywhere; }
.article-content p { white-space: pre-wrap; margin-bottom: 1.5em; }
.article-ending { display: flex; flex-direction: column; align-items: center; gap: 13px; padding-block: 36px 40px; color: var(--text-muted); }
.ending-mark { color: var(--accent); font: 22px/1.5 var(--serif); }
.article-ending p { font-size: .7rem; letter-spacing: .08em; }
.article-footnote { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 15px; padding-block: 20px; border-top: 1px solid var(--border); }
.diary-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.last-edited { font-size: .75rem; color: var(--text-muted); }
.detail-loading { display: flex; flex-direction: column; gap: 24px; padding-top: 20px; }
.detail-loading > span:not(.sr-only) { display: block; background: var(--bg-secondary); height: 160px; border-radius: 4px; }
.detail-loading > span:first-child { width: 80%; height: 46px; }
.detail-loading > span:nth-child(2) { width: 40%; height: 15px; margin-bottom: 30px; }
@media (max-width: 600px) { .back-link { margin-bottom: 30px; } .article-header { margin-bottom: 28px; padding-bottom: 25px; } .article-detail-meta { gap: 12px 16px; margin-top: 20px; } .article-content { font-size: 1rem; line-height: 2; } }
</style>
