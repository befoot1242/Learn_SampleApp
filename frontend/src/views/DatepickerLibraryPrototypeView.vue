<script setup lang="ts">
// Prototype: six Vue datepicker libraries, switchable with ?library=.
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElDatePicker } from 'element-plus'
import PrimeDatePicker from 'primevue/datepicker'
import { VueDatePicker } from '@vuepic/vue-datepicker'
import DatePickerNext from 'vue-datepicker-next'
import { DatePicker as AntDatePicker } from 'ant-design-vue'
import { NDatePicker } from 'naive-ui'
import AppLayout from '../components/AppLayout.vue'
import 'element-plus/dist/index.css'
import '@vuepic/vue-datepicker/dist/main.css'
import 'vue-datepicker-next/index.css'
import 'ant-design-vue/dist/reset.css'

type Precision = 'year' | 'month' | 'day'
type Library = 'element-plus' | 'primevue' | 'vuepic' | 'datepicker-next' | 'ant-design-vue' | 'naive-ui'

const route = useRoute()
const router = useRouter()
const isDevelopment = import.meta.env.DEV
const precision = ref<Precision>('day')

const libraries: Array<{ key: Library; name: string; mode: string }> = [
  { key: 'element-plus', name: 'Element Plus', mode: 'type' },
  { key: 'primevue', name: 'PrimeVue', mode: 'view' },
  { key: 'vuepic', name: '@vuepic/vue-datepicker', mode: 'year-picker / month-picker' },
  { key: 'datepicker-next', name: 'vue-datepicker-next', mode: 'type' },
  { key: 'ant-design-vue', name: 'Ant Design Vue', mode: 'picker' },
  { key: 'naive-ui', name: 'Naive UI', mode: 'type' },
]

const library = computed<Library>(() => {
  const value = route.query.library
  return libraries.some(({ key }) => key === value) ? value as Library : 'element-plus'
})
const libraryInfo = computed(() => libraries.find(({ key }) => key === library.value)!)

const elementValue = ref('2026-11-01')
const primeValue = ref(new Date(2026, 10, 1))
const vuePicDayValue = ref(new Date(2026, 10, 1))
const vuePicMonthValue = ref({ month: 10, year: 2026 })
const vuePicYearValue = ref(2026)
const datepickerNextValue = ref('2026-11-01')
const antValue = ref('2026-11-01')
const naiveValue = ref(new Date(2026, 10, 1).getTime())

const precisionLabels: Record<Precision, string> = {
  year: '年のみ',
  month: '年月',
  day: '年月日',
}
const elementType = computed(() => precision.value === 'day' ? 'date' : precision.value)
const elementValueFormat = computed(() => precision.value === 'day' ? 'YYYY-MM-DD' : precision.value === 'month' ? 'YYYY-MM' : 'YYYY')
const datepickerNextType = elementType
const datepickerNextFormat = computed(() => precision.value === 'day' ? 'YYYY-MM-DD' : precision.value === 'month' ? 'YYYY-MM' : 'YYYY')
const antPicker = elementType
const antValueFormat = elementValueFormat
const naiveType = elementType

const selectedValue = computed(() => {
  switch (library.value) {
    case 'element-plus':
      return elementValue.value
    case 'primevue':
      return primeValue.value.toISOString()
    case 'vuepic':
      return precision.value === 'day'
        ? vuePicDayValue.value.toISOString()
        : precision.value === 'month'
          ? JSON.stringify(vuePicMonthValue.value)
          : String(vuePicYearValue.value)
    case 'datepicker-next':
      return datepickerNextValue.value
    case 'ant-design-vue':
      return antValue.value
    case 'naive-ui':
      return new Date(naiveValue.value).toISOString()
  }
})

function choosePrecision(value: Precision) {
  precision.value = value
}

function moveLibrary(step: number) {
  const currentIndex = libraries.findIndex(({ key }) => key === library.value)
  const next = libraries[(currentIndex + step + libraries.length) % libraries.length]
  router.replace({ query: { ...route.query, library: next.key } })
}

function handleKeydown(event: KeyboardEvent) {
  const target = event.target as HTMLElement
  if (target.matches('input, textarea, [contenteditable="true"]')) return
  if (event.key === 'ArrowLeft') moveLibrary(-1)
  if (event.key === 'ArrowRight') moveLibrary(1)
}

