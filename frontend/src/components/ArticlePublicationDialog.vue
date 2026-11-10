<script setup>
import { ref, watch, useId } from 'vue'
import { articlePublication, plazaCategories } from '@/api'
import BlogIcon from './BlogIcon.vue'
const props = defineProps({ article: { type: Object, default: null } })
const emit = defineEmits(['cancel', 'saved'])
const dialog = ref(null)
const titleId = useId()
const descriptionId = useId()
const categoryId = useId()
const categories = ref([])
const category = ref('')
const loading = ref(false)
const busy = ref(false)
const error = ref('')
let requestId = 0
async function loadCategories() {
  const id = ++requestId
  loading.value = true; error.value = ''
  try { const { data } = await plazaCategories(); if (id === requestId) categories.value = data.data }
  catch (e) { if (id === requestId) error.value = e.response?.data?.message || '广场分类加载失败，请重试。' }
  finally { if (id === requestId) loading.value = false }
}
async function save(published) {
  if (busy.value || !props.article) return
  error.value = ''
  if (published && !category.value) { error.value = '请选择一个广场分类。'; return }
  busy.value = true
  try {
    const { data } = await articlePublication(props.article.id, published, category.value)
    emit('saved', data.data)
  } catch (e) { error.value = e.response?.data?.message || '暂时未能保存发布状态，请重试。' }
  finally { busy.value = false }
}
function cancel(event) { event?.preventDefault(); if (!busy.value) emit('cancel') }
watch(() => props.article?.id, id => {
  requestId++; categories.value = []; error.value = ''
  category.value = props.article?.plazaCategory || ''
  if (id) loadCategories()
}, { immediate: true })
watch([() => !!props.article, dialog], ([open, element]) => {
  if (!element) return
  if (open && !element.open) element.showModal()
  else if (!open && element.open) element.close()
}, { flush: 'post' })
</script>
<template>
  <dialog ref="dialog" class="publication-dialog" :aria-labelledby="titleId" :aria-describedby="descriptionId" @cancel="cancel">
    <p class="eyebrow">A PAGE TO SHARE</p>
    <h2 :id="titleId">{{ article?.published ? '管理广场发布' : '发布到广场' }}</h2>
    <p :id="descriptionId" class="entry-title">{{ article?.title }}</p>
    <p class="visibility-note"><BlogIcon name="globe" />{{ article?.published ? '这篇文章已在广场公开，修改正文后会同步更新。' : '确认发布后，标题、正文和作者名将对所有人可见。' }}</p>
    <p class="form-hint">你的个人分类和补记仍保持私密，也可以随时撤回为私密文章。</p>
    <form @submit.prevent="save(true)">
      <div class="form-group"><label class="form-label" :for="categoryId">广场分类</label><select :id="categoryId" v-model="category" class="form-input" required :disabled="busy || loading || !categories.length"><option disabled value="">请选择广场分类</option><option v-for="name in categories" :key="name" :value="name">{{ name }}</option></select><p v-if="loading" class="form-hint" role="status">正在加载分类…</p></div>
      <p v-if="error" class="error-msg" role="alert">{{ error }}</p>
      <button v-if="!loading && !categories.length" type="button" class="btn btn-sm" @click="loadCategories">重新加载分类</button>
      <div class="publication-actions"><button type="button" class="btn" :disabled="busy" @click="cancel">取消</button><button type="submit" class="btn btn-primary" :disabled="busy || loading || !categories.length">{{ busy ? '正在保存…' : article?.published ? '保存广场分类' : '确认发布' }}</button></div>
    </form>
    <div v-if="article?.published" class="withdraw-row"><p>撤回只从广场移除，原文仍保留在你的日记中。</p><button type="button" class="btn btn-danger btn-sm" :disabled="busy" @click="save(false)">撤回为私密</button></div>
  </dialog>
</template>
<style scoped>
.publication-dialog { width: min(480px, calc(100% - 32px)); max-height: calc(100svh - 40px); overflow-y: auto; padding: 28px; color: var(--text); background: var(--surface); border: 1px solid var(--border); border-radius: 8px; }
.publication-dialog::backdrop { background: rgb(20 30 20 / .45); backdrop-filter: blur(3px); }
h2 { font: 500 1.5rem/1.6 var(--serif); margin-top: 10px; }
.entry-title { color: var(--text-muted); font-size: .85rem; margin-block: 8px 20px; overflow-wrap: anywhere; }
.visibility-note { display: flex; align-items: flex-start; gap: 8px; font-size: .85rem; color: var(--accent); margin-bottom: 10px; }
.visibility-note .icon { flex-shrink: 0; margin-top: 3px; }
form { display: grid; gap: 16px; margin-top: 22px; }
.publication-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 10px; margin-top: 6px; }
.withdraw-row { display: grid; gap: 12px; justify-items: start; margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--border); }
.withdraw-row p { color: var(--text-muted); font-size: .75rem; }
@media (max-width: 420px) { .publication-dialog { padding: 22px 18px; } }
</style>
