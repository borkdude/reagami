# Reagami

Fold your state into the DOM!

Write you a [reagent](https://github.com/reagent-project/reagent) in 100 lines of [squint](https://github.com/squint-cljs/squint) CLJS code.
Or at least something which looks a lot like it since it contains a lot less features.

## Usage

Reagemi is intended to be used with [squint](https://github.com/squint-cljs/squint).
Quickstart example:

``` clojure
(require '["reagami" :refer [render]])

(def state (atom {:counter 0}))

(defn my-hiccup []
  [:div
    [:div "Counted: " (:counter @state)]
    [:button {:on-click #(swap! counter update :counter inc)}
      "Click me!])

(render (js/document.querySelector "#app") [my-hiccup])
```

Reagami only supports:

- Rendering [hiccup](https://github.com/weavejester/hiccup) into a container DOM node. The only public function is `render`:


- Event handlers via `:on-click`, `:on-input`, etc. These get translated to `(.addEventListener node "click" f)`.
- Id and class short notation: `[:div#foo.class1.class2]`
- Disabling properties with `false`: `[:button {:disabled (not true)}]`
- `:style` maps: `{:style {:background-color :green}}`

Reagami does NOT support:

- Auto-rerendering by watching custom atoms. Instead you use `add-watch` on regular atoms.

Reagami uses a very basic patching algorithm explained in [this](TODO) blog
post. It may become more advanced in the future, but the (fun) point of this
library at this point is that it's small, underengineered and thus suited for
educational purposes.









