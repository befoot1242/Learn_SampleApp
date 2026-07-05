<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toApiError } from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const username = ref('')
const password = ref('')
const errorMessage = ref('')
const submitting = ref(false)

async function onSubmit() {
  errorMessage.value = ''
  submitting.value = true
  try {
    await auth.login(username.value, password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.push(redirect)
  } catch (e) {
    errorMessage.value = toApiError(e).message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="card login-card">
      <h1>取引管理システム</h1>
      <p class="text-sub">ユーザIDとパスワードを入力してログインしてください。</p>
      <div v-if="errorMessage" class="alert error">{{ errorMessage }}</div>
      <form @submit.prevent="onSubmit">
        <div class="field">
          <label class="required" for="username">ユーザID</label>
          <input id="username" v-model="username" autocomplete="username" required />
        </div>
        <div class="field">
          <label class="required" for="password">パスワード</label>
          <input id="password" v-model="password" type="password" autocomplete="current-password" required />
        </div>
        <button class="primary login-button" type="submit" :disabled="submitting">
          {{ submitting ? 'ログイン中...' : 'ログイン' }}
        </button>
      </form>
      <p class="text-sub demo-note">デモ用: admin / password123(管理者)、user1 / password123(一般)</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: min(420px, 92vw);
  padding: 32px;
}

.login-button {
  width: 100%;
  margin-top: 8px;
}

.demo-note {
  margin-top: 16px;
}
</style>
