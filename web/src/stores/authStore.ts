import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { EntityId, LoginResponse, UserInfo } from '@/types'
import { userApi } from '@/api/user'

interface AuthState {
  token: string | null
  refreshToken: string | null
  userId: EntityId | null
  wsServerUri: string | null
  offlineTime: number | null
  profile: Partial<UserInfo> & {
    account?: string
    nickname?: string
    avatar?: string
  }
  setFromLogin: (data: LoginResponse) => void
  setToken: (token: string) => void
  setWsServerUri: (uri: string) => void
  updateProfile: (profile: Partial<UserInfo>) => void
  refreshProfile: () => Promise<void>
  clearSession: () => void
  logout: () => Promise<void>
  isAuthenticated: () => boolean
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      refreshToken: null,
      userId: null,
      wsServerUri: null,
      offlineTime: null,
      profile: {},
      setFromLogin: (data) =>
        set({
          token: data.token,
          refreshToken: data.refreshToken,
          userId: data.userId,
          wsServerUri: data.wsServerUri,
          offlineTime: data.offlineTime ?? null,
          profile: {
            userId: data.userId,
            account: data.account,
            nickname: data.nickname,
            avatar: data.avatar,
            gender: data.gender,
            description: data.description,
          },
        }),
      setToken: (token) => set({ token }),
      setWsServerUri: (uri) => set({ wsServerUri: uri }),
      updateProfile: (profile) =>
        set((s) => ({ profile: { ...s.profile, ...profile } })),
      refreshProfile: async () => {
        const userId = get().userId
        if (!userId) return
        const info = await userApi.getUserInfo(userId)
        set({ profile: info })
      },
      clearSession: () =>
        set({
          token: null,
          refreshToken: null,
          userId: null,
          wsServerUri: null,
          offlineTime: null,
          profile: {},
        }),
      logout: async () => {
        const userId = get().userId
        try {
          if (userId) await userApi.logout(userId)
        } catch {
          // ignore logout API errors
        }
        get().clearSession()
      },
      isAuthenticated: () => Boolean(get().token && get().userId),
    }),
    {
      name: 'infinitechat.auth',
      version: 2,
      migrate: (persisted) => {
        const state = (persisted || {}) as Partial<AuthState>
        if (state.userId != null && typeof state.userId !== 'string') {
          return {
            token: null,
            refreshToken: null,
            userId: null,
            wsServerUri: null,
            offlineTime: null,
            profile: {},
          }
        }
        return {
          token: state.token ?? null,
          refreshToken: state.refreshToken ?? null,
          userId: state.userId ?? null,
          wsServerUri: state.wsServerUri ?? null,
          offlineTime: (state as AuthState).offlineTime ?? null,
          profile: state.profile ?? {},
        }
      },
      partialize: (s) => ({
        token: s.token,
        refreshToken: s.refreshToken,
        userId: s.userId,
        wsServerUri: s.wsServerUri,
        offlineTime: s.offlineTime,
        profile: s.profile,
      }),
    },
  ),
)
