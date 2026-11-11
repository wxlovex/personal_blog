<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { articlePublish, articleUpdate, articleDetail, categoryList, categoryCreate } from '@/api'
import { useAuth } from '@/composables/useAuth'
import BlogIcon from '@/components/BlogIcon.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { readArticleFile } from '@/utils/importText'
import { hasVisibleText } from '@/utils/text'
const router = useRouter()
const route = useRoute()
const { isLoggedIn } = useAuth()
const editing = computed(() => !!route.params.id)
const title = ref('')
const content = ref('')
const categoryId = ref(null)
const error = ref('')
const loadError = ref('')
const loading = ref(false)
const fetching = ref(false)
const published = ref(false)
const categories = ref([])
const categoryError = ref('')
const addingCategory = ref(false)
const categoryName = ref('')
const savingCategory = ref(false)
const fileInput = ref(null)
const importing = ref(false)
const importedName = ref('')
const importError = ref('')
const pendingImport = ref(null)
const importRequiresTitle = ref(false)
const busy = computed(() => loading.value || importing.value || savingCategory.value)
let requestId = 0
let importRequestId = 0
async function loadEntry() {
  const id = ++requestId
  importRequestId++
  title.value = ''; content.value = ''; categoryId.value = null
  loadError.value = ''; error.value = ''
  importedName.value = ''; importError.value = ''; pendingImport.value = null; importRequiresTitle.value = false
  importing.value = false; fetching.value = false
  published.value = false
  if (!editing.value) return
  fetching.value = true
  try {
    const { data } = await articleDetail(route.params.id)
    if (id !== requestId) return
    title.value = data.data.title
    content.value = data.data.content
    categoryId.value = data.data.categoryId
    published.value = !!data.data.published
  } catch (e) { if (id === requestId) loadError.value = e.response?.data?.message || '暂时没能取回这一页，请稍后重试。' }
  finally { if (id === requestId) fetching.value = false }
}
async function loadCategories() {
  categoryError.value = ''
  try { categories.value = (await categoryList()).data.data }
  catch (e) { categoryError.value = e.response?.data?.message || '暂时没能取回分类，请重试。' }
}
async function addCategory() {
  if (busy.value) return
  categoryError.value = ''
  if (!hasVisibleText(categoryName.value)) { categoryError.value = '请填写分类名称。'; return }
  savingCategory.value = true
  try {
    const { data } = await categoryCreate({ name: categoryName.value.trim() })
    categories.value.push(data.data)
    categoryId.value = data.data.id
    categoryName.value = ''; addingCategory.value = false
  } catch (e) { categoryError.value = e.response?.data?.message || '暂时没能创建分类，请重试。' }
  finally { savingCategory.value = false }
}
function applyImport(entry) {
  content.value = entry.content
  importedName.value = entry.name
  importRequiresTitle.value = true
  pendingImport.value = null
  error.value = ''
}
async function importFile(event) {
  const file = event.target.files?.[0]
  event.target.value = '' // Allow choosing the same file again after cancelling or fixing it.
  if (!file || busy.value) return
  const id = ++importRequestId
  importError.value = ''; importing.value = true
  try {
    const text = await readArticleFile(file)
    if (id !== importRequestId) return
    const entry = { name: file.name, content: text }
    if (content.value.length) pendingImport.value = entry
    else applyImport(entry)
  } catch (e) { if (id === importRequestId) importError.value = e.message || '文件读取失败，请重试。' }
  finally { if (id === importRequestId) importing.value = false }
}
async function handleSubmit() {
  if (busy.value || pendingImport.value) return
  error.value = ''
  if (importRequiresTitle.value && !title.value.trim()) { error.value = '导入文件后，请为这篇日记填写标题。'; return }
  if (!content.value.trim()) { error.value = '记下一点什么，再保存吧。'; return }
  loading.value = true
  try {
    const payload = { title: title.value.trim(), content: content.value, categoryId: categoryId.value }
    const { data } = editing.value ? await articleUpdate(route.params.id, payload) : await articlePublish(payload)
    router.replace(`/article/${data.data.id}`)
  } catch (e) { error.value = e.response?.data?.message || '暂时没能保存，你的文字仍保留在这里。' }
  finally { loading.value = false }
}
function cancel() { router.push(editing.value ? `/article/${route.params.id}` : '/') }
watch(() => route.params.id, loadEntry, { immediate: true })
loadCategories()
</script>
<template>
  <div class="publish-page page-enter">
    <router-link to="/" class="text-link publish-back"><BlogIcon name="arrow-left" />返回我的日记</router-link>
    <header class="page-heading"><p class="eyebrow">A PAGE FOR MYSELF</p><h1>{{ editing ? '再看看当时的自己。' : '今天，想记下什么？' }}</h1><p class="page-description">{{ published ? '这篇文章已发布到广场。保存后，标题和正文会同步更新到公开页面。' : '一句话也好，一整页也好。默认只对自己可见，保存后可另行发布到广场。' }}</p></header>
    <p v-if="fetching" class="status-panel text-muted" role="status">正在翻开这一页…</p>
    <div v-else-if="loadError" class="status-panel" role="alert"><p>{{ loadError }}</p><button class="btn" @click="loadEntry">重新加载</button></div>
    <form v-else class="publish-form" @submit.prevent="handleSubmit">
      <div class="import-strip"><div><strong>把已有的文字带进来</strong><p>支持 txt、md 文件，原文填入正文，导入后请填写标题。</p></div><input ref="fileInput" class="sr-only" type="file" accept=".txt,.md" aria-label="选择 txt 或 md 文件" tabindex="-1" :disabled="busy" @change="importFile" /><button type="button" class="btn" :disabled="busy" @click="fileInput?.click()">{{ importing ? '正在读取…' : '导入文件' }}</button></div>
      <p v-if="importedName" class="import-notice" role="status">已导入「{{ importedName }}」，可继续编辑，保存后才会存入日记本。</p>
      <p v-if="importError" class="error-msg" role="alert">{{ importError }}</p>
      <div class="form-group"><div class="editor-label"><label class="form-label" for="publish-title">标题<span class="optional">{{ importRequiresTitle ? '导入时必填' : '选填' }}</span></label><span>{{ title.length }} / 50</span></div><input id="publish-title" v-model="title" class="form-input title-input" type="text" :placeholder="importRequiresTitle ? '为导入的文字填写标题' : '也可以不取标题'" maxlength="50" :required="importRequiresTitle" :disabled="busy" aria-describedby="title-hint" /><p id="title-hint" class="form-hint">{{ importRequiresTitle ? '文件名不会自动作为标题，请填写你自己的标题。' : '不填标题时，会用正文第一行为这一页命名。' }}</p></div>
      <div class="form-group"><label class="form-label" for="publish-category">分类<span class="optional">选填</span></label><div class="category-picker"><select id="publish-category" v-model="categoryId" class="form-input" :disabled="busy"><option :value="null">未分类</option><option v-if="categoryId && !categories.some(item => item.id === categoryId)" :value="categoryId">原分类（暂未加载）</option><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option></select><button type="button" class="btn" :disabled="busy" :aria-expanded="addingCategory" @click="addingCategory = !addingCategory">{{ addingCategory ? '收起' : '新建分类' }}</button></div><div v-if="addingCategory" class="category-picker"><label for="new-category" class="sr-only">新分类名称</label><input id="new-category" v-model="categoryName" class="form-input" placeholder="分类名称，最多 40 字" maxlength="40" :disabled="busy" @keydown.enter.prevent="addCategory" /><button type="button" class="btn" :disabled="busy" @click="addCategory">{{ savingCategory ? '创建中…' : '创建并选用' }}</button></div><p v-if="categoryError" class="error-msg" role="alert">{{ categoryError }} <button type="button" class="btn btn-sm" :disabled="busy" @click="loadCategories">重新加载分类</button></p><p class="form-hint">可在首页管理分类；修改已有日记时，也能重新选择或取消分类。</p></div>
      <div class="form-group"><div class="editor-label"><label class="form-label" for="publish-content">正文</label><span>{{ content.length.toLocaleString() }} 字</span></div><textarea id="publish-content" v-model="content" class="form-input content-input" placeholder="可以是小事，可以是心情，也可以只是此刻突然冒出的念头。" maxlength="100000" required :disabled="busy"></textarea><p class="form-hint">支持换行与分段，最多 100,000 字。md 文件按原始文本保存。</p></div>
      <p v-if="error" class="error-msg" role="alert">{{ error }}</p>
      <div class="publish-bottom"><p><BlogIcon :name="published ? 'globe' : 'lock'" />{{ published ? '已公开，保存后会同步到广场。' : '存进自己的日记本，默认仅自己可见。' }}</p><div class="publish-actions"><button type="button" class="btn" :disabled="busy" @click="cancel">取消</button><button type="submit" class="btn btn-primary" :disabled="busy || !isLoggedIn"><BlogIcon name="pen" />{{ loading ? '正在保存…' : published ? '保存并同步更新' : editing ? '保存修改' : '存下这一页' }}</button></div></div>
    </form>
    <ConfirmDialog :open="!!pendingImport" title="用文件内容替换正文？" description="当前正文已有文字。确认后会用文件内容替换，标题与分类会保留。" confirm-text="替换正文" :destructive="false" @cancel="pendingImport = null" @confirm="applyImport(pendingImport)" />
  </div>
