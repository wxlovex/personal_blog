<script setup>
import { ref, watch } from 'vue'
import { formatTime, utcTimestamp } from '@/utils/time'
import { commentList, commentPublish } from '@/api'
import { useAuth } from '@/composables/useAuth'
import BlogIcon from './BlogIcon.vue'
const props = defineProps({ articleId: { type: [Number, String], required: true } })
const { isLoggedIn } = useAuth()
const comments = ref([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const error = ref('')
const newComment = ref('')
const submitting = ref(false)
const submitError = ref('')
let requestId = 0
async function fetchComments(append = false) {
  if (append && loading.value) return
  const id = ++requestId
  loading.value = true
  error.value = ''
  try {
    const nextPage = append ? page.value + 1 : 1
    const { data } = await commentList(props.articleId, nextPage)
    if (id !== requestId) return
    comments.value = append ? [...comments.value, ...data.data.records] : data.data.records
    page.value = nextPage
    total.value = data.data.total
  } catch (e) { if (id === requestId) error.value = e.response?.data?.message || '补记暂时没能加载，请稍后再试。' }
  finally { if (id === requestId) loading.value = false }
}
async function handleSubmit() {
  const content = newComment.value.trim()
  if (!content || submitting.value || !isLoggedIn.value) return
  submitting.value = true
  submitError.value = ''
  try { await commentPublish({ articleId: Number(props.articleId), content }); newComment.value = ''; await fetchComments() }
  catch (e) { submitError.value = e.response?.data?.message || '补记未能保存，请稍后重试。' }
  finally { submitting.value = false }
}
watch(() => props.articleId, () => { comments.value = []; newComment.value = ''; submitError.value = ''; fetchComments() }, { immediate: true })
</script>
<template>
  <section class="comment-section" aria-labelledby="comment-heading">
    <div class="section-label"><h2 id="comment-heading">后来的补记<span v-if="total" class="comment-count">{{ total }}</span></h2><span>也只对自己可见</span></div>
    <form v-if="isLoggedIn" class="comment-form" @submit.prevent="handleSubmit">
      <label for="new-comment" class="sr-only">写下补记</label>
      <textarea id="new-comment" v-model="newComment" class="form-input comment-textarea" placeholder="回看这一页，有了什么新的想法？" rows="3" maxlength="2000" :disabled="submitting" required></textarea>
      <div class="comment-form-footer"><span class="form-hint">{{ newComment.length }} / 2,000</span><button type="submit" class="btn btn-primary btn-sm" :disabled="submitting || !newComment.trim()">{{ submitting ? '正在保存…' : '存下补记' }}<BlogIcon name="arrow-right" /></button></div>
    </form>
    <p v-if="submitError" class="error-msg" role="alert">{{ submitError }}</p>
    <p v-if="loading && !comments.length" class="comment-loading" role="status">正在翻开这些补记…</p>
    <div v-if="error" class="comment-error" role="alert"><p class="error-msg">{{ error }}</p><button class="btn btn-sm" @click="fetchComments(comments.length > 0)">重新加载补记</button></div>
    <div v-if="comments.length" class="comment-list">
      <article v-for="comment in comments" :key="comment.id" class="comment-item"><div class="comment-body"><div class="comment-meta"><span>补记</span><time :datetime="utcTimestamp(comment.createTime)">{{ formatTime(comment.createTime) }}</time></div><p class="comment-content">{{ comment.content }}</p></div></article>
    </div>
    <p v-else-if="!loading && !error" class="comment-empty">还没有补记。日后有新想法，可以在这里继续。</p>
    <button v-if="comments.length < total && !error" class="btn btn-sm load-more" :disabled="loading" @click="fetchComments(true)">{{ loading ? '正在加载…' : '翻开更多补记' }}</button>
  </section>
</template>
<style scoped>
.comment-section { margin-top: 30px; }
.comment-count { margin-left: 10px; color: var(--accent); font: .7rem/1 var(--mono); }
.comment-form { margin-block: 25px 12px; }
.comment-textarea { min-height: 118px; padding: 15px 18px; font-size: .875rem; }
.comment-form-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 12px; }
.comment-login { display: flex; align-items: center; gap: 10px; background: var(--bg-secondary); padding: 20px; margin-block: 24px; color: var(--text-muted); font-size: .85rem; }
.comment-login a { text-decoration: underline; text-underline-offset: 3px; margin-right: 3px; }
.comment-item { display: flex; align-items: flex-start; gap: 14px; padding-block: 26px; border-bottom: 1px solid var(--border); }
.comment-avatar { display: grid; place-items: center; width: 32px; height: 32px; flex-shrink: 0; border: 1px solid var(--border); border-radius: 50%; color: var(--accent); font: .8rem/1 var(--serif); }
.comment-body { flex: 1; min-width: 0; }
.comment-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 6px 16px; font-size: .7rem; color: var(--text-muted); margin-bottom: 7px; }
.comment-author { color: var(--text-h); font-size: .8rem; }
.comment-content { white-space: pre-wrap; overflow-wrap: anywhere; font-size: .875rem; line-height: 1.9; }
.comment-empty, .comment-loading { padding-block: 25px; color: var(--text-muted); font-size: .8rem; text-align: center; }
.comment-error { margin-top: 20px; display: grid; justify-items: start; gap: 15px; }
.load-more { margin-top: 22px; }
@media (max-width: 420px) { .comment-item { gap: 10px; } }
</style>
