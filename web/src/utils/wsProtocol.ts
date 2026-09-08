import type {
  ChatMessage,
  MessageDeliveryPayload,
  MessageRequest,
  WsFrame,
} from '../types/index.ts'
import { parseLosslessJson } from './losslessJson.ts'

export function makeWsFrame<T>(
  type: WsFrame<T>['type'],
  correlationId: string,
  payload?: T,
): WsFrame<T> {
  return { version: 1, type, correlationId, payload }
}

export function makeMessageCommand(payload: MessageRequest) {
  return makeWsFrame('message.command', payload.clientMessageId, {
    sessionId: payload.sessionId,
    receiverId: payload.receiverId || undefined,
    senderId: payload.senderId,
    messageType: payload.type,
    sessionType: payload.sessionType,
    body: payload.body,
    clientMessageId: payload.clientMessageId,
  })
}

export function parseWsPayload(raw: string) {
  return parseLosslessJson<WsFrame | ChatMessage>(raw)
}

export function deliveryToMessage(delivery: MessageDeliveryPayload): ChatMessage {
  return {
    messageId: delivery.messageId,
    sequence: delivery.sequence,
    sessionId: delivery.sessionId,
    senderId: delivery.senderId,
    receiverId: delivery.receiverId,
    type: delivery.messageType,
    sessionType: delivery.sessionType,
    body: delivery.body,
    clientMessageId: delivery.clientMessageId,
    createdTime: delivery.createdAt,
    nickname: delivery.nickname,
    avatar: delivery.avatar,
    role: delivery.role,
  }
}
