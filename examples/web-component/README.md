# Web component

A `<todo-list>` element built with Reagami, used from Reagami and from plain
JavaScript.

Run it:

```shell
bb dev
```

- http://localhost:5174 renders the element from Reagami
- http://localhost:5174/vanilla.html drives the same element from plain JavaScript
- http://localhost:8081 renders it on Babashka and hydrates it

## The element

`src/todo_list.cljs` defines `<todo-list>`. Reagami renders its shadow root, so
the list keeps its own items and reports what changed.

- `label` and `placeholder` come in as attributes, with a property that mirrors
  each one
- `addItem` adds an item, `items` returns the current items
- the items are a Clojure vector of maps in an atom, which JavaScript reads as
  an array of plain objects with no conversion
- `item-added` and `item-removed` go out, both bubbling and composed

A property set on the element before its class is defined hides the accessor, so
`connectedCallback` calls `upgradeProperty` for each one.

## From Reagami

`src/app.cljs` sets the attributes and listens for the events.

```clojure
[:todo-list {:label label
             :placeholder "What needs doing?"
             :on-item-added #(log! (.. % -detail -text))}]
```

The tag has a hyphen, so `:label` becomes an attribute rather than a JS property.
`item-added` has no `on*` property, so Reagami listens with `addEventListener`.

## From plain JavaScript

`vanilla.js` uses the same element with no Reagami and no ClojureScript.

```js
const list = document.createElement("todo-list");
list.setAttribute("label", "Groceries");
list.placeholder = "What needs doing?";
list.addEventListener("item-added", (e) => console.log(e.detail.text));
list.addItem("milk");
```

## Server side

`src/server.clj` renders the page on Babashka and `src/client.cljs` hydrates it.
A shadow root has no HTML form, so the server sends the tag and its attributes
and the element fills itself in the browser.

```html
<todo-list label="Groceries" placeholder="What needs doing?"></todo-list>
```

Hydration builds 0 nodes. The page prints the count.

## What Reagami does not do here

Reagami renders the shadow root. The class, the attributes, the upgrade guard and
the events are the platform's, and this file writes them out by hand.

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
