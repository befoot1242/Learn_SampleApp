<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import { fetchDevelopmentTasks } from '../api/developmentTasks'
import { isUnauthorized, toApiError } from '../api/client'
import { useAuthStore } from '../stores/auth'
import type { ApiError, DevelopmentTaskSummary } from '../types'

const router = useRouter()
const auth = useAuthStore()
const tasks = ref<DevelopmentTaskSummary[] | null>(null)
const loading = ref(false)
const error = ref<ApiError | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    tasks.value = await fetchDevelopmentTasks()
  } catch (e) {
    if (isUnauthorized(e)) {
      auth.markSessionExpired()
      await router.push({ name: 'login' })
      return
    }
    error.value = toApiError(e)
  } finally {
    loading.value = false
  }
}

function open(id: string) {
  void router.push({ name: 'development-task-detail', params: { id } })
}

onMounted(load)
</script>

<template>
  <AppLayout>
    <h1>開発タスク一覧</h1>
    <p class="text-sub">タスクを選択すると詳細を表示します。</p>
    <div v-if="error" class="alert error" role="alert">
      {{ error.message }}
      <button v-if="error.retryable !== false" @click="load">再試行</button>
    </div>
    <p v-if="loading" class="text-sub">読み込み中...</p>
    <table v-else-if="tasks" class="data">
      <thead><tr><th>タスクID</th><th>タスク名</th><th>状態</th><th>更新日</th></tr></thead>
      <tbody>
        <tr v-for="task in tasks" :key="task.id" class="clickable" @click="open(task.id)">
          <td>{{ task.id }}</td>
          <td>
            <router-link :to="{ name: 'development-task-detail', params: { id: task.id } }" @click.stop>
              {{ task.name }}
            </router-link>
          </td>
          <td>{{ task.status }}</td><td>{{ task.updatedDate }}</td>
        </tr>
        <tr v-if="tasks.length === 0"><td colspan="4" class="text-sub">開発タスクはありません</td></tr>
      </tbody>
    </table>
  </AppLayout>
</template>
