import { API_BASE, getToken, http } from './client'
import type { AIHistoryMessage, EntityId } from '@/types'

function authHeaders() {
  const token = getToken() || ''
  return {
    'Access-Token': token,
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  }
}

export const aiApi = {
  async history(_sessionId: EntityId, _limit = 100): Promise<AIHistoryMessage[]> {
    return []
  },
  async chat(sessionId: EntityId, userId: EntityId, prompt: string) {
    const response = await http.post('/api/ai/chat', { sessionId, userId, prompt })
    return typeof response.data === 'string' ? response.data : String(response.data ?? '')
  },
  async stream(
    sessionId: EntityId,
    userId: EntityId,
    prompt: string,
    onDelta: (delta: string) => void,
  ) {
    const response = await fetch(`${API_BASE}/api/ai/streamChat`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ sessionId, userId, prompt }),
    })
    if (!response.ok || !response.body) throw new Error(`AI 流式请求失败 (${response.status})`)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let completed = ''
    while (true) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
      const events = buffer.split('\n\n')
      buffer = events.pop() || ''
      for (const block of events) {
        const dataLine = block.split('\n').find((line) => line.startsWith('data:'))
        const chunk = dataLine ? dataLine.slice(dataLine.startsWith('data: ') ? 6 : 5) : block
        if (!chunk) continue
        completed += chunk
        onDelta(chunk)
      }
      if (done) {
        if (buffer) {
          const leftover = buffer.startsWith('data:') ? buffer.replace(/^data:\s?/, '') : buffer
          if (leftover) {
            completed += leftover
            onDelta(leftover)
          }
        }
        break
      }
    }
    return completed
  },
  async summary(historyLog: string) {
    const response = await http.get('/api/ai/summary', { params: { historyLog } })
    const text = typeof response.data === 'string' ? response.data : String(response.data ?? '')
    return { summary: text, promptVersion: 'java' }
  },
  async ingest(title: string, content: string) {
    const response = await http.post('/api/ai/insert', {
      question: title,
      answer: content,
      sourceName: 'InfiniteChat.md',
    })
    const message = typeof response.data === 'string' ? response.data : String(response.data ?? '')
    return { id: title, inserted: message.includes('成功'), contentHash: title }
  },
}
