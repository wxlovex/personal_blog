<script setup>
import { hasVisibleText } from '@/utils/text'
import { ref } from 'vue'
import { categoryCreate, categoryUpdate, categoryDelete } from '@/api'
import ConfirmDialog from './ConfirmDialog.vue'

const props = defineProps({ categories: { type: Array, required: true } })
const emit = defineEmits(['changed'])
const name = ref('')
const editingId = ref(null)
const busy = ref(false)
const error = ref('')
const toDelete = ref(null)
const deleteError = ref('')

function reset() { editingId.value = null; name.value = ''; error.value = '' }
function edit(category) { editingId.value = category.id; name.value = category.name; error.value = '' }
async function save() {
  if (busy.value) return
  error.value = ''
  if (!hasVisibleText(name.value)) { error.value = '请填写分类名称。'; return }
  busy.value = true
  try {
    const payload = { name: name.value.trim() }
    // Renaming a category should preserve its existing description.
    if (editingId.value) {
      const category = props.categories.find(item => item.id === editingId.value)
      payload.description = category?.description || ''
      await categoryUpdate(editingId.value, payload)
    } else await categoryCreate(payload)
    reset()
    emit('changed')
  } catch (e) { error.value = e.response?.data?.message || '暂时没能保存分类，请稍后重试。' }
  finally { busy.value = false }
}
async function remove() {
  if (busy.value || !toDelete.value) return
  busy.value = true
  deleteError.value = ''
  try {
    await categoryDelete(toDelete.value.id)
    if (editingId.value === toDelete.value.id) reset()
    toDelete.value = null
    emit('changed')
  } catch (e) { deleteError.value = e.response?.data?.message || '暂时没能删除分类，请稍后重试。' }
  finally { busy.value = false }
}
</script>

<template>
  <section class="category-manager" aria-label="分类管理">
    <div class="manager-heading"><h3>给记录一个归处</h3><p>分类只属于你。点击每篇日记旁的“修改分类”，即可调整归属。</p></div>
    <form class="category-form" @submit.prevent="save">
      <label for="category-name" class="sr-only">分类名称</label>
      <input id="category-name" v-model="name" class="form-input" placeholder="例如：生活、读书、灵感" maxlength="40" :disabled="busy" required />
      <button class="btn btn-primary" :disabled="busy">{{ busy ? '处理中…' : editingId ? '保存名称' : '新建分类' }}</button>
      <button v-if="editingId" type="button" class="btn" :disabled="busy" @click="reset">取消修改</button>
    </form>
    <p v-if="error" class="error-msg" role="alert">{{ error }}</p>
    <ul v-if="categories.length" class="category-items">
      <li v-for="category in categories" :key="category.id">
        <span class="category-name">{{ category.name }}<small>{{ category.articleCount || 0 }} 篇</small></span>
        <div><button class="btn btn-quiet btn-sm" :disabled="busy" :aria-label="`重命名分类：${category.name}`" @click="edit(category)">重命名</button><button class="btn btn-quiet btn-sm" :disabled="busy" :aria-label="`删除分类：${category.name}`" @click="toDelete = category; deleteError = ''">删除</button></div>
      </li>
    </ul>
    <p v-else class="form-hint">还没有分类，先为喜欢记录的事取个名字。</p>
    <p class="form-hint">删除分类前，需要先将其中的记录移至其他分类或设为未分类。</p>
    <ConfirmDialog :open="!!toDelete" :busy="busy" title="删除这个分类？" :description="`将删除分类「${toDelete?.name || ''}」。分类中有记录时无法删除。`" :error="deleteError" @cancel="toDelete = null" @confirm="remove" />
  </section>
</template>

<style scoped>
.category-manager { border: 1px solid var(--border); background: var(--surface); padding: 24px; border-radius: 5px; display: grid; gap: 16px; margin-bottom: 24px; }
.manager-heading h3 { font-family: var(--serif); font-weight: 500; }
.manager-heading p { color: var(--text-muted); font-size: .8rem; margin-top: 5px; }
.category-form { display: flex; flex-wrap: wrap; gap: 10px; }
.category-form input { flex: 1 1 180px; }
.category-items { padding: 0; margin: 0; list-style: none; }
.category-items li { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-block: 10px; border-bottom: 1px solid var(--border); }
.category-name { font-size: .875rem; overflow-wrap: anywhere; min-width: 0; }
.category-name small { color: var(--text-muted); font-size: .7rem; margin-left: 12px; white-space: nowrap; }
.category-items li > div { display: flex; flex-shrink: 0; }
@media (max-width: 600px) { .category-manager { padding: 18px 14px; } .category-items .btn { padding-inline: 8px; } }
</style>
