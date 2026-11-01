<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import { registerApplication } from '../api/applications'
import { isUnauthorized, toApiError } from '../api/client'
import { useAuthStore } from '../stores/auth'
import type { ApiError, ApplicationRequest, ApplicationResponse } from '../types'

const router = useRouter()
const auth = useAuthStore()
const fileInput = ref<HTMLInputElement | null>(null)

function createInitialForm(): ApplicationRequest {
  return {
    applicationType: '',
    nameKanji: '',
    nameKana: '',
    nameAlphabet: '',
    employeeNumber: '',
    email: '',
    phoneNumber: '',
    websiteUrl: '',
    birthDate: '',
    desiredDate: '',
    startTime: '',
    endTime: '',
    notificationAt: '',
    periodStart: '',
    periodEnd: '',
    postalCode: '',
    prefecture: '',
    address: '',
    buildingName: '',
    quantity: null,
    amount: null,
    percentage: null,
    rating: null,
    gender: '',
    languages: [],
    contactMethod: '',
    priority: 3,
    themeColor: '#1e6fd9',
    attachmentFileName: '',
    subject: '',
    content: '',
    confidentiality: 'INTERNAL',
    termsAgreed: false,
    marketingOptIn: false,
  }
}

const form = ref<ApplicationRequest>(createInitialForm())
const fieldErrors = ref<Record<string, string>>({})
const error = ref<ApiError | null>(null)
const result = ref<ApplicationResponse | null>(null)
const showConfirm = ref(false)
const submitting = ref(false)

const applicationTypeLabels: Record<string, string> = {
  EQUIPMENT: '備品利用',
  TRAINING: '研修参加',
  LEAVE: '休暇',
  OTHER: 'その他',
}
const genderLabels: Record<string, string> = {
  FEMALE: '女性',
  MALE: '男性',
  NON_BINARY: '回答しない・その他',
}
const contactMethodLabels: Record<string, string> = { EMAIL: 'メール', PHONE: '電話' }
const confidentialityLabels: Record<string, string> = {
  PUBLIC: '公開',
  INTERNAL: '社内限定',
  CONFIDENTIAL: '機密',
}

function displayValue(value: string | number | null): string {
  return value === '' || value === null ? '-' : String(value)
}

function formatAmount(value: number | null): string {
  return value === null ? '-' : `${value.toLocaleString('ja-JP')} 円`
}

const confirmationItems = computed(() => [
  { label: '申請種別', value: applicationTypeLabels[form.value.applicationType] },
  { label: '社員番号', value: form.value.employeeNumber },
  { label: '氏名（漢字）', value: form.value.nameKanji },
  { label: '氏名（カナ）', value: form.value.nameKana },
  { label: '英字氏名', value: form.value.nameAlphabet },
  { label: '性別', value: genderLabels[form.value.gender] },
  { label: 'メールアドレス', value: form.value.email },
  { label: '電話番号', value: form.value.phoneNumber },
  { label: 'WebサイトURL', value: displayValue(form.value.websiteUrl) },
  { label: '郵便番号', value: form.value.postalCode },
  { label: '都道府県', value: form.value.prefecture },
  { label: '住所', value: form.value.address },
  { label: '建物名・部屋番号', value: displayValue(form.value.buildingName) },
  { label: '生年月日', value: form.value.birthDate },
  { label: '希望日', value: form.value.desiredDate },
  { label: '開始時刻', value: form.value.startTime },
  { label: '終了時刻', value: form.value.endTime },
  { label: '通知希望日時', value: displayValue(form.value.notificationAt) },
  { label: '利用開始日', value: form.value.periodStart },
  { label: '利用終了日', value: form.value.periodEnd },
  { label: '数量', value: displayValue(form.value.quantity) },
  { label: '金額', value: formatAmount(form.value.amount) },
  { label: '割合', value: `${displayValue(form.value.percentage)}%` },
  { label: '評価', value: displayValue(form.value.rating) },
  { label: '利用言語', value: form.value.languages.join(', ') },
  { label: '連絡方法', value: contactMethodLabels[form.value.contactMethod] },
  { label: '優先度', value: displayValue(form.value.priority) },
  { label: 'テーマカラー', value: form.value.themeColor },
  { label: '添付ファイル', value: displayValue(form.value.attachmentFileName) },
  { label: '件名', value: form.value.subject },
  { label: '申請内容', value: form.value.content },
  { label: '機密区分', value: confidentialityLabels[form.value.confidentiality] },
  { label: '利用規約への同意', value: form.value.termsAgreed ? '同意する' : '同意しない' },
  { label: 'メール配信希望', value: form.value.marketingOptIn ? '希望する' : '希望しない' },
])

