# Keyed vs old reconciliation demo

Side-by-side: the **old** reagami (no key support, commit `8f12769`) vs the **new** keyed
reconciliation. Same 2000-row keyed list in both columns. Each row has an
uncontrolled `<input>` and a CSS-animated dot, so node reuse vs recreation is
visible.

## Run

```
python3 -m http.server 8091   # in this dir
# open http://localhost:8091/index.html
```

Type text into a few inputs in BOTH columns, then click **Prepend**, **Remove
middle**, or **Shuffle**.

- **OLD**: rebuilds on count change / cannot move keyed nodes -> typed text
  disappears, all dot animations restart in sync (a flash), higher render time.
- **NEW**: reuses nodes by key (LIS minimizes moves) -> typed text stays with
  its row, dots keep their own animation phase, lower render time.

## Rebuild

```
./build.sh
```

Uses `squint-old.edn` (points at a worktree of `8f12769`) and `squint-new.edn` (this
branch) to produce `bundle-old.js` and `bundle-new.js`.
