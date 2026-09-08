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
  101: { label: '好友申请', tone: 'blue' },
  102: { label: '新会话', tone: 'green' },
  103: { label: '群聊邀请', tone: 'orange' },
  104: { label: '群聊变动', tone: 'red' },
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
    const name = String(body.applyUserName || body.applyFriendName || '有人')
    return `${name}：${String(body.message || '请求添加你为好友')}`
  }
  if (Number(n.type) === 102 || Number(n.type) === 103) {
    return `会话「${String(body.sessionName || '未命名')}」已创建`
  }
  if (Number(n.type) === 104) {
    return '你已被移出群聊或成员发生变更'
  }
  return typeof n.content === 'string' && n.content ? n.content : '新通知'
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
    load(userId).catch((e) => toast.error(e instanceof ApiError ? e.message : '加载失败'))
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
          name: String(body.sessionName || '会话'),
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
      toast.info('已标为已读')
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '通知处理失败')
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <div>
          <span className={styles.eyebrow}>ACTIVITY INBOX</span>
          <h2>待处理动态</h2>
          <p>好友申请、群聊邀请与重要变动，都在这里。</p>
        </div>
      </div>
      <div className={styles.overview}>
        <div className={styles.unreadMetric}><strong>{unread}</strong><span>条未读通知</span></div>
        <p>{unread ? '有新的动态等待你查看，处理后会自动从列表移除。' : '已全部处理完成，现在很清爽。'}</p>
        <div className={styles.actions}>
          <Button variant="secondary" loading={loading} onClick={() => load(userId)}>刷新</Button>
          <Button
            disabled={!unread}
            onClick={async () => {
              try {
                await markAll(userId)
                toast.success('已全部标为已读')
              } catch (e) {
                toast.error(e instanceof ApiError ? e.message : '操作失败')
              }
            }}
          >全部已读</Button>
        </div>
      </div>
      {loading ? <SkeletonList rows={4} /> : null}
      <div className={styles.listHeading}><h3>最新通知</h3><span>{list.length} 条</span></div>
      <div className={styles.list}>
        {list.map((n, index) => {
          const type = Number(n.type)
          const meta = typeMeta[type] || { label: `类型 ${n.type}`, tone: 'blue' }
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
                <span className={styles.cta}>查看并处理 <i>→</i></span>
              </div>
            </article>
          )
        })}
        {!list.length && !loading ? (
          <EmptyState title="暂无未读通知" description="新的好友申请和群动态会出现在这里。" />
        ) : null}
      </div>
    </div>
  )
}
