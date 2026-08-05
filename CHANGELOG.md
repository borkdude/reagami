# Changelog

[Reagami](https://github.com/borkdude/reagami): A minimal zero-deps Reagent-like for Squint and CLJS

## Unreleased

- Fix a memory leak. Reagami kept the render root, and its nodes with an `:on-render` hook, in a strong map. A dropped root is now collected.

- `render` now compares against the vnode tree of the previous render instead of reading the DOM. Patching is around 5% faster overall and up to 25% faster on update-heavy operations. See [benchmarks](https://github.com/borkdude/reagami/blob/main/doc/benchmarks.md).
- Reagami owns the children of the nodes it renders. It no longer repairs a node that other code adds, removes or reorders.
- Fix `:on-render` state. The state is per node now. Nodes that share one handler each get their own `:mount` call and their own data.
- Fix `:on-render` to call the handler of the newest render. A handler written as an inline function now sees the values of the current render.
- Fix `:on-render` when a render drops it. Reagami calls the handler once with `:unmount` and then stops. Before, it called `:update` on every render.

## v0.2.38 (2026-07-31)

- Add `reagami.ssr` to render hiccup to an HTML string on the JVM, Babashka, Squint and CLJS. See [Server-side rendering](https://github.com/borkdude/reagami#server-side-rendering).
- `reagami.core/render` (the regular render function) now hydrates a server-rendered page. It adopts the existing DOM instead of clearing the root.
- Add [create-reagami-app](https://github.com/borkdude/reagami/tree/main/create-reagami-app). Run `npm create reagami-app my-app` to create a Vite project with hot reload and a browser nREPL.

## v0.1.37 (2026-06-29)

- Fix [#21](https://github.com/borkdude/reagami/issues/21): support keyed children via `:key` for stable node identification. Keys enable more efficient diffing and better node reuse. See [Patch algorithm](https://github.com/borkdude/reagami#patch-algorithm) for detailed explanation.
- Add [benchmarks](https://github.com/borkdude/reagami#benchmarks). Tl;dr: Reagami can compete with CLJS React wrappers and React-free solutions, while being the leanest in output size. Performance is generally better in Squint compared to CLJS.
- Add [webcomponent example](https://squint-cljs.github.io/squint/?src=gzip%3AH4sIAAAAAAAAE2VSsW7bMBDd9RVXeaEGSZ05FQgKZGjQIdkEDzR5juhQJM07NhUM%2F3shiW5tlBOP9967h3sUnmCaWxVjBSBkwnO2CSsAGOqROZLse6Spo7FPqN7VZGuQiqAU%2BxVJ52w9dzokBJnwiAkGg0ftFNF%2B3zRVJW4lvMzfHU7oeZmHvxm9IThR%2F%2Fz28qN0mqV1tOgMtC5o5VpixQhCcZjgInXInjHB1%2BsiDSB08MQpaw4JBh4trbZAUI6Ymu2ujGk%2FFevxUVPKhN5ggp3oym0RaDbln4cTai4jPGpG86ScOyj98TDokbp6Ki%2F3qLKz%2Fg68NgAGaewvGOQhMwcPFxl8q53VH7AT9Knil0fTOZrV%2B20R1uvmWqTKqZ9W%2BoqAGsRf7Ld7pWZ%2FYw3S%2BpgZLjI6pXEMbrFY8xwRKEzIo%2FXv9XVJc4mzM3i0HkFn4jCV3AjkNLe4Ff%2BCLvF7uK1kX%2F2%2FDHGi3gSdF0J3zpjmV3S4JlrvVIx1A8Od%2BvapNm7zB8tsjFnEAgAA)
- Make CLJS `:lite-mode` once again compatible and test CLJS `:lite-mode` in CI
- Bump squint to latest

## v0.0.35 (2025-11-06)

- Performance enhancements
- treat `innerHTML` as a property rather than an attribute

## v0.0.34 (2025-11-06)

- Drop support for camelCased properties / (css) attributes
- Fix `:default-value` in input range

## v0.0.33 (2025-11-05)

- Support data param in `:on-render`

## v0.0.32 (2025-11-05)

- Support default values for uncontrolled components

## v0.0.31 (2025-11-04)

- Fix child count mismatch

## v0.0.30 (2025-11-04)

- Fix re-rendering/patching of subroots

## v0.0.29 (2025-11-03)

- Add `:on-render` hook for mounting/updating/unmounting third party JS components
