import { http } from './client'
import type { DevelopmentTaskSummary, DevelopmentTaskTab, DevelopmentTaskTabContent } from '../types'

export async function fetchDevelopmentTasks(): Promise<DevelopmentTaskSummary[]> {
  const { data } = await http.get<DevelopmentTaskSummary[]>('/development-tasks')
  return data
}

export async function fetchDevelopmentTaskTab(
  id: string,
  tab: DevelopmentTaskTab,
  signal: AbortSignal,
): Promise<DevelopmentTaskTabContent> {
  const { data } = await http.get<DevelopmentTaskTabContent>(
    `/development-tasks/${encodeURIComponent(id)}/tabs/${tab}`,
    { signal },
  )
  return data
}
