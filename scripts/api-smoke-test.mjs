#!/usr/bin/env node

const args = Object.fromEntries(
  process.argv.slice(2).map((arg) => {
    const [key, ...rest] = arg.replace(/^--/, '').split('=')
    return [key, rest.join('=') || true]
  })
)

const baseUrl = String(args.baseUrl || process.env.API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
let token = String(args.token || process.env.API_TOKEN || '')
const username = String(args.username || process.env.API_USERNAME || '')
const password = String(args.password || process.env.API_PASSWORD || '')
const apiKey = String(args.apiKey || process.env.PV_API_KEY || '')
const stationId = Number(args.stationId || process.env.STATION_ID || 2)
const taskId = Number(args.taskId || process.env.TASK_ID || 8)
const modelId = Number(args.modelId || process.env.MODEL_ID || 1)

const safeStringify = (value) => JSON.stringify(value, (key, inner) => {
  if (/token|authorization|apiKey|refreshToken/i.test(key)) return '***'
  return inner
})

const redact = (value) => {
  if (value == null) return value
  const text = typeof value === 'string' ? value : safeStringify(value)
  return text
    .replace(/Bearer\s+[A-Za-z0-9._-]+/g, 'Bearer ***')
    .replace(/eyJ[A-Za-z0-9._-]{20,}/g, '***')
    .replace(/pv_[0-9a-f]{8}_[A-Za-z0-9_-]{20,}/g, 'pv_********_***')
    .slice(0, 500)
}

const unwrap = (body) => {
  if (body && typeof body === 'object' && typeof body.code === 'number' && 'data' in body) {
    return body.data
  }
  return body
}

async function call(test) {
  const started = performance.now()
  const headers = { ...(test.headers || {}) }
  if (test.auth === 'jwt' && token) headers.Authorization = `Bearer ${token}`
  if (test.auth === 'apiKey' && apiKey) headers['X-API-KEY'] = apiKey
  if (test.body && !(test.body instanceof FormData)) headers['Content-Type'] = 'application/json'

  const url = `${baseUrl}${test.path}`
  try {
    const response = await fetch(url, {
      method: test.method,
      headers,
      body: test.body ? JSON.stringify(test.body) : undefined
    })
    const contentType = response.headers.get('content-type') || ''
    const raw = contentType.includes('application/json') ? await response.json() : await response.text()
    const data = unwrap(raw)
    const ok = test.expect ? test.expect(response.status, raw, data) : response.ok
    return {
      ...test,
      statusCode: response.status,
      ok,
      duration: Math.round(performance.now() - started),
      data,
      summary: redact(raw),
      mockSuspected: /mock|demo|fake|模拟|假数据|sample/i.test(JSON.stringify(raw))
    }
  } catch (error) {
    return {
      ...test,
      statusCode: 'NETWORK',
      ok: false,
      duration: Math.round(performance.now() - started),
      error: error.message,
      summary: ''
    }
  }
}

function predictionInput() {
  const start = new Date(Date.now() - 29 * 60 * 1000)
  return Array.from({ length: 30 }, (_, index) => {
    const time = new Date(start.getTime() + index * 60 * 1000)
      .toISOString()
      .replace('T', ' ')
      .slice(0, 19)
    return { time, power: 100 + index, temperature: 25, irradiance: 700 }
  })
}

const tests = [
  { name: 'auth.login.missing', method: 'POST', path: '/api/auth/login', body: {}, expect: (s) => s >= 400 },
  { name: 'auth.profile.no-token', method: 'GET', path: '/api/user/profile', expect: (s) => s === 401 || s === 403 },
  { name: 'station.list', method: 'GET', path: '/api/stations?pageNum=1&pageSize=10', auth: 'jwt' },
  { name: 'station.detail', method: 'GET', path: `/api/stations/${stationId}`, auth: 'jwt' },
  { name: 'pv.realtime', method: 'GET', path: `/api/stations/${stationId}/realtime`, auth: 'jwt' },
  { name: 'pv.history', method: 'GET', path: `/api/stations/${stationId}/history?interval=1min`, auth: 'jwt' },
  { name: 'weather.current', method: 'GET', path: `/api/stations/${stationId}/weather/current`, auth: 'jwt' },
  { name: 'weather.forecast', method: 'GET', path: `/api/stations/${stationId}/weather/forecast`, auth: 'jwt' },
  { name: 'model.list', method: 'GET', path: '/api/models', auth: 'jwt' },
  { name: 'model.detail', method: 'GET', path: `/api/models/${modelId}`, auth: 'jwt' },
  { name: 'prediction.history', method: 'GET', path: '/api/predictions/history?pageNum=1&pageSize=10', auth: 'jwt' },
  { name: 'prediction.detail', method: 'GET', path: `/api/predictions/${taskId}`, auth: 'jwt' },
  { name: 'prediction.results', method: 'GET', path: `/api/predictions/${taskId}/results`, auth: 'jwt' },
  {
    name: 'report.list',
    method: 'GET',
    path: '/api/analysis/reports?pageNum=1&pageSize=10',
    auth: 'jwt'
  },
  {
    name: 'report.generate',
    method: 'POST',
    path: '/api/analysis/report',
    auth: 'jwt',
    body: {
      stationId,
      taskId,
      title: `Smoke Test 电站 ${stationId} 综合分析报告`,
      includeWeather: true,
      includePrediction: true
    }
  },
  { name: 'api.keys', method: 'GET', path: '/api/open/keys', auth: 'jwt' },
  { name: 'api.logs', method: 'GET', path: '/api/open/call-logs?pageNum=1&pageSize=10', auth: 'jwt' },
  {
    name: 'openapi.predict.no-key',
    method: 'POST',
    path: '/openapi/v1/predict',
    body: { stationId, modelName: 'DLinear', input: predictionInput() },
    expect: (s) => s === 401 || s === 403
  },
  {
    name: 'openapi.predict.with-key',
    method: 'POST',
    path: '/openapi/v1/predict',
    auth: 'apiKey',
    body: { stationId, modelName: 'DLinear', input: predictionInput() },
    skip: !apiKey
  },
  { name: 'news.list', method: 'GET', path: '/api/news?pageNum=1&pageSize=10', auth: 'jwt' },
  { name: 'notifications.list', method: 'GET', path: '/api/notifications?pageNum=1&pageSize=10', auth: 'jwt' },
  { name: 'admin.users', method: 'GET', path: '/api/admin/users?pageNum=1&pageSize=10', auth: 'jwt' },
  { name: 'admin.models', method: 'GET', path: '/api/admin/models', auth: 'jwt' },
  { name: 'admin.api-keys', method: 'GET', path: '/api/admin/api-keys', auth: 'jwt' }
]

if (username && password && !token) {
  const login = await call({
    name: 'auth.login',
    method: 'POST',
    path: '/api/auth/login',
    body: { username, password }
  })
  console.log(`${login.ok ? 'OK ' : 'FAIL'} ${login.method} ${login.path} ${login.statusCode} ${login.duration}ms ${login.error || login.summary}`)
  if (login.ok) {
    token = login.data?.token || ''
  }
}

const results = []
for (const test of tests) {
  if (test.skip) {
    results.push({ ...test, statusCode: 'SKIP', ok: false, duration: 0, summary: 'Skipped: missing optional credential' })
    continue
  }
  const result = await call(test)
  results.push(result)
  const status = result.ok ? 'OK ' : 'FAIL'
  const auth = test.auth ? ` auth=${test.auth}` : ''
  console.log(`${status} ${test.method} ${test.path}${auth} status=${result.statusCode} duration=${result.duration}ms`)
  if (result.error || result.summary) console.log(`  ${result.error || result.summary}`)
}

const total = results.length
const success = results.filter((item) => item.ok).length
const failed = results.filter((item) => !item.ok && item.statusCode !== 'SKIP').length
const authRequired = results.filter((item) => item.auth).length
const mockSuspected = results.filter((item) => item.mockSuspected).length
const notAgentReady = results.filter((item) => !item.ok || item.statusCode === 'SKIP').length

console.log('\nSummary')
console.log(`total=${total}`)
console.log(`success=${success}`)
console.log(`failed=${failed}`)
console.log(`authRequired=${authRequired}`)
console.log(`mockSuspected=${mockSuspected}`)
console.log(`notAgentReady=${notAgentReady}`)