const requiredTextFields: Array<[keyof ApplicationRequest, string]> = [
    ['applicationType', '申請種別を選択してください'],
    ['nameKanji', '氏名（漢字）を入力してください'],
    ['nameKana', '氏名（カナ）を入力してください'],
    ['nameAlphabet', '英字氏名を入力してください'],
    ['employeeNumber', '社員番号を入力してください'],
    ['email', 'メールアドレスを入力してください'],
    ['phoneNumber', '電話番号を入力してください'],
    ['birthDate', '生年月日を入力してください'],
    ['desiredDate', '希望日を入力してください'],
    ['startTime', '開始時刻を入力してください'],
    ['endTime', '終了時刻を入力してください'],
    ['periodStart', '利用開始日を入力してください'],
    ['periodEnd', '利用終了日を入力してください'],
    ['postalCode', '郵便番号を入力してください'],
    ['prefecture', '都道府県を選択してください'],
    ['address', '住所を入力してください'],
    ['gender', '性別を選択してください'],
    ['contactMethod', '連絡方法を選択してください'],
    ['subject', '件名を入力してください'],
    ['content', '申請内容を入力してください'],
    ['confidentiality', '機密区分を選択してください'],
]

const validatedFields: Array<keyof ApplicationRequest> = [
  ...requiredTextFields.map(([field]) => field),
  'websiteUrl',
  'notificationAt',
  'buildingName',
  'quantity',
  'amount',
  'percentage',
  'rating',
  'languages',
  'priority',
  'themeColor',
  'attachmentFileName',
  'termsAgreed',
]

function isValidDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const parsed = new Date(`${value}T00:00:00.000Z`)
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
}

function isValidTime(value: string): boolean {
  return /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value)
}

function validateField(field: keyof ApplicationRequest): string | undefined {
  const value = form.value[field]
  const requiredMessage = requiredTextFields.find(([name]) => name === field)?.[1]
  if (requiredMessage && typeof value === 'string' && !value.trim()) return requiredMessage

  switch (field) {
    case 'nameKana':
      return /^[ァ-ヶー 　]+$/.test(form.value.nameKana)
        ? undefined
        : '氏名（カナ）は全角カタカナで入力してください'
    case 'nameAlphabet':
      return /^[A-Za-z .'-]+$/.test(form.value.nameAlphabet)
        ? undefined
        : '英字氏名は半角英字で入力してください'
    case 'employeeNumber':
      return /^[A-Z0-9]{6,12}$/.test(form.value.employeeNumber)
        ? undefined
        : '社員番号は6～12文字の半角英大文字・数字で入力してください'
    case 'email':
      return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email)
        ? undefined
        : 'メールアドレスの形式が正しくありません'
    case 'phoneNumber':
      return /^0\d{1,4}-?\d{1,4}-?\d{3,4}$/.test(form.value.phoneNumber)
        ? undefined
        : '電話番号の形式が正しくありません'
    case 'websiteUrl':
      return !form.value.websiteUrl || /^https?:\/\/[^\s]+$/.test(form.value.websiteUrl)
        ? undefined
        : 'WebサイトURLはhttp://またはhttps://で入力してください'
    case 'birthDate':
    case 'desiredDate':
    case 'periodStart':
      return isValidDate(value as string) ? undefined : '有効な日付を入力してください'
    case 'startTime':
    case 'endTime':
      if (!isValidTime(value as string)) return '有効な時刻を入力してください'
      if (field === 'endTime' && form.value.startTime && form.value.endTime < form.value.startTime) {
        return '終了時刻は開始時刻以降を指定してください'
      }
      return undefined
    case 'notificationAt':
      if (!form.value.notificationAt) return undefined
      return /^\d{4}-\d{2}-\d{2}T(?:[01]\d|2[0-3]):[0-5]\d$/.test(form.value.notificationAt)
        && isValidDate(form.value.notificationAt.slice(0, 10))
        ? undefined
        : '有効な通知希望日時を入力してください'
    case 'periodEnd':
      if (!isValidDate(form.value.periodEnd)) return '有効な日付を入力してください'
      return form.value.periodStart && form.value.periodEnd < form.value.periodStart
        ? '利用終了日は開始日以降を指定してください'
        : undefined
    case 'postalCode':
      return /^\d{3}-?\d{4}$/.test(form.value.postalCode)
        ? undefined
        : '郵便番号は123-4567形式で入力してください'
    case 'quantity':
      return form.value.quantity !== null
        && Number.isInteger(form.value.quantity)
        && form.value.quantity >= 1
        && form.value.quantity <= 9999
        ? undefined
        : '数量は1から9999の整数で入力してください'
    case 'amount':
      return form.value.amount !== null && form.value.amount >= 0.01
        ? undefined
        : '金額は0.01以上で入力してください'
    case 'percentage':
      return form.value.percentage !== null
        && Number.isInteger(form.value.percentage)
        && form.value.percentage >= 0
        && form.value.percentage <= 100
        ? undefined
        : '割合は0から100の整数で入力してください'
    case 'rating':
      return form.value.rating !== null && Number.isInteger(form.value.rating)
        && form.value.rating >= 1 && form.value.rating <= 5
        ? undefined
        : '評価は1から5の範囲で選択してください'
    case 'languages':
      return form.value.languages.length > 0 && form.value.languages.length <= 5
        ? undefined
        : '利用言語を1つ以上選択してください'
    case 'priority':
      return form.value.priority !== null && Number.isInteger(form.value.priority)
        && form.value.priority >= 1 && form.value.priority <= 5
        ? undefined
        : '優先度は1から5の範囲で指定してください'
    case 'themeColor':
      return /^#[0-9A-Fa-f]{6}$/.test(form.value.themeColor)
        ? undefined
        : 'テーマカラーの形式が正しくありません'
    case 'attachmentFileName':
      return form.value.attachmentFileName.length <= 255
        ? undefined
        : '添付ファイル名は255文字以内で指定してください'
    case 'termsAgreed':
      return form.value.termsAgreed ? undefined : '利用規約への同意が必要です'
    default:
      return undefined
  }
}

