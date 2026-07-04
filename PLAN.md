# Reactive layer experiment (branch reactive-prototype)

Status as of 2026-07-04. Question: can reagami serve as the kernel for a
reagent-like library, and what does core need for that.

## What is on this branch

- `prototype/reactive.cljs`: reagent-like layer using only public reagami API.
  Custom `RAtom` (squint `IDeref`/`IReset`/`IWatchable`, `swap!` works via the
  deref + reset! fallback), deref tracking via a dynamic var bound during
  render, per-component re-render, sibling isolation, props with equality
  check, unmount cleanup.
- `prototype/reactive_test.cljs`: 9 tests, 46 assertions. Run with
  `pnpm squint compile && node lib/reactive_test.mjs`.
- `prototype/demo.cljs` + `demo.html`: todo demo with per-component render
  counters. Note: Chrome automation cannot reach localhost here, demo was
  verified via a claude.ai artifact with an esbuild-inlined bundle.
- Core change (commit 1a63a6e): experimental fragment node support.

## Core shortcomings found (as substrate, not as renderer)

1. `patch-node` never refreshed a node's `:on-render` ref, mount-time closure
   frozen. Fixed for fragments in 1a63a6e (ref refresh preserves mount state).
   Still true for element nodes.
2. No unmount cascade for nested render roots. `ref-registry` is per root and
   only runs during that root's render pass. Layer works around it with an
   instance registry walk. Open design question for core.
3. Components paid one wrapper element each. Fixed by fragments.

## Fragment design

`[:<> {:on-render f}]` creates a comment-node anchor. Content is rendered with
`render-fragment` and lives as real siblings in the parent, tracked in an
owned-nodes array on the anchor and marked with an owner key. The diff treats
anchor + owned as one logical child (`logical-children`, `insert-logical`,
`remove-logical`, `replace-logical`), keyed moves included. `fragment-nodes`
exposes the range. Fragment children from the parent side throw, static
grouping already works via seqs. Multiple top-level forms per component work
(li rows in ul, tr in tbody, verified by tests).

## Cost

Minified + gzip -9, old vs new core:

- core module: 2896 -> 3511 gz (+615, +21%)
- full bundle incl squint core: 5860 -> 6461 gz
- tree-shaken app importing only `render`: 5777 -> 6137 gz (+360, +6%)

Bench vs refreshed old.cljc baseline: no regression, jsdom shows 1.19-1.22x
faster (likely snapshot array vs live NodeList in jsdom, verify in browser).

## What other libs do

- replicant: no fragment node at all, seqs flatten, root render accepts a
  list, lifecycle hooks element-only.
- React/Preact: fragment is a vdom grouping marker, key only, no attrs, no
  refs, no lifecycle, no DOM cost.
- Vue 3: implicit fragment for multi-root templates, two anchor nodes
  delimiting the range, key only.

Nobody offers what our fragment does: an independently re-renderable sub-root
with a lifecycle hook. They get subtree re-render from components. Reagami has
no components, which is exactly why the layer needs it.

## Decision to make

1. Keep main replicant-class minimal: no fragments in core, layer forks or
   uses a display:contents wrapper (fixes layout, not selectors/tables).
2. Ship fragments in core as the substrate feature: costs 615 gz raw module,
   360 gz tree-shaken. Trimming key support and the throw wins back maybe
   100-200 gz, the bulk is the logical-child helpers in the diff paths.

## Before a PR to main (if option 2)

- Core-level fragment tests (current coverage lives in prototype tests).
- Decide parent-side fragment children (currently throw).
- Browser bench, not just jsdom.
- Consider ref refresh for element nodes too (shortcoming 1 in general).
- Unmount cascade for nested roots remains open (shortcoming 2).
