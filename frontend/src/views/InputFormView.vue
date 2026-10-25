<script setup lang="ts">
import { ref } from 'vue'
import * as yup from 'yup'
import AppLayout from '../components/AppLayout.vue'

interface InputFormValues {
  employeeCode: string
  name: string
  address: string
  date: string
  details: string
}

const initialValues: InputFormValues = {
  employeeCode: '',
  name: '',
  address: '',
  date: '',
  details: '',
}

const textCharacterWhitelist = /^[\x20-\x7E\u3000-\u30FF\u4E00-\u9FFF\uFF01-\uFF60\uFF61-\uFF9F\uFFE0-\uFFEE]*$/u
const detailsCharacterWhitelist = /^[\x20-\x7E\u3000-\u30FF\u4E00-\u9FFF\uFF01-\uFF60\uFF61-\uFF9F\uFFE0-\uFFEE\r\n]*$/u

const schema: yup.ObjectSchema<InputFormValues> = yup.object({
  employeeCode: yup
    .string()
    .trim()
    .required('社員コードを入力してください')
    .matches(/^[\x20-\x7E]*$/, '社員コードは半角ASCII文字で入力してください'),
  name: yup
    .string()
    .trim()
    .required('氏名を入力してください')
    .max(100, '氏名は100文字以内で入力してください')
    .matches(textCharacterWhitelist, '氏名に使用できない文字が含まれています'),
  address: yup
    .string()
    .trim()
    .required('住所を入力してください')
    .max(200, '住所は200文字以内で入力してください')
    .matches(textCharacterWhitelist, '住所に使用できない文字が含まれています'),
  date: yup
    .string()
    .required('日付を入力してください')
    .test('valid-date', '有効な日付を入力してください', (value) => {
      if (!value) return true
      if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
      const parsed = new Date(`${value}T00:00:00.000Z`)
      return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
    }),
  details: yup
    .string()
    .max(1000, '自由記入欄は1000文字以内で入力してください')
    .matches(detailsCharacterWhitelist, '自由記入欄に使用できない文字が含まれています')
    .defined(),
})

const values = ref<InputFormValues>({ ...initialValues })
const fieldErrors = ref<Partial<Record<keyof InputFormValues, string>>>({})
const submitted = ref(false)

async function validateField(field: keyof InputFormValues) {
  try {
    await schema.validateAt(field, values.value)
    delete fieldErrors.value[field]
  } catch (error) {
    if (error instanceof yup.ValidationError) {
      fieldErrors.value[field] = error.message
      return
    }
    throw error
  }
}

function clearSubmittedState() {
  submitted.value = false
}

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
    <p class="text-sub">社員コード・氏名・住所・日付・自由記入欄を入力してください。</p>

    <div v-if="submitted" class="alert success" role="status">
      入力を受け付けました。このサンプル画面ではデータは保存されません。
    </div>

    <div class="card">
      <form class="input-form" novalidate @input="clearSubmittedState" @submit.prevent="submitForm">
        <div class="field">
          <label class="required" for="employeeCode">社員コード</label>
          <input
            id="employeeCode"
            v-model="values.employeeCode"
            @blur="validateField('employeeCode')"
            :aria-invalid="Boolean(fieldErrors.employeeCode)"
            :aria-describedby="fieldErrors.employeeCode ? 'employeeCode-error' : undefined"
          />
          <p v-if="fieldErrors.employeeCode" id="employeeCode-error" class="field-error">
            {{ fieldErrors.employeeCode }}
          </p>
        </div>

        <div class="field">
          <label class="required" for="name">氏名</label>
          <input
            id="name"
            v-model="values.name"
            autocomplete="name"
            maxlength="100"
            @blur="validateField('name')"
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
            @blur="validateField('address')"
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
            @blur="validateField('date')"
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
            @blur="validateField('details')"
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
