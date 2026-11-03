<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import { useAuthStore } from '../stores/auth'
import { useDevelopmentTaskStore } from '../stores/developmentTask'
import type { DevelopmentTaskTab } from '../types'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const store = useDevelopmentTaskStore()
const tabs: { id: DevelopmentTaskTab; label: string }[] = [
  { id: 'requirements', label: '要件' },
  { id: 'basic', label: '基本' },
  { id: 'detail', label: '詳細' },
  { id: 'implementation', label: '実装' },
  { id: 'remaining', label: '残タスク' },
]
const selected = ref<DevelopmentTaskTab>('requirements')
const state = computed(() => store.tabs[selected.value])

async function load(tab: DevelopmentTaskTab) {
  if (await store.load(tab)) {
    auth.markSessionExpired()
    await router.push({ name: 'login' })
  }
}

function select(tab: DevelopmentTaskTab) {
  selected.value = tab
  void load(tab)
}

function onTabKey(event: KeyboardEvent, index: number) {
  let next = index
  if (event.key === 'ArrowRight') next = (index + 1) % tabs.length
  else if (event.key === 'ArrowLeft') next = (index + tabs.length - 1) % tabs.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = tabs.length - 1
  else return
  event.preventDefault()
  const tab = tabs[next]
  if (!tab) return
  select(tab.id)
  const list = (event.currentTarget as HTMLElement).parentElement
  list?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next]?.focus()
}

watch(() => route.params.id, (id) => {
  if (route.name !== 'development-task-detail' || typeof id !== 'string') return
  store.open(id)
  selected.value = 'requirements'
  void load('requirements')
}, { immediate: true })

onUnmounted(() => store.clear())
</script>

<template>
  <AppLayout>
    <h1>開発タスク詳細</h1>
    <p class="text-sub">タスクID：{{ store.taskId }}</p>
    <div class="tabs" role="tablist" aria-label="開発タスクの情報">
      <button
        v-for="(tab, index) in tabs"
        :id="`task-tab-${tab.id}`"
        :key="tab.id"
        role="tab"
        :aria-selected="selected === tab.id"
        :aria-controls="`task-panel-${tab.id}`"
        :tabindex="selected === tab.id ? 0 : -1"
        @click="select(tab.id)"
        @keydown="onTabKey($event, index)"
      >{{ tab.label }}</button>
    </div>
    <section
      :id="`task-panel-${selected}`"
      class="card panel"
      role="tabpanel"
      :aria-labelledby="`task-tab-${selected}`"
      tabindex="0"
    >
      <p v-if="!state || state.status === 'loading'" class="text-sub">読み込み中...</p>
      <div v-else-if="state.status === 'error'" class="alert error" role="alert">
        {{ state.error.message }}
        <button v-if="state.error.retryable !== false" @click="load(selected)">再試行</button>
      </div>
      <p v-else-if="!state.data.content" class="text-sub">このタブの情報はありません</p>
      <p v-else class="content">{{ state.data.content }}</p>
    </section>
    <router-link class="back" :to="{ name: 'development-tasks' }">一覧へ戻る</router-link>
  </AppLayout>
</template>

<style scoped>
.tabs { display: flex; flex-wrap: wrap; gap: 4px; }
.tabs button[aria-selected="true"] { background: var(--color-primary); color: white; }
.panel { margin-bottom: 20px; min-height: 120px; }
.content { white-space: pre-wrap; overflow-wrap: anywhere; }
.back { display: inline-block; }
</style>
