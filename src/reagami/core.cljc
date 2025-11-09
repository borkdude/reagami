(ns reagami.core
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude []}}}})

(def svg-ns "http://www.w3.org/2000/svg")

(defn- parse-tag
  "From hiccup, thanks @weavejester"
  [^String tag]
  (let [id-index    (let [index (.indexOf tag "#")] (when (pos? index) index))
        class-index (let [index (.indexOf tag ".")] (when (pos? index) index))]
    [(cond
       id-index    (.substring tag 0 id-index)
       class-index (.substring tag 0 class-index)
       :else tag)
     (when id-index
       (if class-index
         (.substring tag (inc id-index) class-index)
         (.substring tag (inc id-index))))
     (when class-index
       (.substring tag (inc class-index)))]))

(def properties (js/Set. ["checked" "disabled" "selected"
                          "value" "innerHTML"]))

(defn property? [^js x]
  (.has properties x))

(do
  #?@(:squint []
      :cljs [(defn keyword->str [k]
               (if (keyword? k)
                 (name k)
                 k))]))

#?(:squint (defn array-seq [s]
             s))

#?(:squint (defn name [s]
             s))

(defn hiccup-seq? [x]
  (and (not (string? x))
       (seq? x)
       (not (vector? x))))

(defn- move-to-back [o v]
  (when (js-in v o)
    (let [value (aget o v)]
      (js-delete o v)
      (aset o v value))))

(def ^:private on-render-key #?(:squint ::on-render
                                :cljs "reagami.core/on-render"))

(def ^:private attrs-key #?(:squint ::attrs
                            :cljs "reagami.core/attrs"))

(def ^:private props-key #?(:squint ::props
                            :cljs "reagami.core/props"))

(def ^:private vnode-key #?(:squint ::vnode
                            :cljs "reagami.core/vnode"))

(def ^:private root-key #?(:squint ::root
                           :cljs "reagami.core/root"))

(def ^:private is-run-key #?(:squint ::is-run
                             :cljs "reagami.core/is-run"))

(def ^:private data-key #?(:squint ::data
                           :cljs "reagami.core/data"))

(do
  #?@(:squint []
      :cljs [(defn ->attrs [m]
               (let [obj #js {}]
                 (run! (fn [[k v]]
                         (let [k (name k)
                               v (cond (keyword? v)
                                       (name v)
                                       (map? v)
                                       (->attrs v)
                                       :else v)]
                           (aset obj k v)))
                       m)
                 obj))]))

