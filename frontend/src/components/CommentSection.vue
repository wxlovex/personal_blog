<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { formatTime, utcTimestamp } from '@/utils/time'
import { commentDelete, commentList, commentPublish } from '@/api'
import { hasVisibleText } from '@/utils/text'
import { useAuth } from '@/composables/useAuth'
import BlogIcon from './BlogIcon.vue'
import ConfirmDialog from './ConfirmDialog.vue'
const props = defineProps({ articleId: { type: [Number, String], required: true } })
const { isLoggedIn } = useAuth()
const PAGE_SIZE = 10
const loadedComments = ref([])
const recentComments = ref([])
const comments = computed(() => mergeComments(loadedComments.value, recentComments.value))
const commentListElement = ref(null)
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const error = ref('')
const newComment = ref('')
const submitting = ref(false)
const submitError = ref('')
const commentToDelete = ref(null)
const deleting = ref(false)
const deleteError = ref('')
const busy = computed(() => submitting.value || deleting.value)
const canSubmit = computed(() => hasVisibleText(newComment.value))
let requestId = 0
let entryVersion = 0
function mergeComments(previous, incoming) {
  const ids = new Set(previous.map(comment => comment.id))
  return [...previous, ...incoming.filter(comment => !ids.has(comment.id))]
}
async function fetchComments(append = false) {
  if (busy.value || (append && loading.value)) return
  const id = ++requestId
  loading.value = true
  error.value = ''
  try {
    const nextPage = append ? page.value + 1 : 1
    const { data } = await commentList(props.articleId, nextPage, PAGE_SIZE)
    if (id !== requestId) return
    loadedComments.value = append ? mergeComments(loadedComments.value, data.data.records) : data.data.records
    const loadedIds = new Set(loadedComments.value.map(comment => comment.id))
    recentComments.value = recentComments.value.filter(comment => !loadedIds.has(comment.id))
    page.value = nextPage
    total.value = data.data.total
  } catch (e) { if (id === requestId) error.value = e.response?.data?.message || '补记暂时没能加载，请稍后再试。' }
  finally { if (id === requestId) loading.value = false }
}
async function handleSubmit() {
  const content = newComment.value.trim()
  if (!hasVisibleText(content) || busy.value || loading.value || !isLoggedIn.value) return
  const version = entryVersion
  submitting.value = true
  submitError.value = ''
  try {
    const { data } = await commentPublish({ articleId: Number(props.articleId), content })
    if (version !== entryVersion) return
    recentComments.value.push(data.data)
    total.value++
    newComment.value = ''
    await nextTick()
    commentListElement.value?.lastElementChild?.scrollIntoView({ block: 'nearest' })
  } catch (e) { if (version === entryVersion) submitError.value = e.response?.data?.message || '补记未能保存，请稍后重试。' }
  finally { if (version === entryVersion) submitting.value = false }
}
function confirmDelete(comment) { commentToDelete.value = comment; deleteError.value = '' }
async function handleDelete() {
  if (busy.value || loading.value || !commentToDelete.value) return
  const version = entryVersion
  const id = commentToDelete.value.id
  deleting.value = true
  deleteError.value = ''
  try {
    await commentDelete(id)
    if (version !== entryVersion) return
    loadedComments.value = loadedComments.value.filter(comment => comment.id !== id)
    recentComments.value = recentComments.value.filter(comment => comment.id !== id)
    total.value = Math.max(0, total.value - 1)
    // Deletion shifts offset pages; re-read the partial boundary on the next load to avoid skipping a row.
    page.value = Math.floor(loadedComments.value.length / PAGE_SIZE)
    commentToDelete.value = null
  } catch (e) { if (version === entryVersion) deleteError.value = e.response?.data?.message || '补记未能删除，请稍后重试。' }
  finally { if (version === entryVersion) deleting.value = false }
}
watch(() => props.articleId, () => {
  entryVersion++; requestId++
  loadedComments.value = []; recentComments.value = []; total.value = 0; page.value = 0
  newComment.value = ''; submitError.value = ''; commentToDelete.value = null
  submitting.value = false; deleting.value = false
  fetchComments()
}, { immediate: true })
onBeforeUnmount(() => { entryVersion++; requestId++ })
</script>
<template>
  <section class="comment-section" aria-labelledby="comment-heading">
    <div class="section-label"><h2 id="comment-heading">后来的补记<span v-if="total" class="comment-count">{{ total }}</span></h2><span>也只对自己可见</span></div>
    <form v-if="isLoggedIn" class="comment-form" @submit.prevent="handleSubmit">
      <label for="new-comment" class="sr-only">写下补记</label>
      <textarea id="new-comment" v-model="newComment" class="form-input comment-textarea" placeholder="回看这一页，有了什么新的想法？" rows="3" maxlength="2000" :disabled="busy" required></textarea>
      <div class="comment-form-footer"><span class="form-hint">{{ newComment.length }} / 2,000</span><button type="submit" class="btn btn-primary btn-sm" :disabled="busy || loading || !canSubmit">{{ submitting ? '正在保存…' : '存下补记' }}<BlogIcon name="arrow-right" /></button></div>
    </form>
    <p v-if="submitError" class="error-msg" role="alert">{{ submitError }}</p>
    <p v-if="loading && !comments.length" class="comment-loading" role="status">正在翻开这些补记…</p>
    <div v-if="error" class="comment-error" role="alert"><p class="error-msg">{{ error }}</p><button class="btn btn-sm" :disabled="busy || loading" @click="fetchComments(loadedComments.length > 0)">重新加载补记</button></div>
    <div v-if="comments.length" ref="commentListElement" class="comment-list">
      <article v-for="comment in comments" :key="comment.id" class="comment-item"><div class="comment-body"><div class="comment-meta"><span>补记</span><time :datetime="utcTimestamp(comment.createTime)">{{ formatTime(comment.createTime) }}</time></div><p class="comment-content">{{ comment.content }}</p></div><button type="button" class="btn btn-sm" :disabled="busy || loading" aria-label="删除这条补记" @click="confirmDelete(comment)"><BlogIcon name="trash" />删除</button></article>
    </div>
    <p v-else-if="!loading && !error" class="comment-empty">还没有补记。日后有新想法，可以在这里继续。</p>
    <button v-if="comments.length < total && !error" class="btn btn-sm load-more" :disabled="loading || busy" @click="fetchComments(true)">{{ loading ? '正在加载…' : '翻开更多补记' }}</button>
    <ConfirmDialog :open="!!commentToDelete" :busy="deleting" title="删除这条补记？" description="删除后无法恢复，也会释放这条补记占用的空间。" :error="deleteError" @cancel="commentToDelete = null" @confirm="handleDelete" />
  </section>
</template>
<style scoped>
.comment-section { margin-top: 30px; }
.comment-count { margin-left: 10px; color: var(--accent); font: .7rem/1 var(--mono); }
.comment-form { margin-block: 25px 12px; }
.comment-textarea { min-height: 118px; padding: 15px 18px; font-size: .875rem; }
.comment-form-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 12px; }
.comment-item { display: flex; align-items: flex-start; gap: 14px; padding-block: 26px; border-bottom: 1px solid var(--border); }
.comment-body { flex: 1; min-width: 0; }
.comment-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 6px 16px; font-size: .7rem; color: var(--text-muted); margin-bottom: 7px; }
.comment-content { white-space: pre-wrap; overflow-wrap: anywhere; font-size: .875rem; line-height: 1.9; }
.comment-empty, .comment-loading { padding-block: 25px; color: var(--text-muted); font-size: .8rem; text-align: center; }
.comment-error { margin-top: 20px; display: grid; justify-items: start; gap: 15px; }
.load-more { margin-top: 22px; }
@media (max-width: 420px) { .comment-item { gap: 10px; } }
</style>
