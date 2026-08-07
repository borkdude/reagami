# Web component

Reagami builds a `<todo-list>` element. Three pages use it: two with Reagami,
one without. The ClojureScript here is compiled by Squint.

Run it:

```shell
pnpm install
pnpm dev
```

- http://localhost:5174 uses the element from Reagami, in Squint
- http://localhost:5174/reagami-js.html uses it from Reagami, in JavaScript
- http://localhost:5174/vanilla.html uses it with no Reagami

Each page builds its own element. The lists do not share items.

## The element

`src/todo_list.cljs` defines `<todo-list>`. Reagami renders the shadow root of
the element. The element keeps its own items and reports each change.

- `label` arrives as an attribute. A property mirrors it.
- `addItem` adds an item. `items` returns the current items.
- The items are a Clojure vector of maps in an atom. JavaScript reads them as an
  array of plain objects, with no conversion.
- The element sends `item-added`, `item-changed` and `item-removed`. All three
  bubble.

Click the circle to mark an item done. Click the bin to delete the item.

## From Reagami, in Squint

`src/app.cljs` sets the attribute and listens for the events.

```clojure
[:todo-list {:label label
             :on-item-added #(log! (.. % -detail -text))}]
```

The tag holds a hyphen, so `:label` becomes an attribute and not a JS property.
The element has no `onitemadded` property, so Reagami listens with
`addEventListener`.

## From Reagami, in JavaScript

`src/reagami-js.js` does what `src/app.cljs` does, in JavaScript. Hiccup is
arrays and objects, so Reagami does not need Squint.

```js
["todo-list", { label: state.label,
                "on-item-added": (e) => log(`added ${e.detail.text}`) }]
```

A handler is `on-` and the name of the event. This is the same for a standard
event and for a custom one, so `on-input` and `on-item-added`. The `on-` is a
prefix and not part of the name. A custom event called `rated` is `on-rated`.

Reagami renders the page around the element. The element keeps its own items.

## Without Reagami

`src/vanilla.js` uses the element from plain JavaScript.

```js
const list = document.createElement("todo-list");
list.setAttribute("label", "Groceries");
list.label = "Groceries";   // or the property, which mirrors it
list.addEventListener("item-added", (e) => console.log(e.detail.text));
list.addItem("oat milk");
```

## What Reagami does not do here

Reagami renders the shadow root. The class, the attribute and the events come
from the platform, and this file writes them by hand.

An element that renders into its light DOM loses that content on the next
render. Reagami owns the children of the nodes that it renders.
