// Brotli in front of the babashka server, which cannot do it itself. Event
// streams are compressed with one encoder for the whole connection and a flush
// per event, so successive state pushes reference the earlier ones. Reports
// what each push actually costs on the wire. Run with: bb proxy
import http from 'node:http'
import zlib from 'node:zlib'

const PORT = Number(process.env.PROXY_PORT || 8081)
const UPSTREAM = Number(process.env.PORT || 8080)
const QUALITY = Number(process.env.BROTLI_QUALITY || 5)

const br = () => zlib.createBrotliCompress({
  params: {
    [zlib.constants.BROTLI_PARAM_QUALITY]: QUALITY,
    [zlib.constants.BROTLI_PARAM_MODE]: zlib.constants.BROTLI_MODE_TEXT
  }
})

const wantsBrotli = (req) => /\bbr\b/.test(req.headers['accept-encoding'] || '')

http.createServer((req, res) => {
  const headers = { ...req.headers, host: `localhost:${UPSTREAM}`, 'accept-encoding': 'identity' }
  const up = http.request({ host: 'localhost', port: UPSTREAM, path: req.url, method: req.method, headers }, (ur) => {
    const type = ur.headers['content-type'] || ''
    const out = { ...ur.headers }
    delete out['content-length']

    if (!wantsBrotli(req)) {
      if (type.startsWith('text/event-stream')) {
        console.log(`stream NOT compressed: client sent accept-encoding: ${req.headers['accept-encoding'] || '(none)'}`)
      }
      res.writeHead(ur.statusCode, ur.headers)
      ur.pipe(res)
      return
    }

    if (type.startsWith('text/event-stream')) {
      const enc = br()
      let event = 0
      let raw = 0
      let comp = 0
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
          console.log(`event ${String(event).padStart(3)}  raw ${String(chunk.length).padStart(7)}  br ${String(cost).padStart(7)}  (stream ${raw} -> ${comp})`)
          // only this side knows what the push actually cost, so tell the page.
          // its own bytes land in the next event's measurement, as they should.
          enc.write(Buffer.from(`event: wire\ndata: {"raw":${chunk.length},"br":${cost}}\n\n`))
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
        res.writeHead(ur.statusCode, ur.headers)
        res.end()
        return
      }
      zlib.brotliCompress(body, { params: { [zlib.constants.BROTLI_PARAM_QUALITY]: QUALITY } }, (err, z) => {
        if (err) { res.writeHead(ur.statusCode, ur.headers); res.end(body); return }
        console.log(`${req.method} ${req.url}  raw ${body.length}  br ${z.length}`)
        res.writeHead(ur.statusCode, { ...out, 'content-encoding': 'br' })
        res.end(z)
      })
    })
  })
  up.on('error', (e) => { res.writeHead(502); res.end(String(e)) })
  req.pipe(up)
}).listen(PORT, () => {
  console.log(`brotli proxy  http://localhost:${PORT}  ->  ${UPSTREAM}  (quality ${QUALITY})`)
})
