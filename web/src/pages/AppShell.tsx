import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useEffect, useRef, useState } from 'react'
import { Avatar } from '@/components/Avatar'
import { Button } from '@/components/Button'
import { PageErrorBoundary } from '@/components/PageErrorBoundary'
import {
  IconBell,
  IconChat,
  IconGroup,
  IconPeople,
  IconRefresh,
  IconUser,
} from '@/components/Icons'
import { useAuthStore } from '@/stores/authStore'
import { useNotificationStore } from '@/stores/notificationStore'
import { WebSocketProvider } from '@/hooks/WebSocketProvider'
import { useWs } from '@/hooks/useWs'
import { userApi } from '@/api/user'
import { toast } from '@/stores/toastStore'
import { asCount } from '@/utils'
import styles from './AppShell.module.css'
import type { ReactNode } from 'react'

const nav: { to: string; label: string; icon: ReactNode }[] = [
  { to: '/app/chat', label: '聊天', icon: <IconChat size={20} /> },
  { to: '/app/contacts', label: '通讯录', icon: <IconPeople size={20} /> },
  { to: '/app/groups', label: '群组', icon: <IconGroup size={20} /> },
  { to: '/app/notifications', label: '通知', icon: <IconBell size={20} /> },
  { to: '/app/profile', label: '我的', icon: <IconUser size={20} /> },
]

const pageMeta = [
  { match: '/app/chat', eyebrow: 'CONVERSATIONS', title: '消息', description: '让重要对话清晰抵达' },
  { match: '/app/contacts', eyebrow: 'CONNECTIONS', title: '通讯录', description: '管理好友与新的连接' },
  { match: '/app/groups', eyebrow: 'COMMUNITIES', title: '群组', description: '和一群人，把事情聊清楚' },
  { match: '/app/notifications', eyebrow: 'INBOX', title: '通知', description: '集中查看需要你处理的动态' },
  { match: '/app/profile', eyebrow: 'ACCOUNT', title: '个人中心', description: '账户、安全与连接设置' },
]

