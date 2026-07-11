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

export interface AgentToolCallRecord {
  toolCallId: number
  sessionId: number
  messageId?: number
  clientToolCallId?: string
  toolName: string
  displayName?: string
  arguments?: Record<string, unknown>
  result?: Record<string, unknown>
  status: string
  errorMessage?: string
  durationMs?: number
  createdAt?: string
}

export interface AgentApprovalRecord {
  approvalId: number
  sessionId: number
  toolCallId: number
  toolName?: string
  reason?: string
  arguments?: Record<string, unknown>
  status: string
  comment?: string
  decidedAt?: string
  createdAt?: string
}

export interface AgentMessageRecord {
  messageId: number
  sessionId: number
  role: 'user' | 'assistant' | 'tool' | 'system'
  content: string
  metadata?: Record<string, unknown>
  toolCalls?: AgentToolCallRecord[]
  approvals?: AgentApprovalRecord[]
  createdAt?: string
}

export interface AgentSseEnvelope {
  event: string
  data: Record<string, unknown>
}

export interface AgentSessionQuery {
  archived?: boolean
  pinned?: boolean
  keyword?: string
  page?: number
  size?: number
}

export const createAgentSession = (title?: string) => request.post<AgentSession>('/agent/sessions', { title })
export const getAgentSessions = (params: AgentSessionQuery = {}) =>
  request.get<{ records: AgentSession[]; total: number }>('/agent/sessions', { params })
export const getAgentSessionMessages = (sessionId: number) => request.get<AgentMessageRecord[]>(`/agent/sessions/${sessionId}/messages`)
export const getAgentSessionToolCalls = (sessionId: number) => request.get<AgentToolCallRecord[]>(`/agent/sessions/${sessionId}/tool-calls`)
export const archiveAgentSession = (sessionId: number) => request.post<AgentSession>(`/agent/sessions/${sessionId}/archive`)
export const unarchiveAgentSession = (sessionId: number) => request.post<AgentSession>(`/agent/sessions/${sessionId}/unarchive`)
export const pinAgentSession = (sessionId: number) => request.post<AgentSession>(`/agent/sessions/${sessionId}/pin`)
export const unpinAgentSession = (sessionId: number) => request.post<AgentSession>(`/agent/sessions/${sessionId}/unpin`)
export const renameAgentSession = (sessionId: number, title: string) => request.put<AgentSession>(`/agent/sessions/${sessionId}`, { title })
export const deleteAgentSession = (sessionId: number) => request.delete<{ deleted: boolean }>(`/agent/sessions/${sessionId}`)
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
