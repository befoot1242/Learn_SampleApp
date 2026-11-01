<script setup lang="ts">
// Prototype: four transaction-date precision UI variants, switchable via ?variant=A|B|C|D.
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'

type Precision = 'year' | 'month' | 'day'
type CalendarStep = Precision
type Variant = 'A' | 'B' | 'C' | 'D'

const route = useRoute()
const router = useRouter()
const isDevelopment = import.meta.env.DEV
const precision = ref<Precision>('day')
const year = ref('2026')
const month = ref('11')
const day = ref('01')
const calendarOpen = ref(false)
const calendarStep = ref<CalendarStep>('year')
const calendarInput = ref<HTMLInputElement | null>(null)
const calendarDate = ref('2026-11-01')
const calendarYears = [2024, 2025, 2026, 2027, 2028]
const monthLabels = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']

const variant = computed<Variant>(() => {
  const value = route.query.variant
  return value === 'B' || value === 'C' || value === 'D' ? value : 'A'
})

const labels: Record<Variant, string> = {
  A: '精度を先に選ぶ',
  B: '3つの選択肢を並べる',
  C: '入力欄を段階的に開く',
  D: 'テキスト入力を主にする',
}

const preview = computed(() => {
  if (!year.value) return '未入力'
  if (precision.value === 'year') return `${year.value}年`
  if (!month.value) return `${year.value}年（月未入力）`
  if (precision.value === 'month') return `${year.value}年${month.value}月`
  if (!day.value) return `${year.value}年${month.value}月（日未入力）`
  return `${year.value}年${month.value}月${day.value}日`
})

const dateInput = computed({
  get: () => {
    if (!year.value) return ''
    if (precision.value === 'year') return year.value
    if (!month.value) return year.value
    const formattedMonth = month.value.padStart(2, '0')
    if (precision.value === 'month') return `${year.value}-${formattedMonth}`
    if (!day.value) return `${year.value}-${formattedMonth}`
    return `${year.value}-${formattedMonth}-${day.value.padStart(2, '0')}`
  },
  set: (value: string) => {
    const match = value.match(/^(\d{4})(?:-(\d{1,2})(?:-(\d{1,2}))?)?$/)
    if (!match) return
    year.value = match[1]
    month.value = match[2] ?? ''
    day.value = match[3] ?? ''
    precision.value = match[3] ? 'day' : match[2] ? 'month' : 'year'
  },
})

const calendarDays = computed(() => {
  const parsedYear = Number(year.value)
  const parsedMonth = Number(month.value)
  if (!Number.isInteger(parsedYear) || !Number.isInteger(parsedMonth) || parsedMonth < 1 || parsedMonth > 12) {
    return 31
  }
  return new Date(parsedYear, parsedMonth, 0).getDate()
})

function choosePrecision(value: Precision) {
  precision.value = value
}

function toggleCalendar() {
  calendarOpen.value = !calendarOpen.value
  if (calendarOpen.value) calendarStep.value = 'year'
}

function chooseYear(value: number) {
  year.value = String(value)
  month.value = ''
  day.value = ''
  precision.value = 'year'
  calendarStep.value = 'month'
}

function chooseMonth(value: number) {
  month.value = String(value).padStart(2, '0')
  day.value = ''
  precision.value = 'month'
  calendarStep.value = 'day'
}

function chooseDay(value: number) {
  day.value = String(value).padStart(2, '0')
  precision.value = 'day'
  calendarOpen.value = false
}

function moveVariant(step: number) {
  const variants: Variant[] = ['A', 'B', 'C', 'D']
  const next = variants[(variants.indexOf(variant.value) + step + variants.length) % variants.length]
  router.replace({ query: { ...route.query, variant: next } })
}

function handleKeydown(event: KeyboardEvent) {
  const target = event.target as HTMLElement
  if (target.matches('input, textarea, [contenteditable="true"]')) return
  if (event.key === 'ArrowLeft') moveVariant(-1)
  if (event.key === 'ArrowRight') moveVariant(1)
}

