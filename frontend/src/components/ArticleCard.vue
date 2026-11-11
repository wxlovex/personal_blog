<script setup>
import { computed } from 'vue'
import { formatDate, utcTimestamp } from '@/utils/time'
import { useAuth } from '@/composables/useAuth'
import BlogIcon from './BlogIcon.vue'
const { currentUser } = useAuth()
const props = defineProps({ article: { type: Object, required: true } })
defineEmits(['delete', 'category', 'publication'])
const excerpt = computed(() => {
  const content = props.article.summary || props.article.content || ''
  return content.length > 150 ? content.slice(0, 150) + '…' : content
})
</script>
<template>
  <article class="article-card">
    <div class="article-card-body">
      <span class="article-category" :class="{ 'text-muted': !article.categoryName }">{{ article.categoryName || '未分类' }}</span>
      <router-link :to="`/article/${article.id}`" class="article-title"><h2>{{ article.title }}<BlogIcon name="arrow-up-right" /></h2></router-link>
      <p v-if="excerpt" class="article-excerpt">{{ excerpt }}</p>
      <div class="article-meta">
        <time v-if="article.createTime" :datetime="utcTimestamp(article.createTime)">{{ formatDate(article.createTime) }}</time>
        <span v-if="article.updateTime && article.updateTime !== article.createTime">有过修改</span>
        <span class="article-visibility"><BlogIcon :name="article.published ? 'globe' : 'lock'" />{{ article.published ? `已发布到广场 · ${article.plazaCategory}` : '仅自己可见' }}</span>
      </div>
    </div>
    <div v-if="currentUser?.id === article.userId" class="article-actions">
      <button class="article-category-action" :aria-label="`${article.published ? '广场设置' : '发布到广场'}：${article.title}`" @click="$emit('publication')"><BlogIcon name="globe" /><span>{{ article.published ? '广场设置' : '发布到广场' }}</span></button>
      <button class="article-category-action" :aria-label="`修改分类：${article.title}`" @click="$emit('category')"><BlogIcon name="folder" /><span>修改分类</span></button>
      <button class="article-delete" :aria-label="`删除日记：${article.title}`" @click="$emit('delete')"><BlogIcon name="trash" /><span>删除</span></button>
    </div>
  </article>
</template>
<style scoped>
.article-card { display: flex; align-items: flex-start; gap: 28px; padding-block: 32px; border-bottom: 1px solid var(--border); }
.article-card-body { flex: 1; min-width: 0; }
.article-category { display: inline-block; font-size: .7rem; color: var(--accent); margin-bottom: 8px; }
.article-category.text-muted { color: var(--text-muted); }
.article-title { display: block; color: var(--text-h); }
.article-title h2 { display: flex; align-items: baseline; gap: 15px; font-family: var(--serif); font-size: 1.35rem; font-weight: 600; transition: color .18s; }
.article-title .icon { width: 17px; height: 17px; color: var(--accent); opacity: 0; transform: translate(-3px, 3px); transition: opacity .18s, transform .18s; }
.article-title:hover h2, .article-title:focus-visible h2 { color: var(--accent); }
.article-title:hover .icon, .article-title:focus-visible .icon { opacity: 1; transform: translate(0, 0); }
.article-excerpt { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; margin-top: 11px; max-width: 740px; color: var(--text-muted); font-size: .875rem; line-height: 1.85; overflow-wrap: anywhere; }
.article-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 18px; color: var(--text-muted); font-size: .7rem; margin-top: 17px; }
.article-meta time { font-family: var(--mono); }
.article-actions { display: flex; align-items: center; gap: 16px; flex-shrink: 0; }
.article-visibility { display: inline-flex; align-items: center; gap: 5px; }
.article-visibility .icon { width: 12px; height: 12px; }
.article-category-action { display: inline-flex; align-items: center; gap: 5px; background: none; border: 0; color: var(--accent); padding: 9px 0; font-size: .75rem; white-space: nowrap; }
.article-category-action:hover { color: var(--accent-hover); }
.article-category-action .icon { width: 14px; height: 14px; }
.article-delete { display: inline-flex; align-items: center; gap: 5px; background: none; border: 0; padding: 9px 0 9px 9px; color: var(--text-muted); font-size: .7rem; flex-shrink: 0; }
.article-delete:hover { color: var(--danger); }
.article-delete .icon { width: 14px; height: 14px; }
@media (max-width: 760px) { .article-card { flex-wrap: wrap; } .article-card-body { flex-basis: 100%; } .article-actions { margin-left: auto; flex-wrap: wrap; gap: 16px; } }
@media (max-width: 600px) { .article-card { padding-block: 26px; gap: 10px; } .article-title h2 { font-size: 1.16rem; gap: 7px; } .article-meta { gap: 8px 13px; } }
@media (pointer: coarse) { .article-delete, .article-category-action { min-height: 44px; min-width: 44px; justify-content: center; } }
</style>
