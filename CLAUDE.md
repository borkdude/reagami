# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Reagami is a minimal, zero-deps Reagent-like hiccup renderer. It is a **dual-target library**: the same source compiles to JS via [Squint](https://github.com/squint-cljs/squint) and via **ClojureScript** (shadow-cljs). The only public API is `render`.

## Source layout

- `src/reagami/core.cljc` — the entire library (~300 lines). `.cljc` with reader conditionals `#?(:squint ...)` / `#?(:cljs ...)` to paper over differences between the two targets. Edits to the library go here.
- `src/reagami/bench.cljc` — micro-benchmarks.
- `test/*.cljc` — tests, also dual-target.
- `reagami.mjs` (repo root) — the published npm entry point; just re-exports from `lib/reagami/core.mjs` (the Squint-compiled output).
- `lib/` — compiler output (gitignored except for what `package.json` `files` whitelists). Both Squint and shadow-cljs emit here.

Avoid treating any `.mjs` under `lib/` or at the repo root (except `reagami.mjs`) as a source of truth — they are generated. The `reagami/core.mjs` at the repo root is a committed snapshot but derived from `src/reagami/core.cljc` via Squint.

## Commands

Package manager is **pnpm**. Java is required only for the shadow-cljs/CLJS test pass.

- `pnpm install` — install deps.
- `pnpm watch` (or `bb dev`) — run Squint in watch mode; recompiles `.cljc` → `.mjs` on save.
- `pnpm build` — one-shot Squint compile of `src/`.
- `pnpm test` (or `bb test`) — Squint-compile then run `node lib/test_runner.mjs`. This is the primary test loop.
- `pnpm shadow-cljs compile reagami && node lib/tests.js` — CLJS-target test pass (matches CI).
- `bb publish` — runs tests, bumps patch, publishes to npm, pushes tags. Don't run without being asked.

### Running a single test

`lib/test_runner.mjs` accepts `--ns` and `--var`:

```
node lib/test_runner.mjs --ns basic-test
node lib/test_runner.mjs --var basic-test/render-test
```

(Test files are named `*_test.mjs` after Squint compile; functions ending in `-test` are auto-discovered.)

## Architecture notes

- **Patching algorithm**: `create-vnode*` builds a vnode tree from hiccup, then `patch` diffs old DOM against the new vnode tree. The algorithm is deliberately simple — see the [blog post](https://blog.michielborkent.nl/reagami.html) referenced in the README.
- **Attrs vs. properties**: some HTML attributes must be set as JS properties (`checked`, `disabled`, `selected`, `value`, `innerHTML`) — see the `properties` set in `core.cljc`. Event handlers (keys starting with `on`) are set as properties too. Everything else goes through `setAttribute`.
- **Default-prefixed attrs** (`:default-value`, `:default-checked`, …) are stripped of the `default` prefix and set as attributes so the component behaves as uncontrolled.
- **`:on-render` hook**: a lifecycle callback `(fn [node lifecycle data])` where `lifecycle` is `:mount` / `:update` / `:unmount`. Return values thread through as `data`. See README.
- **SVG** is entered when tag is `"svg"`; descendants are created with `createElementNS`. The `in-svg?` flag propagates through the recursion.
- **Reader-conditional idiom**: Squint doesn't have keyword objects — `name` is an identity function under `:squint`, and keyword-ish map keys come in as strings. `->attrs` under `:cljs` normalises hiccup attr maps to a JS object keyed by string names. Keep both branches in mind when editing.
- **Internal state on DOM nodes** is stored under namespaced keys (`reagami.core/attrs`, `reagami.core/props`, `reagami.core/vnode`, etc.). Under Squint these are JS Symbol-ish keys (`::attrs`); under CLJS they're string keys. The `attrs-key` / `props-key` / `vnode-key` constants handle the split.

## CI

`.github/workflows/ci.yaml` runs both the Squint test suite (`pnpm test`) and the shadow-cljs test suite (`node lib/tests.js`) on every push/PR. Both must pass.
