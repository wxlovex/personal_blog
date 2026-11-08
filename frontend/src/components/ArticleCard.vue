<script setup>
import { formatTime } from '@/utils/time'
import { computed } from 'vue'
import { useAuth } from '@/composables/useAuth'

const { currentUser } = useAuth()

const props = defineProps({
  article: { type: Object, required: true },
})

defineEmits(['delete'])

const excerpt = computed(() => {
  const content = props.article.summary || props.article.content || ''
  return content.length > 150 ? content.slice(0, 150) + '...' : content
})


</script>

<template>
  <article class="card article-card">
    <div class="article-card-body">
      <router-link :to="`/article/${article.id}`" class="article-title">
        <h2>{{ article.title }}</h2>
      </router-link>
      <p class="article-excerpt text-muted">{{ excerpt }}</p>
      <div class="article-meta text-muted">
        <span v-if="article.authorName" class="article-author">{{ article.authorName }}</span>
        <span v-if="article.createTime">{{ formatTime(article.createTime) }}</span>
        <span>👁 {{ article.viewCount ?? 0 }}</span>
        <span>❤ {{ article.likeCount ?? 0 }}</span>
      </div>
    </div>
    <div v-if="currentUser?.id === article.userId" class="article-card-actions">
      <button class="btn btn-danger btn-sm" @click="$emit('delete', article.id)">Delete</button>
    </div>
  </article>
</template>

<style scoped>
.article-card {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.article-card-body {
  flex: 1;
  min-width: 0;
}
.article-title {
  text-decoration: none;
  color: inherit;
}
.article-title h2 {
  margin-bottom: 6px;
  font-size: 1.2rem;
  transition: color 0.2s;
}
.article-title:hover h2 {
  color: var(--accent);
}
.article-excerpt {
  margin-bottom: 10px;
  line-height: 1.5;
}
.article-meta {
  display: flex;
  gap: 16px;
  font-size: 0.8rem;
}
.article-author {
  font-weight: 500;
  color: var(--text-h);
}
.article-card-actions {
  flex-shrink: 0;
}
.btn-sm {
  padding: 4px 12px;
  font-size: 0.8rem;
}
</style>
