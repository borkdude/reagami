# Reagami SSR

The smallest server rendering setup: Babashka renders the page, the browser
adopts it.

`src/app.cljc` is loaded by both sides. The server renders it with
`reagami.ssr/render`. The browser then calls `reagami.core/render` on the same
hiccup and reuses the nodes that the server put in `#app`.

```shell
bb dev
```

Open http://localhost:8080 and look at the console:

```text
created 0, adopted 9
```

`:created` is how many DOM nodes Reagami had to build. Zero means it adopted
every node the server rendered into `#app`.

Vite runs the `squint-cljs/vite` plugin. The plugin compiles the CLJS and
hot-swaps changed modules without a page reload. It also starts an nREPL server
on 1339 that evaluates in the live page. An edit to `app.cljc` repaints through
`^:dev/after-load` and keeps the state in the atom.

For sessions, streaming, virtual scrolling, editing and compression, see
[ssr-live](../ssr-live).
