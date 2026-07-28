// What does pushing the whole state on every change cost as the table grows?
// Hydrates the page, then feeds the client one state push per action shape and
// reports payload size and what reagami had to build. Run with: bb measure
import { JSDOM } from 'jsdom'

const html = await new Promise((resolve) => {
  let s = ''
  process.stdin.on('data', (d) => (s += d))
  process.stdin.on('end', () => resolve(s))
})

const dom = new JSDOM(html)
globalThis.window = dom.window
globalThis.document = dom.window.document
globalThis.Node = dom.window.Node
globalThis.Element = dom.window.Element
globalThis.MouseEvent = dom.window.MouseEvent
globalThis.fetch = async () => ({ status: 204 })
let stream = null
globalThis.EventSource = class {
  constructor (url) { this.url = url; stream = this }
}

const state = JSON.parse(document.querySelector('#state').textContent)
const stats = () => document.querySelector('#stats').textContent
const parse = (s) => {
  const m = s.match(/created (\d+), adopted (\d+) \| render (\d+)/)
  return { created: +m[1], adopted: +m[2], ms: +m[3] }
}

const t0 = Date.now()
await import('./dist/client.js')
const hydrate = parse(stats())

const push = (next) => {
  const data = JSON.stringify(next)
  const t = Date.now()
  stream.onmessage({ data })
  const s = parse(stats())
  return { bytes: data.length, created: s.created, ms: Date.now() - t }
}

const clone = () => JSON.parse(JSON.stringify(state))
const bump = clone(); bump.rows[0].qty += 1
const added = clone(); added.rows.push({ id: 99999, name: 'row 99999', qty: 1, status: 'new' })
const removed = clone(); removed.rows.splice(0, 1)
const sorted = clone(); sorted.rows.reverse()

const rows = state.rows.length
const fmt = (n, r) => `${String(n).padEnd(9)} bytes=${String(r.bytes).padStart(7)}  built=${String(r.created).padStart(5)}  ${String(r.ms).padStart(4)}ms`
console.log(`rows=${rows}`)
console.log(`  ${'hydrate'.padEnd(9)} bytes=${String(document.querySelector('#state').textContent.length).padStart(7)}  built=${String(hydrate.created).padStart(5)}  ${String(Date.now() - t0).padStart(4)}ms  (adopted ${hydrate.adopted})`)
for (const [name, next] of [['bump', bump], ['add', added], ['delete', removed], ['reorder', sorted]])
  console.log(`  ${fmt(name, push(next))}`)
