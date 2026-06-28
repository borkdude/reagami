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

This matches mainstream vdom libs, which keep unkeyed reconciliation index based
and use keys for stable identity. Reagami's keyed path (longest increasing
subsequence) already handles remove and reorder with minimal DOM moves. The answer
to "stable middle inserts and removes" is `:key`.

## References

- Experiment branch: `unkeyed-suffix-trim`
- Keyed path: `patch-keyed` and `lis-indices` in `src/reagami/core.cljc`
