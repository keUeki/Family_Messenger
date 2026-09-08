import { create } from 'zustand'
import type { SystemNotification } from '@/types'
import type { EntityId } from '@/types'
import { asCount } from '@/utils'
import { offlineApi } from '@/api/offline'

interface NotificationState {
  unreadCount: number
  items: SystemNotification[]
  loading: boolean
  load: (userId: EntityId) => Promise<void>
  refreshCount: (userId: EntityId) => Promise<void>
  markRead: (userId: EntityId, id: EntityId) => Promise<void>
  markAll: (userId: EntityId) => Promise<void>
}

export const useNotificationStore = create<NotificationState>((set, get) => ({
  unreadCount: 0,
  items: [],
  loading: false,
  load: async (userId) => {
    set({ loading: true })
    try {
      const [page, count] = await Promise.all([
        offlineApi.getUnreadNotifications(userId),
        offlineApi.getUnreadCount(userId),
      ])
      set({
        items: Array.isArray(page?.list) ? page.list : [],
        unreadCount: asCount(count),
      })
    } finally {
      set({ loading: false })
    }
  },
  refreshCount: async (userId) => {
    const count = await offlineApi.getUnreadCount(userId)
    set({ unreadCount: asCount(count) })
  },
  markRead: async (userId, id) => {
    await offlineApi.markAsRead(userId, id)
    set({
      items: get().items.filter((n) => n.id !== id),
      unreadCount: Math.max(0, asCount(get().unreadCount) - 1),
    })
  },
  markAll: async (userId) => {
    await offlineApi.markAllAsRead(userId)
    set({ items: [], unreadCount: 0 })
  },
}))