function openNativeCalendar() {
  if (!calendarInput.value) return
  const input = calendarInput.value
  const showPicker = (input as HTMLInputElement & { showPicker?: () => void }).showPicker
  if (showPicker) {
    showPicker.call(input)
  } else {
    input.focus()
    input.click()
  }
}

watch(precision, (value) => {
  if (value === 'year') {
    month.value = ''
    day.value = ''
  } else if (value === 'month') {
    day.value = ''
  }
})

watch(calendarDate, (value) => {
  if (value) dateInput.value = value
})

onMounted(() => window.addEventListener('keydown', handleKeydown))
onUnmounted(() => window.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <AppLayout>
    <h1>取引日入力プロトタイプ</h1>
    <p class="intro">
      年のみ・年月のみ・年月日の入力を許可する場合のUI試作です。これは送信しないプロトタイプであり、現在のAPIには接続しません。
    </p>

    <section v-if="variant === 'A'" class="prototype variant-a">
      <header>
        <p class="eyebrow">案A</p>
        <h2>単一入力欄 + 粒度を選べるカレンダー</h2>
        <p>年・年月・年月日を1つの入力欄で表現し、カレンダーボタンから値の粒度を選びます。</p>
      </header>
      <div class="single-date-field">
        <label for="prototypeTransactionDate">取引日</label>
        <div class="date-input-with-button">
          <input
            id="prototypeTransactionDate"
            v-model="dateInput"
            inputmode="numeric"
            maxlength="10"
            placeholder="2026 / 2026-11 / 2026-11-01"
          />
          <button type="button" aria-label="カレンダーを開く" @click="toggleCalendar">📅</button>
        </div>
        <p>年、年月、または年月日を入力できます。</p>
        <div v-if="calendarOpen" class="calendar-popover">
          <template v-if="calendarStep === 'year'">
            <h3>年を選択</h3>
            <div class="calendar-grid year-grid">
              <button v-for="calendarYear in calendarYears" :key="calendarYear" type="button" @click="chooseYear(calendarYear)">{{ calendarYear }}年</button>
            </div>
            <button type="button" class="use-current" @click="calendarOpen = false">この年のみを使用</button>
          </template>
          <template v-else-if="calendarStep === 'month'">
            <h3>{{ year }}年: 月を選択</h3>
            <div class="calendar-grid">
              <button v-for="(label, index) in monthLabels" :key="label" type="button" @click="chooseMonth(index + 1)">{{ label }}</button>
            </div>
            <button type="button" class="use-current" @click="calendarOpen = false">{{ year }}年のみを使用</button>
          </template>
          <template v-else>
            <h3>{{ year }}年{{ month }}月: 日を選択</h3>
            <div class="calendar-grid day-grid">
              <button v-for="calendarDay in calendarDays" :key="calendarDay" type="button" @click="chooseDay(calendarDay)">{{ calendarDay }}</button>
            </div>
            <button type="button" class="use-current" @click="calendarOpen = false">{{ year }}年{{ month }}月までを使用</button>
          </template>
        </div>
      </div>
    </section>

    <section v-else-if="variant === 'B'" class="prototype variant-b">
      <header>
        <p class="eyebrow">案B</p>
        <h2>入力した値の粒度を選ぶ</h2>
        <p>3つの選択肢を並べ、日付の粒度が異なることを視覚的に比較できるようにします。</p>
      </header>
      <div class="precision-cards">
        <button :class="{ selected: precision === 'year' }" type="button" @click="choosePrecision('year')">
          <strong>2026</strong><span>年のみ</span>
        </button>
        <button :class="{ selected: precision === 'month' }" type="button" @click="choosePrecision('month')">
          <strong>2026.11</strong><span>年月</span>
        </button>
        <button :class="{ selected: precision === 'day' }" type="button" @click="choosePrecision('day')">
          <strong>2026.11.01</strong><span>年月日</span>
        </button>
      </div>
      <label class="native-date">
        <span>取引日</span>
        <input v-if="precision === 'day'" v-model="dateInput" type="date" aria-label="年月日入力の表示例" />
        <div v-else class="date-fields">
          <label>年<input v-model="year" inputmode="numeric" maxlength="4" />年</label>
          <label v-if="precision === 'month'">月<input v-model="month" inputmode="numeric" maxlength="2" />月</label>
        </div>
      </label>
    </section>

    <section v-else-if="variant === 'C'" class="prototype variant-c">
      <header>
        <p class="eyebrow">案C</p>
        <h2>年を起点に必要な項目を開く</h2>
        <p>年を常に入力し、月と日を「分かる場合に追加」します。精度を選ぶ操作を文章に寄せた案です。</p>
      </header>
      <div class="progressive-fields">
        <label>年<input v-model="year" inputmode="numeric" maxlength="4" placeholder="2026" /></label>
        <label class="checkbox"><input type="checkbox" :checked="precision !== 'year'" @change="choosePrecision(precision === 'year' ? 'month' : 'year')" />月まで分かる</label>
        <label v-if="precision !== 'year'">月<input v-model="month" inputmode="numeric" maxlength="2" placeholder="11" /></label>
        <label v-if="precision !== 'year'" class="checkbox"><input type="checkbox" :checked="precision === 'day'" @change="choosePrecision(precision === 'day' ? 'month' : 'day')" />日まで分かる</label>
        <label v-if="precision === 'day'">日<input v-model="day" inputmode="numeric" maxlength="2" placeholder="01" /></label>
      </div>
    </section>

    <section v-else class="prototype variant-d">
      <header>
        <p class="eyebrow">案D</p>
        <h2>テキスト入力を主にして、カレンダーで年月日を補完する</h2>
        <p>精度を選ぶUIは表示せず、入力した書式から年・年月・年月日を自動判定します。カレンダーで日付を選ぶと年月日を補完します。</p>
      </header>
      <div class="single-date-field">
        <label for="textFirstTransactionDate">取引日</label>
        <div class="date-input-with-button">
          <input
            id="textFirstTransactionDate"
            v-model="dateInput"
            inputmode="numeric"
            maxlength="10"
            placeholder="2026 / 2026-11 / 2026-11-01"
          />
          <button type="button" aria-label="年月日をカレンダーから選ぶ" @click="openNativeCalendar">📅</button>
          <input ref="calendarInput" v-model="calendarDate" class="native-calendar-input" type="date" tabindex="-1" aria-hidden="true" />
        </div>
        <p>入力例: <code>2026</code>、<code>2026-11</code>、<code>2026-11-01</code>。カレンダーを使う場合は年月日を入力します。</p>
      </div>
    </section>

    <aside class="state">
      <h2>試作の状態</h2>
      <dl>
        <div><dt>選択した精度</dt><dd>{{ precision === 'year' ? '年のみ' : precision === 'month' ? '年月' : '年月日' }}</dd></div>
        <div><dt>画面表示の値</dt><dd>{{ preview }}</dd></div>
        <div><dt>現在のバックエンド送信</dt><dd>行わない（`LocalDate`には未対応）</dd></div>
      </dl>
    </aside>

    <div v-if="isDevelopment" class="prototype-switcher" aria-label="プロトタイプ案の切り替え">
      <button type="button" aria-label="前の案" @click="moveVariant(-1)">←</button>
      <strong>{{ variant }}: {{ labels[variant] }}</strong>
      <button type="button" aria-label="次の案" @click="moveVariant(1)">→</button>
    </div>
  </AppLayout>
</template>

<style scoped>
.intro { color: #52606d; max-width: 760px; }
.prototype { margin-top: 1.5rem; border: 1px solid #cbd5e1; border-radius: 12px; padding: 1.5rem; background: #fff; }
.prototype h2 { margin: 0.1rem 0 0.5rem; }
.eyebrow { margin: 0; color: #1d4ed8; font-weight: 700; }
.single-date-field { position: relative; margin-top: 1.5rem; max-width: 32rem; }
.single-date-field > label { display: block; margin-bottom: 0.35rem; font-weight: 700; }
.single-date-field > p { margin: 0.5rem 0 0; color: #52606d; font-size: 0.9rem; }
.date-input-with-button { display: flex; }
.date-input-with-button input { flex: 1; min-width: 0; padding: 0.65rem; border: 1px solid #64748b; border-radius: 6px 0 0 6px; font-size: 1rem; }
.date-input-with-button button { padding: 0 0.9rem; border: 1px solid #64748b; border-left: 0; border-radius: 0 6px 6px 0; background: #e2e8f0; cursor: pointer; }
.date-input-with-button .native-calendar-input { position: absolute; width: 1px; height: 1px; padding: 0; border: 0; opacity: 0; pointer-events: none; }
.calendar-popover { position: absolute; z-index: 10; top: 5.2rem; left: 0; width: min(100%, 28rem); padding: 1rem; border: 1px solid #94a3b8; border-radius: 8px; background: #fff; box-shadow: 0 10px 24px rgb(15 23 42 / 20%); }
.calendar-popover h3 { margin-top: 0; font-size: 1rem; }
.calendar-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.4rem; }
.calendar-grid button, .use-current { padding: 0.55rem; border: 1px solid #cbd5e1; border-radius: 5px; background: #fff; cursor: pointer; }
.calendar-grid button:hover { color: #fff; border-color: #1d4ed8; background: #1d4ed8; }
.year-grid { grid-template-columns: repeat(2, 1fr); }
.day-grid { grid-template-columns: repeat(7, 1fr); }
.use-current { width: 100%; margin-top: 0.75rem; color: #1d4ed8; }
.precision-buttons, .precision-cards, .date-fields, .progressive-fields { display: flex; gap: 0.75rem; align-items: end; flex-wrap: wrap; margin-top: 1.5rem; }
.precision-buttons { border: 0; padding: 0; }
.precision-buttons button, .precision-cards button { border: 1px solid #94a3b8; background: #fff; border-radius: 8px; padding: 0.7rem 1rem; cursor: pointer; }
.precision-buttons .selected, .precision-cards .selected { color: #fff; border-color: #1d4ed8; background: #1d4ed8; }
.date-fields label, .progressive-fields > label { display: flex; flex-direction: column; gap: 0.3rem; font-weight: 700; }
.date-fields input, .progressive-fields input { width: 7rem; padding: 0.5rem; }
.precision-cards { align-items: stretch; }
.precision-cards button { min-width: 10rem; display: flex; flex-direction: column; align-items: start; gap: 0.3rem; }
.precision-cards strong { font-size: 1.4rem; }
.native-date { display: block; margin-top: 1.5rem; font-weight: 700; }
.native-date > input { display: block; margin-top: 0.5rem; padding: 0.5rem; }
.checkbox { flex-direction: row !important; align-items: center; padding: 0.5rem; font-weight: 400 !important; }
.checkbox input { width: auto !important; }
.state { margin-top: 1.5rem; border-left: 4px solid #0f766e; background: #f0fdfa; padding: 1rem; }
.state h2 { margin: 0; font-size: 1rem; }
.state dl { margin-bottom: 0; }
.state dl div { display: grid; grid-template-columns: 12rem 1fr; gap: 0.5rem; padding-top: 0.5rem; }
.state dt { font-weight: 700; }
.state dd { margin: 0; }
.prototype-switcher { position: fixed; z-index: 20; bottom: 1rem; left: 50%; display: flex; gap: 1rem; align-items: center; transform: translateX(-50%); padding: 0.75rem 1rem; color: #fff; background: #172554; border-radius: 999px; box-shadow: 0 8px 20px rgb(15 23 42 / 30%); }
.prototype-switcher button { border: 0; border-radius: 50%; width: 2rem; height: 2rem; cursor: pointer; font-size: 1.2rem; }
@media (max-width: 640px) {
  .state dl div { grid-template-columns: 1fr; }
  .prototype-switcher { width: calc(100% - 2rem); justify-content: space-between; }
}
</style>
