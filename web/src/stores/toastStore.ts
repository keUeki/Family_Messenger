import { create } from 'zustand'
import { uid } from '@/utils'

export type ToastKind = 'info' | 'success' | 'error' | 'warning'

export interface ToastItem {
  id: string
  message: string
  kind: ToastKind
}

interface ToastState {
  items: ToastItem[]
  push: (message: string, kind?: ToastKind) => void
  dismiss: (id: string) => void
}

export const useToastStore = create<ToastState>((set) => ({
  items: [],
  push: (message, kind = 'info') => {
    const id = uid('toast')
    set((s) => ({ items: [...s.items.slice(-4), { id, message, kind }] }))
    window.setTimeout(() => {
      set((s) => ({ items: s.items.filter((t) => t.id !== id) }))
    }, 3200)
  },
  dismiss: (id) => set((s) => ({ items: s.items.filter((t) => t.id !== id) })),
}))

export const toast = {
  info: (msg: string) => useToastStore.getState().push(msg, 'info'),
  success: (msg: string) => useToastStore.getState().push(msg, 'success'),
  error: (msg: string) => useToastStore.getState().push(msg, 'error'),
  warning: (msg: string) => useToastStore.getState().push(msg, 'warning'),
}
