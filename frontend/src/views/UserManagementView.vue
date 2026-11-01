<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import {
  createUser,
  disableUser,
  fetchRoleHistory,
  searchUsers,
  updateUser,
} from '../api/users'
import { isUnauthorized, toApiError } from '../api/client'
import { useAuthStore } from '../stores/auth'
import type { AppUser, RoleHistory } from '../types'

const router = useRouter()
const auth = useAuthStore()

const keyword = ref('')
const roleFilter = ref('')
const statusFilter = ref('')

const users = ref<AppUser[]>([])
const loading = ref(false)
const message = ref('')
const errorMessage = ref('')
const fieldErrors = ref<Record<string, string>>({})
const showCreateConfirm = ref(false)
const creating = ref(false)

// 編集領域: mode が 'create' | 'edit' | null
const mode = ref<'create' | 'edit' | null>(null)
const selectedUser = ref<AppUser | null>(null)
const roleHistories = ref<RoleHistory[]>([])

const form = reactive({
  username: '',
  displayName: '',
  email: '',
  role: 'USER',
  status: 'ACTIVE',
  password: '',
})

const roleLabels: Record<string, string> = { ADMIN: '管理者', USER: '一般' }
const statusLabels: Record<string, string> = { ACTIVE: '有効', DISABLED: '無効' }

function handleError(e: unknown) {
  if (isUnauthorized(e)) {
    auth.markSessionExpired()
    router.push({ name: 'login' })
    return
  }
  const apiError = toApiError(e)
  errorMessage.value = apiError.message
  fieldErrors.value = apiError.fieldErrors ?? {}
}

function validateField(field: 'username' | 'displayName' | 'email' | 'password'): string | undefined {
  const value = form[field]
  if (!value.trim()) {
    const labels = {
      username: 'ユーザID',
      displayName: '表示名',
      email: 'メールアドレス',
      password: '初期パスワード',
    }
    return `${labels[field]}を入力してください`
  }
  if (field === 'username' && mode.value === 'create' && !/^[a-zA-Z0-9_.-]+$/.test(value)) {
    return 'ユーザIDは半角英数字と記号(_.-)のみ使用できます'
  }
  if (field === 'email' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    return 'メールアドレスの形式が正しくありません'
  }
  if (field === 'password' && mode.value === 'create' && (value.length < 8 || value.length > 72)) {
    return '初期パスワードは8～72文字で入力してください'
  }
  return undefined
}

function validate(): boolean {
  const errors: Record<string, string> = {}
  const fields: Array<'username' | 'displayName' | 'email' | 'password'> = [
    'username',
    'displayName',
    'email',
    ...(mode.value === 'create' ? ['password'] as const : []),
  ]
  for (const field of fields) {
    const message = validateField(field)
    if (message) errors[field] = message
  }
  fieldErrors.value = errors
  return Object.keys(errors).length === 0
}

function validateOnFocusOut(field: 'username' | 'displayName' | 'email' | 'password') {
  const message = validateField(field)
  if (message) {
    fieldErrors.value[field] = message
  } else {
    delete fieldErrors.value[field]
  }
}

async function search() {
  loading.value = true
  errorMessage.value = ''
  try {
    users.value = await searchUsers({
      keyword: keyword.value,
      role: roleFilter.value,
      status: statusFilter.value,
    })
  } catch (e) {
    handleError(e)
  } finally {
    loading.value = false
  }
}

function startCreate() {
  mode.value = 'create'
  selectedUser.value = null
  roleHistories.value = []
  form.username = ''
  form.displayName = ''
  form.email = ''
  form.role = 'USER'
  form.status = 'ACTIVE'
  form.password = ''
  message.value = ''
  errorMessage.value = ''
  fieldErrors.value = {}
}

async function startEdit(user: AppUser) {
  mode.value = 'edit'
  selectedUser.value = user
  form.username = user.username
  form.displayName = user.displayName
  form.email = user.email
  form.role = user.role
  form.status = user.status
  form.password = ''
  message.value = ''
  errorMessage.value = ''
  fieldErrors.value = {}
  try {
    roleHistories.value = await fetchRoleHistory(user.id)
  } catch {
    roleHistories.value = []
  }
}

