const ID_KEYS = [
  'id',
  'userId',
  'friendId',
  'receiveuserId',
  'senderId',
  'receiverId',
  'sessionId',
  'messageId',
  'notificationId',
  'operatorId',
  'creatorId',
  'inviterId',
  'replyId',
  'peerId',
]
const ID_ARRAY_KEYS = ['memberIds', 'inviteeIds', 'notificationIds', 'receiveuserIds']
const ID_KEY_SET = new Set(ID_KEYS)
const ID_ARRAY_KEY_SET = new Set(ID_ARRAY_KEYS)

function normalizeIds(value: unknown, parentKey?: string): unknown {
  if (Array.isArray(value)) {
    if (parentKey && ID_ARRAY_KEY_SET.has(parentKey)) return value.map((item) => String(item))
    return value.map((item) => normalizeIds(item))
  }
  if (!value || typeof value !== 'object') return value
  const normalized: Record<string, unknown> = {}
  for (const [key, item] of Object.entries(value)) {
    if (ID_KEY_SET.has(key) && (typeof item === 'number' || typeof item === 'string')) {
      normalized[key] = String(item)
    } else {
      normalized[key] = normalizeIds(item, key)
    }
  }
  return normalized
}

/** Parse JSON without rounding 64-bit Snowflake IDs. */
export function parseLosslessJson<T = unknown>(source: string): T {
  let normalized = ''
  let inString = false
  let escaped = false

  for (let index = 0; index < source.length;) {
    const char = source[index]
    if (inString) {
      normalized += char
      if (escaped) escaped = false
      else if (char === '\\') escaped = true
      else if (char === '"') inString = false
      index += 1
      continue
    }
    if (char === '"') {
      inString = true
      normalized += char
      index += 1
      continue
    }
    if (char === '-' || (char >= '0' && char <= '9')) {
      let end = index + 1
      while (end < source.length && /[0-9eE+.-]/.test(source[end])) end += 1
      const token = source.slice(index, end)
      if (/^-?\d+$/.test(token) && !Number.isSafeInteger(Number(token))) {
        normalized += `"${token}"`
      } else {
        normalized += token
      }
      index = end
      continue
    }
    normalized += char
    index += 1
  }
  return normalizeIds(JSON.parse(normalized)) as T
}

/** Serialize string IDs back to JSON integers expected by the Go API. */
export function stringifyApiPayload(value: unknown) {
  let json = JSON.stringify(value)
  const keyPattern = ID_KEYS.join('|')
  json = json.replace(new RegExp(`"(${keyPattern})":"(-?\\d+)"`, 'g'), '"$1":$2')
  const arrayPattern = ID_ARRAY_KEYS.join('|')
  json = json.replace(
    new RegExp(`"(${arrayPattern})":\\[([^\\]]*)\\]`, 'g'),
    (_match, key: string, content: string) =>
      `"${key}":[${content.replace(/"(-?\d+)"/g, '$1')}]`,
  )
  return json
}
