import { create } from 'zustand'
import type { ChatMessage, EntityId, OfflineSession } from '@/types'
import { mapJavaMessage, offlineApi } from '@/api/offline'
import { AI_USER_ID, MessageType, SessionType } from '@/types'
import { highestSequence, mapOfflineMessages } from '@/utils/offlineMessages'
import { useAuthStore } from '@/stores/authStore'

interface ChatState {
  sessions: OfflineSession[]
  activeSessionId: EntityId | null
  messagesBySession: Record<EntityId, ChatMessage[]>
  loadingSessions: boolean
  loadingMessages: boolean
  lastSequence: EntityId
  setActiveSession: (sessionId: EntityId | null) => void
  loadSessions: (userId: EntityId) => Promise<void>
  loadMessages: (sessionId: EntityId, unreadCount?: number, currentUserId?: EntityId) => Promise<void>
  loadMoreHistory: (sessionId: EntityId) => Promise<number>
  upsertSession: (partial: Partial<OfflineSession> & { sessionId: EntityId }) => void
  appendMessage: (msg: ChatMessage) => void
  markMessageAck: (clientMessageId: string, msg: ChatMessage) => void
  markMessageAccepted: (clientMessageId: string, messageId: EntityId, acceptedAt: string) => void
  markMessageFailed: (clientMessageId: string) => void
  clear: () => void
}

function previewOf(msg: ChatMessage) {
  if (msg.type === MessageType.Image) return '[Image]'
  if (msg.type === MessageType.Emoji) return msg.body.content || '[Sticker]'
  return msg.body.content || ''
}

function normalizeSession(session: OfflineSession): OfflineSession {
  if (session.sessionType === SessionType.Robot || session.peerId === AI_USER_ID || session.senderId === AI_USER_ID) {
    return { ...session, sessionType: SessionType.Robot, name: session.name || 'Infinite AI', peerId: AI_USER_ID }
  }
  return session
}

