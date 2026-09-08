import assert from 'node:assert/strict'
import test from 'node:test'
import { highestSequence, mapOfflineMessages } from '../src/utils/offlineMessages.ts'

test('maps server-owned offline IDs and sequence cursors without fabrication', () => {
  const source = [{
    messageId: '9007199254741999',
    sequence: '9007199254742001',
    sessionId: '8001',
    senderId: '7001',
    type: 0,
    body: { content: 'stored' },
    createdTime: '2026-08-28T10:00:00.000Z',
    name: 'Alice',
    avatar: '/alice.png',
  }]
  const [mapped] = mapOfflineMessages(source, 1)
  assert.equal(mapped.messageId, source[0].messageId)
  assert.equal(mapped.sequence, source[0].sequence)
  assert.equal(mapped.sessionType, 1)
  assert.equal(highestSequence('9007199254742000', [mapped]), '9007199254742001')
})
