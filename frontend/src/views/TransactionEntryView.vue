<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import { registerTransaction } from '../api/transactions'
import { isUnauthorized, toApiError } from '../api/client'
import { useAuthStore } from '../stores/auth'
import type { ApiError, RegisterResult } from '../types'

const router = useRouter()
const auth = useAuthStore()

const partnerName = ref('')
const amount = ref<number | null>(null)
const transactionDate = ref('')
const note = ref('')

const fieldErrors = ref<Record<string, string>>({})
const error = ref<ApiError | null>(null)
const showConfirm = ref(false)
const submitting = ref(false)
const result = ref<RegisterResult | null>(null)

// 重複送信防止用リクエストID。確認ダイアログを開いた時点で採番し、
// 失敗→再試行では同じIDを使う(外部APIが同一IDを冪等に処理する)。
let requestId = ''

function validate(): boolean {
  const errors: Record<string, string> = {}
  if (!partnerName.value.trim()) errors.partnerName = '取引先は必須です'
  if (amount.value === null || Number.isNaN(amount.value)) {
    errors.amount = '金額は必須です'
  } else if (amount.value < 1) {
    errors.amount = '金額は1以上で入力してください'
  } else if (!Number.isInteger(amount.value)) {
    errors.amount = '金額は整数で入力してください'
  }
  if (!transactionDate.value) errors.transactionDate = '取引日は必須です'
  if (note.value.length > 500) errors.note = '備考は500文字以内で入力してください'
  fieldErrors.value = errors
  return Object.keys(errors).length === 0
}

function openConfirm() {
  error.value = null
  if (!validate()) return
  if (!requestId) {
    requestId = crypto.randomUUID()
  }
  showConfirm.value = true
}

async function submit() {
  submitting.value = true
  error.value = null
  try {
    result.value = await registerTransaction({
      requestId,
      partnerName: partnerName.value.trim(),
      amount: amount.value!,
      transactionDate: transactionDate.value,
      note: note.value,
    })
    // 成功したので次回入力用にリクエストIDをリセット
    requestId = ''
    showConfirm.value = false
  } catch (e) {
    if (isUnauthorized(e)) {
      auth.markSessionExpired()
      router.push({ name: 'login' })
      return
    }
    // 失敗時は入力を保持したまま入力画面へ戻し、エラー理由を表示する
    error.value = toApiError(e)
    showConfirm.value = false
  } finally {
    submitting.value = false
  }
}

function clearForm() {
  partnerName.value = ''
  amount.value = null
  transactionDate.value = ''
  note.value = ''
  fieldErrors.value = {}
  error.value = null
  requestId = ''
}

function closeResult() {
  result.value = null
  clearForm()
}

function formatAmount(value: number | null): string {
  return value === null ? '-' : value.toLocaleString('ja-JP')
}
</script>

<template>
  <AppLayout>
    <h1>取引登録</h1>

    <div v-if="error" class="alert error">
      <p class="error-title">登録に失敗しました: {{ error.message }}</p>
      <p v-if="error.code === 'EXTERNAL_TIMEOUT'" class="text-sub">
        外部システムで処理が完了している可能性があります。
        <router-link :to="{ name: 'transactions' }">取引一覧</router-link>
        で状態を確認してください。
      </p>
      <p v-else-if="error.retryable" class="text-sub">
        入力内容は保持されています。「確認」から再度実行してください。
      </p>
    </div>

    <div class="card">
      <form class="entry-form" @submit.prevent="openConfirm">
        <div class="columns">
          <div class="col">
            <div class="field">
              <label class="required" for="partnerName">取引先</label>
              <input id="partnerName" v-model="partnerName" maxlength="100" />
              <p v-if="fieldErrors.partnerName" class="field-error">{{ fieldErrors.partnerName }}</p>
            </div>
            <div class="field">
              <label class="required" for="amount">金額(円)</label>
              <input id="amount" v-model.number="amount" type="number" min="1" step="1" />
              <p v-if="fieldErrors.amount" class="field-error">{{ fieldErrors.amount }}</p>
            </div>
          </div>
          <div class="col">
            <div class="field">
              <label class="required" for="transactionDate">取引日</label>
              <input id="transactionDate" v-model="transactionDate" type="date" />
              <p v-if="fieldErrors.transactionDate" class="field-error">{{ fieldErrors.transactionDate }}</p>
            </div>
            <div class="field">
              <label for="note">備考</label>
              <textarea id="note" v-model="note" rows="3" maxlength="500" />
              <p v-if="fieldErrors.note" class="field-error">{{ fieldErrors.note }}</p>
            </div>
          </div>
        </div>
        <div class="toolbar">
          <span class="spacer" />
          <button type="button" @click="clearForm">クリア</button>
          <button class="primary" type="submit">確認</button>
        </div>
      </form>
    </div>

    <p class="text-sub hint">
      動作確認用: 取引先に「FAIL」を含めるとシステムエラー、「NG」を含めると業務エラーになります。
    </p>

    <!-- 確認ダイアログ -->
    <div v-if="showConfirm" class="modal-backdrop">
      <div class="modal">
        <h3>登録内容の確認</h3>
        <table class="data">
          <tbody>
            <tr><th>取引先</th><td>{{ partnerName }}</td></tr>
            <tr><th>金額</th><td>{{ formatAmount(amount) }} 円</td></tr>
            <tr><th>取引日</th><td>{{ transactionDate }}</td></tr>
            <tr><th>備考</th><td>{{ note || '-' }}</td></tr>
          </tbody>
        </table>
        <div class="modal-actions">
          <button @click="showConfirm = false" :disabled="submitting">戻る</button>
          <button class="primary" @click="submit" :disabled="submitting">
            {{ submitting ? '登録中...' : '登録する' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 結果モーダル -->
    <div v-if="result" class="modal-backdrop">
      <div class="modal">
        <h3>登録が完了しました</h3>
        <div class="alert success">
          受付番号: <strong>{{ result.receiptNumber }}</strong>
        </div>
        <table class="data">
          <tbody>
            <tr><th>取引ID</th><td>{{ result.transactionId }}</td></tr>
            <tr><th>状態</th><td>{{ result.status }}</td></tr>
          </tbody>
        </table>
        <div class="modal-actions">
          <button @click="router.push({ name: 'transactions' })">取引一覧へ</button>
          <button class="primary" @click="closeResult">続けて登録</button>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.columns {
  display: flex;
  gap: 32px;
}

.col {
  flex: 1;
  min-width: 0;
}

.error-title {
  margin: 0 0 6px;
}

.hint {
  margin-top: 12px;
}

@media (max-width: 800px) {
  .columns {
    flex-direction: column;
    gap: 0;
  }
}
</style>
