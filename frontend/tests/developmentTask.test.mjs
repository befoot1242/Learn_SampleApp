import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createServer } from 'vite'

let server, pinia, http, useDevelopmentTaskStore, useAuthStore, AxiosError

before(async () => {
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
  ;({ createPinia: pinia } = await server.ssrLoadModule('pinia'))
  ;({ http } = await server.ssrLoadModule('/src/api/client.ts'))
  ;({ useDevelopmentTaskStore } = await server.ssrLoadModule('/src/stores/developmentTask.ts'))
  ;({ useAuthStore } = await server.ssrLoadModule('/src/stores/auth.ts'))
  ;({ AxiosError } = await server.ssrLoadModule('axios'))
})
after(async () => { await server.close() })

function response(config, content = '内容') {
  const parts = config.url.split('/')
  return { data: { taskId: parts[2], tab: parts[4], content }, status: 200, statusText: 'OK', headers: {}, config }
}

test('tab responses, including empty content, are cached independently', async () => {
  const store = useDevelopmentTaskStore(pinia())
  let calls = 0
  http.defaults.adapter = async (config) => {
    calls++
    return response(config, config.url.endsWith('/basic') ? null : '要件')
  }
  store.open('DT-001')
  await store.load('requirements')
  await store.load('basic')
  await store.load('requirements')
  await store.load('basic')
  assert.equal(calls, 2)
  assert.equal(store.tabs.basic.data.content, null)
  store.clear()
  store.open('DT-001')
  await store.load('requirements')
  assert.equal(calls, 3)
})

test('in-flight requests are shared and cannot populate a reopened task', async () => {
  const store = useDevelopmentTaskStore(pinia())
  let resolveFirst
  let calls = 0
  http.defaults.adapter = (config) => {
    calls++
    if (calls === 1) return new Promise(resolve => { resolveFirst = () => resolve(response(config, '古い内容')) })
    return Promise.resolve(response(config, '新しい内容'))
  }
  store.open('DT-001')
  const first = store.load('requirements')
  const duplicate = store.load('requirements')
  assert.equal(calls, 1)
  store.open('DT-001')
  await store.load('requirements')
  resolveFirst()
  await Promise.all([first, duplicate])
  assert.equal(store.tabs.requirements.data.content, '新しい内容')
  assert.equal(calls, 2)
})

test('errors can be retried and logout or session expiry clears even pending results', async () => {
  const instance = pinia()
  const store = useDevelopmentTaskStore(instance)
  const auth = useAuthStore(instance)
  let calls = 0
  http.defaults.adapter = async (config) => {
    calls++
    if (calls === 1) throw new AxiosError('network', 'ERR_NETWORK', config)
    if (config.url === '/auth/logout') throw new AxiosError('network', 'ERR_NETWORK', config)
    return response(config)
  }
  store.open('DT-001')
  await store.load('requirements')
  assert.equal(store.tabs.requirements.status, 'error')
  await store.load('requirements')
  assert.equal(store.tabs.requirements.status, 'success')
  await assert.rejects(auth.logout())
  assert.equal(store.taskId, null)
  assert.deepEqual(store.tabs, {})
  store.open('DT-002')
  await store.load('basic')
  auth.markSessionExpired()
  assert.equal(store.taskId, null)
  assert.deepEqual(store.tabs, {})
})

test('a late response after logout cannot restore tab data', async () => {
  const instance = pinia()
  const store = useDevelopmentTaskStore(instance)
  const auth = useAuthStore(instance)
  let resolveTab
  http.defaults.adapter = (config) => {
    if (config.url === '/auth/logout') {
      return Promise.resolve({ data: {}, status: 200, statusText: 'OK', headers: {}, config })
    }
    return new Promise(resolve => { resolveTab = () => resolve(response(config)) })
  }
  store.open('DT-001')
  const pending = store.load('requirements')
  await auth.logout()
  resolveTab()
  await pending
  assert.equal(store.taskId, null)
  assert.deepEqual(store.tabs, {})
})

test('401 is reported to the screen without caching it as a successful response', async () => {
  const store = useDevelopmentTaskStore(pinia())
  http.defaults.adapter = async (config) => {
    throw new AxiosError('unauthorized', 'ERR_BAD_REQUEST', config, undefined, {
      status: 401, statusText: 'Unauthorized', headers: {}, config, data: {},
    })
  }
  store.open('DT-001')
  assert.equal(await store.load('requirements'), true)
  assert.notEqual(store.tabs.requirements?.status, 'success')
})