function openCreateConfirm() {
  errorMessage.value = ''
  if (validate()) showCreateConfirm.value = true
}

async function create() {
  creating.value = true
  errorMessage.value = ''
  try {
    await createUser({
      username: form.username,
      displayName: form.displayName,
      email: form.email,
      role: form.role,
      password: form.password,
    })
    message.value = `ユーザ「${form.username}」を登録しました`
    showCreateConfirm.value = false
    mode.value = null
    selectedUser.value = null
    await search()
  } catch (e) {
    handleError(e)
    showCreateConfirm.value = false
  } finally {
    creating.value = false
  }
}

async function save() {
  errorMessage.value = ''
  if (!validate() || mode.value !== 'edit' || !selectedUser.value) return
  try {
    await updateUser(selectedUser.value.id, {
      displayName: form.displayName,
      email: form.email,
      role: form.role,
      status: form.status,
    })
    message.value = `ユーザ「${form.username}」を更新しました`
    mode.value = null
    selectedUser.value = null
    await search()
  } catch (e) {
    handleError(e)
  }
}

async function onDisable(user: AppUser) {
  if (!window.confirm(`ユーザ「${user.username}」を無効化しますか?`)) return
  errorMessage.value = ''
  try {
    await disableUser(user.id)
    message.value = `ユーザ「${user.username}」を無効化しました`
    if (selectedUser.value?.id === user.id) {
      mode.value = null
      selectedUser.value = null
    }
    await search()
  } catch (e) {
    handleError(e)
  }
}

function cancelEdit() {
  mode.value = null
  selectedUser.value = null
  showCreateConfirm.value = false
  fieldErrors.value = {}
}

function formatDateTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(search)
</script>