function ShellInner() {
  const navigate = useNavigate()
  const location = useLocation()
  const profile = useAuthStore((s) => s.profile)
  const userId = useAuthStore((s) => s.userId)
  const logout = useAuthStore((s) => s.logout)
  const setWsServerUri = useAuthStore((s) => s.setWsServerUri)
  const unreadCount = asCount(useNotificationStore((s) => s.unreadCount))
  const refreshCount = useNotificationStore((s) => s.refreshCount)
  const { status, connect } = useWs()
  const [menuOpen, setMenuOpen] = useState(false)
  const [badgePulse, setBadgePulse] = useState(false)
  const prevUnread = useRef(0)
  const menuRef = useRef<HTMLDivElement>(null)
  const isChat = location.pathname.startsWith('/app/chat')
  const currentPage = pageMeta.find((item) => location.pathname.startsWith(item.match)) || pageMeta[0]

  useEffect(() => {
    if (userId) {
      refreshCount(userId).catch(() => undefined)
      const timer = window.setInterval(() => {
        refreshCount(userId).catch(() => undefined)
      }, 30000)
      return () => window.clearInterval(timer)
    }
  }, [userId, refreshCount])

  useEffect(() => {
    if (unreadCount > prevUnread.current) {
      setBadgePulse(true)
      const t = window.setTimeout(() => setBadgePulse(false), 1400)
      prevUnread.current = unreadCount
      return () => window.clearTimeout(t)
    }
    prevUnread.current = unreadCount
  }, [unreadCount])

  useEffect(() => {
    if (!menuOpen) return
    const onDown = (e: MouseEvent) => {
      if (!menuRef.current?.contains(e.target as Node)) setMenuOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMenuOpen(false)
    }
    window.addEventListener('mousedown', onDown)
    window.addEventListener('keydown', onKey)
    return () => {
      window.removeEventListener('mousedown', onDown)
      window.removeEventListener('keydown', onKey)
    }
  }, [menuOpen])

  const statusLabel =
    status === 'open'
      ? '实时已连接'
      : status === 'connecting'
        ? '正在连接'
        : status === 'missing'
          ? '未分配节点'
          : '连接已断开'

  const onLogout = async () => {
    await logout()
    navigate('/auth', { replace: true })
  }

  const onRefreshNode = async () => {
    if (!userId) return
    try {
      const uri = await userApi.refreshUri(userId)
      if (uri) {
        setWsServerUri(uri)
        toast.success('已刷新实时节点')
        window.setTimeout(() => connect(), 50)
      } else {
        toast.warning('未分配到实时节点，请确认 realtime 已注册 etcd')
      }
    } catch {
      toast.error('刷新节点失败')
    }
  }

  return (
    <div className={[styles.shell, isChat ? styles.chatMode : ''].join(' ')}>
      <aside className={styles.nav}>
        <div className={styles.brand} title="InfiniteChat">
          <div className={styles.brandMark} aria-hidden="true">
            <span>∞</span>
          </div>
          <span className={styles.brandName}>Infinite</span>
        </div>
        <nav className={styles.links}>
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/app/chat'}
              className={({ isActive }) =>
                [
                  styles.link,
                  isActive || (item.to === '/app/chat' && isChat) ? styles.linkActive : '',
                ].join(' ')
              }
            >
              <span className={styles.icon}>{item.icon}</span>
              <span>{item.label}</span>
              {item.to.includes('notifications') && unreadCount > 0 ? (
                <i className={[styles.badge, badgePulse ? styles.badgePulse : ''].join(' ')}>
                  {unreadCount > 99 ? '99+' : unreadCount}
                </i>
              ) : null}
            </NavLink>
          ))}
        </nav>
        <div className={styles.navFooter}>
          <span>SECURE CHAT</span>
          <button className={styles.logout} onClick={onLogout} type="button">退出登录</button>
        </div>
      </aside>
      <div className={styles.main}>
        <header className={styles.header}>
          <div className={styles.pageContext}>
            <span className={styles.mobileMark}>∞</span>
            <div>
              <span className={styles.eyebrow}>{currentPage.eyebrow}</span>
              <div className={styles.titleLine}>
                <h1>{currentPage.title}</h1>
                <p>{currentPage.description}</p>
              </div>
            </div>
          </div>
          <div className={styles.headerActions}>
            <button
              type="button"
              className={[styles.status, styles[`st_${status}`]].join(' ')}
              onClick={onRefreshNode}
              title="点击刷新实时节点"
            >
              <i />
              <span className={styles.statusText}>{statusLabel}</span>
              <IconRefresh size={14} />
            </button>
            <div className={styles.userWrap} ref={menuRef}>
              <button
                type="button"
                className={styles.user}
                aria-haspopup="menu"
                aria-expanded={menuOpen}
                onClick={() => setMenuOpen((v) => !v)}
              >
                <Avatar src={profile.avatar} name={profile.nickname} size={36} />
                <span className={styles.userCopy}>
                  <strong>{profile.nickname || profile.account || '用户'}</strong>
                  <small>{profile.account || 'InfiniteChat 用户'}</small>
                </span>
                <span className={styles.chevron}>⌄</span>
              </button>
              {menuOpen ? (
                <div className={styles.menu} role="menu">
                  <div className={styles.menuIntro}>
                    <strong>{profile.nickname || 'InfiniteChat 用户'}</strong>
                    <span>{profile.account}</span>
                  </div>
                  <Button
                    variant="ghost"
                    onClick={() => {
                      setMenuOpen(false)
                      navigate('/app/profile')
                    }}
                  >
                    个人中心
                  </Button>
                  <Button
                    variant="ghost"
                    onClick={() => {
                      setMenuOpen(false)
                      onRefreshNode()
                    }}
                  >
                    刷新实时连接
                  </Button>
                  <Button variant="danger" onClick={onLogout}>退出登录</Button>
                </div>
              ) : null}
            </div>
          </div>
        </header>
        <div className={styles.content}>
          <div
            key={location.pathname.startsWith('/app/chat') ? '/app/chat' : location.pathname}
            className={`${styles.page} ${styles.pageEnter}`}
          >
            <PageErrorBoundary resetKey={location.pathname}>
              <Outlet />
            </PageErrorBoundary>
          </div>
        </div>
      </div>
    </div>
  )
}

export function AppShell() {
  return (
    <WebSocketProvider>
      <ShellInner />
    </WebSocketProvider>
  )
}
