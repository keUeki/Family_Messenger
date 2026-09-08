import assert from 'node:assert/strict'
import test from 'node:test'
import {
  deliveryToMessage,
  makeMessageCommand,
  makeWsFrame,
  parseWsPayload,
} from '../src/utils/wsProtocol.ts'

const snowflake = '9223372036854775001'

test('builds the canonical v1 resume and message command frames', () => {
  assert.deepEqual(makeWsFrame('resume', 'resume-1', { afterSequence: snowflake, limit: 500 }), {
    version: 1,
    type: 'resume',
    correlationId: 'resume-1',
    payload: { afterSequence: snowflake, limit: 500 },
  })

  const command = makeMessageCommand({
    sessionId: snowflake,
    receiverId: '123',
    senderId: '9223372036854775002',
    type: 0,
    sessionType: 0,
    body: { content: 'canonical' },
    clientMessageId: 'client-1',
  })
  assert.equal(command.type, 'message.command')
  assert.equal(command.payload.messageType, 0)
  assert.equal(command.payload.clientMessageId, 'client-1')
  const encoded = JSON.stringify(command)
  assert.match(encoded, new RegExp(`"sessionId":"${snowflake}"`))
  assert.doesNotMatch(encoded, /"type":0/)
})

test('parses exact ACK IDs and maps canonical delivery metadata', () => {
  const ack = parseWsPayload(
    `{"version":1,"type":"message.ack","correlationId":"client-1","payload":{"clientMessageId":"client-1","messageId":${snowflake},"status":"accepted","duplicate":false,"acceptedAt":"2026-08-28T10:00:00.000Z"}}`,
  )
  assert.equal(ack.payload.messageId, snowflake)

  const message = deliveryToMessage({
    messageId: snowflake,
    sequence: '9223372036854775000',
    sessionId: '8001',
    senderId: '123',
    messageType: 0,
    sessionType: 2,
    body: { content: 'hello' },
    createdAt: '2026-08-28T10:00:00.000Z',
    nickname: 'Infinite AI',
    avatar: '/ai.png',
    role: 3,
  })
  assert.equal(message.messageId, snowflake)
  assert.equal(message.nickname, 'Infinite AI')
  assert.equal(message.type, 0)
})
