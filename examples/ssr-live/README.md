# reagami ssr-live

A million rows, rendered by babashka, taken over by the browser without
rebuilding a single node.

```
bb dev
```

Open http://localhost:8081. Start with [ssr](../ssr) for the plain version.

## Things to try

**Scroll to row 500,000.** Drag the bar to the middle. Those rows were generated
on demand and sent while you were dragging. The client never holds more than the
window you can see.

**Drag the delay slider to 100 ms, then fling the scrollbar.** Placeholders fill
the viewport while the server catches up, with a spinner in the corner. Put it
back to 0 and they stop appearing.

**Edit a cell.** Click it, type, press Enter. The change shows immediately,
because the browser ran the same reducer the server is about to run. Refresh and
it is still there.

**Open a row.** Click `open` on any row. That is a different page at `/row/42`,
server rendered if you hit it directly. Go back and the table returns to the row
you left.

**Watch the panel under the table.** After a push it reads something like:

```
rows 4990..5070 of 1000000 | last push 4247 chars, 664 B on the wire (6x smaller)
  | parse 21 us | created 0 | render 3 ms
```

`created 0` is reagami adopting the server's DOM rather than rebuilding it. The
line above reports the page load, where the document paints well before hydration
makes it interactive.

## How it works

`src/app.cljc` runs on both sides. The server renders it with
`reagami.ssr/render`, the browser calls `reagami.core/render` on the same hiccup.

State lives on the server, one entry per tab. Clicking anything calls
`app/dispatch!`, which hands the action to whatever function sits in the
`app/!dispatch` atom. The client puts a POST there, so actions go up over
`POST /action`, the server applies `app/handle`, and the new state comes back
down that tab's `GET /state/<sid>` event stream.

`app/handle` is an ordinary `[state action] -> state` function in a `.cljc`, so
it is the same code in both places. Put it behind the seam instead of the POST:

```clojure
(reset! app/!dispatch (fn [action] (swap! app/!state app/handle action)))
```

and the app runs entirely in the browser, server untouched. Even scrolling to a
new window works, because `app/row` generates rows there too. The round trip is a
transport choice, not the architecture.

Vite runs the `squint-cljs/vite` plugin: it compiles the cljs, hot-swaps changed
modules without reloading, and starts an nREPL on 1339 that evaluates in the live
page. Edit a view and the table repaints, keeping your scroll position. The
babashka server has its own nREPL on 1667.

`ROWS` sets the table size, default 1000000. `LATENCY` sets the server's starting
delay, default 20 ms.

## Brotli

Pushing the whole state on every change sounds wasteful, and mostly is not.

babashka cannot brotli, so `proxy.js` does it in front, with one encoder per
event stream and a flush per event. Each push can then reference the ones before
it, and consecutive windows overlap almost entirely:

```
event   1  raw    4247  br     664
event   2  raw    4394  br     405
event   3  raw    4395  br     461
```

It is starker without windowing. A 1000 row state is 52 KB, and after the first
push, sending all of it again costs about 15 bytes:

```
event   1  raw   52151  br    4065
event   2  raw   52151  br      18
event   3  raw   52151  br      15
```

A hand written patch for the same edit would be around 46 bytes. Brotli computes
the diff for you, and every push stays self correcting: miss one and the next
still carries the whole truth.

The browser cannot see those numbers, so the proxy sends them back as a `wire`
event just after each push. Open 8080 instead and the panel says uncompressed.