<template>
  <AppLayout>
    <h1>ユーザ管理</h1>

    <div v-if="message" class="alert success">{{ message }}</div>
    <div v-if="errorMessage" class="alert error">{{ errorMessage }}</div>

    <div class="card">
      <form class="toolbar" @submit.prevent="search">
        <div class="cond grow">
          <label>キーワード(ユーザID / 表示名 / メール)</label>
          <input v-model="keyword" placeholder="部分一致" />
        </div>
        <div class="cond">
          <label>ロール</label>
          <select v-model="roleFilter">
            <option value="">すべて</option>
            <option value="ADMIN">管理者</option>
            <option value="USER">一般</option>
          </select>
        </div>
        <div class="cond">
          <label>状態</label>
          <select v-model="statusFilter">
            <option value="">すべて</option>
            <option value="ACTIVE">有効</option>
            <option value="DISABLED">無効</option>
          </select>
        </div>
        <button class="primary" type="submit" :disabled="loading">検索</button>
        <button type="button" @click="startCreate">新規登録</button>
      </form>
    </div>

    <div class="main-area">
      <div class="list-area">
        <table class="data">
          <thead>
            <tr>
              <th>ユーザID</th>
              <th>表示名</th>
              <th>メール</th>
              <th>ロール</th>
              <th>状態</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id" :class="{ selected: selectedUser?.id === user.id }">
              <td>{{ user.username }}</td>
              <td>{{ user.displayName }}</td>
              <td>{{ user.email }}</td>
              <td><span class="badge" :class="user.role.toLowerCase()">{{ roleLabels[user.role] }}</span></td>
              <td><span class="badge" :class="user.status.toLowerCase()">{{ statusLabels[user.status] }}</span></td>
              <td class="ops">
                <button @click="startEdit(user)">編集</button>
                <button
                  v-if="user.status === 'ACTIVE' && user.username !== auth.user?.username"
                  class="danger"
                  @click="onDisable(user)"
                >無効化</button>
              </td>
            </tr>
            <tr v-if="users.length === 0 && !loading">
              <td colspan="6" class="text-sub">該当するユーザがいません</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 編集領域(一覧と同一画面) -->
      <div v-if="mode" class="card edit-area">
        <h3>{{ mode === 'create' ? 'ユーザ新規登録' : 'ユーザ編集' }}</h3>
        <form @submit.prevent="mode === 'create' ? openCreateConfirm() : save()">
          <div class="field">
            <label class="required">ユーザID</label>
            <input
              v-model="form.username"
              :disabled="mode === 'edit'"
              maxlength="50"
              required
              @blur="validateOnFocusOut('username')"
            />
            <p v-if="fieldErrors.username" class="field-error">{{ fieldErrors.username }}</p>
          </div>
          <div class="field">
            <label class="required">表示名</label>
            <input v-model="form.displayName" maxlength="100" required @blur="validateOnFocusOut('displayName')" />
            <p v-if="fieldErrors.displayName" class="field-error">{{ fieldErrors.displayName }}</p>
          </div>
          <div class="field">
            <label class="required">メールアドレス</label>
            <input v-model="form.email" type="email" maxlength="255" required @blur="validateOnFocusOut('email')" />
            <p v-if="fieldErrors.email" class="field-error">{{ fieldErrors.email }}</p>
          </div>
          <div class="field">
            <label class="required">ロール</label>
            <select v-model="form.role">
              <option value="USER">一般</option>
              <option value="ADMIN">管理者</option>
            </select>
          </div>
          <div v-if="mode === 'edit'" class="field">
            <label class="required">状態</label>
            <select v-model="form.status">
              <option value="ACTIVE">有効</option>
              <option value="DISABLED">無効</option>
            </select>
          </div>
          <div v-if="mode === 'create'" class="field">
            <label class="required">初期パスワード(8文字以上)</label>
            <input
              v-model="form.password"
              type="password"
              minlength="8"
              maxlength="72"
              required
              @blur="validateOnFocusOut('password')"
            />
            <p v-if="fieldErrors.password" class="field-error">{{ fieldErrors.password }}</p>
          </div>
          <div class="toolbar">
            <span class="spacer" />
            <button type="button" @click="cancelEdit">キャンセル</button>
            <button class="primary" type="submit">保存</button>
          </div>
        </form>

        <template v-if="mode === 'edit' && roleHistories.length > 0">
          <h3 class="history-title">ロール変更履歴</h3>
          <table class="data">
            <thead>
              <tr>
                <th>変更日時</th>
                <th>変更前</th>
                <th>変更後</th>
                <th>変更者</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="h in roleHistories" :key="h.id">
                <td>{{ formatDateTime(h.changedAt) }}</td>
                <td>{{ h.oldRole ? roleLabels[h.oldRole] : '(新規)' }}</td>
                <td>{{ roleLabels[h.newRole] }}</td>
                <td>{{ h.changedBy }}</td>
              </tr>
            </tbody>
          </table>
        </template>
      </div>
    </div>

    <div v-if="showCreateConfirm" class="modal-backdrop">
      <div class="modal">
        <h3>登録内容の確認</h3>
        <table class="data">
          <tbody>
            <tr><th>ユーザID</th><td>{{ form.username }}</td></tr>
            <tr><th>表示名</th><td>{{ form.displayName }}</td></tr>
            <tr><th>メールアドレス</th><td>{{ form.email }}</td></tr>
            <tr><th>ロール</th><td>{{ roleLabels[form.role] }}</td></tr>
            <tr><th>初期パスワード</th><td>********</td></tr>
          </tbody>
        </table>
        <div class="modal-actions">
          <button :disabled="creating" @click="showCreateConfirm = false">戻る</button>
          <button class="primary" :disabled="creating" @click="create">
            {{ creating ? '登録中...' : '登録する' }}
          </button>
        </div>
      </div>
    </div>
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
  min-width: 220px;
}

.toolbar {
  align-items: flex-end;
}

.main-area {
  display: flex;
  gap: 16px;
  margin-top: 16px;
  align-items: flex-start;
}

.list-area {
  flex: 3;
  min-width: 0;
}

.edit-area {
  flex: 2;
  min-width: 300px;
}

.ops {
  display: flex;
  gap: 6px;
}

.ops button {
  padding: 4px 10px;
  font-size: 13px;
}

tr.selected td {
  background: #e5efff;
}

.history-title {
  margin-top: 24px;
}

@media (max-width: 1100px) {
  .main-area {
    flex-direction: column;
  }
  .edit-area {
    width: 100%;
  }
}
</style>
