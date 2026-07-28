# 0003: Render re-entrancy is the caller's problem, for now

Date: 2026-07-28

Status: Open

## Context

`patch-keyed` starts by taking a snapshot of the parent's children:

```clojure
(let [old-nodes (js/Array.from (.-childNodes parent))
```

and works from that array for the rest of the pass. If anything mutates the DOM
while that pass is running, the snapshot goes stale and the pass operates on
nodes that are no longer where it thinks they are. In a browser:

```
Uncaught NotFoundError: Failed to execute 'removeChild' on 'Node':
The node to be removed is no longer a child of this node.
Perhaps it was moved in a 'blur' event handler?
    at patch_keyed (core.mjs:613:3)
    at patch (core.mjs:651:8)
    at patch_node (core.mjs:433:1)
```

A render cannot call itself directly, but the DOM can do it for you. Removing a
focused element fires `blur` synchronously, and a `blur` handler that changes
state starts a second render inside the first. The ssr example hit this by
committing an edit on Enter: the commit re-rendered, the re-render removed the
focused input, and the resulting `blur` committed again.

Focus does it too. `.focus()` on an element inside a scroll container scrolls it
into view, which fires `scroll`, which a handler may turn into another render.

## Decision

Guard in the application for now. `examples/ssr/src/client.cljs` refuses to start
a render while one is running and runs the deferred one afterwards:

```clojure
(if @!rendering
  (reset! !again true)
  (do (reset! !rendering true) ...))
```

reagami itself does nothing about this yet.

## Why not fix it in core yet

The mechanism cannot be reproduced in the test harness. jsdom does not fire
`blur` when a focused element is removed, and does no layout, so `.focus()` never
scrolls:

```
$ node -e "... inp.focus(); inp.remove(); ..."
blur fired on removal of the focused input: false
```

A fix in `render` would be a few lines, the same shape as the guard above. What
is missing is a test that fails without it. That needs a real browser, or a
mechanism jsdom does implement synchronously, such as a custom element's
`disconnectedCallback`.

## References

- Snapshot: `patch-keyed` in `src/reagami/core.cljc`
- Application guard: `render!` in `examples/ssr/src/client.cljs`
