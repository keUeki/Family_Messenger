import { http, unwrap } from './client'
import type { BalanceLog, EntityId, LoginResponse, PageResponse, UserInfo } from '@/types'

type JavaLogin = {
  userId: EntityId
  email?: string
  account?: string
  nickname: string
  avatar: string
  gender: number
  description: string
  accessToken?: string
  refreshToken: string
  token?: string
  nettyUri?: string
  wsServerUri?: string
  offlineTime?: number | string | null
}

function mapLogin(data: JavaLogin): LoginResponse {
  return {
    userId: String(data.userId),
    account: data.email || data.account || '',
    nickname: data.nickname,
    avatar: data.avatar,
    gender: data.gender,
    description: data.description,
    token: data.accessToken || data.token || '',
    refreshToken: data.refreshToken,
    wsServerUri: data.nettyUri || data.wsServerUri || '',
    offlineTime: data.offlineTime == null ? null : Number(data.offlineTime),
  }
}

export const userApi = {
  sendCaptcha(account: string) {
    return unwrap<string>(http.get('/api/user/sendCaptcha', { params: { targetEmail: account } }))
  },
  async loginPassword(account: string, password: string) {
    const data = await unwrap<JavaLogin>(http.post('/api/user/login/password', { email: account, password }))
    return mapLogin(data)
  },
  async loginCode(account: string, code: string) {
    const data = await unwrap<JavaLogin>(http.post('/api/user/login/code', { email: account, code }))
    return mapLogin(data)
  },
  async register(payload: {
    account: string
    password: string
    confirmPassword: string
    code: string
    nickname: string
  }) {
    const data = await unwrap<JavaLogin>(
      http.post('/api/user/register', {
        email: payload.account,
        password: payload.password,
        confirmPassword: payload.confirmPassword,
        code: payload.code,
        nickname: payload.nickname,
      }),
    )
    return mapLogin(data)
  },
  logout(_userId: EntityId) {
    return unwrap<boolean>(http.get('/api/user/logout'))
  },
  getUserInfo(userId: EntityId) {
    return unwrap<UserInfo>(http.get('/api/user/getUserInfo', { params: { userId } }))
  },
  updatePassword(payload: {
    account: string
    password: string
    confirmPassword: string
    code: string
  }) {
    return unwrap<boolean>(
      http.post('/api/user/updatePassword', {
        email: payload.account,
        password: payload.password,
        confirmPassword: payload.confirmPassword,
        code: payload.code,
      }),
    )
  },
  refreshUri(userId: EntityId) {
    return unwrap<string>(http.get('/api/user/refresh/uri', { params: { userId } }))
  },
  getUploadUrl(fileName: string) {
    return unwrap<{ uploadUrl: string; downloadUrl: string }>(
      http.get('/api/user/uploadUrl', { params: { fileName } }),
    )
  },
  updateAvatar(userId: EntityId, uri: string) {
    return unwrap<boolean>(http.post('/api/user/update/avatar', { userId, uri }))
  },
  getBalance(userId: EntityId) {
    return unwrap<{ balance: string | number }>(http.get(`/api/user/balance/${userId}`))
  },
  getBalanceDetail(userId: EntityId, pageNum = 1, pageSize = 20) {
    return unwrap<PageResponse<BalanceLog>>(
      http.get(`/api/user/balanceDetail/${userId}`, { params: { pageNum, pageSize } }),
    )
  },
}
