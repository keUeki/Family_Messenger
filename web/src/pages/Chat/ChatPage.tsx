import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Button } from '@/components/Button'
import { Modal } from '@/components/Modal'
import { Input } from '@/components/Input'
import { EmptyState } from '@/components/EmptyState'
import { SkeletonList } from '@/components/Skeleton'
import { IconButton } from '@/components/IconButton'
import {
  IconImage,
  IconPacket,
  IconRefresh,
  IconSearch,
  IconSend,
  IconSmile,
} from '@/components/Icons'
import { useAuthStore } from '@/stores/authStore'
import { useChatStore } from '@/stores/chatStore'
import { useWs } from '@/hooks/useWs'
import { toast } from '@/stores/toastStore'
import { formatDayLabel, formatTime, sameDay, uid, uploadFile } from '@/utils'
import { userApi } from '@/api/user'
import { redPacketApi } from '@/api/redpacket'
import { aiApi } from '@/api/ai'
import { MessageType, SessionType, AI_USER_ID, type ChatMessage, type EntityId, type RedPacketDetailVO } from '@/types'
import { ApiError } from '@/api/client'
import styles from './ChatPage.module.css'

const EMOJIS = ['😀', '😁', '😂', '🥰', '😍', '🤔', '👍', '🎉', '🔥', '❤️', '🙏', '😎']
const MOBILE_MQ = '(max-width: 860px)'

