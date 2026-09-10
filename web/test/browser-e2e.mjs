import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { chromium } from '@playwright/test'

const require = createRequire(import.meta.url)
const axePath = require.resolve('axe-core/axe.min.js')
const outputDir = process.env.INFINITECHAT_BROWSER_OUTPUT
const baseUrl = process.env.INFINITECHAT_WEB_BASE || 'http://127.0.0.1:5173'
const account = process.env.INFINITECHAT_TEST_ACCOUNT || 'compose@example.test'
const password = process.env.INFINITECHAT_TEST_PASSWORD || 'compose-password'
if (!outputDir) throw new Error('INFINITECHAT_BROWSER_OUTPUT is required')
await fs.mkdir(outputDir, { recursive: true })

const results = {
  viewports: [],
  pages: {},
  consoleErrors: [],
  networkErrors: [],
  journeys: {
    login: false,
    realtimeCanonical: false,
    aiStreaming: false,
    aiKnowledge: false,
    aiSummary: false,
    aiHistoryReload: false,
    mobileBackNavigation: false,
  },
}

function watchPage(page, label, websocketState) {
  page.on('console', (message) => {
    if (message.type() === 'error' || message.type() === 'warning') {
      results.consoleErrors.push(`${label}:${message.type()}:${message.text()}`)
    }
  })
  page.on('pageerror', (error) => results.consoleErrors.push(`${label}:pageerror:${error.message}`))
  page.on('requestfailed', (request) => {
    const detail = request.failure()?.errorText || 'unknown'
    if (!detail.includes('ERR_ABORTED')) results.networkErrors.push(`${label}:requestfailed:${request.url()}:${detail}`)
  })
  page.on('response', (response) => {
    if (response.status() >= 500) results.networkErrors.push(`${label}:http-${response.status()}:${response.url()}`)
  })
  page.on('websocket', (socket) => {
    socket.on('framereceived', (event) => {
      try {
        const payload = typeof event.payload === 'string' ? event.payload : event.payload.toString('utf8')
        const frame = JSON.parse(payload)
        if (frame.type === 'message.ack') websocketState.ack = true
        if (frame.type === 'message.delivery') websocketState.delivery = true
      } catch {
        // Ping/pong or binary frames are not journey evidence.
      }
    })
  })
}

async function login(page) {
  await page.goto(`${baseUrl}/auth`, { waitUntil: 'networkidle' })
  await page.getByPlaceholder('Phone number or email').fill(account)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Enter InfiniteChat' }).click()
  await page.waitForURL(/\/app\/chat/, { timeout: 20_000 })
  await page.getByText('Recent chats').waitFor({ timeout: 20_000 })
}

async function axe(page) {
  await page.addScriptTag({ path: axePath })
  const violations = await page.evaluate(async () => {
    const report = await globalThis.axe.run(document, { resultTypes: ['violations'] })
    return report.violations
      .filter((violation) => violation.impact === 'critical' || violation.impact === 'serious')
      .map(({ id, impact, description, help, nodes }) => ({ id, impact, description, help, nodes: nodes.length }))
  })
  return violations
}

