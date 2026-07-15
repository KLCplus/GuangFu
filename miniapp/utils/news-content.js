function normalizeText(value) {
  return String(value || '').toLowerCase().replace(/[\s，。！？、；：“”‘’（）《》【】,.!?;:'"()\[\]<>\-_—]/g, '')
}

function visibleSummary(summary, content) {
  const normalizedSummary = normalizeText(summary)
  if (!normalizedSummary) return ''
  const metadataMatches = ['目录项的基本信息', '公开事项名称', '索引号', '主办单位', '制发日期'].filter((label) => String(summary).includes(label))
  if (metadataMatches.length >= 2) return ''
  const normalizedBody = normalizeText(String(content || '').replace(/<[^>]+>/g, ' '))
  const bodyStart = normalizedBody.slice(0, Math.max(180, normalizedSummary.length * 2))
  if (bodyStart.indexOf(normalizedSummary) === 0 || normalizedSummary.indexOf(bodyStart) === 0) return ''
  const length = Math.min(normalizedSummary.length, bodyStart.length)
  if (length >= 24) {
    let same = 0
    for (let index = 0; index < length; index += 1) if (normalizedSummary[index] === bodyStart[index]) same += 1
    if (same / length >= 0.82) return ''
  }
  return String(summary).trim()
}

function cleanLegacyContent(content, title) {
  let value = String(content || '').trim()
  if (!value) return ''
  value = value.replace(/<(script|style|iframe|nav|footer|form|button)\b[^>]*>[\s\S]*?<\/\1>/gi, '')
  value = value.replace(/<table\b[^>]*>[\s\S]*?<\/table>/gi, (table) => {
    const text = table.replace(/<[^>]+>/g, ' ')
    const matches = ['目录项的基本信息', '公开事项名称', '索引号', '主办单位', '制发日期'].filter((label) => text.includes(label))
    return matches.length >= 2 ? '' : table
  })
  const normalizedTitle = normalizeText(title)
  value = value.replace(/^\s*<(h[1-4]|p)\b[^>]*>([\s\S]*?)<\/\1>/i, (block, tag, text) => normalizeText(text.replace(/<[^>]+>/g, '')) === normalizedTitle ? '' : block)
  return value.trim()
}

module.exports = { visibleSummary, cleanLegacyContent }
