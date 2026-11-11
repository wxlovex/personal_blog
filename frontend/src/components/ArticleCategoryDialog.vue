<script setup>
import { hasVisibleText } from '@/utils/text'
import { ref, computed, watch, useId } from 'vue'
import { articleUpdateCategory, categoryList, categoryCreate } from '@/api'
import BlogIcon from './BlogIcon.vue'

const props = defineProps({ article: { type: Object, default: null } })
const emit = defineEmits(['cancel', 'saved', 'categories-changed'])
const dialog = ref(null)
const titleId = useId()
const descriptionId = useId()
const selectId = useId()
const nameId = useId()
const categories = ref([])
const selectedId = ref(null)
const loading = ref(false)
const ready = ref(false)
const saving = ref(false)
const creating = ref(false)
const addingCategory = ref(false)
const newName = ref('')
const error = ref('')
const busy = computed(() => saving.value || creating.value)
let requestId = 0

async function loadCategories() {
  const id = ++requestId
  loading.value = true; ready.value = false; error.value = ''
  try {
    const { data } = await categoryList()
    if (id !== requestId) return
    categories.value = data.data
    ready.value = true
    if (selectedId.value && !categories.value.some(category => category.id === selectedId.value)) selectedId.value = null
  } catch (e) { if (id === requestId) error.value = e.response?.data?.message || '分类加载失败，请重试。' }
  finally { if (id === requestId) loading.value = false }
}
function cancel(event) { event?.preventDefault(); if (!busy.value) emit('cancel') }
async function createCategory() {
  if (busy.value || !ready.value) return
  error.value = ''
  if (!hasVisibleText(newName.value)) { error.value = '请填写新分类名称。'; return }
  creating.value = true
  try {
    const { data } = await categoryCreate({ name: newName.value.trim() })
    categories.value.push(data.data)
    emit('categories-changed')
    selectedId.value = data.data.id
    addingCategory.value = false; newName.value = ''
  } catch (e) { error.value = e.response?.data?.message || '创建分类失败，请重试。' }
  finally { creating.value = false }
}
async function save() {
  if (busy.value || !ready.value || !props.article) return
  error.value = ''; saving.value = true
  try {
    const { data } = await articleUpdateCategory(props.article.id, selectedId.value)
    emit('saved', data.data)
  } catch (e) { error.value = e.response?.data?.message || '分类保存失败，请重试。' }
  finally { saving.value = false }
}
watch(() => props.article?.id, id => {
  requestId++
  categories.value = []; ready.value = false; error.value = ''
  selectedId.value = props.article?.categoryId ?? null
  addingCategory.value = false; newName.value = ''
  if (id) loadCategories()
}, { immediate: true })
watch([() => !!props.article, dialog], ([open, element]) => {
  if (!element) return
  if (open && !element.open) element.showModal()
  else if (!open && element.open) element.close()
}, { flush: 'post' })
</script>

<template>
  <dialog ref="dialog" class="category-dialog" :aria-labelledby="titleId" :aria-describedby="descriptionId" @cancel="cancel">
    <div class="dialog-eyebrow"><BlogIcon name="folder" /><span>整理这一页</span></div>
    <h2 :id="titleId">修改分类</h2>
    <p :id="descriptionId" class="dialog-description">{{ article?.title }}</p>
    <form @submit.prevent="save">
      <div class="form-group">
        <label :for="selectId" class="form-label">文章分类</label>
        <p v-if="loading" class="form-hint" role="status">正在加载分类…</p>
        <select :id="selectId" v-model="selectedId" class="form-input" :disabled="loading || !ready || busy">
          <option :value="null">未分类</option>
          <option v-if="selectedId && !categories.some(category => category.id === selectedId)" :value="selectedId">{{ article?.categoryName || '原分类' }}</option>
          <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
        </select>
        <p class="form-hint">选用已有分类，或选择“未分类”移除当前分类。</p>
      </div>
      <button type="button" class="text-link new-category-toggle" :disabled="!ready || busy" :aria-expanded="addingCategory" @click="addingCategory = !addingCategory">{{ addingCategory ? '收起新分类' : '＋ 新建分类' }}</button>
      <div v-if="addingCategory" class="form-group new-category">
        <label :for="nameId" class="form-label">新分类名称</label>
        <div class="create-row"><input :id="nameId" v-model="newName" class="form-input" maxlength="40" placeholder="最多 40 字" :disabled="busy" @keydown.enter.prevent="createCategory" /><button type="button" class="btn" :disabled="busy" @click="createCategory">{{ creating ? '创建中…' : '创建并选用' }}</button></div>
      </div>
      <p v-if="error" class="error-msg" role="alert">{{ error }}</p>
      <button v-if="!loading && !ready" type="button" class="btn btn-sm" @click="loadCategories">重新加载分类</button>
      <div class="dialog-actions"><button type="button" class="btn" :disabled="busy" @click="cancel">取消</button><button type="submit" class="btn btn-primary" :disabled="!ready || busy">{{ saving ? '正在保存…' : '保存分类' }}</button></div>
    </form>
  </dialog>
</template>

<style scoped>
.category-dialog { width: min(460px, calc(100% - 32px)); max-height: calc(100svh - 40px); overflow-y: auto; padding: 28px; color: var(--text); background: var(--surface); border: 1px solid var(--border); border-radius: 8px; }
.category-dialog::backdrop { background: rgb(20 30 20 / .45); backdrop-filter: blur(3px); }
.dialog-eyebrow { display: flex; align-items: center; gap: 8px; color: var(--accent); font-size: .75rem; margin-bottom: 14px; }
h2 { font-family: var(--serif); font-size: 1.5rem; font-weight: 500; }
.dialog-description { margin-block: 8px 24px; font-size: .85rem; color: var(--text-muted); overflow-wrap: anywhere; }
.new-category-toggle { padding: 0; border: 0; background: none; color: var(--accent); margin-block: 16px; font-size: .8rem; min-height: 32px; }
.new-category { margin-bottom: 18px; }
.create-row { display: flex; gap: 8px; }
.create-row .btn { flex-shrink: 0; }
.dialog-actions { display: flex; gap: 10px; justify-content: flex-end; margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--border); }
@media (max-width: 420px) { .category-dialog { padding: 22px 18px; } .create-row { flex-wrap: wrap; } .create-row input { flex: 1 1 140px; } }
</style>
