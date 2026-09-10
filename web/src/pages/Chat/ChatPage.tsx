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
import { aiApi } from '@/api/ai'
import { MessageType, SessionType, AI_USER_ID, type ChatMessage, type EntityId } from '@/types'
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
      toast.error(e instanceof ApiError ? e.message : 'Failed to load the conversations'),
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
    load().catch((e) => toast.error(e instanceof ApiError ? e.message : 'Failed to load the messages'))
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
        toast.info('You have reached the start of the conversation')
      }
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Failed to load the message history')
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
      toast.warning('The realtime connection is not ready, reconnect first')
      return false
    }
    let peer: EntityId | null = null
    if (active.sessionType === SessionType.Single) {
      peer = resolvePeer()
      if (!peer) {
        toast.error('Could not work out who this chat is with; open it again from your contacts')
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
      toast.error(e instanceof Error ? e.message : 'Failed to send')
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
      appendMessage({ sessionId: activeSessionId, senderId: AI_USER_ID, type: MessageType.Text, sessionType: SessionType.Robot, body: { content: answer || 'The AI could not reply, please try again.' }, createdTime: new Date().toISOString(), messageId: assistantId, failed: true, nickname: 'Infinite AI' })
      toast.error(error instanceof Error ? error.message : 'The AI could not reply')
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
        .map((item) => `${item.senderId === userId ? 'User' : 'AI'}: ${item.body?.content || ''}`)
        .join('\n')
      const result = await aiApi.summary(historyLog || 'No message history')
      appendMessage({ sessionId: activeSessionId, senderId: AI_USER_ID, type: MessageType.Text, sessionType: SessionType.Robot, body: { content: result.summary }, createdTime: new Date().toISOString(), messageId: uid('summary'), nickname: 'Infinite AI' })
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to summarise')
    } finally {
      setAiBusy(false)
    }
  }

  const ingestKnowledge = async () => {
    if (!knowledgeTitle.trim() || !knowledgeContent.trim()) return
    try {
      const result = await aiApi.ingest(knowledgeTitle.trim(), knowledgeContent.trim())
      toast.success(result.inserted ? 'Knowledge imported' : 'That knowledge already exists')
      setKnowledgeOpen(false)
      setKnowledgeTitle('')
      setKnowledgeContent('')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to import the knowledge')
    }
  }

  const insertEmoji = (emoji: string) => {
    setText((prev) => prev + emoji)
  }

  const onPickImage = async (file: File) => {
    try {
      toast.info('Uploading the image...')
      const url = await uploadFile(file, (name) => userApi.getUploadUrl(name))
      sendPayload(MessageType.Image, { content: url })
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Upload failed, check the MinIO CORS settings')
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
            <h2>Recent chats</h2>
            <p>{sessions.length} conversations - {sessions.reduce((sum, item) => sum + (item.count || 0), 0)} unread</p>
          </div>
          <IconButton onClick={() => loadSessions(userId)} title="Refresh conversations" aria-label="Refresh conversations">
            <IconRefresh size={18} />
          </IconButton>
        </div>
        <div className={styles.searchWrap}>
          <span className={styles.searchIcon}>
            <IconSearch size={16} />
          </span>
          <input
            className={styles.search}
            placeholder="Search conversations"
            aria-label="Search conversations"
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
                  <strong>{s.name || `Chat ${s.sessionId}`}</strong>
                  <span>{formatTime(s.lastMsgTime)}</span>
                </div>
                <div className={styles.sessionBottom}>
                  <p>{s.lastMsgContent || 'No messages yet'}</p>
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
              title="No conversations yet"
              description="Find a friend in your contacts, or create a group to start talking."
              actionLabel="Go to contacts"
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
                  Back
                </button>
              ) : null}
              <Avatar src={active.avatar} name={isAI ? 'Infinite AI' : active.name} size={40} />
              <div className={styles.chatIdentity}>
                <h3>{isAI ? 'Infinite AI' : active.name}</h3>
                <span>
                  <i className={isAI || status === 'open' ? styles.online : ''} />
                  {isAI
                    ? 'Knowledge-augmented assistant'
                    : active.sessionType === SessionType.Group
                      ? 'Group conversation'
                      : status === 'open'
                        ? 'Online'
                        : 'Waiting to connect'}
                </span>
              </div>
              {isAI ? (
                <div className={styles.aiActions}>
                  <Button variant="ghost" onClick={() => void summarizeAI()} disabled={aiBusy}>
                    Summarise chat
                  </Button>
                  <Button variant="secondary" onClick={() => setKnowledgeOpen(true)}>
                    Import knowledge
                  </Button>
                </div>
              ) : (
                <div className={styles.chatMeta}>Chat #{active.sessionId}</div>
              )}
            </div>

            <div className={styles.messages} ref={listRef} onScroll={onMessagesScroll}>
              {messages.length ? (
                <button type="button" className={styles.loadMore} onClick={() => void onLoadMore()}>
                  <span>↑</span> Load earlier messages
                </button>
              ) : null}
              {loadingMessages ? <p className={styles.muted}>Loading messages...</p> : null}
              {!loadingMessages && !messages.length ? (
                <div className={styles.threadEmpty}>
                  <span>∞</span>
                  <strong>This is the start of the conversation</strong>
                  <p>Send a message to begin your InfiniteChat.</p>
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
                            {m.pending ? ' - sending' : ''}
                            {m.failed ? ' - failed, tap the bubble to retry' : ''}
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
                      ? 'Connecting to the realtime channel...'
                      : status === 'missing'
                        ? 'No realtime node assigned'
                        : 'The realtime connection has dropped'}
                  </span>
                  <Button type="button" variant="secondary" onClick={() => connect()}>
                    Reconnect
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
                  title="Emoji"
                  aria-label="Emoji"
                >
                  <IconSmile size={18} />
                </IconButton>
                {!isAI ? (
                  <>
                    <IconButton
                      onClick={() => fileRef.current?.click()}
                      title="Image"
                      aria-label="Image"
                    >
                      <IconImage size={18} />
                    </IconButton>
                  </>
                ) : null}
                <span className={styles.hint}>Enter to send - Shift+Enter for a new line</span>
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
                  placeholder={isAI ? 'Ask Infinite AI...' : status === 'open' ? 'Type a message...' : 'Reconnect the realtime channel first...'}
                  rows={2}
                  maxLength={4000}
                  aria-label="Message input"
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
                  Send
                </Button>
              </div>
            </div>
          </>
        ) : (
          <div className={styles.noSelection}>
            <EmptyState
              title="Pick a conversation"
              description={
                isMobile ? 'Open a chat from the conversation list.' : 'Open a conversation on the left, or start one from your contacts.'
              }
            />
          </div>
        )}
      </section>


      <Modal open={Boolean(lightbox)} title="Image" onClose={() => setLightbox(null)} width={640}>
        {lightbox ? <img className={styles.lightbox} src={lightbox} alt="Preview" /> : null}
      </Modal>

      <Modal
        open={knowledgeOpen}
        title="Import personal knowledge"
        onClose={() => setKnowledgeOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setKnowledgeOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={() => void ingestKnowledge()}
              disabled={!knowledgeTitle.trim() || !knowledgeContent.trim()}
            >
              Import
            </Button>
          </>
        }
      >
        <div className={styles.knowledgeForm}>
          <p>This content only goes into your private knowledge base, where it is retrieved to inform later answers.</p>
          <Input
            label="Title"
            value={knowledgeTitle}
            onChange={(event) => setKnowledgeTitle(event.target.value)}
            placeholder="e.g. Release process"
          />
          <label>
            <span>Content</span>
            <textarea
              value={knowledgeContent}
              onChange={(event) => setKnowledgeContent(event.target.value)}
              placeholder="Paste the material you want Infinite AI to draw on"
              aria-label="Knowledge content"
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
  onOpenImage,
  onRetry,
}: {
  message: ChatMessage
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
        <img className={styles.image} src={message.body.content} alt="Image message" />
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