export const useChatStore = create<ChatState>((set, get) => ({
  sessions: [],
  activeSessionId: null,
  messagesBySession: {},
  loadingSessions: false,
  loadingMessages: false,
  lastSequence: '0',
  setActiveSession: (sessionId) => set({ activeSessionId: sessionId }),
  loadSessions: async (userId) => {
    const cached = get().sessions.length > 0
    if (!cached) set({ loadingSessions: true })
    try {
      const sessions = (await offlineApi.getSessions(userId)) || []
      const offlineTime = useAuthStore.getState().offlineTime
      if (offlineTime) {
        const offline = await offlineApi.getOfflineMessages(userId, offlineTime)
        for (const [sessionId, messages] of Object.entries(offline || {})) {
          const last = (messages || []).at(-1)
          if (!last) continue
          const mapped = mapJavaMessage(last)
          const idx = sessions.findIndex((item) => String(item.sessionId) === String(sessionId))
          if (idx >= 0) {
            sessions[idx] = {
              ...sessions[idx],
              lastMsgContent: previewOf(mapped),
              lastMsgTime: mapped.createdTime,
              count: messages.length,
            }
          }
        }
      }
      set({ sessions: sessions.map(normalizeSession) })
    } finally {
      set({ loadingSessions: false })
    }
  },
  loadMessages: async (sessionId, unreadCount = 0, currentUserId) => {
    const cached = get().messagesBySession[sessionId]
    if (cached?.length && !unreadCount) return
    set({ loadingMessages: true })
    try {
      const history = await offlineApi.getHistoricalMessages(sessionId, Date.now(), 50)
      const list = mapOfflineMessages(
        (history || []).reverse(),
        get().sessions.find((s) => s.sessionId === sessionId)?.sessionType ?? 0,
      )
      const peerFromHistory =
        currentUserId != null
          ? list.find((m) => m.senderId !== currentUserId)?.senderId
          : undefined
      set((s) => ({
        messagesBySession: { ...s.messagesBySession, [sessionId]: list },
        lastSequence: highestSequence(s.lastSequence, list),
        sessions: s.sessions.map((sess) =>
          sess.sessionId === sessionId
            ? {
                ...sess,
                count: 0,
                peerId: sess.peerId || peerFromHistory,
              }
            : sess,
        ),
      }))
    } finally {
      set({ loadingMessages: false })
    }
  },
  loadMoreHistory: async (sessionId) => {
    const current = get().messagesBySession[sessionId] || []
    const oldest = current[0]?.createdTime
    if (!oldest) return 0
    const beforeTime = Date.parse(oldest) || Date.now()
    const history = await offlineApi.getHistoricalMessages(sessionId, beforeTime, 20)
    const mapped = mapOfflineMessages(
      (history || []).reverse(),
      get().sessions.find((s) => s.sessionId === sessionId)?.sessionType ?? 0,
    )
    if (!mapped.length) return 0
    set((s) => ({
      messagesBySession: {
        ...s.messagesBySession,
        [sessionId]: [...mapped, ...(s.messagesBySession[sessionId] || [])],
      },
    }))
    return mapped.length
  },
  upsertSession: (partial) => {
    set((s) => {
      const idx = s.sessions.findIndex((x) => x.sessionId === partial.sessionId)
      if (idx >= 0) {
        const next = [...s.sessions]
        next[idx] = normalizeSession({ ...next[idx], ...partial })
        next.sort((a, b) => (a.lastMsgTime < b.lastMsgTime ? 1 : -1))
        return { sessions: next }
      }
      return {
        sessions: [
          normalizeSession({
            type: 0,
            sessionType: 0,
            senderId: '0',
            avatar: '',
            name: 'New chat',
            lastMsgContent: '',
            lastMsgTime: new Date().toISOString(),
            count: 0,
            ...partial,
          }),
          ...s.sessions,
        ],
      }
    })
  },
  appendMessage: (msg) => {
    set((s) => {
      const list = s.messagesBySession[msg.sessionId] || []
      const exists = list.some(
        (m) =>
          (msg.clientMessageId && m.clientMessageId === msg.clientMessageId) ||
          (msg.messageId && m.messageId === msg.messageId),
      )
      const nextList = exists
        ? list.map((m) =>
            (msg.clientMessageId && m.clientMessageId === msg.clientMessageId) ||
            (msg.messageId && m.messageId === msg.messageId)
              ? { ...m, ...msg, pending: msg.pending ?? false, failed: msg.failed ?? false }
              : m,
          )
        : [...list, msg]
      const isActive = s.activeSessionId === msg.sessionId
      const sessions = [...s.sessions]
      const idx = sessions.findIndex((x) => x.sessionId === msg.sessionId)
      if (idx >= 0) {
        sessions[idx] = {
          ...sessions[idx],
          lastMsgContent: previewOf(msg),
          lastMsgTime: msg.createdTime,
          count: isActive ? 0 : (sessions[idx].count || 0) + (msg.pending ? 0 : 1),
        }
        sessions.sort((a, b) => (a.lastMsgTime < b.lastMsgTime ? 1 : -1))
      }
      return {
        messagesBySession: { ...s.messagesBySession, [msg.sessionId]: nextList },
        sessions,
        lastSequence: highestSequence(s.lastSequence, [msg]),
      }
    })
  },
  markMessageAck: (clientMessageId, msg) => {
    set((s) => {
      const list = s.messagesBySession[msg.sessionId] || []
      return {
        messagesBySession: {
          ...s.messagesBySession,
          [msg.sessionId]: list.map((m) =>
            m.clientMessageId === clientMessageId
              ? { ...m, ...msg, pending: false, failed: false }
              : m,
          ),
        },
      }
    })
  },
  markMessageAccepted: (clientMessageId, messageId, acceptedAt) => {
    set((s) => {
      const next: Record<EntityId, ChatMessage[]> = {}
      for (const [sessionId, list] of Object.entries(s.messagesBySession)) {
        next[sessionId] = list.map((message) =>
          message.clientMessageId === clientMessageId
            ? { ...message, messageId, createdTime: acceptedAt, pending: false, failed: false }
            : message,
        )
      }
      return { messagesBySession: next }
    })
  },
  markMessageFailed: (clientMessageId) => {
    set((s) => {
      const next: Record<EntityId, ChatMessage[]> = {}
      for (const [k, list] of Object.entries(s.messagesBySession)) {
        next[k] = list.map((m) =>
          m.clientMessageId === clientMessageId ? { ...m, pending: false, failed: true } : m,
        )
      }
      return { messagesBySession: next }
    })
  },
  clear: () =>
    set({
      sessions: [],
      activeSessionId: null,
      messagesBySession: {},
      lastSequence: '0',
    }),
}))
