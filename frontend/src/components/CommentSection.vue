<script setup>
import { formatTime } from '@/utils/time'
import { ref, onMounted } from 'vue'
import { commentList, commentPublish } from '@/api'

const props = defineProps({
  articleId: {
    type: [Number, String],
    required: true,
  },
})

const comments = ref([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const error = ref('')
const newComment = ref('')
const submitting = ref(false)
const submitError = ref('')

async function fetchComments(append = false) {
  loading.value = true
  error.value = ''
  try {
    const nextPage = append ? page.value + 1 : 1
    const res = await commentList(props.articleId, nextPage)
    const result = res.data.data
    comments.value = append ? [...comments.value, ...result.records] : result.records
    page.value = nextPage
    total.value = result.total
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Failed to load comments'
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  const content = newComment.value.trim()
  if (!content) return

  submitting.value = true
  submitError.value = ''
  try {
    await commentPublish({ articleId: Number(props.articleId), content })
    newComment.value = ''
    await fetchComments()
  } catch (e) {
    submitError.value = e.response?.data?.message || e.message || 'Failed to publish comment'
  } finally {
    submitting.value = false
  }
}



onMounted(() => fetchComments())
</script>

<template>
  <section class="comment-section">
    <h2 class="comment-heading">
      Comments
      <span v-if="total" class="comment-count">{{ total }}</span>
    </h2>

    <!-- Comment form -->
    <div class="comment-form">
      <textarea
        v-model="newComment"
        class="form-input comment-textarea"
        placeholder="Share your thoughts..."
        rows="3"
        :disabled="submitting"
      ></textarea>
      <div class="comment-form-footer">
        <p v-if="submitError" class="error-msg">{{ submitError }}</p>
        <button
          class="btn btn-primary comment-submit"
          :disabled="submitting || !newComment.trim()"
          @click="handleSubmit"
        >
          {{ submitting ? 'Posting...' : 'Post Comment' }}
        </button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="comment-loading">Loading comments...</div>

    <!-- Error -->
    <p v-if="error" class="error-msg">{{ error }}</p>

    <!-- Comment list -->
    <div v-if="!loading && !error" class="comment-list">
      <div v-if="!comments.length" class="comment-empty">
        No comments yet. Be the first to share your thoughts!
      </div>

      <div
        v-for="comment in comments"
        :key="comment.id"
        class="comment-item"
      >
        <div class="comment-meta text-muted">
          <span>{{ formatTime(comment.createTime) }}</span>
        </div>
        <p class="comment-content">{{ comment.content }}</p>
      </div>
    </div>
    <button v-if="comments.length < total" class="btn btn-secondary"
      :disabled="loading" @click="fetchComments(true)">
      {{ loading ? 'Loading...' : 'Load more comments' }}
    </button>
  </section>
</template>

<style scoped>
.comment-section {
  margin-top: 48px;
  padding-top: 28px;
  border-top: 1px solid var(--border);
}

.comment-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}

.comment-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  padding: 0 7px;
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--accent);
  background: var(--accent-bg);
  border-radius: 12px;
}

/* ── Form ── */

.comment-form {
  margin-bottom: 28px;
}

.comment-textarea {
  min-height: 80px;
  resize: vertical;
}

.comment-form-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 10px;
  min-height: 32px;
}

.comment-form-footer .error-msg {
  margin-top: 0;
  margin-right: auto;
}

/* ── Loading ── */

.comment-loading {
  text-align: center;
  padding: 32px 0;
  color: var(--text-muted);
  font-size: 0.9rem;
}

/* ── Empty state ── */

.comment-empty {
  text-align: center;
  padding: 32px 0;
  color: var(--text-muted);
  font-size: 0.9rem;
}

/* ── Comment items ── */

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.comment-item {
  padding: 16px 0;
  border-bottom: 1px solid var(--border);
}

.comment-item:first-child {
  padding-top: 0;
}

.comment-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.comment-meta {
  margin-bottom: 6px;
  font-size: 0.8rem;
}

.comment-content {
  line-height: 1.7;
  white-space: pre-wrap;
  word-wrap: break-word;
}
</style>
