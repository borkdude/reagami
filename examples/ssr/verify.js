// Hydrates the server's HTML in jsdom and checks that reagami adopted it, then
// drives the virtual scroller. Reads the page on stdin. Run with: bb verify
import { JSDOM } from 'jsdom'

const html = await new Promise((resolve) => {
  let s = ''
  process.stdin.on('data', (d) => (s += d))
  process.stdin.on('end', () => resolve(s))
})

// a real URL, or pushState is illegal from an about:blank origin
const dom = new JSDOM(html, { url: 'http://localhost:8080/' })
globalThis.window = dom.window
globalThis.document = dom.window.document
globalThis.Node = dom.window.Node
globalThis.Element = dom.window.Element
globalThis.MouseEvent = dom.window.MouseEvent
globalThis.history = dom.window.history

// jsdom has neither, so these stand in for POST /action and the /state stream.
// the README's curl commands exercise both against the real server.
const posts = []
globalThis.fetch = async (url, opts) => {
  posts.push({ url, body: JSON.parse(opts.body) })
  return { status: 204 }
}
let stream = null
globalThis.EventSource = class {
  constructor (url) { this.url = url; this.listeners = {}; stream = this }
  addEventListener (type, fn) { this.listeners[type] = fn }
  emit (type, data) { this.listeners[type]({ data: JSON.stringify(data) }) }
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

// without the proxy there is nothing to report, so the panel says so
check(`the panel admits it is uncompressed (${stats()})`, stats().includes('uncompressed'))
// the proxy reports each push's real cost in band, after the push
stream.emit('wire', { raw: 7080, br: 975 })
check(`the panel shows what brotli saved (${stats().split('|')[1].trim()})`,
      stats().includes('975 B on the wire') && stats().includes('7x smaller'))

// the latency slider is server state: it round-trips like any other action
const slider = document.querySelector('#latency')
check(`the slider starts at the server's delay (${slider?.getAttribute('value')} ms)`,
      slider !== null && slider.getAttribute('value') === String(state.latency))
const beforeSlider = posts.length
slider.value = '25'
slider.dispatchEvent(new dom.window.Event('input', { bubbles: true }))
check(`dragging moves the label at once (${document.querySelector('#latency-value').textContent.trim()})`,
      document.querySelector('#latency-value').textContent.trim() === '25 ms')
check('dragging alone sends nothing', posts.length === beforeSlider)
slider.dispatchEvent(new dom.window.Event('change', { bubbles: true }))
const lat = posts.at(-1)?.body.action
check(`releasing sends one latency action (${lat?.ms} ms)`,
      posts.length === beforeSlider + 1 && lat?.type === 'latency' && lat.ms === 25)

// jsdom does no layout, so give the scroller a viewport and scroll it by hand.
// the canvas is capped, so scrollTop is scaled rather than row * 24
const MAX_CANVAS = 15000000
const scale = (state.total * 24) / Math.min(state.total * 24, MAX_CANVAS)
const topFor = (row) => (row * 24) / scale
const rowAt = (px) => Math.floor((px * scale) / 24)
const scroller = document.querySelector('#scroller')
Object.defineProperty(scroller, 'clientHeight', { value: 480, configurable: true })
scroller.scrollTop = topFor(1000)
scroller.dispatchEvent(new dom.window.Event('scroll'))

check(`the canvas is capped below what browsers allow (${document.querySelector('#canvas').style.height})`,
      parseInt(document.querySelector('#canvas').style.height, 10) === Math.min(state.total * 24, MAX_CANVAS))
// the bottom of the scrollbar must still reach the last row
scroller.scrollTop = Math.min(state.total * 24, MAX_CANVAS) - 480
scroller.dispatchEvent(new dom.window.Event('scroll'))
const atEnd = posts.at(-1).body.action
check(`dragging to the bottom reaches the last rows (${atEnd.from}..${atEnd.to} of ${state.total})`,
      atEnd.to >= state.total - 1)
scroller.scrollTop = topFor(1000)
scroller.dispatchEvent(new dom.window.Event('scroll'))

const asked = posts.at(-1)?.body.action
// the fetch is deliberately wider than the viewport, so a small scroll stays
// inside what the client already holds
const seenFrom = rowAt(scroller.scrollTop)
const seenTo = seenFrom + 20
const base = scroller.scrollTop - ((scroller.scrollTop * scale) % 24)
const topOf = (row) => base + (row - seenFrom) * 24
check(`scrolling asked for more than the viewport shows (${asked?.from}..${asked?.to})`,
      asked?.type === 'window' && asked.from < seenFrom && asked.to > seenTo)

// scrolled past everything the client holds, so the whole viewport must be
// placeholders rather than blank canvas, and only the viewport
const placeholders = [...document.querySelectorAll(".ghost")]
const tops = placeholders.map((e) => parseInt(e.style.top, 10)).sort((a, b) => a - b)
check(`every visible row is a placeholder, and no more (${placeholders.length} of ${seenTo - seenFrom})`,
      placeholders.length === seenTo - seenFrom)
check(`placeholders cover the viewport (${tops[0]}px..${tops.at(-1)}px)`,
      tops[0] === topOf(seenFrom) && tops.at(-1) === topOf(seenTo - 1))
check('a spinner is showing while they load', spinner() !== null)
check('no loaded rows are left stranded on screen', rows() === 0 || rowsOffScreen(tops))

function rowsOffScreen () {
  return [...document.querySelectorAll('.row')]
    .every((e) => parseInt(e.style.top, 10) < topOf(seenFrom))
}

const from = asked.from
const next = { total: state.total, from, rows: [] }
for (let i = from; i < asked.to; i++) next.rows.push({ id: i, name: `row ${i}`, qty: 1, status: 'new' })
stream.onmessage({ data: JSON.stringify(next) })

check('the pushed window replaced the old rows', rows() === next.rows.length)
check(`parse time is reported once a push has been parsed (${stats().split('|')[2].trim()})`,
      /parse \d+ us/.test(stats()))

check('placeholders cleared once the window arrived', ghosts() === 0)
check('spinner is gone too', spinner() === null)
check(`rows are positioned relative to the viewport (${document.querySelector('.row').style.top})`,
      document.querySelector('.row').style.top === `${topOf(from)}px`)

// a slow server reorders responses: a window asked for earlier can land later
const stale = { total: state.total, from: 500, rows: [{ id: 500, name: 'stale', qty: 1, status: 'new' }] }
stream.onmessage({ data: JSON.stringify(stale) })
check('a window we no longer want is ignored',
      document.querySelector('.row').style.top === `${topOf(from)}px` &&
      !document.body.textContent.includes('stale'))

// the flicker case: small scrolls inside the fetched margin must not show a
// single placeholder, or the table blinks on every wheel notch
stream.onmessage({ data: JSON.stringify({ total: state.total, from, rows: next.rows }) })
let blinked = 0
for (const px of [24, 48, 96, 168]) {
  scroller.scrollTop = topFor(from) + px
  scroller.dispatchEvent(new dom.window.Event('scroll'))
  blinked += ghosts()
}
check(`small scrolls inside the loaded window never blink (${blinked} placeholders)`, blinked === 0)

// editing: click a cell, type, commit, and the change round-trips as an action.
// answer whatever window was asked for last, so the push is not treated as stale
const cur = posts.at(-1).body.action
const win = { total: state.total, from: cur.from, rows: [] }
for (let i = cur.from; i < cur.to; i++) win.rows.push({ id: i, name: `row ${i}`, qty: 1, status: 'new' })
stream.onmessage({ data: JSON.stringify(win) })
const nameCell = document.querySelector('.row .cell-name')
check('a cell starts as text, not an input', nameCell.tagName === 'SPAN')

nameCell.dispatchEvent(new dom.window.MouseEvent('click', { bubbles: true }))
const input = document.querySelector('.row input.cell-name')
check('clicking a cell turns it into an input', input !== null)
check('the input starts at the current value', input?.getAttribute('value') === win.rows[0].name)

// enter commits, the re-render removes the input, and removing a focused input
// fires blur. that must not commit a second time in the middle of the render.
// the observer catches any frame that draws the pre-edit value on the way.
// oldValue is captured at mutation time, unlike re-reading the DOM in the
// callback, which only ever shows the final state
const overwritten = []
const observer = new dom.window.MutationObserver((records) => {
  for (const r of records) if (r.type === 'characterData') overwritten.push(r.oldValue)
})
observer.observe(document.querySelector('#canvas'),
                 { subtree: true, childList: true, characterData: true, characterDataOldValue: true })

const before = posts.length
input.value = 'edited by hand'
input.dispatchEvent(new dom.window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
input.dispatchEvent(new dom.window.Event('blur', { bubbles: true }))
await new Promise((r) => setTimeout(r, 0))
check(`enter then blur commits once, not twice (${posts.length - before} action)`,
      posts.length - before === 1)
const edit = posts.at(-1)?.body.action
check(`committing posted an edit action (${edit?.field} = ${edit?.value})`,
      edit?.type === 'edit' && edit.id === win.rows[0].id &&
      edit.field === 'name' && edit.value === 'edited by hand')
check('the cell is text again while the server answers',
      document.querySelector('.row .cell-name').tagName === 'SPAN')
check('the edit shows immediately, before the server pushes anything',
      document.querySelector('.row .cell-name').textContent === 'edited by hand')
observer.disconnect()
check(`the pre-edit value is never drawn on the way (${overwritten.length} text nodes rewritten)`,
      !overwritten.includes(win.rows[0].name))

// a row edited in an earlier session arrives already changed. editing a
// different row must not regenerate it back to its original value.
const withPrior = JSON.parse(JSON.stringify(win))
withPrior.rows[3].name = 'EDITED EARLIER'
stream.onmessage({ data: JSON.stringify(withPrior) })
const other = document.querySelectorAll('.row')[1].querySelector('.cell-name')
other.dispatchEvent(new dom.window.MouseEvent('click', { bubbles: true }))
const i3 = document.querySelector('.row input.cell-name')
i3.value = 'a different row'
i3.dispatchEvent(new dom.window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
const priorCell = [...document.querySelectorAll('.row')]
  .find((r) => r.querySelector('.cell-name').textContent === 'EDITED EARLIER')
  .querySelector('.cell-name')
check(`editing one row keeps another row's saved value (${priorCell.textContent})`,
      priorCell.textContent === 'EDITED EARLIER')

// the server's version is authoritative and replaces the optimistic one
const confirmed = JSON.parse(JSON.stringify(win))
confirmed.rows[0].name = 'edited by hand (from server)'
stream.onmessage({ data: JSON.stringify(confirmed) })
check('the server push overwrites the optimistic value',
      document.querySelector('.row .cell-name').textContent === 'edited by hand (from server)')

// NOTE: the re-entrancy this guards against cannot be reproduced here. it needs
// blur to fire when a focused node is removed, which browsers do and jsdom does
// not. see doc/dev/adr/0003-render-re-entrancy.md

// the detail page: opening it is an action, and the URL follows the state
const openLink = document.querySelector('.row a.open')
check('every row links to its own page', openLink?.getAttribute('href')?.startsWith('/row/'))
openLink.dispatchEvent(new dom.window.MouseEvent('click', { bubbles: true, cancelable: true }))
const opened = posts.at(-1)?.body.action
check(`clicking it sends an open action (id ${opened?.id})`, opened?.type === 'open')
check('nothing changed until the server pushed', document.querySelector('#detail') === null)

const detail = JSON.parse(JSON.stringify(win))
detail.page = 'row'
detail.row = { ...win.rows[0], name: 'detail row' }
stream.onmessage({ data: JSON.stringify(detail) })
check('the push swapped the whole page', document.querySelector('#detail') !== null)
check('the table is gone', document.querySelector('#scroller') === null)
check(`the URL followed the state (${dom.window.location.pathname})`,
      dom.window.location.pathname === `/row/${detail.row.id}`)
check(`every column is shown (${document.querySelectorAll('#detail .field').length})`,
      document.querySelectorAll('#detail .field').length === 9)

// editing works on this page too, through the same cell component
const dCell = document.querySelector('#detail .cell-owner')
dCell.dispatchEvent(new dom.window.MouseEvent('click', { bubbles: true }))
const dInput = document.querySelector('#detail input.cell-owner')
dInput.value = 'changed here'
dInput.dispatchEvent(new dom.window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
check(`editing on the detail page works (${document.querySelector('#detail .cell-owner').textContent})`,
      document.querySelector('#detail .cell-owner').textContent === 'changed here')
check('and it posted an edit', posts.at(-1)?.body.action.type === 'edit')

// going back
document.querySelector('#back').dispatchEvent(new dom.window.MouseEvent('click', { bubbles: true, cancelable: true }))
check('back sends a close action', posts.at(-1)?.body.action.type === 'close')
const back = JSON.parse(JSON.stringify(win))
stream.onmessage({ data: JSON.stringify(back) })
check('the table came back', document.querySelector('#scroller') !== null)
check(`the URL followed again (${dom.window.location.pathname})`,
      dom.window.location.pathname === '/')

await new Promise((r) => setTimeout(r, 0))
const back2 = document.querySelector('#scroller')
check(`the table came back at the row we opened (scrollTop ${Math.round(back2.scrollTop)}, row ${detail.row.id})`,
      Math.round(back2.scrollTop) === Math.round(topFor(detail.row.id)))

process.exit(failed === 0 ? 0 : 1)
