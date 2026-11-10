<script setup>
import { ref, watch, useId } from 'vue'
import BlogIcon from './BlogIcon.vue'
const props = defineProps({
  open: Boolean, busy: Boolean, title: String, description: String, error: String,
  confirmText: { type: String, default: '确认删除' },
  busyText: { type: String, default: '正在删除…' },
  destructive: { type: Boolean, default: true },
})
const emit = defineEmits(['cancel', 'confirm'])
const dialog = ref(null)
const titleId = useId()
const descriptionId = useId()
watch([() => props.open, dialog], ([open, element]) => {
  if (!element) return
  if (open && !element.open) element.showModal()
  else if (!open && element.open) element.close()
}, { flush: 'post' })
function cancel(event) { event?.preventDefault(); if (!props.busy) emit('cancel') }
</script>
<template>
  <dialog ref="dialog" class="confirm-dialog" :aria-labelledby="titleId" :aria-describedby="descriptionId" @cancel="cancel">
    <div class="dialog-symbol" :class="{ neutral: !destructive }"><BlogIcon :name="destructive ? 'trash' : 'pen'" /></div>
    <h2 :id="titleId">{{ title }}</h2>
    <p :id="descriptionId" class="dialog-description">{{ description }}</p>
    <p v-if="error" class="error-msg" role="alert">{{ error }}</p>
    <div class="dialog-actions">
      <button class="btn" :disabled="busy" autofocus @click="cancel">再想一想</button>
      <button class="btn" :class="destructive ? 'btn-danger' : 'btn-primary'" :disabled="busy" @click="emit('confirm')">{{ busy ? busyText : confirmText }}</button>
    </div>
  </dialog>
</template>
<style scoped>
.confirm-dialog { width: min(420px, calc(100% - 40px)); padding: 32px; color: var(--text); background: var(--surface); border: 1px solid var(--border); border-radius: 8px; }
.confirm-dialog::backdrop { background: rgb(20 30 20 / .45); backdrop-filter: blur(3px); }
.dialog-symbol { color: var(--danger); margin-bottom: 18px; }
.dialog-symbol.neutral { color: var(--accent); }
.dialog-symbol .icon { width: 25px; height: 25px; }
h2 { font-family: var(--serif); font-weight: 500; font-size: 1.4rem; }
.dialog-description { color: var(--text-muted); font-size: .875rem; margin-block: 12px 24px; overflow-wrap: anywhere; }
.dialog-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 24px; }
</style>
