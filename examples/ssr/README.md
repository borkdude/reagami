# reagami ssr

The smallest useful server rendering setup: babashka renders the page, the
browser adopts it.

`src/app.cljc` is loaded by both sides. The server renders it with
`reagami.ssr/render`, the browser calls `reagami.core/render` on the same hiccup
and reuses the nodes the server sent instead of building its own.

```
bb serve
```

Open http://localhost:8080 and check the console:

```
created 0, adopted 9
```

`:created` is how many DOM nodes reagami had to build. Zero means it adopted
every node in the page.

For sessions, streaming, virtual scrolling, editing and compression, see
[ssr-live](../ssr-live).
