import { defineStore } from 'pinia'
import { fetchMe, login as apiLogin, logout as apiLogout } from '../api/auth'
import type { CurrentUser } from '../types'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as CurrentUser | null,
    initialized: false,
  }),
  getters: {
    isAuthenticated: (state) => state.user?.authenticated === true,
    isAdmin: (state) => state.user?.role === 'ADMIN',
  },
  actions: {
    /** アプリ起動時にセッション状態を確認する(CSRF Cookieの取得も兼ねる)。 */
    async initialize() {
      if (this.initialized) return
      try {
        this.user = await fetchMe()
      } catch {
        this.user = { authenticated: false }
      } finally {
        this.initialized = true
      }
    },
    async login(username: string, password: string) {
      this.user = await apiLogin(username, password)
    },
    async logout() {
      try {
        await apiLogout()
      } finally {
        this.user = { authenticated: false }
      }
    },
    /** 401検知時にセッション切れとしてクリアする。 */
    markSessionExpired() {
      this.user = { authenticated: false }
    },
  },
})
