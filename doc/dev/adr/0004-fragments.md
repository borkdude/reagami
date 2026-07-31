# 0004: Fragments are deferred to a branch

Date: 2026-07-31

Status: Deferred. Implemented and tested on the `fragments` branch.

## Context

A fragment is a hiccup node that renders its children into the parent without an
element of its own:

```clojure
[:dl (for [[term d] entries]
       [:<> [:dt term] [:dd d]])]
```

The wrapper element that would otherwise be needed is illegal inside `dl`,
`table`, `select`, and any flex or grid container whose children carry layout.

Reagami already gives a nil child a comment node so it keeps its slot, per ADR
0002. A fragment reuses that anchor. It builds to a comment plus its children,
spliced flat into the parent's child array, so patch never sees a fragment at
all.

## What the branch implements

Two commits of behavior on top of the anchor.

Plain fragments. `[:<> ...]` builds an anchor comment followed by its children.
`reagami.ssr` emits `<!---->` for the same node, so a fragment page hydrates by
adoption like any other.

Keyed fragments. `[:<> {:key k} ...]` composes the fragment key into every
child, so the flat keyed patch moves the whole unit with no range bookkeeping.
A child's own key nests behind one NUL separator and a positional one behind
two, so the two cannot collide. Any attribute other than `:key` throws.

## Cost

Measured on a minimal counter application, an atom plus `render` plus a
three element view, bundled with esbuild and squint core included.

| | gzip |
|---|---|
| main | 6142 B |
| hydration only | 6475 B |
| hydration and fragments | 6667 B |

Fragments cost 192 B gzip, 37 percent of what the whole branch adds. On the core
bundle alone the same two commits are 201 B, 3308 to 3509.

## Prior art

Fragments are a client side reconciliation feature, not a hiccup feature.

hiccup 2.0.0 has no fragment. `:<>` parses as an element name and renders
malformed HTML:

```clojure
(str (h/html [:<> [:dt "a"] [:dd "b"]]))
;; => "<<>><dt>a</dt><dd>b</dd></<>>"
```

It does not need one. A one shot string renderer splices a seq already, and
there is nothing to reconcile later.

React, Preact and Vue all have fragments and all accept a key on one. React
taxes it: the `<>` shorthand takes no props, so a keyed fragment has to be
written `<React.Fragment key={...}>`. Reagent maps `:<>` onto `react/Fragment`
and passes its props through.

So a user arriving from any client side library expects `[:<> {:key k} ...]` to
work, and a user arriving from hiccup expects nothing at all.

## Why deferred

The merge this ADR lands in is about hydration. Fragments are a separate
feature that reached a working state in the same branch, which is the only
reason they were together.

The two namespaces fail differently without fragments. `reagami.core` throws,
because `createElement` rejects the name:

```
DOMException: "<>" did not match the Name production
```

`reagami.ssr` does not throw. It treats `<>` as an element name and emits
`<div><<>><p>a</p></<>></div>`, which is what hiccup 2.0.0 does with the same
input. A server rendered fragment is malformed and reports nothing.

A special case throw in `reagami.ssr` would cost about five lines in a namespace
that no client bundle loads. It was left out so that `reagami.ssr` keeps
answering the way hiccup does for every tag name, rather than singling one out.

Adding fragments later is additive. Nothing in the hydration work has to change
to accept them.

## Status

Deferred. The work is on the `fragments` branch, with tests in
`test/ssr_test.cljc`, `test/ssr_jvm_test.cljc` and `test/ssr_corpus.cljc`.

## References

- Branch: `fragments`
- Anchor comment: ADR 0002, `create-vnode*` in `src/reagami/core.cljc`
- hiccup: `normalize-element*` in `hiccup/compiler.clj`, version 2.0.0
- Reagent: `fragment-element` in `reagent/impl/template.cljs`
