# 0002: A nil child is a comment node

Date: 2026-07-28

Status: Accepted

## Context

`create-vnode*` gave a nil child a text vnode with an empty string, so
`(when cond [:span])` produced an empty text node when `cond` was false. The node
looks like waste, but it holds the child's slot.

It was also the one thing server rendering could not reproduce. `reagami.ssr`
emitted nothing for a nil child, because HTML cannot write an empty text node,
so the client built one more child than the server sent.

That broke hydration. In the ssr example, a virtual scroller rendered

```clojure
[:div#canvas
 (when loading? [spinner ...])
 (for [r rows] [:div.row ...])
 (when loading? [spinner ...])]
```

The server sent 30 row divs, the client built 32 children, and every row was
matched against the wrong node. Hydration reported 9 nodes built instead of 0 and
discarded the server's DOM. Nothing errored: the only symptom was the `:created`
count `render` returns.

## Experiment

First attempt: drop the placeholder and skip nil children entirely. Hydration
lined up, and the cost moved somewhere worse.

```clojure
[:div (when x [:span]) [:p "keep"]]
```

With a placeholder, `x` false gives `[marker, p]` and `x` true gives `[span, p]`.
`patch-node` swaps index 0, index 1 is untouched, and `p` survives the toggle.

Without one, the child count changes from 1 to 2. Unkeyed reconciliation patches
the common prefix by index, so `p` is matched against the span vnode, replaced,
and a fresh `p` appended. One swap becomes a cascade, and every sibling after the
conditional shifts.

## Decision

Keep the placeholder, and make it a comment node instead of an empty text node.
A comment holds the slot exactly as well and it serializes, so
`reagami.ssr` writes `<!---->` and the two sides agree. `adopt` already builds
vnodes from `nodeName`, so a server comment matches the marker with no extra
handling.

Applications write conditionals directly. The `concat` workaround the example
carried is gone, and it still hydrates with zero nodes built.

React does the same thing for the same reason.

## Cost

- `:lite-mode` gzip 18386 to 18462, 76 bytes, against a 22000 budget
- Benchmark against the previous commit over five trials: 1.00 to 1.04, no regression
- Seven bytes of server HTML per conditional, which brotli reduces to nearly nothing
- Comment nodes are visible in devtools where empty text nodes were. `children`
  and `querySelector` skip both alike.

## Notes

`false` is not nil. `(and x [:span])` renders the text `false` when `x` is false,
in both `reagami.core` and `reagami.ssr`. They agree, so hydration is unaffected,
but it is not the same as `(when x [:span])`.

## References

- Marker: `comment-tag` in `src/reagami/core.cljc`, used by `create-vnode*`,
  `create-node` and `patch-node`
- Server side: `->html` in `src/reagami/ssr.cljc`
- Tests: `hydrate-conditional-children-test` in `test/ssr_test.cljc`
