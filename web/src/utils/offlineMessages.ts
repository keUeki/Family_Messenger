import type { ChatMessage, EntityId, OfflineHistoryMessage } from '../types/index.ts'

export function mapOfflineMessages(
  messages: OfflineHistoryMessage[],
  sessionType: number,
): ChatMessage[] {
  return messages.map((message) => ({
    sessionId: message.sessionId,
    senderId: message.senderId,
    type: message.type,
    sessionType,
    body: message.body,
    createdTime: message.createdTime,
    messageId: message.messageId,
    sequence: message.sequence,
    nickname: message.name,
    avatar: message.avatar,
  }))
}

export function highestSequence(current: EntityId, messages: ChatMessage[]) {
  let maximum = /^\d+$/.test(current) ? BigInt(current) : 0n
  for (const message of messages) {
    const value = message.sequence || message.messageId
    if (/^\d+$/.test(value) && BigInt(value) > maximum) maximum = BigInt(value)
  }
  return maximum.toString()
}