onMounted(() => window.addEventListener('keydown', handleKeydown))
onUnmounted(() => window.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <AppLayout>
    <h1>Vue datepickerライブラリ比較プロトタイプ</h1>
    <p class="intro">
      年のみ・年月のみ・年月日の選択モードを、候補ライブラリごとに実際のコンポーネントで比較します。
      この画面は送信しない試作であり、取引登録APIには接続しません。
    </p>

    <section class="prototype">
      <header>
        <p class="eyebrow">{{ libraryInfo.name }}</p>
        <h2>{{ libraryInfo.mode }} による選択モード</h2>
        <p>ライブラリの標準APIで、確定する値の精度を切り替えています。</p>
      </header>

      <div class="precision-buttons" role="group" aria-label="取引日の精度">
        <button
          v-for="(label, value) in precisionLabels"
          :key="value"
          :class="{ selected: precision === value }"
          type="button"
          @click="choosePrecision(value as Precision)"
        >
          {{ label }}
        </button>
      </div>

      <div class="picker-sample">
        <label for="library-datepicker">取引日</label>

        <ElDatePicker
          v-if="library === 'element-plus'"
          id="library-datepicker"
          v-model="elementValue"
          :type="elementType"
          :value-format="elementValueFormat"
          placeholder="選択してください"
        />

        <PrimeDatePicker
          v-else-if="library === 'primevue'"
          id="library-datepicker"
          v-model="primeValue"
          :view="precision === 'day' ? 'date' : precision"
          date-format="yy-mm-dd"
          show-icon
        />

        <VueDatePicker
          v-else-if="library === 'vuepic' && precision === 'day'"
          id="library-datepicker"
          v-model="vuePicDayValue"
          :enable-time-picker="false"
        />
        <VueDatePicker
          v-else-if="library === 'vuepic' && precision === 'month'"
          id="library-datepicker"
          v-model="vuePicMonthValue"
          month-picker
        />
        <VueDatePicker
          v-else-if="library === 'vuepic'"
          id="library-datepicker"
          v-model="vuePicYearValue"
          year-picker
        />

        <DatePickerNext
          v-else-if="library === 'datepicker-next'"
          id="library-datepicker"
          v-model:value="datepickerNextValue"
          :type="datepickerNextType"
          :format="datepickerNextFormat"
          value-type="format"
          placeholder="選択してください"
        />

        <AntDatePicker
          v-else-if="library === 'ant-design-vue'"
          id="library-datepicker"
          v-model:value="antValue"
          :picker="antPicker"
          :value-format="antValueFormat"
          placeholder="選択してください"
        />

        <NDatePicker
          v-else
          id="library-datepicker"
          v-model:value="naiveValue"
          :type="naiveType"
          value-format="yyyy-MM-dd"
          clearable
        />
      </div>
    </section>

    <section class="state">
      <h2>現在の状態</h2>
      <dl>
        <div><dt>ライブラリ</dt><dd>{{ libraryInfo.name }}</dd></div>
        <div><dt>選択モード</dt><dd>{{ precisionLabels[precision] }}</dd></div>
        <div><dt>モデル値</dt><dd><code>{{ selectedValue }}</code></dd></div>
      </dl>
    </section>

    <div v-if="isDevelopment" class="prototype-switcher" aria-label="ライブラリ切替">
      <button type="button" aria-label="前のライブラリ" @click="moveLibrary(-1)">←</button>
      <span>{{ libraryInfo.name }}</span>
      <button type="button" aria-label="次のライブラリ" @click="moveLibrary(1)">→</button>
    </div>
  </AppLayout>
</template>

<style scoped>
.intro { color: var(--color-text-sub); max-width: 760px; }
.prototype { max-width: 760px; margin-top: 24px; padding: 28px; border: 1px solid var(--color-border); border-radius: 12px; background: #fff; }
.prototype h2 { margin: 4px 0 8px; }
.eyebrow { margin: 0; color: #1d4ed8; font-size: 13px; font-weight: 700; }
.precision-buttons { display: flex; flex-wrap: wrap; gap: 8px; margin: 24px 0; }
.precision-buttons button { padding: 8px 14px; }
.precision-buttons .selected { color: #fff; border-color: #1d4ed8; background: #1d4ed8; }
.picker-sample { display: flex; flex-direction: column; gap: 8px; max-width: 360px; }
.picker-sample > label { font-size: 14px; font-weight: 700; }
.state { max-width: 760px; margin-top: 20px; padding: 18px; border-left: 4px solid #0f766e; background: #f0fdfa; }
.state h2 { margin: 0; font-size: 16px; }
.state dl { margin-bottom: 0; }
.state dl div { display: grid; grid-template-columns: 10rem 1fr; gap: 8px; padding-top: 8px; }
.state dt { font-weight: 700; }
.state dd { margin: 0; overflow-wrap: anywhere; }
.prototype-switcher { position: fixed; z-index: 20; bottom: 1rem; left: 50%; display: flex; gap: 1rem; align-items: center; transform: translateX(-50%); padding: 0.75rem 1rem; color: #fff; background: #172554; border-radius: 999px; box-shadow: 0 8px 20px rgb(15 23 42 / 30%); }
.prototype-switcher button { width: 2rem; height: 2rem; border: 0; border-radius: 50%; cursor: pointer; font-size: 1.2rem; }
@media (max-width: 640px) {
  .prototype { padding: 20px; }
  .state dl div { grid-template-columns: 1fr; }
  .prototype-switcher { width: calc(100% - 2rem); justify-content: space-between; }
}
</style>
