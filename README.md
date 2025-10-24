# Reagami

Fold your state into the DOM!

A minimal [Reagent](https://github.com/reagent-project/reagent)-like in 100 lines of [squint](https://github.com/squint-cljs/squint).

## Usage

Reagemi is intended to be used with [Squint](https://github.com/squint-cljs/squint).

Quickstart example:

``` clojure
(ns my-app
  (:require ["https://esm.sh/reagami@0.0.6" :refer [render]]) )

(def state (atom {:counter 0}))

(defn my-hiccup []
  [:div
   [:div "Counted: " (:counter @state)]
   [:button {:on-click #(swap! state update :counter inc)}
    "Click me!"]])

(or (js/document.querySelector "#app"))
(doto (js/document.createElement "div")
  (set! -id "app")
  (js/document.body.prepend))

(defn do-render []
  (render (js/document.querySelector "#app") [my-hiccup]))

(add-watch state ::render (fn [_ _ _ _]
                            (do-render)))

(do-render)
```

Reagami supports:

- Building small reactive apps with the only dependency being squint. Smallest app after minification is around 3.5kb gzip.
- Rendering [hiccup](https://github.com/weavejester/hiccup) into a container DOM node. The only public function is `render`.
- Event handlers via `:on-click`, `:on-input`, etc. These get translated to `(.addEventListener node "click" f)`.
- Id and class short notation: `[:div#foo.class1.class2]`
- Disabling properties with `false`: `[:button {:disabled (not true)}]`
- `:style` maps: `{:style {:background-color :green}}`

Reagami does NOT support:

- Auto-rerendering by watching custom atoms. Instead you use `add-watch` on regular atoms! :)

Reagami uses a very basic patching algorithm explained in [this](TODO) blog
post. It may become more advanced in the future, but the (fun) point of this
library at this point is that it's small, underengineered and thus suited for
educational purposes.

For a more fully features version of Reagent in squint, check out [Eucalypt](https://github.com/chr15m/eucalypt)

## License

MIT
