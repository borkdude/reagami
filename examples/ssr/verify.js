// Hydrates the server's HTML in jsdom and checks that reagami adopted it, then
// drives the virtual scroller. Reads the page on stdin. Run with: bb verify
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

// jsdom has neither, so these stand in for POST /action and the /state stream.
// the README's curl commands exercise both against the real server.
const posts = []
globalThis.fetch = async (url, opts) => {
  posts.push({ url, body: JSON.parse(opts.body) })
  return { status: 204 }
}
let stream = null
globalThis.EventSource = class {
  constructor (url) { this.url = url; stream = this }
}

const sid = document.body.dataset.sid
const state = JSON.parse(document.querySelector('#state').textContent)
const serverRow = document.querySelector('.row')
const rows = () => document.querySelectorAll('.row').length
const stats = () => document.querySelector('#stats').textContent
const spinners = () => document.querySelectorAll('.spinner').length

await import('./dist/client.js')

let failed = 0
const check = (label, ok) => {
  console.log(`${ok ? 'PASS' : 'FAIL'} ${label}`)
  if (!ok) failed++
}

check(`hydrated the first window without building nodes (${stats()})`,
      stats().includes('created 0'))
check('server row object survived', serverRow === document.querySelector('.row'))
check(`only the window is in the client, not ${state.total} rows`,
      rows() === state.rows.length && rows() < 100)
check('subscribed to its own state stream', stream?.url === `/state/${sid}`)

// jsdom does no layout, so give the scroller a viewport and scroll it by hand
const scroller = document.querySelector('#scroller')
Object.defineProperty(scroller, 'clientHeight', { value: 480, configurable: true })
scroller.scrollTop = 24000 // row 1000 at 24px each
scroller.dispatchEvent(new dom.window.Event('scroll'))

const asked = posts.at(-1)?.body.action
check(`scrolling asked for the window around row 1000 (${asked?.from}..${asked?.to})`,
      asked?.type === 'window' && asked.from > 950 && asked.to < 1050)
check('a spinner shows while that window loads', spinners() > 0)

const from = asked.from
const next = { total: state.total, from, rows: [] }
for (let i = from; i < asked.to; i++) next.rows.push({ id: i, name: `row ${i}`, qty: 1, status: 'new' })
stream.onmessage({ data: JSON.stringify(next) })

check('the pushed window replaced the old rows', rows() === next.rows.length)
check('spinners cleared once the window arrived', spinners() === 0)
check('rows are positioned at their true offset',
      document.querySelector('.row').style.top === `${from * 24}px`)

process.exit(failed === 0 ? 0 : 1)
