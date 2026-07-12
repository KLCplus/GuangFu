#!/usr/bin/env node

const baseUrl = process.env.AGENT_BASE_URL || 'http://127.0.0.1:8080'
const token = process.env.AGENT_TEST_TOKEN || ''

const headers = {
  'Content-Type': 'application/json',
  ...(token ? { Authorization: `Bearer ${token}` } : {})
}

function parseSse(buffer) {
  const events = []
  const blocks = buffer.split(/\n\n/)
  const rest = blocks.pop() || ''
  for (const block of blocks) {
    const lines = block.split(/\n/)
    let event = 'message'
    const dataLines = []
    for (const line of lines) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
    }
    const raw = dataLines.join('\n')
    if (!raw) continue
    try {
      const parsed = JSON.parse(raw)
      events.push({ event, data: parsed?.data ?? parsed })
    } catch {
      events.push({ event, data: { raw } })
    }
  }
  return { events, rest }
}

async function streamChat(payload) {
  const response = await fetch(`${baseUrl}/api/agent/chat/stream`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload)
  })
  if (!response.ok || !response.body) {
    const text = await response.text().catch(() => '')
    throw new Error(`chat stream failed: HTTP ${response.status} ${text.slice(0, 240)}`)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  const events = []
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parsed = parseSse(buffer)
    events.push(...parsed.events)
    buffer = parsed.rest
  }
  if (buffer.trim()) events.push(...parseSse(`${buffer}\n\n`).events)
  return events
}

function requireEvent(events, name, predicate = () => true) {
  const found = events.find((event) => event.event === name && predicate(event.data))
  if (!found) {
    throw new Error(`missing event ${name}`)
  }
  return found.data
}

function requireUiComponents(events, components) {
  const actual = events
    .filter((event) => event.event === 'ui_instruction')
    .map((event) => event.data?.component)
  for (const component of components) {
    if (!actual.includes(component)) {
      throw new Error(`missing ui component ${component}; got ${actual.join(', ') || '-'}`)
    }
  }
}

async function approve(approvalId, approved) {
  const response = await fetch(`${baseUrl}/api/agent/approvals/${approvalId}/approve`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ approved, comment: approved ? 'E2E approved' : 'E2E rejected' })
  })
  if (!response.ok) {
    const text = await response.text().catch(() => '')
    throw new Error(`approval failed: HTTP ${response.status} ${text.slice(0, 240)}`)
  }
  return response.json()
}

async function main() {
  console.log(`[migrated-agent-e2e] baseUrl=${baseUrl}`)

  const analysisEvents = await streamChat({
    message: '分析 1 号电站当前运行情况，结合天气和最近预测结果。',
    context: { conversationMode: 'migrated_agent_e2e' },
    requireApproval: true
  })
  requireEvent(analysisEvents, 'started', (data) => data.runtime === 'migrated')
  requireEvent(analysisEvents, 'run_completed')
  requireUiComponents(analysisEvents, ['StationSummaryCard', 'WeatherImpactCard', 'PredictionTrendCard'])
  console.log('[PASS] analysis chain emits migrated runtime events and photovoltaic UI instructions')

  const reportEvents = await streamChat({
    message: '分析 1 号电站当前运行情况，结合天气和最近预测结果，并生成报告。',
    context: { conversationMode: 'migrated_agent_e2e' },
    requireApproval: true
  })
  requireUiComponents(reportEvents, ['StationSummaryCard', 'WeatherImpactCard', 'PredictionTrendCard', 'ApprovalActionCard'])
  const approval = requireEvent(reportEvents, 'approval_required', (data) => data.toolName === 'report.generate' && data.approvalId)
  console.log(`[PASS] report generation pauses for approval approvalId=${approval.approvalId}`)

  await approve(approval.approvalId, true)
  const continuationEvents = await streamChat({
    message: '继续执行已确认的操作',
    approvalId: approval.approvalId,
    sessionId: approval.sessionId ?? undefined,
    context: { conversationMode: 'migrated_agent_e2e' }
  })
  const toolResult = requireEvent(continuationEvents, 'tool_result', (data) => data.toolName === 'report.generate' && data.status === 'success')
  const reportId = toolResult.dataPreview?.reportId ?? toolResult.data?.reportId
  if (!reportId) {
    throw new Error('report.generate succeeded but no reportId was returned')
  }
  requireEvent(continuationEvents, 'final')
  console.log(`[PASS] approved report generation completed reportId=${reportId}`)

  console.log('[migrated-agent-e2e] 3/3 passed')
}

main().catch((error) => {
  console.error(`[FAIL] ${error instanceof Error ? error.message : String(error)}`)
  process.exit(1)
})
