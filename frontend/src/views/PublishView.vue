<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { articlePublish } from '@/api'

const router = useRouter()

const title = ref('')
const content = ref('')
const error = ref('')
const loading = ref(false)

async function handleSubmit() {
  error.value = ''
  if (!title.value.trim()) {
    error.value = 'Title is required'
    return
  }
  if (!content.value.trim()) {
    error.value = 'Content is required'
    return
  }

  loading.value = true
  try {
    await articlePublish({
      title: title.value,
      content: content.value,
    })
    router.push('/')
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Failed to publish'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="publish-page">
    <h1>Write a New Article</h1>

    <form class="publish-form" @submit.prevent="handleSubmit">
      <div class="form-group">
        <label class="form-label" for="publish-title">Title</label>
        <input
          id="publish-title"
          v-model="title"
          class="form-input"
          type="text"
          placeholder="Article title"
        />
      </div>

      <div class="form-group">
        <label class="form-label" for="publish-content">Content</label>
        <textarea
          id="publish-content"
          v-model="content"
          class="form-input"
          placeholder="Write your article content here..."
        ></textarea>
      </div>

      <p v-if="error" class="error-msg">{{ error }}</p>

      <div class="publish-actions">
        <button type="submit" class="btn btn-primary" :disabled="loading">
          {{ loading ? 'Publishing...' : 'Publish' }}
        </button>
        <button type="button" class="btn btn-secondary" @click="router.push('/')">
          Cancel
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.publish-page h1 {
  margin-bottom: 24px;
}
.publish-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.publish-actions {
  display: flex;
  gap: 10px;
  margin-top: 8px;
}
</style>
