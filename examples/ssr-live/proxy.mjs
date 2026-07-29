// Brotli in front of the Babashka server, which cannot compress itself. Event
// streams use one encoder for the connection and a flush per event, so each
// push can reference the ones before it. Run with: bb proxy
import http from 'node:http'
import zlib from 'node:zlib'

const PORT = Number(process.env.PROXY_PORT || 8081)
const UPSTREAM = Number(process.env.PORT || 8080)
// loopback by default: behind nginx there is no reason to be reachable directly
const HOST = process.env.PROXY_HOST || '127.0.0.1'
// an address, not "localhost", which resolves to ::1 first on many machines and
// would miss a server bound to IPv4 loopback
const UPSTREAM_HOST = process.env.UPSTREAM_HOST || '127.0.0.1'
const QUALITY = Number(process.env.BROTLI_QUALITY || 5)

const br = () => zlib.createBrotliCompress({
  params: {
    [zlib.constants.BROTLI_PARAM_QUALITY]: QUALITY,
    [zlib.constants.BROTLI_PARAM_MODE]: zlib.constants.BROTLI_MODE_TEXT
  }
})

const wantsBrotli = (req) => /\bbr\b/.test(req.headers['accept-encoding'] || '')

// one line per event is what makes the numbers visible in dev, and what would
// fill the journal in production. errors are always logged.
const VERBOSE = process.env.PROXY_VERBOSE !== '0'
const log = (...args) => { if (VERBOSE) console.log(...args) }

// an upstream socket error can race the response, so every path that writes
// headers checks first. writing them twice throws, and an unhandled throw here
// takes down every connection, not just this one.
const head = (res, status, headers) => {
  if (res.headersSent) return false
  res.writeHead(status, headers)
  return true
}

http.createServer((req, res) => {
  const headers = { ...req.headers, host: `${UPSTREAM_HOST}:${UPSTREAM}`, 'accept-encoding': 'identity' }
  const up = http.request({ host: UPSTREAM_HOST, port: UPSTREAM, path: req.url, method: req.method, headers }, (ur) => {
    const type = ur.headers['content-type'] || ''
    const out = { ...ur.headers }
    delete out['content-length']

    // a HEAD carries no body to compress, and crawlers send plenty of them
    if (req.method === 'HEAD') {
      head(res, ur.statusCode, ur.headers)
      ur.resume()
      res.end()
      return
    }

    if (!wantsBrotli(req)) {
      if (type.startsWith('text/event-stream')) {
        log(`stream NOT compressed: client sent accept-encoding: ${req.headers['accept-encoding'] || '(none)'}`)
      }
      head(res, ur.statusCode, ur.headers)
      ur.pipe(res)
      return
    }

    if (type.startsWith('text/event-stream')) {
      const enc = br()
      let event = 0
      let raw = 0
      let comp = 0
      // state pushes only, so the wire events this proxy injects do not count
      // against the app's own totals
      let brTotal = 0
      res.writeHead(ur.statusCode, { ...out, 'content-encoding': 'br' })
      enc.on('data', (c) => { comp += c.length; res.write(c) })
      ur.on('data', (chunk) => {
        raw += chunk.length
        const before = comp
        enc.write(chunk)
        // flush so the event reaches the browser now, keeping the window intact
        enc.flush(zlib.constants.BROTLI_OPERATION_FLUSH, () => {
          event++
          const cost = comp - before
          brTotal += cost
          log(`event ${String(event).padStart(3)}  raw ${String(chunk.length).padStart(7)}  br ${String(cost).padStart(7)}  (stream ${raw} -> ${brTotal})`)
          // only this side knows what the push cost, so report it to the page
          enc.write(Buffer.from(`event: wire\ndata: {"raw":${chunk.length},"br":${cost},"rawTotal":${raw},"brTotal":${brTotal}}\n\n`))
          enc.flush(zlib.constants.BROTLI_OPERATION_FLUSH, () => {})
        })
      })
      ur.on('end', () => enc.end(() => res.end()))
      return
    }

    const chunks = []
    ur.on('data', (c) => chunks.push(c))
    ur.on('end', () => {
      const body = Buffer.concat(chunks)
      // 204 and 304 carry no body, and compressing one into existence makes the
      // response invalid. an empty body is not worth compressing either.
      if (body.length === 0 || ur.statusCode === 204 || ur.statusCode === 304) {
        head(res, ur.statusCode, ur.headers)
        res.end()
        return
      }
      zlib.brotliCompress(body, { params: { [zlib.constants.BROTLI_PARAM_QUALITY]: QUALITY } }, (err, z) => {
        if (err) { head(res, ur.statusCode, ur.headers); res.end(body); return }
        log(`${req.method} ${req.url}  raw ${body.length}  br ${z.length}`)
        head(res, ur.statusCode, { ...out, 'content-encoding': 'br' })
        res.end(z)
      })
    })
  })
  up.on('error', (e) => {
    console.error(`upstream ${req.method} ${req.url}: ${e.message}`)
    if (head(res, 502, { 'content-type': 'text/plain' })) res.end('bad gateway')
    else res.end()
  })
  req.on('error', (e) => console.error(`client ${req.method} ${req.url}: ${e.message}`))
  res.on('error', (e) => console.error(`response ${req.method} ${req.url}: ${e.message}`))
  req.pipe(up)
}).listen(PORT, HOST, () => {
  console.log(`Brotli proxy  http://localhost:${PORT}  ->  ${UPSTREAM}  (quality ${QUALITY})`)
})
