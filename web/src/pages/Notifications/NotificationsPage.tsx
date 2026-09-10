import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/Button'
import { SkeletonList } from '@/components/Skeleton'
import { EmptyState } from '@/components/EmptyState'
import { useAuthStore } from '@/stores/authStore'
import { useNotificationStore } from '@/stores/notificationStore'
import { useChatStore } from '@/stores/chatStore'
import { toast } from '@/stores/toastStore'
import { ApiError, parseLosslessJson } from '@/api/client'
import { formatTime, asCount } from '@/utils'
import type { SystemNotification } from '@/types'
import styles from './NotificationsPage.module.css'

const typeMeta: Record<number, { label: string; tone: string }> = {
  101: { label: 'Friend request', tone: 'blue' },
  102: { label: 'New conversation', tone: 'green' },
  103: { label: 'Group invitation', tone: 'orange' },
  104: { label: 'Group change', tone: 'red' },
}

function parseBody(n: SystemNotification) {
  try {
    const raw =
      typeof n.content === 'string'
        ? parseLosslessJson<Record<string, unknown>>(n.content)
        : (n.content as unknown)
    if (!raw || typeof raw !== 'object') return {}
    const record = raw as Record<string, unknown>
    return ((record.body || record) as Record<string, unknown>) || {}
  } catch {
    return {}
  }
}

function summarize(n: SystemNotification) {
  const body = parseBody(n)
  if (Number(n.type) === 101) {
    const name = String(body.applyUserName || body.applyFriendName || 'Someone')
    return `${name}: ${String(body.message || 'would like to add you as a friend')}`
  }
  if (Number(n.type) === 102 || Number(n.type) === 103) {
    return `The conversation "${String(body.sessionName || 'Untitled')}" was created`
  }
  if (Number(n.type) === 104) {
    return 'You were removed from a group, or its members changed'
  }
  return typeof n.content === 'string' && n.content ? n.content : 'New notification'
}

export function NotificationsPage() {
  const navigate = useNavigate()
  const userId = useAuthStore((s) => s.userId)!
  const { items, unreadCount, loading, load, markRead, markAll } = useNotificationStore()
  const list = Array.isArray(items) ? items : []
  const unread = asCount(unreadCount)
  const upsertSession = useChatStore((s) => s.upsertSession)
  const setActiveSession = useChatStore((s) => s.setActiveSession)

  useEffect(() => {
    load(userId).catch((e) => toast.error(e instanceof ApiError ? e.message : 'Failed to load'))
  }, [userId, load])

  const openNotification = async (n: SystemNotification) => {
    try {
      const body = parseBody(n)
      await markRead(userId, n.id)
      if (Number(n.type) === 101) {
        navigate('/app/contacts')
        return
      }
      const sessionId = String(body.sessionId || '')
      if (/^\d+$/.test(sessionId) && sessionId !== '0') {
        upsertSession({
          sessionId,
          name: String(body.sessionName || 'Conversation'),
          avatar: String(body.avatar || ''),
          sessionType: Number(n.type) === 103 || Number(n.type) === 104 ? 1 : 0,
          count: 0,
          type: 0,
          senderId: userId,
          lastMsgContent: '',
          lastMsgTime: new Date().toISOString(),
        })
        setActiveSession(sessionId)
        navigate(`/app/chat/${sessionId}`)
        return
      }
      toast.info('Marked as read')
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Failed to handle the notification')
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <div>
          <span className={styles.eyebrow}>ACTIVITY INBOX</span>
          <h2>Waiting on you</h2>
          <p>Friend requests, group invitations and important changes all land here.</p>
        </div>
      </div>
      <div className={styles.overview}>
        <div className={styles.unreadMetric}><strong>{unread}</strong><span>unread notifications</span></div>
        <p>{unread ? 'There is something new to look at; items disappear once you handle them.' : 'Everything is handled. All clear.'}</p>
        <div className={styles.actions}>
          <Button variant="secondary" loading={loading} onClick={() => load(userId)}>Refresh</Button>
          <Button
            disabled={!unread}
            onClick={async () => {
              try {
                await markAll(userId)
                toast.success('All marked as read')
              } catch (e) {
                toast.error(e instanceof ApiError ? e.message : 'The operation failed')
              }
            }}
          >Mark all read</Button>
        </div>
      </div>
      {loading ? <SkeletonList rows={4} /> : null}
      <div className={styles.listHeading}><h3>Latest notifications</h3><span>{list.length} items</span></div>
      <div className={styles.list}>
        {list.map((n, index) => {
          const type = Number(n.type)
          const meta = typeMeta[type] || { label: `Type ${n.type}`, tone: 'blue' }
          return (
            <article
              key={n.id || n.messageId || String(index)}
              className={`${styles.row} ${styles[meta.tone] || ''}`}
              onClick={() => void openNotification(n)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault()
                  void openNotification(n)
                }
              }}
              role="button"
              tabIndex={0}
            >
              <span className={styles.typeIcon} aria-hidden="true">{meta.label.slice(0, 1)}</span>
              <div className={styles.notificationBody}>
                <div className={styles.top}>
                  <strong>{meta.label}</strong>
                  <span>{formatTime(n.createdTime)}</span>
                </div>
                <p className={styles.summary}>{summarize(n)}</p>
                <span className={styles.cta}>Review and handle <i>→</i></span>
              </div>
            </article>
          )
        })}
        {!list.length && !loading ? (
          <EmptyState title="No unread notifications" description="New friend requests and group activity will show up here." />
        ) : null}
      </div>
    </div>
  )
}
