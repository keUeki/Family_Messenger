import { useEffect, useRef, useCallback, useState } from 'react'
import { useAuthStore } from '@/stores/authStore'
import { useChatStore } from '@/stores/chatStore'
import { useNotificationStore } from '@/stores/notificationStore'
import { toast } from '@/stores/toastStore'
import type { MessageErrorResponse, MessageRequest } from '@/types'
import { parseLosslessJson } from '@/api/client'
import { mapJavaMessage } from '@/api/offline'

export type WsStatus = 'idle' | 'connecting' | 'open' | 'closed' | 'missing'

/**
 * In development VITE_WS_BASE can override the address the backend hands out, so the
 * Vite proxy forwards to Netty and nothing depends on the LAN IP registered by the backend.
 */
const WS_BASE_OVERRIDE = import.meta.env.VITE_WS_BASE ?? ''

/** Normalises the various address shapes into an absolute ws:// or wss:// URL. */
function toAbsoluteWsUrl(base: string) {
  const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  if (/^wss?:\/\//i.test(base)) return base
  if (/^https?:\/\//i.test(base)) return base.replace(/^http/i, 'ws')
  if (base.startsWith('//')) return `${wsProtocol}${base}`
  if (base.startsWith('/')) return `${wsProtocol}//${window.location.host}${base}`
  // Accepts the protocol-less host:port/path form the backend used to return
  return `${wsProtocol}//${base}`
}

function buildWsUrl(base: string, token: string) {
  try {
    const url = new URL(toAbsoluteWsUrl(base))
    url.searchParams.set('accessToken', token)
    return url.toString()
  } catch {
    const join = base.includes('?') ? '&' : '?'
    return `${base}${join}accessToken=${encodeURIComponent(token)}`
  }
}

export function useWebSocket() {
  const userId = useAuthStore((state) => state.userId)
  const token = useAuthStore((state) => state.token)
  const wsServerUri = useAuthStore((state) => state.wsServerUri)
  const appendMessage = useChatStore((state) => state.appendMessage)
  const markMessageFailed = useChatStore((state) => state.markMessageFailed)
  const upsertSession = useChatStore((state) => state.upsertSession)
  const refreshCount = useNotificationStore((state) => state.refreshCount)

  const wsRef = useRef<WebSocket | null>(null)
  const heartbeatRef = useRef<number | null>(null)
  const reconnectRef = useRef<number | null>(null)
  const attemptRef = useRef(0)
  const manualCloseRef = useRef(false)
  const connectRef = useRef<() => void>(() => undefined)
  const [status, setStatus] = useState<WsStatus>('idle')

  const clearHeartbeat = useCallback(() => {
    if (heartbeatRef.current) window.clearInterval(heartbeatRef.current)
    heartbeatRef.current = null
  }, [])

  const clearReconnect = useCallback(() => {
    if (reconnectRef.current) window.clearTimeout(reconnectRef.current)
    reconnectRef.current = null
  }, [])

  const scheduleReconnect = useCallback(() => {
    if (manualCloseRef.current || !userId || !token || !(WS_BASE_OVERRIDE || wsServerUri) || reconnectRef.current) return
    const delay = Math.min(1000 * 2 ** attemptRef.current, 16000)
    attemptRef.current += 1
    reconnectRef.current = window.setTimeout(() => {
      reconnectRef.current = null
      connectRef.current()
    }, delay)
  }, [userId, token, wsServerUri])

  const disconnect = useCallback(() => {
    manualCloseRef.current = true
    clearHeartbeat()
    clearReconnect()
    if (wsRef.current) {
      wsRef.current.onclose = null
      wsRef.current.close()
      wsRef.current = null
    }
    setStatus((previous) => (previous === 'missing' ? 'missing' : 'closed'))
  }, [clearHeartbeat, clearReconnect])

  const connect = useCallback(() => {
    if (!userId || !token) return
    const wsBase = WS_BASE_OVERRIDE || wsServerUri
    if (!wsBase) {
      setStatus('missing')
      return
    }
    manualCloseRef.current = false
    clearHeartbeat()
    clearReconnect()
    if (wsRef.current) {
      wsRef.current.onclose = null
      wsRef.current.close()
    }
    setStatus('connecting')
    const socket = new WebSocket(buildWsUrl(wsBase, token))
    wsRef.current = socket

    socket.onopen = () => {
      attemptRef.current = 0
      setStatus('open')
      heartbeatRef.current = window.setInterval(() => {
        if (socket.readyState === WebSocket.OPEN) socket.send('ping')
      }, 25000)
    }

    socket.onmessage = (event) => {
      const raw = String(event.data)
      if (raw === 'pong' || raw === 'ping') return
      try {
        const value = parseLosslessJson<Record<string, unknown>>(raw)
        if (value && value.type === 'ERROR') {
          const error = value as unknown as MessageErrorResponse
          if (error.clientMessageId) markMessageFailed(error.clientMessageId)
          toast.error(error.errorMessage || 'Failed to send the message')
          return
        }
        const message = mapJavaMessage(value as Parameters<typeof mapJavaMessage>[0])
        if (typeof message.type === 'number' && message.type >= 100 && userId) {
          refreshCount(userId).catch(() => undefined)
          return
        }
        if (message.sessionId) {
          appendMessage(message)
          if (userId && message.sessionType === 0) {
            const peer = message.senderId === userId ? message.receiverId : message.senderId
            if (peer) upsertSession({ sessionId: message.sessionId, peerId: peer, sessionType: 0 })
          }
        }
      } catch {
        toast.warning('Received a realtime message that could not be parsed')
      }
    }

    socket.onclose = () => {
      setStatus('closed')
      clearHeartbeat()
      wsRef.current = null
      scheduleReconnect()
    }
    socket.onerror = () => {
      setStatus('closed')
      if (socket.readyState !== WebSocket.CLOSED) socket.close()
    }
  }, [userId, token, wsServerUri, clearHeartbeat, clearReconnect, scheduleReconnect, appendMessage, markMessageFailed, upsertSession, refreshCount])

  connectRef.current = connect

  const send = useCallback((payload: MessageRequest) => {
    const socket = wsRef.current
    if (!socket || socket.readyState !== WebSocket.OPEN) throw new Error('The realtime connection is not ready')
    socket.send(JSON.stringify(payload))
  }, [])

  useEffect(() => {
    connect()
    return () => disconnect()
  }, [connect, disconnect])

  return { send, connect, disconnect, status, getStatus: () => status }
}
