# Keyed patch algorithm

Step through the keyed patch algorithm from the Reagami README, one DOM
operation at a time.

To run it:

```shell
pnpm install
pnpm dev
```

http://localhost:5175

The top row is the children in the DOM, the bottom row is the new children with
their source value. Green stays in place, orange moves, blue is created. The
buttons switch between cases, a label in parentheses is an unkeyed child.

`src/app.cljs` copies `lis-indices` from `reagami.core` and renders every frame
with Reagami itself.

To share the demo on the squint playground:

```shell
bb playground-url
```
