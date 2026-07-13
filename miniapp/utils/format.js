function number(value, fallback = '—') {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return fallback
  return numeric.toLocaleString('zh-CN')
}

function money(value, currency = 'CNY') {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return '—'
  return `${currency === 'CNY' ? '¥' : `${currency} `}${numeric.toFixed(2)}`
}

function dateTime(value, fallback = '—') {
  if (!value) return fallback
  return String(value).replace('T', ' ').slice(0, 16)
}

function relativeDate(value) {
  if (!value) return '时间未知'
  const text = dateTime(value)
  const timestamp = new Date(String(value).replace(' ', 'T')).getTime()
  if (!Number.isFinite(timestamp)) return text
  const diff = Date.now() - timestamp
  if (diff >= 0 && diff < 60 * 1000) return '刚刚'
  if (diff >= 0 && diff < 60 * 60 * 1000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff >= 0 && diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / 3600000)} 小时前`
  return text
}

function errorMessage(error, fallback) {
  return error && error.message ? error.message : fallback
}

function resourceUrl(value) {
  if (!value) return ''
  if (/^https?:\/\//i.test(value)) return value
  const baseUrl = getApp().globalData.apiBaseUrl.replace(/\/$/, '')
  return `${baseUrl}${String(value).startsWith('/') ? '' : '/'}${value}`
}

function maskApiKey(item) {
  const prefix = item && item.apiKeyPrefix ? String(item.apiKeyPrefix) : ''
  return prefix ? `${prefix}••••••••••••` : '已安全隐藏'
}

function percent(used, total) {
  const usedValue = Number(used)
  const totalValue = Number(total)
  if (!Number.isFinite(usedValue) || !Number.isFinite(totalValue) || totalValue <= 0) return 0
  return Math.max(0, Math.min(100, Math.round(usedValue / totalValue * 100)))
}

module.exports = { number, money, dateTime, relativeDate, errorMessage, resourceUrl, maskApiKey, percent }
