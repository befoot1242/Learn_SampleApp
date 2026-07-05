<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

async function onLogout() {
  await auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="layout">
    <header class="header">
      <span class="app-title">取引管理システム</span>
      <span class="spacer" />
      <span v-if="auth.user" class="user-info">
        {{ auth.user.displayName }}
        <span class="badge" :class="auth.isAdmin ? 'admin' : 'user'">
          {{ auth.isAdmin ? '管理者' : '一般' }}
        </span>
      </span>
      <button @click="onLogout">ログアウト</button>
    </header>
    <div class="body">
      <nav class="menu">
        <router-link :to="{ name: 'home' }">ホーム</router-link>
        <router-link :to="{ name: 'transactions' }">取引一覧</router-link>
        <router-link :to="{ name: 'transaction-entry' }">取引登録</router-link>
        <router-link v-if="auth.isAdmin" :to="{ name: 'users' }">ユーザ管理</router-link>
      </nav>
      <main class="content">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 24px;
  background: #14355c;
  color: #fff;
}

.app-title {
  font-size: 18px;
  font-weight: 700;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.body {
  display: flex;
  flex: 1;
}

.menu {
  width: 200px;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
  padding: 16px 0;
  display: flex;
  flex-direction: column;
}

.menu a {
  padding: 10px 24px;
  color: var(--color-text);
  text-decoration: none;
  font-size: 14px;
}

.menu a:hover {
  background: #f0f6ff;
}

.menu a.router-link-active {
  background: #e5efff;
  color: var(--color-primary-dark);
  font-weight: 600;
  border-right: 3px solid var(--color-primary);
}

.content {
  flex: 1;
  padding: 24px 32px;
  min-width: 0;
}
</style>