(defn- create-vnode*
  [hiccup in-svg?]
  (cond
    (or (nil? hiccup)
        (string? hiccup)
        (number? hiccup)
        (boolean? hiccup))
    #js {:tag "#text"
         :text (str hiccup)}
    (vector? hiccup)
    (let [#?@(:squint [] :cljs [hiccup (into-array hiccup)])
          tag (aget hiccup 0)
          children-idx 1
          #?@(:squint []
              :cljs [tag (if (keyword? tag)
                           (name tag)
                           tag)])
          [tag id class] (if (string? tag) (parse-tag tag) [tag])
          classes (when class (.split class "."))
          first-child (aget hiccup children-idx)
          attr-idx (if (map? first-child) 1 -1)
          children-idx (if (identical? -1 attr-idx)
                         children-idx (inc children-idx))
          in-svg? (or in-svg? (= "svg" tag))
          node (if (fn? tag)
                 (do
                   (let [;; note: .slice was even faster in benchmarks than .shift-mutating
                         res (apply tag (.slice hiccup 1))]
                     (create-vnode* res in-svg?)))
                 (let [new-children #js []
                       node #js {:type :element :svg in-svg?
                                 :tag (if in-svg?
                                        tag
                                        (.toUpperCase tag))
                                 :children new-children}
                       modified-props #js {}
                       modified-attrs #js {}]
                   (aset node props-key modified-props)
                   (aset node attrs-key modified-attrs)
                   (dotimes [i (- (alength hiccup) children-idx)]
                     (let [child (aget hiccup (+ i children-idx))]
                       (if (hiccup-seq? child)
                         (doseq [x child]
                           (.push new-children (create-vnode* x in-svg?)))
                         (.push new-children (create-vnode* child in-svg?)))))
                   (when-not (identical? -1 attr-idx)
                     (let [attrs (aget hiccup 1)
                           #?@(:squint []
                               :cljs [attrs (->attrs attrs)])
                           entry-names (js/Object.getOwnPropertyNames attrs)
                           entry-count (alength entry-names)]
                       ;; fix for input type range where min / max must be in place before value / default-value
                       (when (or (js-in "max" attrs) (js-in "min" attrs))
                         (move-to-back attrs "default-value")
                         (move-to-back attrs "value"))
                       (dotimes [i entry-count]
                         (let [k (aget entry-names i)]
                           (let [v (aget attrs k)]
                             (cond
                               (= "on-render" k) (aset node on-render-key v)
                               (.startsWith k "on")
                               (let [event (-> k
                                               (.replaceAll "-" ""))]
                                 (aset modified-props event v))
                               (.startsWith k "default")
                               (let [default-attr (-> (subs k 7)
                                                      (.replaceAll "-" ""))]
                                 (aset modified-attrs default-attr v))
                               :else
                               (do
                                 (cond
                                   (and (= "style" k) (object? v))
                                   (do (let [style (reduce
                                                    (fn [s e]
                                                      (str s (aget e 0) ": " (aget e 1) ";"))
                                                    "" (js/Object.entries v))]
                                         ;; set/get attribute is faster to set, get
                                         ;; and compare (in patch)than setting
                                         ;; individual props and using cssText
                                         (aset modified-attrs "style" style)))
                                   (property? k) (aset modified-props k v)
                                   :else (when v
                                           ;; not adding means it will be removed on new render
                                           (aset modified-attrs k v))))))))))
                   (when (and (not (nil? classes))
                              (pos? (alength classes)))
                     (aset modified-attrs "class"
                           (str (when-let [c (aget modified-attrs "class")]
                                  (str c " "))
                                (.join classes " "))))
                   (when id
                     (aset modified-attrs "id" id))
                   node))]
      node)
    :else
    (throw (do
             (js/console.error "Invalid hiccup:" hiccup)
             (js/Error. (str "Invalid hiccup: " hiccup))))))

(defn- create-vnode [hiccup]
  (create-vnode* hiccup false))

(def ref-registry (js/Map.))

#?(:squint nil
   :cljs (defn update! [^js js-map k f & args]
           (.set js-map k (apply f (.get js-map k) args))))

