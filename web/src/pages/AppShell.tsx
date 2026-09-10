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
  { to: '/app/chat', label: 'Chats', icon: <IconChat size={20} /> },
  { to: '/app/contacts', label: 'Contacts', icon: <IconPeople size={20} /> },
  { to: '/app/groups', label: 'Groups', icon: <IconGroup size={20} /> },
  { to: '/app/notifications', label: 'Notifications', icon: <IconBell size={20} /> },
  { to: '/app/profile', label: 'Me', icon: <IconUser size={20} /> },
]

const pageMeta = [
  { match: '/app/chat', eyebrow: 'CONVERSATIONS', title: 'Messages', description: 'Every conversation that matters, delivered clearly' },
  { match: '/app/contacts', eyebrow: 'CONNECTIONS', title: 'Contacts', description: 'Manage your friends and new connections' },
  { match: '/app/groups', eyebrow: 'COMMUNITIES', title: 'Groups', description: 'Get everyone on the same page' },
  { match: '/app/notifications', eyebrow: 'INBOX', title: 'Notifications', description: 'Everything waiting on you, in one place' },
  { match: '/app/profile', eyebrow: 'ACCOUNT', title: 'Profile', description: 'Account, security and connection settings' },
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
      ? 'Realtime connected'
      : status === 'connecting'
        ? 'Connecting'
        : status === 'missing'
          ? 'No node assigned'
          : 'Disconnected'

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
        toast.success('Realtime node refreshed')
        window.setTimeout(() => connect(), 50)
      } else {
        toast.warning('No realtime node was assigned; check that realtime is registered in etcd')
      }
    } catch {
      toast.error('Failed to refresh the node')
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
          <button className={styles.logout} onClick={onLogout} type="button">Log out</button>
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
              title="Click to refresh the realtime node"
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
                  <strong>{profile.nickname || profile.account || 'User'}</strong>
                  <small>{profile.account || 'InfiniteChat user'}</small>
                </span>
                <span className={styles.chevron}>⌄</span>
              </button>
              {menuOpen ? (
                <div className={styles.menu} role="menu">
                  <div className={styles.menuIntro}>
                    <strong>{profile.nickname || 'InfiniteChat user'}</strong>
                    <span>{profile.account}</span>
                  </div>
                  <Button
                    variant="ghost"
                    onClick={() => {
                      setMenuOpen(false)
                      navigate('/app/profile')
                    }}
                  >
                    Profile
                  </Button>
                  <Button
                    variant="ghost"
                    onClick={() => {
                      setMenuOpen(false)
                      onRefreshNode()
                    }}
                  >
                    Refresh realtime connection
                  </Button>
                  <Button variant="danger" onClick={onLogout}>Log out</Button>
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
