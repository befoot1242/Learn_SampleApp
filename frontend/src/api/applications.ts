import { http } from './client'
import type { ApplicationRequest, ApplicationResponse } from '../types'

export async function registerApplication(request: ApplicationRequest): Promise<ApplicationResponse> {
  const { data } = await http.post<ApplicationResponse>('/applications', request)
  return data
}
