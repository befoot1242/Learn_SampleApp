import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      name: 'home',
      component: () => import('../views/HomeView.vue'),
    },
    {
      path: '/users',
      name: 'users',
      component: () => import('../views/UserManagementView.vue'),
      meta: { adminOnly: true },
    },
    {
      path: '/transactions',
      name: 'transactions',
      component: () => import('../views/TransactionListView.vue'),
    },
    {
      path: '/transactions/entry',
      name: 'transaction-entry',
      component: () => import('../views/TransactionEntryView.vue'),
    },
    {
      path: '/transactions/:id',
      name: 'transaction-detail',
      component: () => import('../views/TransactionDetailView.vue'),
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

// 認証・認可ガード
router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.initialize()

  if (to.meta.public) {
    // ログイン済みでログイン画面に来たらホームへ
    if (auth.isAuthenticated && to.name === 'login') return { name: 'home' }
    return true
  }
  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.adminOnly && !auth.isAdmin) {
    return { name: 'home' }
  }
  return true
})

export default router
