import assert from 'node:assert/strict'
import test from 'node:test'
import { parseDate } from '../src/utils/index.ts'

test('parses canonical UTC timestamps and legacy local timestamps', () => {
  const canonical = parseDate('2026-08-28T10:51:30.844Z')
  assert.equal(canonical?.toISOString(), '2026-08-28T10:51:30.844Z')

  const legacy = parseDate('2026-08-28 18:51:30')
  assert.ok(legacy)
  assert.equal(Number.isNaN(legacy.getTime()), false)
})
