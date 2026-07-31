# Reagami SSR live

What if we just send the whole app state on every client action and re-render the whole app?

A million row CRUD virtual scroll, rendered by Babashka, streamed over SSE, and adopted by the
browser.

Deployed at https://reagami-ssr-live.michielborkent.nl. All
visitors share the same table, so don't be surprised if you see edits from other people show up :-).

```shell
bb dev
```

Then open http://localhost:8081. For the smallest setup, start with
[ssr](../ssr).

Scroll through the table and edit some cells.

The line under the table shows the last push:

```text
push 5357 chars of state, 463 B on the wire (12x smaller) | created 0
```

Every state push triggers a new render of the whole page.

Babashka cannot compress with Brotli, so a tiny `proxy.mjs` sits in front of the stream. It also reports what each push costs on the wire, which is where the numbers above come from.

The text `created 0` shows how many new nodes Reagami has created. Initially this should be 0 since the server-rendered HTML should be wholly adopted.

The main component lives in `src/app.cljc` and renders on both sides. `app/handle` is a
plain `[state action] -> state` function. Point `app/!dispatch` at it instead
of the POST, and the same app runs entirely in the browser:

```clojure
(reset! app/!dispatch (fn [action] (swap! app/!state app/handle action)))
```
