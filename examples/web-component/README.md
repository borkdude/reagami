# Web component

A `<todo-list>` element built with Reagami, used from Reagami and from plain
JavaScript.

Run it:

```shell
pnpm install
pnpm dev
```

- http://localhost:5174 renders the element from Reagami
- http://localhost:5174/vanilla.html drives the same element from plain JavaScript

## The element

`src/todo_list.cljs` defines `<todo-list>`. Reagami renders its shadow root, so
the list keeps its own items and reports what changed.

- `label` comes in as an attribute, with a property that mirrors it
- `addItem` adds an item, `items` returns the current items
- the items are a Clojure vector of maps in an atom, which JavaScript reads as
  an array of plain objects with no conversion
- `item-added`, `item-changed` and `item-removed` go out, all bubbling

Click the circle to mark an item done, and the bin to delete it.

## From Reagami

`src/app.cljs` sets the attributes and listens for the events.

```clojure
[:todo-list {:label label
             :on-item-added #(log! (.. % -detail -text))}]
```

The tag has a hyphen, so `:label` becomes an attribute rather than a JS property.
`item-added` has no `on*` property, so Reagami listens with `addEventListener`.

## From plain JavaScript

`vanilla.js` uses the same element with no Reagami and no ClojureScript.

```js
const list = document.createElement("todo-list");
list.setAttribute("label", "Groceries");
list.label = "Groceries";   // or the property, which mirrors it
list.addEventListener("item-added", (e) => console.log(e.detail.text));
list.addItem("milk");
```

## What Reagami does not do here

Reagami renders the shadow root. The class, the attributes and the events are the
platform's, and this file writes them out by hand.

An attribute carries a string. To hand an element an array or a map, set a
property from `:on-render`:

```clojure
[:my-list {:on-render (fn [{:keys [node state save]}]
                        (when-not (identical? state items)
                          (set! (.-items node) items)
                          (save items)))}]
```

An element that renders into its light DOM instead of a shadow root loses that
content on the next render. Reagami owns the children of the nodes it renders.
