# 0003: Render re-entrancy is unguarded

Date: 2026-07-28

Status: Open. Deferred, not blocked.

## Context

`patch-keyed` starts by taking a snapshot of the parent's children:

```clojure
(let [old-nodes (js/Array.from (.-childNodes parent))
```

and works from that array for the rest of the pass. Anything that mutates the
DOM while the pass is running makes the snapshot stale.

A render cannot call itself directly, but the DOM can do it for you. Several
mutations fire handlers synchronously:

- removing a focused element fires `blur`
- `.focus()` on an element inside a scroll container fires `scroll`
- removing a custom element runs its `disconnectedCallback`

A handler that changes state then starts a second render inside the first.

The ssr-live example hit this by committing an edit on Enter: the commit
re-rendered, the re-render removed the focused input, and the resulting `blur`
committed again.

## Failure modes

Two, depending on where the stale snapshot lands.

A crash, in a browser:

```
Uncaught NotFoundError: Failed to execute 'removeChild' on 'Node':
The node to be removed is no longer a child of this node.
Perhaps it was moved in a 'blur' event handler?
    at patch_keyed (core.mjs:613:3)
```

Or, worse, no error at all. Rendering from a `disconnectedCallback` while patch
is mid-pass:

```
before: <div><re-entrant-el></re-entrant-el><span>b</span><span>c</span></div>
outer render asks for:  b2 / c2
nested render asks for: B! / C!
result: <div><span>B!</span><span>C!</span></div>     no throw
```

The outer render's work is discarded and nothing reports it.

## Reproduction

jsdom does not fire `blur` when a focused element is removed, and does no
layout, so `.focus()` never scrolls. Custom elements do work: define one whose
`disconnectedCallback` renders the same root, render it into a keyed list, then
render again without it. `HTMLElement` and `customElements` have to be copied
onto `globalThis` from the jsdom window first, as `install-jsdom` does for
`Node` and `Element`.

## Decision

Not fixed here. Applications guard it themselves, as
`examples/ssr-live/src/client.cljs` does: refuse to start a render while one is
running, and run the deferred one afterwards.

```clojure
(if @!rendering
  (reset! !again true)
  (do (reset! !rendering true) ...))
```

The fix belongs in `render` and is the same shape. It was left out of the server
rendering work to keep that change to one subject.

## Open question for the fix

A deferred render cannot return its counts, because it has not run yet. Either
it returns nil, or it returns the counts of the render that was already in
flight. Nil looks right: the caller is inside an event handler during a render
and is not reading the result.

## References

- Snapshot: `patch-keyed` in `src/reagami/core.cljc`
- Application guard: `render!` in `examples/ssr-live/src/client.cljs`
