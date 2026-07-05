<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import { fetchTransactionDetail } from '../api/transactions'
import { isUnauthorized, toApiError } from '../api/client'
import { useAuthStore } from '../stores/auth'
import type { ApiError, TransactionDetail } from '../types'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const detail = ref<TransactionDetail | null>(null)
const loading = ref(false)
const error = ref<ApiError | null>(null)

const statusLabels: Record<string, string> = {
  COMPLETED: '完了',
  PENDING: '処理中',
  CANCELLED: '取消',
}

const eventLabels: Record<string, string> = {
  REGISTERED: '登録',
  APPROVED: '承認',
  CANCELLED: '取消',
}

async function load() {
  loading.value = true
  error.value = null
  try {
    detail.value = await fetchTransactionDetail(String(route.params.id))
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

function formatAmount(amount: number): string {
  return amount.toLocaleString('ja-JP')
}

function formatDateTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<template>
  <AppLayout>
    <div class="toolbar">
      <h1>取引詳細</h1>
      <span class="spacer" />
      <button @click="router.push({ name: 'transactions' })">一覧へ戻る</button>
      <button @click="load" :disabled="loading">再読込</button>
    </div>

    <div v-if="error" class="alert error retry-area">
      <span>{{ error.message }}</span>
      <button v-if="error.retryable !== false" @click="load">再試行</button>
    </div>

    <p v-if="loading" class="text-sub">読み込み中...</p>

    <template v-if="detail && !loading">
      <div class="card">
        <h3>ヘッダ情報</h3>
        <dl class="header-grid">
          <div><dt>取引ID</dt><dd>{{ detail.transactionId }}</dd></div>
          <div><dt>取引日</dt><dd>{{ detail.transactionDate }}</dd></div>
          <div><dt>取引先</dt><dd>{{ detail.partnerName }}</dd></div>
          <div><dt>金額</dt><dd>{{ formatAmount(detail.amount) }} 円</dd></div>
          <div>
            <dt>状態</dt>
            <dd>
              <span class="badge" :class="detail.status.toLowerCase()">
                {{ statusLabels[detail.status] ?? detail.status }}
              </span>
            </dd>
          </div>
          <div><dt>備考</dt><dd>{{ detail.note || '-' }}</dd></div>
        </dl>
      </div>

      <div class="card section">
        <h3>明細</h3>
        <table class="data" v-if="detail.lines.length > 0">
          <thead>
            <tr>
              <th>行</th>
              <th>品目</th>
              <th class="amount">数量</th>
              <th class="amount">単価(円)</th>
              <th class="amount">金額(円)</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="line in detail.lines" :key="line.lineNo">
              <td>{{ line.lineNo }}</td>
              <td>{{ line.itemName }}</td>
              <td class="amount">{{ line.quantity }}</td>
              <td class="amount">{{ formatAmount(line.unitPrice) }}</td>
              <td class="amount">{{ formatAmount(line.quantity * line.unitPrice) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="text-sub">明細情報はありません</p>
      </div>

      <div class="card section">
        <h3>履歴</h3>
        <table class="data">
          <thead>
            <tr>
              <th>日時</th>
              <th>イベント</th>
              <th>操作者</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(h, i) in detail.histories" :key="i">
              <td>{{ formatDateTime(h.occurredAt) }}</td>
              <td>{{ eventLabels[h.event] ?? h.event }}</td>
              <td>{{ h.operator }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </AppLayout>
</template>

<style scoped>
.section {
  margin-top: 16px;
}

.retry-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px 24px;
  margin: 0;
}

.header-grid dt {
  font-size: 12px;
  color: var(--color-text-sub);
}

.header-grid dd {
  margin: 2px 0 0;
  font-size: 15px;
}
</style>
