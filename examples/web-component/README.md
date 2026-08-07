# Web component

A `<todo-list>` element built with Reagami, used three ways: from Reagami in
ClojureScript, from Reagami in JavaScript, and from plain JavaScript.

Run it:

```shell
pnpm install
pnpm dev
```

- http://localhost:5174 uses the element from Reagami, in ClojureScript
- http://localhost:5174/reagami-js.html does the same with Reagami called from JavaScript
- http://localhost:5174/vanilla.html drives the element with no Reagami at all

Each page builds its own element, so the lists do not share items.

## The element

`src/todo_list.cljs` defines `<todo-list>`. Reagami renders its shadow root, so
the list keeps its own items and reports what changed.

- `label` comes in as an attribute, with a property that mirrors it
- `addItem` adds an item, `items` returns the current items
- the items are a Clojure vector of maps in an atom, which JavaScript reads as
  an array of plain objects with no conversion
- `item-added`, `item-changed` and `item-removed` go out, all bubbling

Click the circle to mark an item done, and the bin to delete it.

## From Reagami, in ClojureScript

`src/app.cljs` sets the attributes and listens for the events.

```clojure
[:todo-list {:label label
             :on-item-added #(log! (.. % -detail -text))}]
```

The tag has a hyphen, so `:label` becomes an attribute rather than a JS property.
`item-added` has no `on*` property, so Reagami listens with `addEventListener`.

## From JavaScript

`src/reagami-js.js` does what `src/app.cljs` does, in JavaScript. Hiccup is
arrays and objects, so Reagami needs no ClojureScript to use.

```js
["todo-list", { label: state.label,
                "on-item-added": (e) => log(`added ${e.detail.text}`) }]
```

A standard event is its DOM property name, so `onclick` and `oninput`. A custom
event keeps its dashes, so `on-item-added` listens for "item-added". Reagami
re-renders the page around the element and the element keeps its own items.

## From plain JavaScript

`src/vanilla.js` uses the element with no Reagami and no ClojureScript.

```js
const list = document.createElement("todo-list");
list.setAttribute("label", "Groceries");
list.label = "Groceries";   // or the property, which mirrors it
list.addEventListener("item-added", (e) => console.log(e.detail.text));
list.addItem("oat milk");
```

## What Reagami does not do here

Reagami renders the shadow root. The class, the attribute and the events are the
platform's, and this file writes them out by hand.

An element that renders into its light DOM instead of a shadow root loses that
content on the next render. Reagami owns the children of the nodes it renders.
