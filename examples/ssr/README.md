# reagami ssr

Hiccup rendered to HTML by babashka, adopted by reagami in the browser.

A virtual-scrolled table of a million rows. `src/app.cljc` runs on both sides.
The server renders it with `reagami.ssr/render` and embeds the state as JSON. The
browser calls `reagami.core/render` on the same hiccup and reuses the server's
DOM nodes instead of rebuilding them.

Only the visible window is ever in the client. `app/row` builds a row from its
index, so the million rows never exist anywhere at once. Scrolling posts the
range you are looking at, the server sends those rows, and spinners sit above and
below until they arrive. Row count comes from `ROWS`, default 1000000.

Two pages. `/` is the table, `/row/42` is one row on its own, and both are
server-rendered, hydrated and driven by the same stream. Which page you are on is
just `:page` in the state, so opening a row is an action like any other and the
URL follows the state rather than driving it. A direct hit on `/row/42` renders
on the server from the same components.

State lives on the server, one entry per tab. Clicking posts an action to
`POST /action`, the server applies `app/handle`, and the new state comes back
down that tab's `GET /state/<sid>` event stream. `handle` is portable, so
pointing `app/!dispatch` at it runs the same reducer locally instead.

Drive it without a browser:

```
sid=$(curl -s localhost:8080/ | grep -o 'data-sid="[^"]*"' | cut -d'"' -f2)
curl -sN localhost:8080/state/$sid &
curl -s -XPOST localhost:8080/action -d "{\"sid\":\"$sid\",\"action\":{\"type\":\"add\"}}"
```

Click a cell to edit it. Enter or blur commits: the client applies `app/handle`
locally so the change shows at once, sends the action, and the server's push
replaces it. Edits are laid over the generated rows and kept in `server/db`, which stands in
for a database: they belong to the data rather than to a tab, so they survive a
refresh and every session sees them. They never travel back as a growing map.

The server sleeps before answering, so the spinners are visible. The slider sets
that delay from 0 to 100 ms, and it is server state like anything else: it rides
along on every push and the round trip changes it. `LATENCY` picks the value it
starts at.

`src/debug.cljs` renders what `render` returned, into its own root. It shows the
push size two ways: the JSON the client parsed, and what it cost on the wire. The
browser cannot see the second, so the proxy reports it back as a `wire` event
just after each push. On 8080 there is no proxy and the panel says uncompressed.

`reagami.ssr` renders the whole page, not just the island, so the app component
nests inside `[:html ...]` rather than being rendered separately and spliced in.
`:innerHTML` is the escape hatch for the two places that must not be escaped:
script and style are raw text to the HTML parser, so entities inside them are
never decoded.

## Brotli

babashka cannot brotli, so `proxy.js` does it in front. `bb dev` starts it on
8081. One encoder per event stream and a flush per event, which lets each push
reference the ones before it. Open 8081 rather than 8080 and it prints what every
push costs on the wire:

```
event   1  raw   52151  br    4065
event   2  raw   52151  br      18
event   3  raw   52151  br      14
```

A window of 30 nine-column rows is about 4.4 KB of JSON and 400 to 660 bytes on
the wire, because consecutive windows overlap and brotli references what it
already sent.

Windowing is what keeps this bounded. Without it the whole table would go over
the wire on every change, which for a million rows is gigabytes.

## Run

```
bb dev
```

Open http://localhost:8080. Vite serves the client on 5173, so open the babashka
server rather than vite. nREPL listens on 1667.

```
bb verify
```

Hydrates the server HTML in jsdom and fails if reagami built any node.

```
bb measure
```

Reports payload size and nodes built per action shape. `ROWS=1000 bb measure`
for the interesting case.

```
bb serve
```

Production build, no vite.
