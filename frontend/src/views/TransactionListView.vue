<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import { searchTransactions } from '../api/transactions'
import { isUnauthorized, toApiError } from '../api/client'
import { useAuthStore } from '../stores/auth'
import type { ApiError, TransactionPage } from '../types'

const router = useRouter()
const auth = useAuthStore()

const dateFrom = ref('')
const dateTo = ref('')
const status = ref('')
const partnerName = ref('')
const page = ref(0)
const size = 10

const result = ref<TransactionPage | null>(null)
const loading = ref(false)
const error = ref<ApiError | null>(null)

const statusLabels: Record<string, string> = {
  COMPLETED: '完了',
  PENDING: '処理中',
  CANCELLED: '取消',
}

async function search(targetPage = 0) {
  loading.value = true
  error.value = null
  try {
    page.value = targetPage
    result.value = await searchTransactions({
      dateFrom: dateFrom.value,
      dateTo: dateTo.value,
      status: status.value,
      partnerName: partnerName.value,
      page: targetPage,
      size,
    })
  } catch (e) {
    if (isUnauthorized(e)) {
      auth.markSessionExpired()
      router.push({ name: 'login' })
      return
    }
    error.value = toApiError(e)
  } finally {
    loading.value = false
  }
}

function openDetail(id: string) {
  router.push({ name: 'transaction-detail', params: { id } })
}

function formatAmount(amount: number): string {
  return amount.toLocaleString('ja-JP')
}

onMounted(() => search(0))
</script>

<template>
  <AppLayout>
    <h1>取引一覧</h1>

    <div class="card">
      <form class="toolbar" @submit.prevent="search(0)">
        <div class="cond">
          <label>取引日(From)</label>
          <input v-model="dateFrom" type="date" />
        </div>
        <div class="cond">
          <label>取引日(To)</label>
          <input v-model="dateTo" type="date" />
        </div>
        <div class="cond">
          <label>状態</label>
          <select v-model="status">
            <option value="">すべて</option>
            <option value="COMPLETED">完了</option>
            <option value="PENDING">処理中</option>
            <option value="CANCELLED">取消</option>
          </select>
        </div>
        <div class="cond grow">
          <label>取引先名</label>
          <input v-model="partnerName" placeholder="部分一致" />
        </div>
        <button class="primary" type="submit" :disabled="loading">検索</button>
      </form>
    </div>

    <!-- 外部API障害時は同一画面で再試行導線を表示する -->
    <div v-if="error" class="alert error retry-area">
      <span>{{ error.message }}</span>
      <button v-if="error.retryable !== false" @click="search(page)">再試行</button>
    </div>

    <p v-if="loading" class="text-sub">読み込み中...</p>

    <template v-if="result && !loading">
      <table class="data">
        <thead>
          <tr>
            <th>取引ID</th>
            <th>取引日</th>
            <th>取引先</th>
            <th class="amount">金額(円)</th>
            <th>状態</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="tx in result.content"
            :key="tx.transactionId"
            class="clickable"
            @click="openDetail(tx.transactionId)"
          >
            <td>{{ tx.transactionId }}</td>
            <td>{{ tx.transactionDate }}</td>
            <td>{{ tx.partnerName }}</td>
            <td class="amount">{{ formatAmount(tx.amount) }}</td>
            <td>
              <span class="badge" :class="tx.status.toLowerCase()">
                {{ statusLabels[tx.status] ?? tx.status }}
              </span>
            </td>
          </tr>
          <tr v-if="result.content.length === 0">
            <td colspan="5" class="text-sub">該当する取引がありません</td>
          </tr>
        </tbody>
      </table>

      <div class="toolbar pager">
        <span class="text-sub">全{{ result.totalElements }}件</span>
        <span class="spacer" />
        <button :disabled="page <= 0" @click="search(page - 1)">前へ</button>
        <span class="text-sub">{{ page + 1 }} / {{ result.totalPages }} ページ</span>
        <button :disabled="page >= result.totalPages - 1" @click="search(page + 1)">次へ</button>
      </div>
    </template>
  </AppLayout>
</template>

<style scoped>
.cond {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: var(--color-text-sub);
}

.cond.grow {
  flex: 1;
  min-width: 160px;
}

.toolbar {
  align-items: flex-end;
}

.pager {
  margin-top: 12px;
}

.retry-area {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}

table.data {
  margin-top: 16px;
}
</style>
