#!/usr/bin/env node

const baseUrl = process.env.AGENT_BASE_URL || 'http://127.0.0.1:8080'
const token = process.env.AGENT_TEST_TOKEN

const cases = [
  { name: 'slash station', input: '/station 2', expectTool: 'station.detail', expectApproval: false },
  { name: 'slash weather', input: '/weather 2', expectTool: 'weather.current', expectApproval: false },
  { name: 'prediction detail', input: '/predict 8', expectTool: 'prediction.detail', expectApproval: false },
  { name: 'report approval', input: '/report 2', expectTool: 'report.generate', expectApproval: true },
  { name: 'normal chat', input: '什么是光伏功率预测？', expectTool: null, expectApproval: false }
]

if (!token) {
  console.error('AGENT_TEST_TOKEN is required. Example: AGENT_TEST_TOKEN=*** node scripts/agent-e2e-test.mjs')
  process.exit(2)
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
    let data = {}
    const raw = dataLines.join('\n')
    if (raw) {
      try { data = JSON.parse(raw) } catch { data = { raw } }
    }
    events.push({ event, data })
  }
  return { events, rest }
}

async function runCase(testCase) {
  const response = await fetch(`${baseUrl}/api/agent/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify({
      message: testCase.input,
      context: { conversationMode: 'independent_agent' },
      mode: 'auto',
      allowedTools: [],
      requireApproval: true
    })
  })

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    return { ...testCase, ok: false, events: [], tools: [], error: `HTTP ${response.status} ${text.slice(0, 160)}` }
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

  const tools = events
    .filter((item) => item.event === 'tool_call' || item.event === 'approval_required')
    .map((item) => item.data?.toolName)
    .filter(Boolean)
  const hasFinal = events.some((item) => item.event === 'final')
  const hasApproval = events.some((item) => item.event === 'approval_required')
  const hasExpectedTool = testCase.expectTool ? tools.includes(testCase.expectTool) : tools.length === 0
  const ok = hasExpectedTool && (testCase.expectApproval ? hasApproval : hasFinal || hasApproval)
  return { ...testCase, ok, events: events.map((item) => item.event), tools, error: '' }
}

const results = []
for (const testCase of cases) {
  try {
    results.push(await runCase(testCase))
  } catch (error) {
    results.push({ ...testCase, ok: false, events: [], tools: [], error: error instanceof Error ? error.message : String(error) })
  }
}

for (const result of results) {
  console.log(`\n[${result.ok ? 'PASS' : 'FAIL'}] ${result.name}`)
  console.log(`input: ${result.input}`)
  console.log(`events: ${result.events.join(' -> ') || '-'}`)
  console.log(`tools: ${result.tools.join(', ') || '-'}`)
  if (result.error) console.log(`error: ${result.error}`)
}

const failed = results.filter((item) => !item.ok).length
console.log(`\nsummary: ${results.length - failed}/${results.length} passed`)
process.exit(failed ? 1 : 0)