function validate(): boolean {
  const errors: Record<string, string> = {}
  for (const field of validatedFields) {
    const message = validateField(field)
    if (message) errors[field] = message
  }
  fieldErrors.value = errors
  return Object.keys(errors).length === 0
}

function validateOnFocusOut(event: FocusEvent) {
  const target = event.target
  if (!(target instanceof HTMLElement)) return

  const field = (target.id === 'attachment' ? 'attachmentFileName' : target.id) as keyof ApplicationRequest
  if (!(field in form.value)) return
  const message = validateField(field)
  if (message) {
    fieldErrors.value[field] = message
  } else {
    delete fieldErrors.value[field]
  }
}

function openConfirm() {
  error.value = null
  if (validate()) showConfirm.value = true
}

async function submit() {
  submitting.value = true
  error.value = null
  try {
    result.value = await registerApplication(form.value)
    showConfirm.value = false
  } catch (caught) {
    if (isUnauthorized(caught)) {
      auth.markSessionExpired()
      router.push({ name: 'login' })
      return
    }
    error.value = toApiError(caught)
    fieldErrors.value = error.value.fieldErrors ?? {}
    showConfirm.value = false
  } finally {
    submitting.value = false
  }
}

function clearForm() {
  form.value = createInitialForm()
  fieldErrors.value = {}
  error.value = null
  if (fileInput.value) fileInput.value.value = ''
}

function closeResult() {
  result.value = null
  clearForm()
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  form.value.attachmentFileName = input.files?.[0]?.name ?? ''
}
</script>

