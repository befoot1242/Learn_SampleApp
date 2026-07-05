<script setup lang="ts">
import AppLayout from '../components/AppLayout.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
</script>

<template>
  <AppLayout>
    <h1>ホーム</h1>
    <p>{{ auth.user?.displayName }} さん、ようこそ。メニューから機能を選択してください。</p>
    <div class="menu-cards">
      <router-link class="card menu-card" :to="{ name: 'transactions' }">
        <h3>取引一覧</h3>
        <p class="text-sub">外部システムの取引情報を検索・参照します。</p>
      </router-link>
      <router-link class="card menu-card" :to="{ name: 'transaction-entry' }">
        <h3>取引登録</h3>
        <p class="text-sub">新しい取引を入力し、外部システムへ登録します。</p>
      </router-link>
      <router-link v-if="auth.isAdmin" class="card menu-card" :to="{ name: 'users' }">
        <h3>ユーザ管理</h3>
        <p class="text-sub">ユーザの登録・編集・無効化とロール管理を行います。</p>
      </router-link>
    </div>
  </AppLayout>
</template>

<style scoped>
.menu-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.menu-card {
  text-decoration: none;
  color: inherit;
  transition: box-shadow 0.15s;
}

.menu-card:hover {
  box-shadow: 0 2px 10px rgba(20, 53, 92, 0.15);
}
</style>