async function runDesktopJourneys(page, websocketState) {
  results.journeys.login = true

  await page.getByRole('button', { name: /Compose Peer/ }).first().click()
  const realtimeText = `browser-realtime-${Date.now()}`
  const realtimeInput = page.getByLabel('Message input')
  await realtimeInput.waitFor({ timeout: 15_000 })
  await realtimeInput.fill(realtimeText)
  await realtimeInput.press('Enter')
  await page.getByRole('button', { name: realtimeText, exact: true }).waitFor({ timeout: 15_000 })
  for (let index = 0; index < 100 && !websocketState.ack; index += 1) {
    await page.waitForTimeout(100)
  }
  results.journeys.realtimeCanonical = websocketState.ack

  await page.getByRole('button', { name: /Infinite AI/ }).first().click()
  await page.getByRole('heading', { name: 'Infinite AI' }).waitFor({ timeout: 15_000 })
  await page.getByRole('button', { name: 'Import knowledge' }).click()
  const knowledgeTitle = `Browser knowledge ${Date.now()}`
  await page.getByPlaceholder('e.g. Release process').fill(knowledgeTitle)
  await page.getByLabel('Knowledge content').fill('Browser acceptance knowledge: every release must pass all quality gates.')
  const ingestResponse = page.waitForResponse((response) => response.url().includes('/api/ai/knowledge') && response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Import', exact: true }).click()
  results.journeys.aiKnowledge = (await ingestResponse).ok()

  const aiText = `Please cite the release knowledge just imported ${Date.now()}`
  const aiInput = page.getByLabel('Message input')
  await aiInput.fill(aiText)
  const streamResponse = page.waitForResponse((response) => response.url().includes('/api/ai/chat/stream'))
  await aiInput.press('Enter')
  results.journeys.aiStreaming = (await streamResponse).ok()
  await page.getByRole('button', { name: aiText, exact: true }).waitFor({ timeout: 20_000 })
  await page.getByText(/every release must pass all quality gates/).last().waitFor({ timeout: 20_000 })

  const summaryResponse = page.waitForResponse((response) => response.url().includes('/api/ai/summary'))
  await page.getByRole('button', { name: 'Summarise chat' }).click()
  results.journeys.aiSummary = (await summaryResponse).ok()
  await page.waitForTimeout(300)

  await page.reload({ waitUntil: 'networkidle' })
  await page.getByRole('button', { name: aiText, exact: true }).waitFor({ timeout: 20_000 })
  results.journeys.aiHistoryReload = true
}

const browser = await chromium.launch({ headless: true })
try {
  const desktopContext = await browser.newContext({ viewport: { width: 1440, height: 900 }, reducedMotion: 'reduce' })
  const desktop = await desktopContext.newPage()
  const desktopWebsocket = { ack: false, delivery: false }
  watchPage(desktop, 'desktop', desktopWebsocket)
  await login(desktop)
  await runDesktopJourneys(desktop, desktopWebsocket)

  for (const pageName of ['contacts', 'groups', 'notifications', 'profile']) {
    await desktop.goto(`${baseUrl}/app/${pageName}`, { waitUntil: 'networkidle' })
    results.pages[pageName] = await axe(desktop)
  }
  await desktop.goto(`${baseUrl}/app/chat`, { waitUntil: 'networkidle' })
  results.viewports.push({ name: 'desktop', width: 1440, height: 900, violations: await axe(desktop) })
  await desktop.screenshot({ path: path.join(outputDir, 'desktop.png'), fullPage: true })
  await desktopContext.close()

  for (const viewport of [
    { name: 'tablet', width: 834, height: 1112 },
    { name: 'mobile', width: 390, height: 844 },
  ]) {
    const context = await browser.newContext({ viewport: { width: viewport.width, height: viewport.height }, reducedMotion: 'reduce' })
    const page = await context.newPage()
    const websocketState = { ack: false, delivery: false }
    watchPage(page, viewport.name, websocketState)
    await login(page)
    if (viewport.name === 'mobile') {
      await page.getByRole('button', { name: /Infinite AI/ }).first().click()
      await page.getByRole('button', { name: 'Back' }).click()
      await page.waitForURL(/\/app\/chat$/)
      results.journeys.mobileBackNavigation = true
    }
    results.viewports.push({ ...viewport, violations: await axe(page) })
    await page.screenshot({ path: path.join(outputDir, `${viewport.name}.png`), fullPage: true })
    await context.close()
  }
} finally {
  await browser.close()
  await fs.writeFile(path.join(outputDir, 'results.json'), `${JSON.stringify(results, null, 2)}\n`)
}

const failedJourneys = Object.entries(results.journeys).filter(([, passed]) => !passed).map(([name]) => name)
const seriousViolations = [
  ...results.viewports.flatMap((item) => item.violations),
  ...Object.values(results.pages).flatMap((items) => items),
]
if (failedJourneys.length || seriousViolations.length || results.consoleErrors.length || results.networkErrors.length) {
  throw new Error(JSON.stringify({ failedJourneys, seriousViolations, consoleErrors: results.consoleErrors, networkErrors: results.networkErrors }))
}
console.log('browser_function=PASS responsive=PASS axe_critical_serious=0 console_errors=0 network_errors=0 journeys=7')
