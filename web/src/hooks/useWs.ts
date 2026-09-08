import { createContext, useContext } from 'react'
import type { WsStatus } from '@/hooks/useWebSocket'
import type { MessageRequest } from '@/types'

export interface WsContextValue {
  send: (payload: MessageRequest) => void
  connect: () => void
  disconnect: () => void
  status: WsStatus
  getStatus: () => WsStatus
}

export const WsContext = createContext<WsContextValue | null>(null)

export function useWs() {
  const context = useContext(WsContext)
  if (!context) throw new Error('useWs must be used within WebSocketProvider')
  return context
}
