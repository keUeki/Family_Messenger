import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthPage } from '@/pages/Auth/AuthPage'
import { AppShell } from '@/pages/AppShell'
import { ChatPage } from '@/pages/Chat/ChatPage'
import { ContactsPage } from '@/pages/Contacts/ContactsPage'
import { GroupsPage } from '@/pages/Groups/GroupsPage'
import { NotificationsPage } from '@/pages/Notifications/NotificationsPage'
import { ProfilePage } from '@/pages/Profile/ProfilePage'
import { useAuthStore } from '@/stores/authStore'
import { toast } from '@/stores/toastStore'
import { useEffect, type ReactNode } from 'react'

function RequireAuth({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token)
  const userId = useAuthStore((s) => s.userId)
  if (!token || !userId) {
    return <Navigate to="/auth" replace />
  }
  return children
}

function RedirectIfAuth({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token)
  const userId = useAuthStore((s) => s.userId)
  if (token && userId) {
    return <Navigate to="/app/chat" replace />
  }
  return children
}

export default function App() {
  useEffect(() => {
    const onTokenRefreshed = (event: Event) => {
      const token = (event as CustomEvent<string>).detail
      if (token) useAuthStore.getState().setToken(token)
    }
    const onAuthExpired = () => {
      useAuthStore.getState().clearSession()
      toast.warning('Your session has expired, please log in again')
    }
    window.addEventListener('infinitechat:token-refreshed', onTokenRefreshed)
    window.addEventListener('infinitechat:auth-expired', onAuthExpired)
    return () => {
      window.removeEventListener('infinitechat:token-refreshed', onTokenRefreshed)
      window.removeEventListener('infinitechat:auth-expired', onAuthExpired)
    }
  }, [])

  return (
    <Routes>
      <Route
        path="/auth"
        element={
          <RedirectIfAuth>
            <AuthPage />
          </RedirectIfAuth>
        }
      />
      <Route
        path="/app"
        element={
          <RequireAuth>
            <AppShell />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="chat" replace />} />
        <Route path="chat" element={<ChatPage />} />
        <Route path="chat/:sessionId" element={<ChatPage />} />
        <Route path="contacts" element={<ContactsPage />} />
        <Route path="groups" element={<GroupsPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="profile" element={<ProfilePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/app/chat" replace />} />
    </Routes>
  )
}
