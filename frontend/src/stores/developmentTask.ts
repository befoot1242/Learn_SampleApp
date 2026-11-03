import { ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchDevelopmentTaskTab } from '../api/developmentTasks'
import { isUnauthorized, toApiError } from '../api/client'
import type { ApiError, DevelopmentTaskTab, DevelopmentTaskTabContent } from '../types'

export type TabState =
  | { status: 'loading' }
  | { status: 'success'; data: DevelopmentTaskTabContent }
  | { status: 'error'; error: ApiError }

export const useDevelopmentTaskStore = defineStore('developmentTask', () => {
  const taskId = ref<string | null>(null)
  const tabs = ref<Partial<Record<DevelopmentTaskTab, TabState>>>({})
  const pending = new Map<DevelopmentTaskTab, { controller: AbortController; promise: Promise<boolean> }>()
  let generation = 0

  function clear() {
    generation++
    for (const request of pending.values()) request.controller.abort()
    pending.clear()
    taskId.value = null
    tabs.value = {}
  }

  function open(id: string) {
    clear()
    taskId.value = id
  }

  function load(tab: DevelopmentTaskTab): Promise<boolean> {
    const id = taskId.value
    if (!id || tabs.value[tab]?.status === 'success') return Promise.resolve(false)
    const existing = pending.get(tab)
    if (existing) return existing.promise

    const currentGeneration = generation
    const controller = new AbortController()
    tabs.value[tab] = { status: 'loading' }
    const promise = (async () => {
      try {
        const data = await fetchDevelopmentTaskTab(id, tab, controller.signal)
        if (generation !== currentGeneration) return false
        tabs.value[tab] = { status: 'success', data }
        return false
      } catch (error) {
        if (generation !== currentGeneration) return false
        if (isUnauthorized(error)) return true
        tabs.value[tab] = { status: 'error', error: toApiError(error) }
        return false
      } finally {
        if (generation === currentGeneration) pending.delete(tab)
      }
    })()
    pending.set(tab, { controller, promise })
    return promise
  }

  return { taskId, tabs, open, load, clear }
})
