import { http } from './client'
import type { CurrentUser } from '../types'

export async function fetchMe(): Promise<CurrentUser> {
  const { data } = await http.get<CurrentUser>('/auth/me')
  return data
}

export async function login(username: string, password: string): Promise<CurrentUser> {
  const { data } = await http.post<CurrentUser>('/auth/login', { username, password })
  return data
}

export async function logout(): Promise<void> {
  await http.post('/auth/logout')
}
