// The example's claim, checked on the real page: hydration adopts the server's
// DOM instead of rebuilding it. reagami's own behaviour is covered by the
// library suite in test/. Reads the page on stdin. Run with: bb verify
import { JSDOM } from 'jsdom'

const html = await new Promise((resolve) => {
  let s = ''
  process.stdin.on('data', (d) => (s += d))
  process.stdin.on('end', () => resolve(s))
})

const dom = new JSDOM(html, { url: 'http://localhost:8080/' })
globalThis.window = dom.window
globalThis.document = dom.window.document
globalThis.Node = dom.window.Node
globalThis.Element = dom.window.Element
globalThis.MouseEvent = dom.window.MouseEvent
globalThis.history = dom.window.history
globalThis.fetch = async () => ({ status: 204 })
globalThis.EventSource = class {
  constructor (url) { this.url = url; this.listeners = {} }
  addEventListener (type, fn) { this.listeners[type] = fn }
}

const rows = document.querySelectorAll('.row').length
const firstRow = document.querySelector('.row')

await import('./dist/client.js')

const stats = document.querySelector('#stats').textContent
let failed = 0
const check = (label, ok) => {
  console.log(`${ok ? 'PASS' : 'FAIL'} ${label}`)
  if (!ok) failed++
}

check(`hydrated ${rows} server rows without building a node (${stats})`,
      stats.includes('created 0'))
check('the server\'s own row objects are still in the tree',
      firstRow === document.querySelector('.row'))

process.exit(failed === 0 ? 0 : 1)