export function ChatPage() {
  const navigate = useNavigate()
  const { sessionId: sessionIdParam } = useParams()
  const userId = useAuthStore((s) => s.userId)!
  const profile = useAuthStore((s) => s.profile)
  const {
    sessions,
    activeSessionId,
    messagesBySession,
    loadingSessions,
    loadingMessages,
    setActiveSession,
    loadSessions,
    loadMessages,
    loadMoreHistory,
    appendMessage,
    markMessageFailed,
    upsertSession,
  } = useChatStore()
  const { send, status, connect } = useWs()

  const [text, setText] = useState('')
  const [query, setQuery] = useState('')
  const [showEmoji, setShowEmoji] = useState(false)
  const [rpOpen, setRpOpen] = useState(false)
  const [rpAmount, setRpAmount] = useState('1.00')
  const [rpCount, setRpCount] = useState('1')
  const [rpText, setRpText] = useState('恭喜发财')
  const [rpType, setRpType] = useState(0)
  const [detail, setDetail] = useState<RedPacketDetailVO | null>(null)
  const [lightbox, setLightbox] = useState<string | null>(null)
  const [knowledgeOpen, setKnowledgeOpen] = useState(false)
  const [knowledgeTitle, setKnowledgeTitle] = useState('')
  const [knowledgeContent, setKnowledgeContent] = useState('')
  const [aiBusy, setAiBusy] = useState(false)
  const [isMobile, setIsMobile] = useState(
    () => typeof window !== 'undefined' && window.matchMedia(MOBILE_MQ).matches,
  )
  const listRef = useRef<HTMLDivElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const stickBottomRef = useRef(true)
  const skipScrollRef = useRef(false)

  const routeSessionId = sessionIdParam && /^\d+$/.test(sessionIdParam) ? sessionIdParam : null
  const active = useMemo(
    () => sessions.find((s) => s.sessionId === activeSessionId) || null,
    [sessions, activeSessionId],
  )
  const isAI = Boolean(
    active &&
      (active.sessionType === SessionType.Robot ||
        active.peerId === AI_USER_ID ||
        active.senderId === AI_USER_ID),
  )
  const messages = activeSessionId ? messagesBySession[activeSessionId] || [] : []
  const filteredSessions = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return sessions
    return sessions.filter(
      (s) =>
        (s.name || '').toLowerCase().includes(q) ||
        String(s.sessionId).includes(q) ||
        (s.lastMsgContent || '').toLowerCase().includes(q),
    )
  }, [sessions, query])

  const showList = !isMobile || !activeSessionId
  const showThread = !isMobile || Boolean(activeSessionId)

  useEffect(() => {
    const mq = window.matchMedia(MOBILE_MQ)
    const onChange = () => setIsMobile(mq.matches)
    onChange()
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])

  useEffect(() => {
    loadSessions(userId).catch((e) =>
      toast.error(e instanceof ApiError ? e.message : '加载会话失败'),
    )
  }, [userId, loadSessions])

  // URL -> store
  useEffect(() => {
    if (routeSessionId) {
      if (activeSessionId !== routeSessionId) setActiveSession(routeSessionId)
    } else if (activeSessionId != null && !sessionIdParam) {
      setActiveSession(null)
    }
  }, [routeSessionId, sessionIdParam]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!activeSessionId || !active?.sessionId) return
    stickBottomRef.current = true
    const load = async () => {
      await loadMessages(activeSessionId, active.count, userId)
      if (!isAI) return
      const history = await aiApi.history(activeSessionId)
      for (const message of history) {
        appendMessage({
          sessionId: activeSessionId,
          senderId: message.role === 'user' ? userId : AI_USER_ID,
          type: MessageType.Text,
          sessionType: SessionType.Robot,
          body: { content: message.content },
          createdTime: message.createdAt,
          messageId: message.id,
          nickname: message.role === 'user' ? profile.nickname : 'Infinite AI',
          avatar: message.role === 'user' ? profile.avatar : active.avatar,
        })
      }
    }
    load().catch((e) => toast.error(e instanceof ApiError ? e.message : '加载消息失败'))
  }, [activeSessionId, active?.sessionId, isAI, userId, loadMessages, appendMessage, profile.nickname, profile.avatar, active?.avatar, active?.count])

  useEffect(() => {
    const el = listRef.current
    if (!el || skipScrollRef.current) {
      skipScrollRef.current = false
      return
    }
    if (stickBottomRef.current) {
      el.scrollTop = el.scrollHeight
    }
  }, [messages.length, activeSessionId])

  const openSession = (id: EntityId) => {
    setActiveSession(id)
    navigate(`/app/chat/${id}`)
  }

  const backToList = () => {
    setActiveSession(null)
    navigate('/app/chat')
  }

  const onMessagesScroll = () => {
    const el = listRef.current
    if (!el) return
    stickBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
  }

  const onLoadMore = async () => {
    if (!activeSessionId || !listRef.current) return
    const el = listRef.current
    const prevHeight = el.scrollHeight
    const prevTop = el.scrollTop
    skipScrollRef.current = true
    stickBottomRef.current = false
    try {
      const added = await loadMoreHistory(activeSessionId)
      if (added > 0) {
        requestAnimationFrame(() => {
          if (!listRef.current) return
          listRef.current.scrollTop = listRef.current.scrollHeight - prevHeight + prevTop
        })
      } else {
        toast.info('已经到达对话起点')
      }
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '历史消息加载失败')
    }
  }

  const resolvePeer = () => {
    if (!active || active.sessionType !== SessionType.Single) return null
    if (active.peerId) return active.peerId
    if (active.senderId && active.senderId !== userId) return active.senderId
    const other = messages.find((m) => m.senderId !== userId)
    return other?.senderId ?? null
  }

  const sendPayload = (type: number, body: ChatMessage['body'], clientMessageId = uid('c')) => {
    if (!active || !activeSessionId) return false
    if (status !== 'open') {
      toast.warning('实时连接未就绪，请先重连')
      return false
    }
    let peer: EntityId | null = null
    if (active.sessionType === SessionType.Single) {
      peer = resolvePeer()
      if (!peer) {
        toast.error('无法确定聊天对象，请从通讯录重新进入会话')
        return false
      }
    }

    const req = {
      sessionId: activeSessionId,
      receiverId: active.sessionType === SessionType.Single ? peer : null,
      senderId: userId,
      type,
      sessionType: active.sessionType,
      body,
      clientMessageId,
    }

    const optimistic: ChatMessage = {
      ...req,
      createdTime: new Date().toISOString().replace('T', ' ').slice(0, 19),
      messageId: uid('optimistic'),
      nickname: profile.nickname,
      avatar: profile.avatar,
      pending: true,
      failed: false,
      body,
    }
    stickBottomRef.current = true
    appendMessage(optimistic)
    if (peer) upsertSession({ sessionId: activeSessionId, peerId: peer })
    try {
      send(req)
    } catch (e) {
      markMessageFailed(clientMessageId)
      toast.error(e instanceof Error ? e.message : '发送失败')
    }
    return true
  }

  const retryMessage = (m: ChatMessage) => {
    if (!m.clientMessageId || !m.body) return
    sendPayload(m.type, m.body, m.clientMessageId)
  }

  const sendAIMessage = async (content: string) => {
    if (!activeSessionId || aiBusy) return
    const requestId = uid('ai')
    const assistantId = `assistant-${requestId}`
    appendMessage({ sessionId: activeSessionId, senderId: userId, type: MessageType.Text, sessionType: SessionType.Robot, body: { content }, createdTime: new Date().toISOString(), messageId: `user-${requestId}`, clientMessageId: requestId, nickname: profile.nickname, avatar: profile.avatar })
    let answer = ''
    setAiBusy(true)
    try {
      const completed = await aiApi.stream(activeSessionId, userId, content, (delta) => {
        answer += delta
        appendMessage({ sessionId: activeSessionId, senderId: AI_USER_ID, type: MessageType.Text, sessionType: SessionType.Robot, body: { content: answer }, createdTime: new Date().toISOString(), messageId: assistantId, pending: true, nickname: 'Infinite AI' })
      })
      answer = completed || answer
      appendMessage({ sessionId: activeSessionId, senderId: AI_USER_ID, type: MessageType.Text, sessionType: SessionType.Robot, body: { content: answer }, createdTime: new Date().toISOString(), messageId: assistantId, pending: false, nickname: 'Infinite AI' })
    } catch (error) {
      appendMessage({ sessionId: activeSessionId, senderId: AI_USER_ID, type: MessageType.Text, sessionType: SessionType.Robot, body: { content: answer || 'AI 回复失败，请重试。' }, createdTime: new Date().toISOString(), messageId: assistantId, failed: true, nickname: 'Infinite AI' })
      toast.error(error instanceof Error ? error.message : 'AI 回复失败')
    } finally {
      setAiBusy(false)
    }
  }

  const onSendText = async () => {
    const content = text.trim()
    if (!content) return
    if (isAI) {
      setText('')
      await sendAIMessage(content)
      return
    }
    if (sendPayload(MessageType.Text, { content })) {
      setText('')
      setShowEmoji(false)
    }
  }

  const summarizeAI = async () => {
    if (!activeSessionId) return
    try {
      setAiBusy(true)
      const historyLog = (useChatStore.getState().messagesBySession[activeSessionId] || [])
        .map((item) => `${item.senderId === userId ? '用户' : 'AI'}: ${item.body?.content || ''}`)
        .join('\n')
      const result = await aiApi.summary(historyLog || '无历史消息')
      appendMessage({ sessionId: activeSessionId, senderId: AI_USER_ID, type: MessageType.Text, sessionType: SessionType.Robot, body: { content: result.summary }, createdTime: new Date().toISOString(), messageId: uid('summary'), nickname: 'Infinite AI' })
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '总结失败')
    } finally {
      setAiBusy(false)
    }
  }

  const ingestKnowledge = async () => {
    if (!knowledgeTitle.trim() || !knowledgeContent.trim()) return
    try {
      const result = await aiApi.ingest(knowledgeTitle.trim(), knowledgeContent.trim())
      toast.success(result.inserted ? '知识已导入' : '相同知识已存在')
      setKnowledgeOpen(false)
      setKnowledgeTitle('')
      setKnowledgeContent('')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '知识导入失败')
    }
  }

  const insertEmoji = (emoji: string) => {
    setText((prev) => prev + emoji)
  }

  const onPickImage = async (file: File) => {
    try {
      toast.info('正在上传图片…')
      const url = await uploadFile(file, (name) => userApi.getUploadUrl(name))
      sendPayload(MessageType.Image, { content: url })
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '上传失败，请检查 MinIO CORS')
    }
  }

  const onSendRedPacket = async () => {
    if (!active || !activeSessionId) return
    const amount = Number(rpAmount)
    const count = Number(rpCount)
    if (!Number.isFinite(amount) || amount <= 0) {
      toast.warning('请输入有效的红包金额')
      return
    }
    if (!Number.isInteger(count) || count < 1 || count > 100) {
      toast.warning('红包个数需为 1–100 的整数')
      return
    }
    try {
      const clientMessageId = uid('rp')
      const peer = active.sessionType === SessionType.Single ? resolvePeer() || undefined : undefined
      await redPacketApi.send({
        senderId: userId,
        sessionId: activeSessionId,
        sessionType: active.sessionType,
        receiverId: peer,
        clientMessageId,
        body: {
          redPacketType: rpType,
          totalAmount: rpAmount,
          totalCount: Number(rpCount),
          redPacketWrapperText: rpText,
        },
      })
      setRpOpen(false)
      toast.success('红包已发送')
      await loadSessions(userId)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '发红包失败')
    }
  }

  const openRedPacket = async (redPacketId: string) => {
    try {
      const id = redPacketId.trim()
      if (!/^\d+$/.test(id) || id === '0') throw new Error('红包信息无效')
      try {
        const result = await redPacketApi.receive(userId, id)
        if (result.amount) toast.success(`领取 ${result.amount} 元`)
        else if (result.message) toast.info(result.message)
      } catch {
        // already received or unavailable — still show detail
      }
      setDetail(await redPacketApi.detail(id))
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '红包详情加载失败')
    }
  }

  return (
    <div
      className={[
        styles.layout,
        showList && !showThread ? styles.listOnly : '',
        !showList && showThread ? styles.threadOnly : '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <aside className={styles.sessions} hidden={!showList}>
        <div className={styles.sideHeader}>
          <div>
            <span className={styles.sideEyebrow}>RECENT CHATS</span>
            <h2>最近对话</h2>
            <p>{sessions.length} 个会话 · {sessions.reduce((sum, item) => sum + (item.count || 0), 0)} 条未读</p>
          </div>
          <IconButton onClick={() => loadSessions(userId)} title="刷新会话" aria-label="刷新会话">
            <IconRefresh size={18} />
          </IconButton>
        </div>
        <div className={styles.searchWrap}>
          <span className={styles.searchIcon}>
            <IconSearch size={16} />
          </span>
          <input
            className={styles.search}
            placeholder="搜索会话"
            aria-label="搜索会话"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        {loadingSessions ? <SkeletonList rows={5} /> : null}
        <div className={styles.sessionList}>
          {filteredSessions.map((s) => (
            <button
              key={s.sessionId}
              type="button"
              className={[
                styles.sessionItem,
                activeSessionId === s.sessionId ? styles.sessionActive : '',
              ].join(' ')}
              onClick={() => openSession(s.sessionId)}
              aria-current={activeSessionId === s.sessionId ? 'true' : undefined}
            >
              <Avatar src={s.avatar} name={s.name} size={44} />
              <div className={styles.sessionMeta}>
                <div className={styles.sessionTop}>
                  <strong>{s.name || `会话 ${s.sessionId}`}</strong>
                  <span>{formatTime(s.lastMsgTime)}</span>
                </div>
                <div className={styles.sessionBottom}>
                  <p>{s.lastMsgContent || '暂无消息'}</p>
                  {s.count > 0 ? (
                    <i className={s.count > 0 ? styles.badgePulse : ''}>
                      {s.count > 99 ? '99+' : s.count}
                    </i>
                  ) : null}
                </div>
              </div>
            </button>
          ))}
          {!filteredSessions.length && !loadingSessions ? (
            <EmptyState
              title="还没有会话"
              description="去通讯录找好友，或创建一个群聊开始对话。"
              actionLabel="去通讯录"
              onAction={() => navigate('/app/contacts')}
            />
          ) : null}
        </div>
      </aside>

      <section className={styles.chat} hidden={!showThread}>
        {active ? (
          <>
            <div className={styles.chatHeader}>
              {isMobile ? (
                <button type="button" className={styles.backBtn} onClick={backToList}>
                  返回
                </button>
              ) : null}
              <Avatar src={active.avatar} name={isAI ? 'Infinite AI' : active.name} size={40} />
              <div className={styles.chatIdentity}>
                <h3>{isAI ? 'Infinite AI' : active.name}</h3>
                <span>
                  <i className={isAI || status === 'open' ? styles.online : ''} />
                  {isAI
                    ? '知识增强助手'
                    : active.sessionType === SessionType.Group
                      ? '群组对话'
                      : status === 'open'
                        ? '实时在线'
                        : '等待连接'}
                </span>
              </div>
              {isAI ? (
                <div className={styles.aiActions}>
                  <Button variant="ghost" onClick={() => void summarizeAI()} disabled={aiBusy}>
                    总结对话
                  </Button>
                  <Button variant="secondary" onClick={() => setKnowledgeOpen(true)}>
                    导入知识
                  </Button>
                </div>
              ) : (
                <div className={styles.chatMeta}>会话 #{active.sessionId}</div>
              )}
            </div>

            <div className={styles.messages} ref={listRef} onScroll={onMessagesScroll}>
              {messages.length ? (
                <button type="button" className={styles.loadMore} onClick={() => void onLoadMore()}>
                  <span>↑</span> 查看更早的消息
                </button>
              ) : null}
              {loadingMessages ? <p className={styles.muted}>加载消息…</p> : null}
              {!loadingMessages && !messages.length ? (
                <div className={styles.threadEmpty}>
                  <span>∞</span>
                  <strong>这是对话的开始</strong>
                  <p>发一条消息，开启你们的 InfiniteChat。</p>
                </div>
              ) : null}
              {messages.map((m, idx) => {
                const mine = m.senderId === userId
                const prev = messages[idx - 1]
                const next = messages[idx + 1]
                const showDay = !prev || !sameDay(prev.createdTime, m.createdTime)
                const clustered =
                  Boolean(prev) &&
                  prev.senderId === m.senderId &&
                  sameDay(prev.createdTime, m.createdTime)
                const showTime =
                  !next ||
                  next.senderId !== m.senderId ||
                  !sameDay(next.createdTime, m.createdTime)
                return (
                  <div key={`${m.messageId}-${m.clientMessageId || idx}`}>
                    {showDay ? (
                      <div className={styles.daySep}>
                        <span>{formatDayLabel(m.createdTime)}</span>
                      </div>
                    ) : null}
                    <div
                      className={[
                        styles.row,
                        mine ? styles.mine : styles.other,
                        clustered ? styles.clustered : '',
                      ].join(' ')}
                    >
                      {!mine ? (
                        <Avatar src={m.avatar} name={m.nickname} size={32} />
                      ) : (
                        <span className={styles.avatarSpacer} />
                      )}
                      <div className={styles.bubbleWrap}>
                        {!mine && active.sessionType === SessionType.Group && !clustered ? (
                          <span className={styles.nick}>{m.nickname}</span>
                        ) : null}
                        <MessageBubble
                          message={m}
                          onOpenRedPacket={openRedPacket}
                          onOpenImage={setLightbox}
                          onRetry={retryMessage}
                        />
                        {showTime || m.pending || m.failed ? (
                          <span
                            className={[
                              styles.time,
                              m.failed ? styles.failed : '',
                              m.pending ? styles.pending : '',
                            ].join(' ')}
                          >
                            {formatTime(m.createdTime)}
                            {m.pending ? ' · 发送中' : ''}
                            {m.failed ? ' · 失败，点击气泡重试' : ''}
                          </span>
                        ) : null}
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>

            <div className={styles.composer}>
              {!isAI && status !== 'open' ? (
                <div className={styles.connBar}>
                  <span><i />
                    {status === 'connecting'
                      ? '正在连接实时通道…'
                      : status === 'missing'
                        ? '未分配实时节点'
                        : '实时连接已断开'}
                  </span>
                  <Button type="button" variant="secondary" onClick={() => connect()}>
                    重连
                  </Button>
                </div>
              ) : null}
              {showEmoji ? (
                <div className={styles.emojiPanel}>
                  {EMOJIS.map((e) => (
                    <button key={e} type="button" onClick={() => insertEmoji(e)}>
                      {e}
                    </button>
                  ))}
                </div>
              ) : null}
              <div className={styles.tools}>
                <IconButton
                  active={showEmoji}
                  onClick={() => setShowEmoji((v) => !v)}
                  title="表情"
                  aria-label="表情"
                >
                  <IconSmile size={18} />
                </IconButton>
                {!isAI ? (
                  <>
                    <IconButton
                      onClick={() => fileRef.current?.click()}
                      title="图片"
                      aria-label="图片"
                    >
                      <IconImage size={18} />
                    </IconButton>
                    <IconButton onClick={() => setRpOpen(true)} title="红包" aria-label="红包">
                      <IconPacket size={18} />
                    </IconButton>
                  </>
                ) : null}
                <span className={styles.hint}>Enter 发送 · Shift+Enter 换行</span>
                <input
                  ref={fileRef}
                  type="file"
                  accept="image/*"
                  hidden
                  onChange={(e) => {
                    const f = e.target.files?.[0]
                    if (f) onPickImage(f)
                    e.target.value = ''
                  }}
                />
              </div>
              <div className={styles.inputRow}>
                <textarea
                  value={text}
                  onChange={(e) => setText(e.target.value)}
                  placeholder={isAI ? '向 Infinite AI 提问…' : status === 'open' ? '输入消息…' : '请先重连实时通道…'}
                  rows={2}
                  maxLength={4000}
                  aria-label="输入消息"
                  onPaste={(e) => {
                    const item = Array.from(e.clipboardData.items).find((i) =>
                      i.type.startsWith('image/'),
                    )
                    if (item) {
                      const file = item.getAsFile()
                      if (file) {
                        e.preventDefault()
                        onPickImage(file)
                      }
                    }
                  }}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault()
                      void onSendText()
                    }
                    if (e.key === 'Escape') setShowEmoji(false)
                  }}
                />
                <Button
                  onClick={() => void onSendText()}
                  disabled={!text.trim() || aiBusy || (!isAI && status !== 'open')}
                  loading={isAI && aiBusy}
                >
                  <IconSend size={16} />
                  发送
                </Button>
              </div>
            </div>
          </>
        ) : (
          <div className={styles.noSelection}>
            <EmptyState
              title="选择一个对话"
              description={
                isMobile ? '从会话列表打开聊天。' : '从左侧打开会话，或从通讯录发起聊天。'
              }
            />
          </div>
        )}
      </section>

      <Modal
        open={rpOpen}
        title="发红包"
        onClose={() => setRpOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setRpOpen(false)}>
              取消
            </Button>
            <Button onClick={onSendRedPacket}>塞钱进红包</Button>
          </>
        }
      >
        <div className={styles.rpForm}>
          <label className={styles.radioRow}>
            <input type="radio" checked={rpType === 0} onChange={() => setRpType(0)} />
            普通红包
          </label>
          <label className={styles.radioRow}>
            <input type="radio" checked={rpType === 1} onChange={() => setRpType(1)} />
            拼手气红包
          </label>
          <Input label="金额（元）" value={rpAmount} onChange={(e) => setRpAmount(e.target.value)} />
          <Input label="个数" type="number" min="1" max="100" inputMode="numeric" value={rpCount} onChange={(e) => setRpCount(e.target.value)} />
          <Input label="祝福语" value={rpText} onChange={(e) => setRpText(e.target.value)} />
        </div>
      </Modal>

      <Modal open={Boolean(detail)} title="红包详情" onClose={() => setDetail(null)} width={480}>
        {detail ? (
          <div className={styles.rpDetail}>
            <div className={styles.rpHero}>
              <Avatar src={detail.senderAvatar} name={detail.senderNickname} size={48} />
              <p>{detail.senderNickname} 的红包</p>
              <h3>{detail.redPacketWrapperText || '恭喜发财'}</h3>
              <strong>
                {detail.receivedAmount} / {detail.totalAmount} 元
              </strong>
              <span>
                已领 {detail.receivedCount}/{detail.totalCount}
              </span>
            </div>
            <ul>
              {detail.receiveRecords?.map((r) => (
                <li key={`${r.receiverId}-${r.receivedAt}`}>
                  <Avatar src={r.receiverAvatar} name={r.receiverNickname} size={32} />
                  <div>
                    <strong>{r.receiverNickname}</strong>
                    <span>{r.receivedAt}</span>
                  </div>
                  <em>{r.amount} 元</em>
                </li>
              ))}
            </ul>
          </div>
        ) : null}
      </Modal>

      <Modal open={Boolean(lightbox)} title="图片" onClose={() => setLightbox(null)} width={640}>
        {lightbox ? <img className={styles.lightbox} src={lightbox} alt="预览" /> : null}
      </Modal>

      <Modal
        open={knowledgeOpen}
        title="导入个人知识"
        onClose={() => setKnowledgeOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setKnowledgeOpen(false)}>
              取消
            </Button>
            <Button
              onClick={() => void ingestKnowledge()}
              disabled={!knowledgeTitle.trim() || !knowledgeContent.trim()}
            >
              导入
            </Button>
          </>
        }
      >
        <div className={styles.knowledgeForm}>
          <p>这段内容只会进入你的私有知识库，并用于后续回答的相关内容检索。</p>
          <Input
            label="标题"
            value={knowledgeTitle}
            onChange={(event) => setKnowledgeTitle(event.target.value)}
            placeholder="例如：项目发布流程"
          />
          <label>
            <span>内容</span>
            <textarea
              value={knowledgeContent}
              onChange={(event) => setKnowledgeContent(event.target.value)}
              placeholder="粘贴要让 Infinite AI 参考的资料"
              aria-label="知识内容"
              rows={8}
              maxLength={20000}
            />
          </label>
        </div>
      </Modal>
    </div>
  )
}

