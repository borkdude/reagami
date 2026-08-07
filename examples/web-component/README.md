# Web components

Use reagami to create a `<todo-list>` custom element backed by a web component.

To run it:

```shell
pnpm install
pnpm dev
```

- http://localhost:5174 uses the element from Reagami, in Squint
- http://localhost:5174/reagami-js.html uses it from Reagami, in JavaScript
- http://localhost:5174/vanilla.html uses it with no Reagami

## The element

`src/todo_list.cljs` defines `<todo-list>` in Squint

- `label` is implemented as an attribute, mirrored by a property.
- `addItem` adds an item
- `items` returns the current items
- The element communicates via `item-added`, `item-changed` and `item-removed` events.

## From Reagami, in Squint

`src/app.cljs` sets the attribute and listens for the events.

```clojure
[:todo-list {:label label
             :on-item-added #(log! (.. % -detail -text))}]
```

## From Reagami, in JavaScript

`src/reagami-js.js` does exactly what `src/app.cljs` does but in JavaScript. Hiccup is arrays and objects,
so Reagami does not need Squint.

```js
["todo-list", { label: state.label,
                "on-item-added": (e) => log(`added ${e.detail.text}`) }]
```

## Without Reagami

`src/vanilla.js` uses the element from plain JavaScript.

```js
const list = document.createElement("todo-list");
list.setAttribute("label", "Groceries");
list.label = "Groceries";   // or the property, which mirrors it
list.addEventListener("item-added", (e) => console.log(e.detail.text));
list.addItem("oat milk");
```
