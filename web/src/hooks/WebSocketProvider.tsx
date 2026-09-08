import type { ReactNode } from 'react'
import { useWebSocket } from '@/hooks/useWebSocket'
import { WsContext } from '@/hooks/useWs'

export function WebSocketProvider({ children }: { children: ReactNode }) {
  const ws = useWebSocket()
  return <WsContext.Provider value={ws}>{children}</WsContext.Provider>
}
