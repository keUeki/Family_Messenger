export function uid(prefix = 'id') {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
}

export function asCount(value: unknown): number {
  if (typeof value === 'number' && Number.isFinite(value)) return Math.max(0, value)
  if (typeof value === 'string' && /^-?\d+(\.\d+)?$/.test(value.trim())) {
    return Math.max(0, Number(value))
  }
  if (value && typeof value === 'object' && 'count' in value) {
    return asCount((value as { count: unknown }).count)
  }
  return 0
}

export function parseDate(value?: string | number | null) {
  if (value == null || value === '') return null
  if (typeof value === 'number') {
    const fromNumber = new Date(value > 1e12 ? value : value * 1000)
    return Number.isNaN(fromNumber.getTime()) ? null : fromNumber
  }
  const trimmed = String(value).trim()
  if (/^\d+$/.test(trimmed)) {
    const numeric = Number(trimmed)
    const fromEpoch = new Date(trimmed.length > 11 ? numeric : numeric * 1000)
    if (!Number.isNaN(fromEpoch.getTime())) return fromEpoch
  }
  const exact = new Date(trimmed)
  if (!Number.isNaN(exact.getTime())) return exact
  const legacy = new Date(trimmed.replace(/^(\d{4})-(\d{2})-(\d{2}) /, '$1/$2/$3 '))
  return Number.isNaN(legacy.getTime()) ? null : legacy
}

export function formatTime(value?: string | number | null) {
  const d = parseDate(value)
  if (!d) return value == null ? '' : String(value)
  const now = new Date()
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  if (sameDay) return `${hh}:${mm}`
  const mon = d.getMonth() + 1
  const day = d.getDate()
  return `${mon}/${day} ${hh}:${mm}`
}

export function formatDayLabel(value?: string | null) {
  const d = parseDate(value)
  if (!d) return ''
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const target = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  const diff = (today.getTime() - target.getTime()) / 86400000
  if (diff === 0) return '今天'
  if (diff === 1) return '昨天'
  if (d.getFullYear() === now.getFullYear()) {
    return `${d.getMonth() + 1}月${d.getDate()}日`
  }
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
}

export function sameDay(a?: string | null, b?: string | null) {
  const da = parseDate(a)
  const db = parseDate(b)
  if (!da || !db) return false
  return (
    da.getFullYear() === db.getFullYear() &&
    da.getMonth() === db.getMonth() &&
    da.getDate() === db.getDate()
  )
}

export function initials(name?: string) {
  if (!name) return '?'
  return name.trim().slice(0, 1).toUpperCase()
}

import { getToken } from '@/api/client'

export async function uploadFile(file: File, getUploadUrl: (name: string) => Promise<{ uploadUrl: string; downloadUrl: string }>) {
  if (!file.type.startsWith('image/')) {
    throw new Error('请选择图片文件')
  }
  if (file.size > 10 * 1024 * 1024) {
    throw new Error('图片不能超过 10MB')
  }
  const safeName = file.name.replace(/[^a-zA-Z0-9._-]/g, '_') || `image_${Date.now()}`
  const { uploadUrl, downloadUrl } = await getUploadUrl(safeName)
  const token = getToken() || ''
  const res = await fetch(uploadUrl, {
    method: 'PUT',
    body: file,
    headers: {
      'Content-Type': file.type || 'application/octet-stream',
      'Access-Token': token,
      Authorization: `Bearer ${token}`,
    },
  })
  if (!res.ok) throw new Error(`文件上传失败（${res.status}）`)
  const body = await res.json().catch(() => null) as { data?: string } | null
  return body?.data || downloadUrl
}
