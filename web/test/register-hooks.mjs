// Loaded via `node --import`; registers the `@` alias resolver and the import.meta.env patch for tests.
import { register } from 'node:module'
import { pathToFileURL } from 'node:url'

globalThis.__VITE_ENV__ = { DEV: true, PROD: false, MODE: 'test', VITE_API_BASE: '', VITE_WS_BASE: '' }

register('./alias-hooks.mjs', pathToFileURL(import.meta.filename))
