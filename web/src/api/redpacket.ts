import { http, unwrap } from './client'
import type {
  ReceiveResultVO,
  RedPacketBasicVO,
  RedPacketDetailVO,
  RedPacketSendVO,
  EntityId,
} from '@/types'

export const redPacketApi = {
  send(payload: {
    senderId: EntityId
    sessionId: EntityId
    sessionType: number
    receiverId?: EntityId | null
    clientMessageId?: string
    body: {
      redPacketType: number
      totalAmount: string
      totalCount: number
      redPacketWrapperText?: string
    }
  }) {
    return unwrap<RedPacketSendVO>(http.post('/api/chat/redPacket/send', payload))
  },
  receive(userId: EntityId, redPacketId: EntityId) {
    return unwrap<ReceiveResultVO>(
      http.post('/api/chat/redPacket/receive', { userId, redPacketId }),
    )
  },
  detail(redPacketId: EntityId, pageNum = 1, pageSize = 20) {
    return unwrap<RedPacketDetailVO>(
      http.get('/api/chat/redPacket/', { params: { redPacketId, pageNum, pageSize } }),
    )
  },
  basic(redPacketId: EntityId) {
    return unwrap<RedPacketBasicVO>(
      http.get('/api/chat/redPacket/basic', { params: { redPacketId } }),
    )
  },
}