(defn create-node [vnode root]
  (let [node (if-let [text (aget vnode "text")]
               (js/document.createTextNode text)
               (let [tag (aget vnode "tag")
                     node (if (aget vnode "svg")
                            (js/document.createElementNS svg-ns tag)
                            (js/document.createElement tag))
                     props (aget vnode props-key)
                     attrs (aget vnode attrs-key)
                     attr-names (js/Object.getOwnPropertyNames attrs)
                     prop-names (js/Object.getOwnPropertyNames props)]
                 ;; always make sure to first set attrs, then props because value should go last
                 (dotimes [i (alength attr-names)]
                   (let [n (aget attr-names i)
                         new-attr (aget attrs n)]
                     (.setAttribute node n new-attr)))
                 (dotimes [i (alength prop-names)]
                   (let [n (aget prop-names i)
                         new-prop (aget props n)
                         new-prop (if (undefined? new-prop) nil new-prop)]
                     (aset node n new-prop)))
                 (when-let [children (aget vnode "children")]
                   (let [len (alength children)]
                     (dotimes [i len]
                       (let [child (aget children i)]
                         (.appendChild node (create-node child root))))))
                 (when-let [ref (aget vnode on-render-key)]
                   (aset node on-render-key ref)
                   (update! ref-registry root (fnil conj #{}) node))
                 node))]
    (aset node vnode-key vnode)
    node))

(defn- patch [^js parent new-children root]
  (let [parent-vnode (aget parent vnode-key)
        old-children-count (cond (and parent-vnode
                                      ;; other render root
                                      (not (aget parent root-key)))
                                 (alength (aget parent-vnode "children"))
                                 ;; current render root
                                 (identical? root parent) (alength (.-childNodes parent))
                                 :else -1)]
    ;; -1: we've stumbled upon a different render root
    (when-not (identical? -1 old-children-count)
      (let [old-children (.-childNodes parent)
            new-children-count (count new-children)]
        (if (not (== old-children-count new-children-count))
          (.apply parent.replaceChildren parent (.map new-children #(create-node % root)))
          (dotimes [i new-children-count]
            (let [^js old (aget old-children i)
                  ^js old-vnode (aget old vnode-key)
                  ^js new-vnode (aget new-children i)
                  txt-old (aget old-vnode "text")
                  txt (aget new-vnode "text")]
              (let [new-tag (aget new-vnode "tag")]
                (cond
                  (and txt-old txt)
                  (when-not (identical? txt txt-old)
                    (do (set! (.-textContent old) txt)
                        (aset old vnode-key new-vnode)))
                  (and old old-vnode new-vnode (identical? new-tag (aget old-vnode "tag")))
                  (let [^js old-props (aget old-vnode props-key)
                        ^js old-attrs (aget old-vnode attrs-key)
                        ^js new-props (aget new-vnode props-key)
                        ^js new-attrs (aget new-vnode attrs-key)
                        old-prop-names (js/Object.getOwnPropertyNames old-props)
                        old-attr-names (js/Object.getOwnPropertyNames old-attrs)
                        new-attr-names (js/Object.getOwnPropertyNames new-attrs)
                        new-prop-names (js/Object.getOwnPropertyNames new-props)]
                    (dotimes [i (count old-prop-names)]
                      (let [o (aget old-prop-names i)]
                        (when-not (js-in o new-props)
                          (aset old o nil))))
                    (dotimes [i (count old-attr-names)]
                      (let [o (aget old-attr-names i)]
                        (when-not (js-in o new-attrs)
                          (.removeAttribute old o))))
                    ;; always set attrs first, then props
                    (dotimes [i (count new-attr-names)]
                      (let [n (aget new-attr-names i)
                            new-attr (aget new-attrs n)]
                        (when-not (identical? new-attr (aget old-attrs n))
                          (.setAttribute old n new-attr))))
                    (dotimes [i (count new-prop-names)]
                      (let [n (aget new-prop-names i)
                            new-prop (let [v (aget new-props n)]
                                       (if (undefined? v) nil v))]
                        (when-not (identical? (aget old-props n) new-prop)
                          (aset old n new-prop))))
                    (when-let [new-children (aget new-vnode "children")]
                      (patch old new-children root))
                    ;; it's important that we set the vnode of the old node last
                    ;; since while patching children, the old vnode should
                    ;; remain in place since it's used at the top for reading the amount of children
                    (aset old vnode-key new-vnode))
                  :else (let [new-node (create-node new-vnode root)]
                          (.replaceChild parent new-node old)))))))))))

(defn render [root hiccup]
  (when-not (aget root root-key)
    ;; clear all root children so we can rely on every child having a vnode
    (set! root -textContent "")
    (aset root root-key true))
  (let [new-node (create-vnode hiccup)]
    (patch root #js [new-node] root))
  (doseq [node (.get ref-registry root)]
    (let [ref (aget node on-render-key)]
      (if (.-isConnected node)
        (if (not (aget ref is-run-key))
          (do (let [data (ref node :mount nil)]
                (aset ref is-run-key true)
                (aset ref data-key data)))
          (let [data (ref node :update (aget ref data-key))]
            (aset ref data-key data)))
        (do (ref node :unmount (aget ref data-key))
            (js-delete ref data-key)
            (update! ref-registry root disj node))))))