</template>
<style scoped>
.publish-page { max-width: 780px; margin-inline: auto; }
.publish-back { color: var(--text-muted); margin-bottom: 30px; }
.publish-form { display: flex; flex-direction: column; gap: 28px; }
.import-strip { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 18px 20px; border: 1px dashed var(--border); border-radius: 5px; }
.import-strip strong { font-size: .85rem; font-weight: 500; color: var(--text-h); }
.import-strip p { margin-top: 4px; font-size: .75rem; color: var(--text-muted); }
.import-strip .btn { flex-shrink: 0; }
.import-notice { color: var(--accent); font-size: .8rem; overflow-wrap: anywhere; }
.category-picker { display: flex; gap: 10px; }
.category-picker .btn { flex-shrink: 0; }
.editor-label { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; }
.editor-label > span { color: var(--text-muted); font-size: .7rem; }
.title-input { font-family: var(--serif); font-size: 1.35rem; padding: 18px 20px; }
.content-input { min-height: 370px; padding: 22px; font-size: 1rem; line-height: 2; }
.publish-bottom { display: flex; align-items: center; justify-content: space-between; gap: 20px; border-top: 1px solid var(--border); padding-top: 25px; }
.publish-bottom > p { display: flex; align-items: center; gap: 6px; color: var(--text-muted); font-size: .75rem; }
.publish-bottom .icon { width: 14px; height: 14px; }
.publish-actions { display: flex; flex-shrink: 0; gap: 12px; }
@media (max-width: 600px) { .import-strip { align-items: flex-start; flex-direction: column; padding: 16px; } .publish-bottom { flex-direction: column; align-items: flex-start; } .publish-actions { width: 100%; justify-content: flex-end; } .content-input { min-height: 320px; padding: 16px; } .title-input { padding: 14px 16px; font-size: 1.15rem; } }
</style>
