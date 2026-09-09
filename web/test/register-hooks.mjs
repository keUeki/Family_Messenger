// 由 `node --import` 加载，为测试注册 `@` 别名解析与 import.meta.env 补丁。
import { register } from 'node:module'
import { pathToFileURL } from 'node:url'

globalThis.__VITE_ENV__ = { DEV: true, PROD: false, MODE: 'test', VITE_API_BASE: '', VITE_WS_BASE: '' }

register('./alias-hooks.mjs', pathToFileURL(import.meta.filename))
