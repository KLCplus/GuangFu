import request from './request'

export interface AgentChatRequest {
  sessionId?: number | null
  message: string
  context?: Record<string, unknown>
  mode?: string
  allowedTools?: string[]
  requireApproval?: boolean
  approvalId?: number | null
}

export interface AgentToolInfo {
  name: string
  displayName: string
  category: string
  permissionLevel: string
  requiresApproval: boolean
  enabled: boolean
  description: string
  inputSchema: Record<string, unknown>
}

export interface AgentSession {
  sessionId: number
  title: string
  archived?: boolean
  pinned?: boolean
  status?: string
  createdAt?: string
  updatedAt?: string
}

export interface AgentSseEnvelope {
  event: string
  data: Record<string, unknown>
}

export const createAgentSession = (title?: string) => request.post<AgentSession>('/agent/sessions', { title })
export const getAgentSessions = () => request.get<{ records: AgentSession[]; total: number }>('/agent/sessions')
export const getAgentTools = () => request.get<AgentToolInfo[]>('/agent/tools')
export const approveAgentApproval = (approvalId: number, approved: boolean, comment?: string) =>
  request.post(`/agent/approvals/${approvalId}/approve`, { approved, comment })

export async function streamAgentChat(payload: AgentChatRequest, onEvent: (event: AgentSseEnvelope) => void) {
  const token = localStorage.getItem('token')
  const response = await fetch('/api/agent/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(payload)
  })
  if (!response.ok || !response.body) {
    throw new Error(`Agent SSE 请求失败：${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const chunks = buffer.split(/\n\n/)
    buffer = chunks.pop() ?? ''
    for (const chunk of chunks) {
      const dataLine = chunk.split(/\n/).find((line) => line.startsWith('data:'))
      if (!dataLine) continue
      const raw = dataLine.slice(5).trim()
      if (!raw) continue
      try {
        const parsed = JSON.parse(raw) as AgentSseEnvelope
        onEvent(parsed)
      } catch {
        onEvent({ event: 'error', data: { message: 'SSE 事件解析失败', detail: raw, recoverable: true } })
      }
    }
  }
}
