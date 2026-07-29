# Reagami SSR live

A million rows rendered by Babashka, streamed over SSE and adopted by the
browser.

```shell
bb dev
```

Open http://localhost:8081. Start with [ssr](../ssr) for the plain version.

## Things to try

**Scroll to row 500,000.** Drag the bar to the middle. Those rows were generated
on demand and sent while you were dragging. The client never holds more than the
window you can see.

**Drag the delay slider to 100 ms, then fling the scrollbar.** Rows the client
does not hold yet draw as placeholders, and a bar across the top of the page
marks every request still in flight. Put the slider back to 0 and both stop
appearing.

**Edit a cell.** Click it, type, press Enter. The change shows immediately,
because the browser ran the same reducer the server is about to run. Refresh and
it is still there. Nothing else can be edited until the server answers, so a
second edit cannot be undone by the push already on its way.

**Clear the optimistic edits box and edit again,** with the delay at 100 ms. The
cell still shows what was typed, greyed until the server confirms it, but the
reducer no longer runs in the browser and the value arrives from the server.

**Open a second tab.** Edit a cell in one and the other updates without a
reload. Both tabs keep their own scroll position and their own open row.

**Open a row.** Click `open` on any row. That is a different page at `/row/42`,
server rendered if you hit it directly. Go back and the table returns to the row
you left.

**Watch the panel under the table.** It renders three lines:

```text
received 12.2 kB over the wire | document 3.9 kB, assets 4.5 kB, stream 3.8 kB over 39 pushes (113x)
rows 73..153 of 1000000 | state 11.2 kB uncompressed | parse 100 us | created 0 | render 1 ms
load: response 39 ms, painted 76 ms, interactive 65 ms
```

The first line is everything the page has received since it loaded, split into
the document, the JavaScript, and the state stream. Scroll for a while and watch
the ratio climb well past what any single push achieves, because each window is
mostly the one before it. The second line is the state the client is holding and
the render it caused. Every push carries the whole state, so that figure is both
the JSON last parsed and the size of a push before compression, around 140 bytes
per row. `created 0` means Reagami adopted the nodes the server rendered into
`#app` rather than rebuilding them. The panel itself is rendered by the client
into a separate empty root, so it is not part of that count.

## How it works

`src/app.cljc` runs on both sides. The server renders it with
`reagami.ssr/render`, the browser calls `reagami.core/render` on the same hiccup.

State lives on the server, one entry per tab. Clicking anything calls
`app/dispatch!`, which hands the action to whatever function sits in the
`app/!dispatch` atom. The client puts a POST there, so actions go up over
`POST /action`, the server applies `app/handle`, and the new state comes back
down that tab's `GET /state/<sid>` event stream. An edit is shared data, so the
server runs it through `app/handle` for every other open tab as well and pushes
each one its own window.

`app/handle` is an ordinary `[state action] -> state` function in a `.cljc`, so
it is the same code in both places. Put it behind the seam instead of the POST:

```clojure
(reset! app/!dispatch (fn [action] (swap! app/!state app/handle action)))
```

and the app runs in the browser with the server untouched. Scrolling to a new
window still works, because `app/row` generates rows there too.

Vite runs the `squint-cljs/vite` plugin: it compiles the CLJS, hot-swaps changed
modules without a page reload, and starts an nREPL on 1339 that evaluates in the
live page. Edit a view and the table repaints, keeping your scroll position. The
Babashka server has its own nREPL on 1667.

`ROWS` sets the table size, default 1000000. `LATENCY` sets the server's starting
delay, default 20 ms.

## Brotli

Babashka cannot compress with Brotli, so `proxy.mjs` does it in front, with one
encoder per event stream and a flush per event. Each push can then reference the
ones before it, and consecutive windows overlap almost entirely:

```text
event   1  raw    4247  br     664
event   2  raw    4394  br     405
event   3  raw    4395  br     461
```

The browser cannot read those numbers, so the proxy sends them back as a `wire`
event just after each push. Open 8080 instead and the panel says uncompressed.
