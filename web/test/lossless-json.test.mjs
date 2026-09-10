import assert from 'node:assert/strict'
import test from 'node:test'
import { parseLosslessJson, stringifyApiPayload } from '../src/utils/losslessJson.ts'

const snowflake = '2085927479381463041'

test('keeps unsafe JSON integers exact while preserving normal values', () => {
  const parsed = parseLosslessJson(
    `{"userId":${snowflake},"count":3,"amount":1.25,"content":"id ${snowflake}"}`,
  )

  assert.equal(parsed.userId, snowflake)
  assert.equal(parsed.count, 3)
  assert.equal(parsed.amount, 1.25)
  assert.equal(parsed.content, `id ${snowflake}`)
})

test('serializes ID strings as Go-compatible JSON integers only in ID fields', () => {
  const serialized = stringifyApiPayload({
    userId: snowflake,
    sessionId: snowflake,
    memberIds: [snowflake, '123'],
    account: '13900000008',
    refreshToken: snowflake,
    body: { content: snowflake },
  })

  assert.match(serialized, new RegExp(`"userId":${snowflake}`))
  assert.match(serialized, new RegExp(`"sessionId":${snowflake}`))
  assert.match(serialized, new RegExp(`"memberIds":\\[${snowflake},123\\]`))
  assert.match(serialized, /"account":"13900000008"/)
  assert.match(serialized, new RegExp(`"refreshToken":"${snowflake}"`))
  assert.match(serialized, new RegExp(`"content":"${snowflake}"`))
})

test('round-trips a WebSocket message without changing IDs', () => {
  const serialized = stringifyApiPayload({
    sessionId: snowflake,
    senderId: snowflake,
    receiverId: '123',
    type: 0,
    body: { content: 'Integration test message' },
  })
  const parsed = parseLosslessJson(serialized)

  assert.equal(parsed.sessionId, snowflake)
  assert.equal(parsed.senderId, snowflake)
  assert.equal(parsed.receiverId, '123')
})