function MessageBubble({
  message,
  onOpenRedPacket,
  onOpenImage,
  onRetry,
}: {
  message: ChatMessage
  onOpenRedPacket: (id: string) => void
  onOpenImage: (url: string) => void
  onRetry: (m: ChatMessage) => void
}) {
  const failedClass = message.failed ? styles.bubbleFailed : ''
  if (message.type === MessageType.Image) {
    return (
      <button
        type="button"
        className={[styles.imageBtn, failedClass].join(' ')}
        onClick={() => {
          if (message.failed) onRetry(message)
          else if (message.body.content) onOpenImage(message.body.content)
        }}
      >
        <img className={styles.image} src={message.body.content} alt="图片消息" />
      </button>
    )
  }
  if (message.type === MessageType.Emoji) {
    return (
      <button
        type="button"
        className={[styles.emojiBubble, failedClass].join(' ')}
        onClick={() => message.failed && onRetry(message)}
      >
        {message.body.content}
      </button>
    )
  }
  if (message.type === MessageType.RedPacket) {
    return (
      <button
        type="button"
        className={[styles.redpacket, failedClass].join(' ')}
        onClick={() => {
          if (message.failed) onRetry(message)
          else if (message.body.redPacketId) onOpenRedPacket(message.body.redPacketId)
        }}
      >
        <span>红包</span>
        <strong>{message.body.redPacketWrapperText || '恭喜发财'}</strong>
      </button>
    )
  }
  return (
    <button
      type="button"
      className={[styles.bubble, failedClass].join(' ')}
      onClick={() => message.failed && onRetry(message)}
    >
      {message.body.content}
    </button>
  )
}