<template>
  <AppLayout>
    <h1>各種申請フォーム</h1>
    <p class="text-sub">多様な文字種・入力形式を含む申請内容を登録します。</p>

    <div v-if="error" class="alert error">
      登録に失敗しました: {{ error.message }}
    </div>

    <div class="card">
      <form class="application-form" novalidate @focusout="validateOnFocusOut" @submit.prevent="openConfirm">
        <section>
          <h2>基本情報</h2>
          <div class="form-grid">
            <div class="field">
              <label class="required" for="applicationType">申請種別</label>
              <select id="applicationType" v-model="form.applicationType">
                <option value="">選択してください</option>
                <option value="EQUIPMENT">備品利用</option>
                <option value="TRAINING">研修参加</option>
                <option value="LEAVE">休暇</option>
                <option value="OTHER">その他</option>
              </select>
              <p v-if="fieldErrors.applicationType" class="field-error">{{ fieldErrors.applicationType }}</p>
            </div>
            <div class="field">
              <label class="required" for="employeeNumber">社員番号</label>
              <input id="employeeNumber" v-model="form.employeeNumber" maxlength="12" placeholder="AB123456" />
              <p v-if="fieldErrors.employeeNumber" class="field-error">{{ fieldErrors.employeeNumber }}</p>
            </div>
            <div class="field">
              <label class="required" for="nameKanji">氏名（漢字）</label>
              <input id="nameKanji" v-model="form.nameKanji" maxlength="100" autocomplete="name" />
              <p v-if="fieldErrors.nameKanji" class="field-error">{{ fieldErrors.nameKanji }}</p>
            </div>
            <div class="field">
              <label class="required" for="nameKana">氏名（カナ）</label>
              <input id="nameKana" v-model="form.nameKana" maxlength="100" placeholder="ヤマダ タロウ" />
              <p v-if="fieldErrors.nameKana" class="field-error">{{ fieldErrors.nameKana }}</p>
            </div>
            <div class="field">
              <label class="required" for="nameAlphabet">英字氏名</label>
              <input id="nameAlphabet" v-model="form.nameAlphabet" maxlength="100" placeholder="Taro Yamada" />
              <p v-if="fieldErrors.nameAlphabet" class="field-error">{{ fieldErrors.nameAlphabet }}</p>
            </div>
            <div class="field">
              <label class="required" for="gender">性別</label>
              <select id="gender" v-model="form.gender">
                <option value="">選択してください</option>
                <option value="FEMALE">女性</option>
                <option value="MALE">男性</option>
                <option value="NON_BINARY">回答しない・その他</option>
              </select>
              <p v-if="fieldErrors.gender" class="field-error">{{ fieldErrors.gender }}</p>
            </div>
          </div>
        </section>

        <section>
          <h2>連絡先・住所</h2>
          <div class="form-grid">
            <div class="field">
              <label class="required" for="email">メールアドレス</label>
              <input id="email" v-model="form.email" type="email" maxlength="255" autocomplete="email" />
              <p v-if="fieldErrors.email" class="field-error">{{ fieldErrors.email }}</p>
            </div>
            <div class="field">
              <label class="required" for="phoneNumber">電話番号</label>
              <input id="phoneNumber" v-model="form.phoneNumber" type="tel" maxlength="20" placeholder="090-1234-5678" />
              <p v-if="fieldErrors.phoneNumber" class="field-error">{{ fieldErrors.phoneNumber }}</p>
            </div>
            <div class="field">
              <label for="websiteUrl">WebサイトURL</label>
              <input id="websiteUrl" v-model="form.websiteUrl" type="url" maxlength="255" placeholder="https://example.com" />
              <p v-if="fieldErrors.websiteUrl" class="field-error">{{ fieldErrors.websiteUrl }}</p>
            </div>
            <div class="field">
              <label class="required" for="postalCode">郵便番号</label>
              <input id="postalCode" v-model="form.postalCode" inputmode="numeric" maxlength="8" placeholder="123-4567" />
              <p v-if="fieldErrors.postalCode" class="field-error">{{ fieldErrors.postalCode }}</p>
            </div>
            <div class="field">
              <label class="required" for="prefecture">都道府県</label>
              <select id="prefecture" v-model="form.prefecture">
                <option value="">選択してください</option>
                <option>北海道</option><option>青森県</option><option>岩手県</option><option>宮城県</option><option>秋田県</option><option>山形県</option><option>福島県</option><option>茨城県</option><option>栃木県</option><option>群馬県</option><option>埼玉県</option><option>千葉県</option><option>東京都</option><option>神奈川県</option><option>新潟県</option><option>富山県</option><option>石川県</option><option>福井県</option><option>山梨県</option><option>長野県</option><option>岐阜県</option><option>静岡県</option><option>愛知県</option><option>三重県</option><option>滋賀県</option><option>京都府</option><option>大阪府</option><option>兵庫県</option><option>奈良県</option><option>和歌山県</option><option>鳥取県</option><option>島根県</option><option>岡山県</option><option>広島県</option><option>山口県</option><option>徳島県</option><option>香川県</option><option>愛媛県</option><option>高知県</option><option>福岡県</option><option>佐賀県</option><option>長崎県</option><option>熊本県</option><option>大分県</option><option>宮崎県</option><option>鹿児島県</option><option>沖縄県</option>
              </select>
              <p v-if="fieldErrors.prefecture" class="field-error">{{ fieldErrors.prefecture }}</p>
            </div>
            <div class="field wide">
              <label class="required" for="address">住所</label>
              <input id="address" v-model="form.address" maxlength="200" autocomplete="street-address" />
              <p v-if="fieldErrors.address" class="field-error">{{ fieldErrors.address }}</p>
            </div>
            <div class="field wide">
              <label for="buildingName">建物名・部屋番号</label>
              <input id="buildingName" v-model="form.buildingName" maxlength="200" />
              <p v-if="fieldErrors.buildingName" class="field-error">{{ fieldErrors.buildingName }}</p>
            </div>
          </div>
        </section>

        <section>
          <h2>日付・数値</h2>
          <div class="form-grid">
            <div class="field"><label class="required" for="birthDate">生年月日</label><input id="birthDate" v-model="form.birthDate" type="date" /><p v-if="fieldErrors.birthDate" class="field-error">{{ fieldErrors.birthDate }}</p></div>
            <div class="field"><label class="required" for="desiredDate">希望日</label><input id="desiredDate" v-model="form.desiredDate" type="date" /><p v-if="fieldErrors.desiredDate" class="field-error">{{ fieldErrors.desiredDate }}</p></div>
            <div class="field"><label class="required" for="startTime">開始時刻</label><input id="startTime" v-model="form.startTime" type="time" /><p v-if="fieldErrors.startTime" class="field-error">{{ fieldErrors.startTime }}</p></div>
            <div class="field"><label class="required" for="endTime">終了時刻</label><input id="endTime" v-model="form.endTime" type="time" /><p v-if="fieldErrors.endTime" class="field-error">{{ fieldErrors.endTime }}</p></div>
            <div class="field"><label for="notificationAt">通知希望日時</label><input id="notificationAt" v-model="form.notificationAt" type="datetime-local" /><p v-if="fieldErrors.notificationAt" class="field-error">{{ fieldErrors.notificationAt }}</p></div>
            <div class="field"><label class="required" for="periodStart">利用開始日</label><input id="periodStart" v-model="form.periodStart" type="date" /><p v-if="fieldErrors.periodStart" class="field-error">{{ fieldErrors.periodStart }}</p></div>
            <div class="field"><label class="required" for="periodEnd">利用終了日</label><input id="periodEnd" v-model="form.periodEnd" type="date" /><p v-if="fieldErrors.periodEnd" class="field-error">{{ fieldErrors.periodEnd }}</p></div>
            <div class="field"><label class="required" for="quantity">数量</label><input id="quantity" v-model.number="form.quantity" type="number" min="1" max="9999" step="1" /><p v-if="fieldErrors.quantity" class="field-error">{{ fieldErrors.quantity }}</p></div>
            <div class="field"><label class="required" for="amount">金額（円）</label><input id="amount" v-model.number="form.amount" type="number" min="0.01" step="0.01" /><p v-if="fieldErrors.amount" class="field-error">{{ fieldErrors.amount }}</p></div>
            <div class="field"><label class="required" for="percentage">割合（%）</label><input id="percentage" v-model.number="form.percentage" type="number" min="0" max="100" step="1" /><p v-if="fieldErrors.percentage" class="field-error">{{ fieldErrors.percentage }}</p></div>
            <div class="field"><label class="required" for="rating">評価</label><select id="rating" v-model.number="form.rating"><option :value="null">選択してください</option><option :value="1">1</option><option :value="2">2</option><option :value="3">3</option><option :value="4">4</option><option :value="5">5</option></select><p v-if="fieldErrors.rating" class="field-error">{{ fieldErrors.rating }}</p></div>
          </div>
        </section>

        <section>
          <h2>選択・記述</h2>
          <div class="form-grid">
            <fieldset class="field wide"><legend class="required">利用言語</legend><label v-for="language in ['Java', 'TypeScript', 'Python', 'SQL', 'Other']" :key="language" class="choice"><input v-model="form.languages" type="checkbox" :value="language" />{{ language }}</label><p v-if="fieldErrors.languages" class="field-error">{{ fieldErrors.languages }}</p></fieldset>
            <fieldset class="field"><legend class="required">連絡方法</legend><label class="choice"><input v-model="form.contactMethod" type="radio" value="EMAIL" />メール</label><label class="choice"><input v-model="form.contactMethod" type="radio" value="PHONE" />電話</label><p v-if="fieldErrors.contactMethod" class="field-error">{{ fieldErrors.contactMethod }}</p></fieldset>
            <div class="field"><label class="required" for="priority">優先度</label><input id="priority" v-model.number="form.priority" type="range" min="1" max="5" /><span>{{ form.priority }}</span><p v-if="fieldErrors.priority" class="field-error">{{ fieldErrors.priority }}</p></div>
            <div class="field"><label class="required" for="themeColor">テーマカラー</label><input id="themeColor" v-model="form.themeColor" type="color" /><p v-if="fieldErrors.themeColor" class="field-error">{{ fieldErrors.themeColor }}</p></div>
            <div class="field"><label for="attachment">添付ファイル</label><input id="attachment" ref="fileInput" type="file" @change="onFileChange" /><p class="text-sub">ファイル本体は保存せず、ファイル名のみ記録します。</p></div>
            <div class="field wide"><label class="required" for="subject">件名</label><input id="subject" v-model="form.subject" maxlength="200" /><p v-if="fieldErrors.subject" class="field-error">{{ fieldErrors.subject }}</p></div>
            <div class="field wide"><label class="required" for="content">申請内容</label><textarea id="content" v-model="form.content" rows="6" maxlength="2000" /><span class="text-sub">{{ form.content.length }}/2000文字</span><p v-if="fieldErrors.content" class="field-error">{{ fieldErrors.content }}</p></div>
            <fieldset class="field"><legend class="required">機密区分</legend><label class="choice"><input v-model="form.confidentiality" type="radio" value="PUBLIC" />公開</label><label class="choice"><input v-model="form.confidentiality" type="radio" value="INTERNAL" />社内限定</label><label class="choice"><input v-model="form.confidentiality" type="radio" value="CONFIDENTIAL" />機密</label><p v-if="fieldErrors.confidentiality" class="field-error">{{ fieldErrors.confidentiality }}</p></fieldset>
            <div class="field"><label class="choice required"><input v-model="form.termsAgreed" type="checkbox" />利用規約に同意する</label><p v-if="fieldErrors.termsAgreed" class="field-error">{{ fieldErrors.termsAgreed }}</p><label class="choice"><input v-model="form.marketingOptIn" type="checkbox" />メール配信を希望する</label></div>
          </div>
        </section>

        <div class="toolbar"><span class="spacer" /><button type="button" @click="clearForm">クリア</button><button class="primary" type="submit">確認</button></div>
      </form>
    </div>

    <div v-if="showConfirm" class="modal-backdrop">
      <div class="modal confirm-modal">
        <h3>申請内容の確認</h3>
        <table class="data">
          <tbody>
            <tr v-for="item in confirmationItems" :key="item.label">
              <th>{{ item.label }}</th>
              <td class="confirmation-value">{{ item.value }}</td>
            </tr>
          </tbody>
        </table>
        <div class="modal-actions">
          <button :disabled="submitting" @click="showConfirm = false">戻る</button>
          <button class="primary" :disabled="submitting" @click="submit">
            {{ submitting ? '登録中...' : '登録する' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="result" class="modal-backdrop">
      <div class="modal"><h3>申請を受け付けました</h3><div class="alert success">申請ID: <strong>{{ result.id }}</strong></div><table class="data"><tbody><tr><th>状態</th><td>{{ result.status }}</td></tr><tr><th>登録日時</th><td>{{ new Date(result.createdAt).toLocaleString('ja-JP') }}</td></tr></tbody></table><div class="modal-actions"><button class="primary" @click="closeResult">続けて登録</button></div></div>
    </div>
  </AppLayout>
</template>

<style scoped>
.application-form { max-width: 1000px; }
section { border-bottom: 1px solid var(--color-border); margin-bottom: 24px; padding-bottom: 10px; }
section h2 { font-size: 17px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 20px; }
.wide { grid-column: 1 / -1; }
fieldset { border: 0; margin: 0; padding: 0; min-width: 0; }
legend { font-size: 13px; color: var(--color-text-sub); margin-bottom: 4px; }
.choice { display: inline-flex; align-items: center; gap: 5px; margin: 0 12px 6px 0; font-size: 14px; }
.choice input { width: auto; }
input[type='color'] { height: 38px; padding: 3px; }
input[type='range'] { padding: 0; }
input[type='file'] { padding: 5px; }
.confirm-modal { max-height: calc(100vh - 48px); overflow-y: auto; }
.confirmation-value { white-space: pre-wrap; word-break: break-word; }
@media (max-width: 800px) { .form-grid { grid-template-columns: 1fr; } .wide { grid-column: auto; } }
</style>
