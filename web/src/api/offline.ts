import { http, unwrap } from './client'
import { contactApi } from './contact'
import type {
  ChatMessage,
  MessageBody,
  OfflineHistoryMessage,
  OfflineSession,
  PageResponse,
  SystemNotification,
  EntityId,
} from '@/types'

type JavaMessage = {
  sessionId: EntityId
  senderId: EntityId
  type: number
  sessionType?: number
  createdTime: string
  messageId: EntityId
  clientMessageId?: string
  nickname?: string
  avatar?: string
  role?: number | null
  body?: MessageBody
}

function asMillis(value?: string | number | null) {
  if (value == null || value === '') return Date.now()
  if (typeof value === 'number') return value
  if (/^\d+$/.test(value)) return Number(value)
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? Date.now() : parsed
}

function toIsoTime(value?: string | number | null) {
  return new Date(asMillis(value)).toISOString()
}

export function mapJavaMessage(message: JavaMessage, sessionType?: number): ChatMessage {
  return {
    sessionId: String(message.sessionId),
    senderId: String(message.senderId),
    type: message.type,
    sessionType: sessionType ?? message.sessionType ?? 0,
    body: message.body || { content: '' },
    createdTime: toIsoTime(message.createdTime),
    messageId: String(message.messageId),
    clientMessageId: message.clientMessageId,
    nickname: message.nickname,
    avatar: message.avatar,
    role: message.role,
  }
}

export const offlineApi = {
  getSessions(userId: EntityId) {
    return unwrap<OfflineSession[]>(http.get('/api/user/sessions', { params: { userId } }))
  },
  async getOfflineMessages(userId: EntityId, offlineTime: number) {
    const data = await unwrap<Record<string, JavaMessage[]>>(
      http.post('/api/message/offline', { userId, offlineTime }),
    )
    return data || {}
  },
  async getHistoricalMessages(sessionId: EntityId, beforeTime = Date.now(), limit = 20) {
    const list = await unwrap<JavaMessage[]>(
      http.post('/api/message/history', { sessionId, beforeTime, limit }),
    )
    return (list || []).map((message) => ({
      messageId: String(message.messageId),
      sequence: String(message.messageId),
      sessionId: String(message.sessionId),
      type: message.type,
      senderId: String(message.senderId),
      avatar: message.avatar || '',
      name: message.nickname || '',
      createdTime: toIsoTime(message.createdTime),
      body: message.body || { content: '' },
    })) as OfflineHistoryMessage[]
  },
  async getUnreadNotifications(userId: EntityId, pageNum = 1, pageSize = 20) {
    const page = await contactApi.getApplyList(userId, pageNum, pageSize)
    const list: SystemNotification[] = (page?.list || [])
      .filter((item) => item.isReceiver === 1 && (item.status === 0 || item.status === 3))
      .map((item) => ({
        id: String(item.userId),
        messageId: String(item.userId),
        receiverId: String(userId),
        type: 101,
        content: JSON.stringify({
          applyUserName: item.nickname,
          message: item.msg,
        }),
        isRead: item.status === 0 ? 0 : 1,
        createdTime: typeof item.time === 'string' ? item.time : String(item.time || ''),
      }))
    return {
      list,
      total: list.length,
      pageSize,
      pageNum,
      pages: 1,
      hasNext: false,
      hasPrevious: false,
    } as PageResponse<SystemNotification>
  },
  async getUnreadCount(userId: EntityId) {
    return contactApi.getUnreadApplyCount(userId)
  },
  async markAsRead(userId: EntityId, notificationId: EntityId) {
    await contactApi.modifyApplicationStatus(userId, '3', [String(notificationId)])
    return true
  },
  async markAsReadBatch(userId: EntityId, notificationIds: EntityId[]) {
    await contactApi.modifyApplicationStatus(userId, '3', notificationIds.map(String))
    return notificationIds.length
  },
  async markAllAsRead(userId: EntityId) {
    const page = await contactApi.getApplyList(userId, 1, 100)
    const ids = (page?.list || [])
      .filter((item) => item.isReceiver === 1 && item.status === 0)
      .map((item) => String(item.userId))
    if (ids.length) await contactApi.modifyApplicationStatus(userId, '3', ids)
    return ids.length
  },
}
