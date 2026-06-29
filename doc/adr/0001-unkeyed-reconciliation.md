# 0001: Unkeyed reconciliation stays index based

Date: 2026-06-28

Status: Accepted

## Context

Unkeyed children are reconciled by index: patch the common prefix in place, then
add or remove the tail. An insert or remove away from the end shifts every later
node by one position, so each gets its content rewritten even though only one node
actually changed.

Question raised: would computing a longest common subsequence by tag and attrs
first (a two-end prefix and suffix trim) let inserts and removes in the middle
touch only the changed nodes?

## Experiment

Branch `unkeyed-suffix-trim` (commit 640fcac). Added a two-end trim to
`patch-unkeyed`: a structural `vnode=?` (tag, text, attrs, children, ignoring
handler props) finds the matching prefix and suffix, those nodes are patched in
place, and only the differing middle is inserted or removed. Aligned nodes are
still patched, so handlers stay correct.

Measured with js-framework-benchmark, non-keyed reagami, headless Chrome, CPU
throttle, 10 iterations, same machine. Median ms.

| op | index-diff | two-end trim | delta |
|---|---|---|---|
| create 1k | 27.3 | 27.7 | +1% |
| replace 1k | 17.8 | 19.9 | +12% |
| update 10th | 42.7 | 47.5 | +11% |
| select | 30.7 | 39.2 | +28% |
| swap | 37.5 | 41.2 | +10% |
| remove | 31.5 | 27.5 | -13% |
| create 10k | 296 | 291 | -2% |
| append 1k | 36.8 | 40.8 | +11% |
| clear | 10.6 | 9.4 | -11% |

## Decision

Keep the index based diff. Do not adopt the two-end trim.

Only `remove` improved (-13%), the one op that deletes a node away from the end.
Every render-heavy op regressed +10 to +28%. The `vnode=?` scan walks the tree to
locate divergence, which costs about as much as patching, and runs on every
render. It only pays off when it prevents a cascade. Worst case is `select`
(+28%): one row changes class, so the scan compares nearly all rows to find it
where index-diff just patches in place.

The regression is specific to the structural `vnode=?` compare (tag, attrs,
children). A cheaper type-only two-end, like Vue ships, was measured separately
(see Follow-up): it taxes the common uniform-tag case for only a niche
heterogeneous win, so it was dropped too. Reagami's keyed path (longest increasing
subsequence) already handles remove and reorder with minimal DOM moves. The answer
to "stable middle inserts and removes" is `:key`.

## Follow-up: tag-only two-end

Branch `unkeyed-tag-twoend`. Replaced the structural `vnode=?` with a cheap
`alignable?` (same tag, or both text), the variant Vue ships. Same measurement
setup, plus a heterogeneous micro-bench.

Size, non-keyed reagami bundle, squint + esbuild min, gzip: index-diff 7825 B,
tag-only 8028 B, +203 B (+2.0%).

Perf, js-framework-benchmark non-keyed, uniform tags, real Chrome, median ms:

| op | index-diff | tag-only | delta |
|---|---|---|---|
| create 1k | 26.9 | 27.0 | +0.6% |
| replace 1k | 17.4 | 18.8 | +7.4% |
| update 10th | 41.2 | 45.1 | +9.7% |
| select | 30.7 | 33.7 | +9.8% |
| swap | 37.8 | 40.3 | +6.6% |
| remove | 31.8 | 32.5 | +2.2% |
| create 10k | 294 | 290 | -1.7% |
| append | 37.0 | 38.1 | +3.0% |
| clear | 10.8 | 10.4 | -3.3% |

The per-node `alignable?` check in the prefix loop runs on every patch. For a
uniform-tag list, which is almost every real list and all of the above, it never
helps but costs +7 to +10% on the patch-heavy ops.

Heterogeneous micro-bench, prepend a `div` to 1000 distinct `span`, jsdom:
index-diff 0.609 ms, tag-only 0.566 ms, about 7% faster. Modest, and jsdom does no
layout, so a real browser pays the prepend reflow either way.

So the common case is a loss on both size and perf, and the win is niche and small.
Dropped. This matches React (pure index, no two-end) and Vue 3 (compiler-known
unkeyed lists go through the plain index `patchUnkeyedChildren`; the two-end is only
paid on the general path that already needs `isSameVNodeType` for keys).

## Prior art

Checked Vue and React sources (June 2026):

- React (`reconcileChildrenArray`, `ReactChildFiber.js`): forward-only index pass,
  no two-end. Its own comment: "This algorithm can't optimize by searching from both
  ends ... we'll just live with hitting the bad case for every insert/move." The
  key-to-index map fallback runs only for keyed reorders. Unkeyed `[a a a] -> [b a a a]`
  recreates the bumped node, same as reagami.
- Vue 2 (`updateChildren`, `sameVnode` in `patch.ts`): four-pointer two-end for every
  array. `sameVnode` matches unkeyed by tag, so `[a a a] -> [b a a a]` reuses the three
  `a` and inserts `b`.
- Vue 3 (`renderer.ts`): two paths. A compiler-marked unkeyed `v-for`
  (`UNKEYED_FRAGMENT`) uses `patchUnkeyedChildren`, a pure index diff like reagami. Any
  other array goes through `patchKeyedChildren` (two-end + LIS) using `isSameVNodeType`
  (`n1.type === n2.type && n1.key === n2.key`), so unkeyed matches by type and the
  heterogeneous insert is handled.

So only React (and Vue 3's optimized path) is purely index based. Vue matches unkeyed
by tag/type at the ends, but compares only the node type (O(1)), not the structural
`vnode=?` this experiment measured.

## References

- Experiment branches: `unkeyed-suffix-trim` (structural), `unkeyed-tag-twoend` (tag-only)
- Keyed path: `patch-keyed` and `lis-indices` in `src/reagami/core.cljc`
