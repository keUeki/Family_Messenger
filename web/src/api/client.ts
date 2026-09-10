import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { BaseResponse } from '@/types'
import { parseLosslessJson, stringifyApiPayload } from '@/utils/losslessJson'

export { parseLosslessJson, stringifyApiPayload } from '@/utils/losslessJson'

export const API_BASE = import.meta.env.VITE_API_BASE ?? ''

export class ApiError extends Error {
  code: number
  canonicalCode?: string
  constructor(code: number, message: string, canonicalCode?: string) {
    super(message)
    this.code = code
    this.canonicalCode = canonicalCode
    this.name = 'ApiError'
  }
}

type ErrorEnvelope = {
  schemaVersion?: number
  code?: string | number
  legacyCode?: number
  message?: string
  correlationId?: string
  retryable?: boolean
}

function isBaseResponse(body: unknown): body is BaseResponse<unknown> {
  return (
    !!body &&
    typeof body === 'object' &&
    'code' in body &&
    typeof (body as BaseResponse<unknown>).code === 'number' &&
    'data' in body
  )
}

function parseApiError(data: unknown, httpStatus?: number): { code: number; message: string; canonicalCode?: string } {
  if (data && typeof data === 'object') {
    const envelope = data as ErrorEnvelope
    if (typeof envelope.schemaVersion === 'number' && typeof envelope.code === 'string') {
      const code = typeof envelope.legacyCode === 'number' ? envelope.legacyCode : httpStatus ?? -1
      return {
        code,
        message: envelope.message || 'Request failed',
        canonicalCode: envelope.code,
      }
    }
    if (isBaseResponse(data)) {
      return { code: data.code, message: data.message || 'Request failed' }
    }
    if (typeof envelope.code === 'number') {
      return { code: envelope.code, message: envelope.message || 'Request failed' }
    }
  }
  return { code: httpStatus ?? -1, message: 'Request failed' }
}

const AUTH_REFRESH_LEGACY_CODES = new Set([40100, 40102, 40103])
const AUTH_REFRESH_CANONICAL_CODES = new Set(['AUTH_REQUIRED', 'AUTH_EXPIRED', 'AUTH_INVALID'])

function shouldRefreshAuth(parsed: { code: number; canonicalCode?: string }, httpStatus?: number): boolean {
  if (parsed.canonicalCode === 'IDENTITY_MISMATCH' || parsed.code === 40105) return false
  if (httpStatus === 403) return false
  if (httpStatus === 401) return true
  if (AUTH_REFRESH_LEGACY_CODES.has(parsed.code)) return true
  if (parsed.canonicalCode && AUTH_REFRESH_CANONICAL_CODES.has(parsed.canonicalCode)) return true
  return false
}

export const http = axios.create({
  baseURL: API_BASE,
  timeout: 30000,
  withCredentials: false,
  transformResponse: [
    (data) => {
      if (typeof data !== 'string') return data
      try {
        return parseLosslessJson(data)
      } catch {
        return data
      }
    },
  ],
})

function readAuth() {
  const raw = localStorage.getItem('infinitechat.auth')
  if (!raw) return null
  try {
    return JSON.parse(raw) as {
      state?: {
        token?: string
        refreshToken?: string
      }
      token?: string
      refreshToken?: string
    }
  } catch {
    return null
  }
}

export function getToken() {
  const auth = readAuth()
  return auth?.state?.token || auth?.token || null
}

function getRefreshToken() {
  const auth = readAuth()
  return auth?.state?.refreshToken || auth?.refreshToken || null
}

function applyAuthHeaders(headers: Record<string, unknown>, token?: string | null, refreshToken?: string | null) {
  const access = token ?? getToken()
  const refresh = refreshToken ?? getRefreshToken()
  if (access) {
    headers['Access-Token'] = access
    headers.Authorization = `Bearer ${access}`
  }
  if (refresh) {
    headers['Refresh-Token'] = refresh
  }
}

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  applyAuthHeaders(config.headers as Record<string, unknown>)
  if (config.data && typeof config.data === 'object' && !(config.data instanceof FormData)) {
    config.data = stringifyApiPayload(config.data)
    config.headers['Content-Type'] = 'application/json'
  }
  return config
})

let refreshing: Promise<string | null> | null = null
let authExpiryNotified = false

function notifyAuthExpired() {
  if (authExpiryNotified) return
  authExpiryNotified = true
  window.dispatchEvent(new Event('infinitechat:auth-expired'))
}

async function refreshAccessToken() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return null
  const res = await axios.post<BaseResponse<{ accessToken: string; refreshToken: string }>>(
    `${API_BASE}/api/user/refresh`,
    {},
    { headers: { 'Refresh-Token': refreshToken } },
  )
  if (res.data.code !== 200) return null
  const token = res.data.data?.accessToken
  const nextRefreshToken = res.data.data?.refreshToken
  const raw = localStorage.getItem('infinitechat.auth')
  if (raw && token) {
    try {
      const parsed = JSON.parse(raw) as { state?: Record<string, unknown> }
      if (parsed.state) {
        parsed.state.token = token
        if (nextRefreshToken) parsed.state.refreshToken = nextRefreshToken
        localStorage.setItem('infinitechat.auth', JSON.stringify(parsed))
      }
    } catch {
      // ignore
    }
  }
  window.dispatchEvent(new CustomEvent('infinitechat:token-refreshed', { detail: token }))
  authExpiryNotified = false
  return token || null
}

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (isBaseResponse(body)) {
      if (body.code !== 200) {
        return Promise.reject(new ApiError(body.code, body.message || 'Request failed'))
      }
      return { ...response, data: body }
    }
    return response
  },
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & { _retry?: boolean }
    const parsed = parseApiError(error.response?.data, error.response?.status)
    if (shouldRefreshAuth(parsed, error.response?.status) && original && !original._retry) {
      original._retry = true
      try {
        refreshing = refreshing || refreshAccessToken()
        const token = await refreshing
        refreshing = null
        if (token) {
          applyAuthHeaders(original.headers as Record<string, unknown>, token)
          return http(original)
        }
      } catch {
        refreshing = null
      }
      notifyAuthExpired()
    }
    const msg = parsed.message || error.message || 'Network error, please try again shortly'
    return Promise.reject(new ApiError(parsed.code, msg, parsed.canonicalCode))
  },
)

export async function unwrap<T>(promise: Promise<{ data: BaseResponse<T> }>): Promise<T> {
  const res = await promise
  return res.data.data
}
