# Reagami SSR live

A million rows, rendered by Babashka, streamed over SSE, and adopted by the
browser.

A live instance runs at https://reagami-ssr-live.michielborkent.nl. All
visitors share the same table, so edits from other people can appear.

To run it locally:

```shell
bb dev
```

Then open http://localhost:8081. For the smallest setup, start with
[ssr](../ssr).

## Things to try

**Scroll to row 500,000.** Drag the scrollbar to the middle. The server makes
the rows on demand and sends them while you drag. The client holds only the
rows near your screen.

**Set the delay slider to 100 ms. Then fling the scrollbar.** Rows that the
client does not hold yet show as placeholders. A bar at the top of the page
shows every request in flight. Put the slider back to 0, and both no longer
appear.

**Drag the client cache slider.** The client then asks for a wider window
around the viewport: 80 rows by default, up to about 2000. A push of 1000 rows
is 145 kB of JSON and about 12 kB on the wire. The panel below the table shows
the parse and render cost.

**Edit a cell.** Click a cell, type, and press Enter. The change shows
immediately, because the browser runs the same reducer as the server. Refresh
the page, and the edit is still there. Until the server answers, you cannot
edit another cell.

**Uncheck the optimistic edits box. Then edit again with the delay at 100 ms.**
The cell shows the typed value in grey until the push from the server arrives.
The reducer no longer runs in the browser.

**Open a second tab.** Edit a cell in one tab. The other tab updates without a
reload. Each tab keeps its own scroll position and its own open row.

**Open a row.** Click `open` on a row. This is a different page at `/row/42`.
If you open that URL directly, the server renders it. Go back, and the table
shows the row you left.

**Watch the panel under the table.** It renders three lines:

```text
stream 39 pushes, 425.9 kB of state, 3.8 kB on the wire (113x smaller)
rows 73..153 of 1000000 | state 11.2 kB uncompressed | parse 100 us | created 0 | render 1 ms
load: response 39 ms, painted 76 ms, interactive 65 ms
```

- The stream line counts every push since the page loaded. Each window
  overlaps the one before it, so the compression ratio climbs while you
  scroll.
- The rows line shows the state that the client holds and the cost of the
  last render. `created 0` means that Reagami adopted the server nodes in
  `#app` and made no new ones. The panel itself renders into a separate root,
  outside that count.
- The load line is the page load as the browser recorded it.

## How it works

`src/app.cljc` runs on both sides. The server renders it with
`reagami.ssr/render`. The browser calls `reagami.core/render` on the same
hiccup.

State lives on the server, one entry per tab. A click calls `app/dispatch!`,
which sends the action to the function in the `app/!dispatch` atom. In the
browser that function is a POST to `/action`. The server applies `app/handle`
and pushes the new state down the `GET /state/<sid>` event stream of that tab.
An edit is shared data, so the server also applies it to the other open tabs
and pushes each tab its own window.

`app/handle` is a plain `[state action] -> state` function in a `.cljc` file,
so it is the same code on both sides. To run the app in the browser only,
point the atom at `handle`:

```clojure
(reset! app/!dispatch (fn [action] (swap! app/!state app/handle action)))
```

Scroll then still works, because `app/row` also makes the rows in the browser.

Vite runs the `squint-cljs/vite` plugin. The plugin compiles the CLJS,
hot-swaps changed modules without a page reload, and starts an nREPL on 1339
that evaluates in the live page. Edit a view, and the table repaints with your
scroll position intact. The Babashka server has its own nREPL on 1667.

`ROWS` sets the table size, default 1000000. `LATENCY` sets the start delay of
the server, default 20 ms.

## Brotli

Babashka cannot compress with Brotli, so `proxy.mjs` does it in front, with
one encoder per event stream and a flush per event. Each push can then
reference the pushes before it, and consecutive windows overlap almost
completely:

```text
event   1  raw    4247  br     664
event   2  raw    4394  br     405
event   3  raw    4395  br     461
```

The browser cannot read these numbers, so the proxy sends them back as a
`wire` event after each push. If you open port 8080 instead, the panel shows
uncompressed sizes.
