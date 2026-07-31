# Reagami SSR live

A million row CRUD virtual scroll, rendered by Babashka, streamed over SSE, and adopted by the
browser.

A live instance runs at https://reagami-ssr-live.michielborkent.nl. All
visitors share the same table, so don't be surprised if you see edits from other people show up.

```shell
bb dev
```

Then open http://localhost:8081. For the smallest setup, start with
[ssr](../ssr).

Scroll anywhere. The rows do not exist until you ask for them: `app/row`
builds a row from its index, and the server sends only the window on your
screen. Click a cell, type, and press Enter. The server applies the edit and
pushes the new window to every open tab.

The line under the table shows the last push:

```text
push 5357 chars of state, 463 B on the wire (12x smaller) | created 0
```

Every push carries the whole window. `proxy.mjs` keeps one Brotli encoder per
stream, so a push only costs the difference with the pushes before it.
`created 0` means that Reagami adopted the server nodes and made no new ones.

`src/app.cljc` renders on both sides and holds the reducer. `app/handle` is a
plain `[state action] -> state` function. Point the dispatch atom at it, and
the same app runs with no server at all:

```clojure
(reset! app/!dispatch (fn [action] (swap! app/!state app/handle action)))
```
