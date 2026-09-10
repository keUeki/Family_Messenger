import assert from 'node:assert/strict'
import { parseLosslessJson, stringifyApiPayload } from '../src/utils/losslessJson.ts'

const baseUrl = process.env.INFINITECHAT_API_BASE || 'http://127.0.0.1:10010'
const account = process.env.INFINITECHAT_TEST_ACCOUNT
const password = process.env.INFINITECHAT_TEST_PASSWORD

assert.ok(account, 'INFINITECHAT_TEST_ACCOUNT is required')
assert.ok(password, 'INFINITECHAT_TEST_PASSWORD is required')

async function rawRequest(path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, options)
  const raw = await response.text()
  assert.ok(response.ok, `${path} returned HTTP ${response.status}: ${raw}`)
  return { raw, body: parseLosslessJson(raw) }
}

async function apiRequest(path, options = {}) {
  const result = await rawRequest(path, options)
  assert.equal(result.body.code, 200, `${path} failed: ${result.body.message}`)
  return result.body.data
}

const login = await apiRequest('/api/user/loginPassword', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ account, password }),
})

assert.match(login.userId, /^\d+$/)
assert.ok(login.token)
assert.ok(login.refreshToken)
assert.match(login.wsServerUri, /^wss?:\/\//)

const authorization = { Authorization: `Bearer ${login.token}` }
const profile = await apiRequest(`/api/user/getUserInfo?userId=${login.userId}`, {
  headers: authorization,
})
const friends = await apiRequest(`/api/contact/${login.userId}/friend?pageNum=1&pageSize=50`, {
  headers: authorization,
})
const groups = await apiRequest(`/api/group/user/${login.userId}?pageNum=1&pageSize=50`, {
  headers: authorization,
})
const sessions = await apiRequest(`/api/offline/data?userId=${login.userId}`, {
  headers: authorization,
})
const notifications = await apiRequest(
  `/api/offline/notification/${login.userId}/unread?pageNum=1&pageSize=20`,
  { headers: authorization },
)
const unread = await apiRequest(`/api/offline/notification/${login.userId}/unread/count`, {
  headers: authorization,
})
const balance = await apiRequest(`/api/user/balance/${login.userId}`, {
  headers: authorization,
})
const upload = await apiRequest(`/api/user/uploadUrl?fileName=integration-smoke.png`, {
  headers: authorization,
})
const wsServerUri = await apiRequest(`/api/user/refresh/uri?userId=${login.userId}`, {
  headers: authorization,
})

assert.equal(profile.userId, login.userId)
assert.ok(Array.isArray(friends.list))
assert.ok(Array.isArray(groups.list))
assert.ok(Array.isArray(sessions))
assert.ok(Array.isArray(notifications.list))
assert.equal(typeof unread.count, 'number')
assert.ok('balance' in balance)
assert.match(upload.uploadUrl, /^https?:\/\//)
assert.match(upload.downloadUrl, /^https?:\/\//)
assert.match(wsServerUri, /^wss?:\/\//)

const pixel = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  'base64',
)
const uploadResponse = await fetch(upload.uploadUrl, {
  method: 'PUT',
  headers: { 'Content-Type': 'image/png' },
  body: pixel,
})
assert.ok(uploadResponse.ok, `object upload failed with HTTP ${uploadResponse.status}`)
const downloadResponse = await fetch(upload.downloadUrl)
assert.ok(downloadResponse.ok, `object download failed with HTTP ${downloadResponse.status}`)
assert.ok((await downloadResponse.arrayBuffer()).byteLength > 0)

const refreshedToken = await apiRequest('/api/user/auth/refresh', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ refreshToken: login.refreshToken }),
})
assert.equal(typeof refreshedToken, 'string')
assert.ok(refreshedToken.length > 20)

const peer = friends.list.find((friend) => friend.userId !== login.userId && friend.sessionId)
assert.ok(peer?.sessionId, 'test user should have a realtime peer conversation')

const clientMessageId = `integration_${Date.now()}`
const socketResult = await new Promise((resolve, reject) => {
  const socket = new WebSocket(wsServerUri, ['bearer', login.token])
  let sentMessage = false
  let acknowledgement = false
  let delivery = false
  let exactMessageId = false
  const timeout = setTimeout(() => {
    socket.close()
    reject(new Error('canonical WebSocket journey timed out'))
  }, 12000)

  socket.addEventListener('open', () => {
    socket.send(JSON.stringify({
      version: 1,
      type: 'resume',
      correlationId: 'integration-resume',
      payload: { afterSequence: '0', limit: 100 },
    }))
  })
  socket.addEventListener('message', (event) => {
    const raw = String(event.data)
    const message = parseLosslessJson(raw)
    if (message.version === 1 && message.type === 'resume' && !sentMessage) {
      sentMessage = true
      socket.send(
        JSON.stringify({
          version: 1,
          type: 'message.command',
          correlationId: clientMessageId,
          payload: {
            sessionId: peer.sessionId,
            receiverId: peer.userId,
            senderId: login.userId,
            messageType: 0,
            sessionType: 0,
            body: { content: 'InfiniteChat canonical integration smoke message' },
            clientMessageId,
          },
        }),
      )
      return
    }
    if (message.version === 1 && message.type === 'error') {
      clearTimeout(timeout)
      socket.close()
      reject(new Error(message.payload?.message || 'WebSocket message failed'))
      return
    }
    if (message.version === 1 && message.type === 'message.ack' && message.correlationId === clientMessageId) {
      acknowledgement = message.payload?.status === 'accepted'
      exactMessageId = /^\d+$/.test(message.payload?.messageId || '')
    }
    if (message.version === 1 && message.type === 'message.delivery' && message.payload?.clientMessageId === clientMessageId) {
      delivery = /^\d+$/.test(message.payload?.sequence || '')
      socket.send(JSON.stringify({
        version: 1,
        type: 'message.read',
        correlationId: `read-${message.payload.messageId}`,
        payload: { messageId: message.payload.messageId },
      }))
    }
    if (acknowledgement && delivery) {
      clearTimeout(timeout)
      socket.close()
      resolve({ resume: true, acknowledgement, delivery, readSent: true, exactMessageId })
    }
  })
  socket.addEventListener('error', () => {
    clearTimeout(timeout)
    reject(new Error('WebSocket connection failed'))
  })
})

await apiRequest('/api/user/logout', {
  method: 'POST',
  headers: { ...authorization, 'Content-Type': 'application/json' },
  body: stringifyApiPayload({ userId: login.userId }),
})

console.log(
  JSON.stringify({
    login: true,
    exactSnowflakeIds: profile.userId === login.userId,
    friends: friends.list.length,
    groups: groups.list.length,
    sessions: sessions.length,
    notifications: notifications.list.length,
    unreadCount: unread.count,
    balance: Boolean(balance),
    objectUploadAndDownload: true,
    tokenRefresh: true,
    websocket: socketResult,
    logout: true,
  }),
)
