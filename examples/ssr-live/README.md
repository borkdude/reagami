# reagami ssr-live

Server rendering with everything an app tends to need around it: a live stream,
server state per tab, virtual scrolling over a million rows, editing, two pages
and compression.

Start with [ssr](../ssr) for the plain version.

## Run

```
bb dev
```

Open http://localhost:8080, or 8081 to go through brotli. Vite runs on 5173 and
is not meant to be opened.

Vite runs the `squint-cljs/vite` plugin: it compiles the cljs, hot-swaps changed
modules without reloading the page, and starts an nREPL on 1339 that evaluates in
the live page. `^:dev/after-load` in `client.cljs` repaints after a swap, so an
edit keeps scroll position and the loaded window. The babashka server has its own
nREPL on 1667.

| task | |
|---|---|
| `bb dev` | vite, brotli proxy and the server |
| `bb verify` | hydrates the server HTML in jsdom, fails if reagami built a node |
| `bb serve` | production build, no vite |

`ROWS` sets the table size, default 1000000. `LATENCY` sets the server's
starting delay, default 20 ms.

## How it fits together

`src/app.cljc` runs on both sides. The server renders it with
`reagami.ssr/render`, the browser calls `reagami.core/render` on the same hiccup,
and reuses the nodes the server sent rather than building its own.

State lives on the server, one entry per tab. An action goes up over
`POST /action`, the server applies `app/handle`, and the new state comes back
down that tab's `GET /state/<sid>` event stream. `app/handle` is portable, so
pointing `app/!dispatch` at it runs the same reducer in the browser instead.

Without a browser:

```
sid=$(curl -s localhost:8080/ | grep -o 'data-sid="[^"]*"' | cut -d'"' -f2)
curl -sN localhost:8080/state/$sid &
curl -s -XPOST localhost:8080/action \
  -d "{\"sid\":\"$sid\",\"action\":{\"type\":\"edit\",\"id\":2,\"field\":\"name\",\"value\":\"hello\"}}"
```

## The parts

**Windowing.** `app/row` builds a row from its index, so a million rows never
exist anywhere at once. Scrolling posts the range on screen, the server sends
those rows, and placeholders fill the viewport until they arrive.

**The scroll canvas** is capped at 15M px, because browsers refuse to lay out an
element much taller and Firefox gives up before Chrome does. Past the cap the
scroll position is scaled through it so dragging still reaches the last row.
Below it the scale is 1.

**Two pages.** `/` is the table, `/row/42` is one row. Which page you are on is
`:page` in the state, so opening a row is an action like any other and the URL
follows the state rather than driving it. A direct hit on `/row/42` is rendered
by the server from the same components.

**Editing.** Click a cell. Enter or blur commits: the client runs `app/handle`
locally so the change shows at once, sends the action, and the server's push
replaces it. Edits live in `server/db`, standing in for a database, so they
belong to the data rather than to a tab and survive a refresh.

**The delay.** The server sleeps before answering so the placeholders are
visible. The slider sets it, and it is server state like anything else.

**The panel** under the table is `src/debug.cljs`, rendered into its own root. It
reports the page load as the browser recorded it, and each push twice: the JSON
the client parsed, and what it cost on the wire.

## Brotli

babashka cannot brotli, so `proxy.js` does it in front and `bb dev` starts it on
8081. One encoder per event stream with a flush per event, so each push can
reference the ones before it:

```
event   1  raw    4247  br     664
event   2  raw    4394  br     405
event   3  raw    4395  br     461
```

The browser cannot see those numbers, so the proxy reports them back as a `wire`
event just after each push, which is what the panel shows. On 8080 there is no
proxy and it says uncompressed.

Windowing is what keeps this bounded. Unwindowed, a 1000 row state was 52 KB per
push, and a million rows would be gigabytes.
