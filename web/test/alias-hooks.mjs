// Node 里没有 Vite 的 `@` 别名和 `import.meta.env`，
// 这两个钩子把它们补齐，让测试可以直接 import src 下的 TypeScript 源码。
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const srcDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'src')
const EXTENSIONS = ['', '.ts', '.tsx', '.mts', '.js', '/index.ts', '/index.tsx']

export function resolve(specifier, context, nextResolve) {
  if (!specifier.startsWith('@/')) return nextResolve(specifier, context)

  const base = path.join(srcDir, specifier.slice(2))
  for (const extension of EXTENSIONS) {
    const candidate = `${base}${extension}`
    if (existsSync(candidate)) {
      return nextResolve(pathToFileURL(candidate).href, context)
    }
  }
  return nextResolve(specifier, context)
}

export async function load(url, context, nextLoad) {
  const result = await nextLoad(url, context)
  if (!result.source || !url.includes('/src/')) return result
  const source = result.source.toString()
  if (!source.includes('import.meta.env')) return result
  return {
    ...result,
    source: source.replaceAll('import.meta.env', '(globalThis.__VITE_ENV__ ?? {})'),
  }
}
