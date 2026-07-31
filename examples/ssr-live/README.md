# Reagami SSR live

A million row CRUD virtual scroll, rendered by Babashka, streamed over SSE, and adopted by the
browser.

A live instance runs at https://reagami-ssr-live.michielborkent.nl. All
visitors share the same table, so don't be surprised if you see edits from other people show up.

To run it locally:

```shell
bb dev
```

Then open http://localhost:8081. For the smallest setup, start with
[ssr](../ssr).

Scroll anywhere in the table. The rows do not exist until you ask for them:
`app/row` builds a row from its index, and the server sends only the window on
your screen. Click a cell, type, and press Enter. The server applies the edit
and pushes the new window to every open tab.

The line under the table shows the last push:

```text
push 5357 chars of state, 463 B on the wire (12x smaller) | created 0
```

Every push carries the whole window. Brotli only sends the difference with the
pushes before it, so a scroll costs a few hundred bytes per frame. `created 0`
means that Reagami adopted the server nodes and made no new ones.

## How it works

`src/app.cljc` runs on both sides. The server renders it with
`reagami.ssr/render`. The browser calls `reagami.core/render` on the same
hiccup.

State lives on the server, one entry per tab. A click calls `app/dispatch!`,
which sends the action to the function in the `app/!dispatch` atom. In the
browser that function is a POST to `/action`. The server applies `app/handle`
and pushes the new state down the `GET /state/<sid>` event stream of that tab.
An edit is shared data, so the server also applies it to the other open tabs.

`app/handle` is a plain `[state action] -> state` function in a `.cljc` file,
so it is the same code on both sides. To run the app in the browser only,
point the atom at `handle`:

```clojure
(reset! app/!dispatch (fn [action] (swap! app/!state app/handle action)))
```

Scroll then still works, because `app/row` also makes the rows in the browser.

Vite runs the `squint-cljs/vite` plugin. The plugin compiles the CLJS,
hot-swaps changed modules without a page reload, and starts an nREPL on 1339
that evaluates in the live page. The Babashka server has its own nREPL on 1667.

`ROWS` sets the table size, default 1000000.

## Brotli

Babashka cannot compress with Brotli, so `proxy.mjs` does it in front, with
one encoder per event stream and a flush per event. Each push can then
reference the pushes before it, and consecutive windows overlap almost
completely:

```text
event   1  raw    3140  br     441
event   2  raw    5356  br     592
event   3  raw    5367  br     560
```

The browser cannot read these numbers, so the proxy sends them back as a
`wire` event after each push. If you open port 8080 instead, the line under
the table shows no wire size.
