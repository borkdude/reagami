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
const ghosts = () => document.querySelectorAll('.ghost').length
const spinner = () => document.querySelector('.spinner')

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

// scrolled past everything the client holds, so the whole viewport must be
// placeholders rather than blank canvas
const placeholders = [...document.querySelectorAll(".ghost")]
const tops = placeholders.map((e) => parseInt(e.style.top, 10)).sort((a, b) => a - b)
check(`every visible row is a placeholder, not blank (${placeholders.length} of ${asked.to - asked.from})`,
      placeholders.length === asked.to - asked.from)
check(`placeholders cover the viewport (${tops[0]}px..${tops.at(-1)}px)`,
      tops[0] === asked.from * 24 && tops.at(-1) === (asked.to - 1) * 24)
check('a spinner is showing while they load', spinner() !== null)
check('no loaded rows are left stranded on screen', rows() === 0 || rowsOffScreen(tops))

function rowsOffScreen () {
  return [...document.querySelectorAll('.row')]
    .every((e) => parseInt(e.style.top, 10) < asked.from * 24)
}

const from = asked.from
const next = { total: state.total, from, rows: [] }
for (let i = from; i < asked.to; i++) next.rows.push({ id: i, name: `row ${i}`, qty: 1, status: 'new' })
stream.onmessage({ data: JSON.stringify(next) })

check('the pushed window replaced the old rows', rows() === next.rows.length)
check('placeholders cleared once the window arrived', ghosts() === 0)
check('spinner is gone too', spinner() === null)
check('rows are positioned at their true offset',
      document.querySelector('.row').style.top === `${from * 24}px`)

// a slow server reorders responses: a window asked for earlier can land later
const stale = { total: state.total, from: 500, rows: [{ id: 500, name: 'stale', qty: 1, status: 'new' }] }
stream.onmessage({ data: JSON.stringify(stale) })
check('a window we no longer want is ignored',
      document.querySelector('.row').style.top === `${from * 24}px` &&
      !document.body.textContent.includes('stale'))

// editing: click a cell, type, commit, and the change round-trips as an action
stream.onmessage({ data: JSON.stringify({ total: state.total, from, rows: next.rows }) })
const nameCell = document.querySelector('.row .cell-name')
check('a cell starts as text, not an input', nameCell.tagName === 'SPAN')

nameCell.dispatchEvent(new dom.window.MouseEvent('click', { bubbles: true }))
const input = document.querySelector('.row input.cell-name')
check('clicking a cell turns it into an input', input !== null)
check('the input starts at the current value', input?.getAttribute('value') === next.rows[0].name)

input.value = 'edited by hand'
input.dispatchEvent(new dom.window.Event('blur', { bubbles: true }))
await new Promise((r) => setTimeout(r, 0))
const edit = posts.at(-1)?.body.action
check(`committing posted an edit action (${edit?.field} = ${edit?.value})`,
      edit?.type === 'edit' && edit.id === next.rows[0].id &&
      edit.field === 'name' && edit.value === 'edited by hand')
check('the cell is text again while the server answers',
      document.querySelector('.row .cell-name').tagName === 'SPAN')
check('the edit shows immediately, before the server pushes anything',
      document.querySelector('.row .cell-name').textContent === 'edited by hand')

// the server's version is authoritative and replaces the optimistic one
const confirmed = JSON.parse(JSON.stringify(next))
confirmed.rows[0].name = 'edited by hand (from server)'
stream.onmessage({ data: JSON.stringify(confirmed) })
check('the server push overwrites the optimistic value',
      document.querySelector('.row .cell-name').textContent === 'edited by hand (from server)')

process.exit(failed === 0 ? 0 : 1)
