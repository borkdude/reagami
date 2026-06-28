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
children). A cheaper type-only two-end, like Vue ships, was not measured here, so
that variant stays an open question. It would only help heterogeneous-tag sibling
lists though, which are rare. Reagami's keyed path (longest increasing subsequence)
already handles remove and reorder with minimal DOM moves. The answer to "stable
middle inserts and removes" is `:key`.

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

- Experiment branch: `unkeyed-suffix-trim`
- Keyed path: `patch-keyed` and `lis-indices` in `src/reagami/core.cljc`
