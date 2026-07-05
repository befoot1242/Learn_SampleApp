import { http } from './client'
import type { AppUser, RoleHistory } from '../types'

export interface UserSearchCondition {
  keyword?: string
  role?: string
  status?: string
}

export async function searchUsers(condition: UserSearchCondition): Promise<AppUser[]> {
  const params: Record<string, string> = {}
  if (condition.keyword) params.keyword = condition.keyword
  if (condition.role) params.role = condition.role
  if (condition.status) params.status = condition.status
  const { data } = await http.get<AppUser[]>('/users', { params })
  return data
}

export interface CreateUserInput {
  username: string
  displayName: string
  email: string
  role: string
  password: string
}

export async function createUser(input: CreateUserInput): Promise<AppUser> {
  const { data } = await http.post<AppUser>('/users', input)
  return data
}

export interface UpdateUserInput {
  displayName: string
  email: string
  role: string
  status: string
}

export async function updateUser(id: number, input: UpdateUserInput): Promise<AppUser> {
  const { data } = await http.put<AppUser>(`/users/${id}`, input)
  return data
}

export async function disableUser(id: number): Promise<AppUser> {
  const { data } = await http.post<AppUser>(`/users/${id}/disable`)
  return data
}

export async function fetchRoleHistory(id: number): Promise<RoleHistory[]> {
  const { data } = await http.get<RoleHistory[]>(`/users/${id}/role-history`)
  return data
}
