export interface BaseResponse<T> {
  code: number
  data: T
  message: string
}

/** Snowflake IDs exceed JavaScript's safe integer range and must stay as strings. */
export type EntityId = string

export interface PageResponse<T> {
  list: T[]
  total: number
  pageSize: number
  pageNum: number
  pages: number
  hasNext: boolean
  hasPrevious: boolean
}

export interface LoginResponse {
  userId: EntityId
  account: string
  nickname: string
  avatar: string
  gender: number
  description: string
  token: string
  refreshToken: string
  wsServerUri: string
  offlineTime?: number | null
}

export interface UserInfo {
  userId: EntityId
  nickname: string
  avatar: string
  description: string
  account: string
  gender: number
}

export interface FriendDetail {
  userId: EntityId
  nickname: string
  avatar: string
  email?: string
  phone?: string
  signature: string
  gender: number
  sessionId: EntityId
  status: number
}

export interface FriendDTO {
  userId: EntityId
  nickname: string
  avatar: string
  signature: string
  status: number
  sessionId: EntityId
}

export interface ApplyFriendDTO {
  userId: EntityId
  nickname: string
  avatar: string
  msg: string
  status: number
  time: string
  isReceiver: number
}

export interface OfflineSession {
  type: number
  sessionType: number
  sessionId: EntityId
  senderId: EntityId
  /** 单聊对方用户 ID（前端补充） */
  peerId?: EntityId
  avatar: string
  name: string
  lastMsgContent: string
  lastMsgTime: string
  count: number
}

export interface MessageBody {
  content?: string
  replyId?: EntityId | null
  redPacketId?: string
  redPacketWrapperText?: string
}

export interface ChatMessage {
  sessionId: EntityId
  senderId: EntityId
  receiverId?: EntityId | null
  type: number
  sessionType: number
  body: MessageBody
  createdTime: string
  messageId: EntityId
  sequence?: EntityId
  clientMessageId?: string
  nickname?: string
  avatar?: string
  role?: number | null
  pending?: boolean
  failed?: boolean
}

export interface MessageRequest {
  sessionId: EntityId
  receiverId?: EntityId | null
  senderId: EntityId
  type: number
  sessionType: number
  body: MessageBody
  clientMessageId: string
}

export interface MessageErrorResponse {
  type: 'ERROR'
  errorCode: number
  errorMessage: string
  clientMessageId?: string
  timestamp: number
}

export interface OfflineHistoryMessage {
  messageId: EntityId
  sequence: EntityId
  sessionId: EntityId
  type: number
  senderId: EntityId
  avatar: string
  name: string
  createdTime: string
  body: MessageBody
}

export interface WsFrame<T = unknown> {
  version: 1
  type: 'ping' | 'pong' | 'message.command' | 'message.ack' | 'message.delivery' | 'message.read' | 'resume' | 'error'
  correlationId: string
  payload?: T
}

export interface MessageAckPayload {
  clientMessageId: string
  messageId: EntityId
  status: 'accepted'
  duplicate: boolean
  acceptedAt: string
}

export interface MessageDeliveryPayload {
  messageId: EntityId
  sequence: EntityId
  sessionId: EntityId
  senderId: EntityId
  receiverId?: EntityId
  messageType: number
  sessionType: number
  body: MessageBody
  clientMessageId?: string
  createdAt: string
  nickname?: string
  avatar?: string
  role?: number | null
}

export interface WsErrorPayload {
  code: string
  message: string
  correlationId: string
  retryable: boolean
}

export interface AIChatResponse {
  requestId: string
  text: string
  provider: string
  promptVersion: string
  citations: EntityId[]
  usage: { inputTokens: number; outputTokens: number }
  duplicate: boolean
}

export interface AIKnowledge {
  id: EntityId
  title: string
  content: string
  score: number
}

export interface AIHistoryMessage {
  id: EntityId
  role: 'user' | 'assistant' | 'tool'
  content: string
  createdAt: string
}

export interface UserGroup {
  sessionId: EntityId
  sessionName: string
  avatar: string
  creatorId: EntityId
  role: number
  memberCount: number
  createdTime: string
}

export interface GroupMember {
  userId: EntityId
  nickname: string
  avatar: string
  role: number
}

export interface CreateGroupResponse {
  sessionId: EntityId
  sessionName: string
  sessionType: number
  avatar: string
  creatorId: EntityId
  membersCount: number
  failedMemberIds: EntityId[]
}

export interface SystemNotification {
  id: EntityId
  messageId: EntityId
  receiverId: EntityId
  type: number
  content: string
  isRead: number
  createdTime: string
  updatedTime?: string
}

export interface BalanceLog {
  userName: string
  type: number
  amount: string | number
  time: string
}

export interface RedPacketSendVO {
  redPacketId: EntityId
  messageId: EntityId
}

export interface ReceiveResultVO {
  status: number
  message: string
  amount?: string
}

export interface RedPacketDetailVO {
  redPacketId: EntityId
  senderId: EntityId
  senderNickname: string
  senderAvatar: string
  sessionId: EntityId
  sessionType: number
  redPacketWrapperText: string
  redPacketType: number
  totalAmount: string
  totalCount: number
  receivedCount: number
  receivedAmount: string
  status: number
  createdTime: string
  receiveRecords: Array<{
    receiverId: EntityId
    receiverNickname: string
    receiverAvatar: string
    amount: string
    receivedAt: string
  }>
}

export interface RedPacketBasicVO {
  redPacketId: EntityId
  redPacketType: number
  totalAmount: string
  totalCount: number
  receivedCount: number
  receivedAmount: string
  status: number
  createdTime: string
}

export const MessageType = {
  Text: 0,
  Image: 1,
  Emoji: 2,
  RedPacket: 3,
} as const

export const SessionType = {
  Single: 0,
  Group: 1,
  Robot: 2,
} as const

export const AI_USER_ID = '111111111'
