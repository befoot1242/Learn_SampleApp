<script setup lang="ts">
import { ref } from 'vue'
import * as yup from 'yup'
import AppLayout from '../components/AppLayout.vue'

interface InputFormValues {
  name: string
  address: string
  date: string
  details: string
}

const initialValues: InputFormValues = {
  name: '',
  address: '',
  date: '',
  details: '',
}

const schema: yup.ObjectSchema<InputFormValues> = yup.object({
  name: yup.string().trim().required('氏名を入力してください').max(100, '氏名は100文字以内で入力してください'),
  address: yup.string().trim().required('住所を入力してください').max(200, '住所は200文字以内で入力してください'),
  date: yup
    .string()
    .required('日付を入力してください')
    .test('valid-date', '有効な日付を入力してください', (value) => {
      if (!value) return true
      if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
      const parsed = new Date(`${value}T00:00:00.000Z`)
      return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
    }),
  details: yup.string().max(1000, '自由記入欄は1000文字以内で入力してください').defined(),
})

const values = ref<InputFormValues>({ ...initialValues })
const fieldErrors = ref<Partial<Record<keyof InputFormValues, string>>>({})
const submitted = ref(false)

async function submitForm() {
  fieldErrors.value = {}
  submitted.value = false

  try {
    values.value = await schema.validate(values.value, { abortEarly: false })
    submitted.value = true
  } catch (error) {
    if (error instanceof yup.ValidationError) {
      for (const issue of error.inner) {
        if (issue.path && !fieldErrors.value[issue.path as keyof InputFormValues]) {
          fieldErrors.value[issue.path as keyof InputFormValues] = issue.message
        }
      }
    }
  }
}

function clearForm() {
  values.value = { ...initialValues }
  fieldErrors.value = {}
  submitted.value = false
}
</script>

<template>
  <AppLayout>
    <h1>入力フォーム</h1>
    <p class="text-sub">氏名・住所・日付・自由記入欄を入力してください。</p>

    <div v-if="submitted" class="alert success" role="status">
      入力を受け付けました。このサンプル画面ではデータは保存されません。
    </div>

    <div class="card">
      <form class="input-form" novalidate @submit.prevent="submitForm">
        <div class="field">
          <label class="required" for="name">氏名</label>
          <input
            id="name"
            v-model="values.name"
            autocomplete="name"
            maxlength="100"
            :aria-invalid="Boolean(fieldErrors.name)"
            :aria-describedby="fieldErrors.name ? 'name-error' : undefined"
          />
          <p v-if="fieldErrors.name" id="name-error" class="field-error">{{ fieldErrors.name }}</p>
        </div>

        <div class="field">
          <label class="required" for="address">住所</label>
          <input
            id="address"
            v-model="values.address"
            autocomplete="street-address"
            maxlength="200"
            :aria-invalid="Boolean(fieldErrors.address)"
            :aria-describedby="fieldErrors.address ? 'address-error' : undefined"
          />
          <p v-if="fieldErrors.address" id="address-error" class="field-error">{{ fieldErrors.address }}</p>
        </div>

        <div class="field">
          <label class="required" for="date">日付</label>
          <input
            id="date"
            v-model="values.date"
            type="date"
            :aria-invalid="Boolean(fieldErrors.date)"
            :aria-describedby="fieldErrors.date ? 'date-error' : undefined"
          />
          <p v-if="fieldErrors.date" id="date-error" class="field-error">{{ fieldErrors.date }}</p>
        </div>

        <div class="field">
          <label for="details">自由記入欄</label>
          <textarea
            id="details"
            v-model="values.details"
            rows="5"
            maxlength="1000"
            :aria-invalid="Boolean(fieldErrors.details)"
            :aria-describedby="fieldErrors.details ? 'details-error' : undefined"
          />
          <p v-if="fieldErrors.details" id="details-error" class="field-error">{{ fieldErrors.details }}</p>
        </div>

        <div class="toolbar">
          <span class="spacer" />
          <button type="button" @click="clearForm">クリア</button>
          <button class="primary" type="submit">送信</button>
        </div>
      </form>
    </div>
  </AppLayout>
</template>

<style scoped>
.input-form {
  max-width: 640px;
}
</style>
