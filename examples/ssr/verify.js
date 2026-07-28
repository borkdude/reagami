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
// the fetch is deliberately wider than the viewport, so a small scroll stays
// inside what the client already holds
const seenFrom = 1000
const seenTo = 1020
check(`scrolling asked for more than the viewport shows (${asked?.from}..${asked?.to})`,
      asked?.type === 'window' && asked.from < seenFrom && asked.to > seenTo)

// scrolled past everything the client holds, so the whole viewport must be
// placeholders rather than blank canvas, and only the viewport
const placeholders = [...document.querySelectorAll(".ghost")]
const tops = placeholders.map((e) => parseInt(e.style.top, 10)).sort((a, b) => a - b)
check(`every visible row is a placeholder, and no more (${placeholders.length} of ${seenTo - seenFrom})`,
      placeholders.length === seenTo - seenFrom)
check(`placeholders cover the viewport (${tops[0]}px..${tops.at(-1)}px)`,
      tops[0] === seenFrom * 24 && tops.at(-1) === (seenTo - 1) * 24)
check('a spinner is showing while they load', spinner() !== null)
check('no loaded rows are left stranded on screen', rows() === 0 || rowsOffScreen(tops))

function rowsOffScreen () {
  return [...document.querySelectorAll('.row')]
    .every((e) => parseInt(e.style.top, 10) < seenFrom * 24)
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

// the flicker case: small scrolls inside the fetched margin must not show a
// single placeholder, or the table blinks on every wheel notch
stream.onmessage({ data: JSON.stringify({ total: state.total, from, rows: next.rows }) })
let blinked = 0
for (const px of [24, 48, 96, 168]) {
  scroller.scrollTop = from * 24 + px
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
  .find((r) => parseInt(r.style.top, 10) === withPrior.rows[3].id * 24)
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

process.exit(failed === 0 ? 0 : 1)
