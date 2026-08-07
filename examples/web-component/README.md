# Web components

Reagami builds a `<todo-list>` element. Three pages use it: two with Reagami,
one without.

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

`src/todo_list.cljs` defines `<todo-list>`. Squint compiles it, and Reagami
renders the shadow root.

- `label` arrives as an attribute. A property mirrors it.
- `addItem` adds an item. `items` returns the current items.
- The items are a Clojure vector of maps in an atom. JavaScript reads them as an
  array of plain objects, with no conversion.
- The element sends `item-added`, `item-changed` and `item-removed`. All three
  bubble.

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

`src/reagami-js.js` does what `src/app.cljs` does. Hiccup is arrays and objects,
so Reagami does not need Squint.

```js
["todo-list", { label: state.label,
                "on-item-added": (e) => log(`added ${e.detail.text}`) }]
```

A handler is `on-` and the name of the event, so a custom event called `rated`
is `on-rated`.

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
