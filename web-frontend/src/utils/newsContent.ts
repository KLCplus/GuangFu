function normalizeText(value?: string | null) {
  return String(value || '').toLowerCase().replace(/[\p{P}\p{S}\s]+/gu, '')
}

export function visibleNewsSummary(summary?: string | null, content?: string | null) {
  const normalizedSummary = normalizeText(summary)
  if (!normalizedSummary) return ''
  const metadataMatches = ['目录项的基本信息', '公开事项名称', '索引号', '主办单位', '制发日期']
    .filter((label) => String(summary).includes(label))
  if (metadataMatches.length >= 2) return ''
  const bodyText = new DOMParser().parseFromString(String(content || ''), 'text/html').body.textContent || ''
  const normalizedBody = normalizeText(bodyText)
  const bodyStart = normalizedBody.slice(0, Math.max(180, normalizedSummary.length * 2))
  if (bodyStart.startsWith(normalizedSummary) || normalizedSummary.startsWith(bodyStart)) return ''
  const length = Math.min(normalizedSummary.length, bodyStart.length)
  if (length >= 24) {
    let same = 0
    for (let index = 0; index < length; index += 1) if (normalizedSummary[index] === bodyStart[index]) same += 1
    if (same / length >= 0.82) return ''
  }
  return String(summary).trim()
}

export function renderNewsContent(content?: string | null) {
  const value = String(content || '').trim()
  if (!value) return ''
  if (/<(?:p|br|h[1-4]|ul|ol|li|blockquote|table|strong|em|a)\b/i.test(value)) return value
  return value.split(/\n{2,}/).map((paragraph) => `<p>${escapeHtml(paragraph).replace(/\n/g, '<br>')}</p>`).join('')
}

function escapeHtml(value: string) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;')
}
