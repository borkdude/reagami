# 0002: A nil child keeps its slot

Date: 2026-07-28

Status: Accepted

## Context

`create-vnode*` turns a nil child into a text vnode with an empty string, so
`(when cond [:span])` produces a real, empty text node in the DOM when `cond` is
false. It looks like waste, and it is the one thing server rendering cannot
reproduce: `reagami.ssr` emits nothing for a nil child, because HTML has no way
to write an empty text node.

That gap breaks hydration. In the ssr example, a virtual scroller rendered

```clojure
[:div#canvas
 (when loading? [spinner ...])
 (for [r rows] [:div.row ...])
 (when loading? [spinner ...])]
```

The server sent 30 row divs. The client built 32 children, an empty text node
either side of them, so every row was matched against the wrong node. Hydration
reported 9 nodes built instead of 0, and the server's DOM was discarded.

## Experiment

Removed the placeholder: skip nil children in the children loop of
`create-vnode*` rather than giving them a vnode. Hydration then lined up, but
the runtime cost moved somewhere worse.

With the placeholder, toggling a conditional swaps one node:

```clojure
[:div (when x [:span]) [:p "keep"]]
```

- `x` false, children are `[#text "", p]`
- `x` true, children are `[span, p]`
- `patch-node` replaces the text node with the span, index 1 is untouched

Measured: `p survived the toggle: true`.

Without it, the child count changes from 1 to 2. Unkeyed reconciliation patches
the common prefix by index, so `p` is matched against the span vnode, replaced,
and a fresh `p` appended. One swap becomes a cascade, and every sibling after
the conditional shifts.

## Decision

Keep the placeholder. Conditionals appear in far more renders than hydration
happens, and a cascade on every toggle costs more than a one-off misalignment on
the first render.

Applications that hydrate a list containing conditionals build the children as
one sequence, so the nils never become siblings:

```clojure
(concat (when loading? [[spinner ...]])
        (for [r rows] [:div.row ...])
        (when loading? [[spinner ...]]))
```

`concat` drops the nils, the child count matches the server's, and hydration
adopts.

## Notes

The placeholder could be made to survive server rendering by emitting a comment
node for it, which is what React does with `<!-- -->`. That needs `create-node`,
`patch-node` and `reagami.ssr` to agree on a marker node type. Not done, because
the workaround above costs applications one `concat`.

Unrelated but adjacent: `false` is not nil. `(and x [:span])` renders the text
`false` when `x` is false, in both `reagami.core` and `reagami.ssr`. They agree,
so hydration is unaffected.

## References

- Children loop: `create-vnode*` in `src/reagami/core.cljc`
- Nil child handling: `->html` in `src/reagami/ssr.cljc`
- Workaround in use: `app` in `examples/ssr/src/app.cljc`
