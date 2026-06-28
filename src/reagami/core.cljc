(ns reagami.core
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude []}}}}
  ;; Functions that leak Persistent*/Chunked* types into a :lite-mode build are
  ;; excluded so the compiler and clj-kondo both reject them here. Prefer
  ;; dotimes (arrays), run! (seqs/sets), and map literals.
  #_{:clj-kondo/ignore [:unused-excluded-var]}
  (:refer-clojure :exclude [doseq for set hash-map array-map sorted-map sorted-set
                            sorted-map-by sorted-set-by zipmap frequencies group-by
                            ex-info pr-str prn println vec]))

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


#_{:clj-kondo/ignore [:redundant-do]}
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

(def ^:private key-key #?(:squint ::key
                          :cljs "reagami.core/key"))

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
          in-svg? (or in-svg? (identical? "svg" tag))
          node (if (fn? tag)
                 (let [;; note: .slice was even faster in benchmarks than .shift-mutating
                         res (apply tag (.slice hiccup 1))]
                   (create-vnode* res in-svg?))
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
                         (run! (fn [x] (.push new-children (create-vnode* x in-svg?))) child)
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
                         (let [k (aget entry-names i)
                               v (aget attrs k)]
                           (cond
                             (identical? "key" k) (aset node key-key v)
                             (identical? "on-render" k) (aset node on-render-key v)
                             (.startsWith k "on")
                             (let [event (-> k
                                             (.replaceAll "-" ""))]
                               (aset modified-props event v))
                             (.startsWith k "default")
                             (let [default-attr (-> (subs k 7)
                                                    (.replaceAll "-" ""))]
                               (aset modified-attrs default-attr v))
                             :else
                             (cond
                               (and (identical? "style" k) (object? v))
                               (let [style (reduce
                                            (fn [s e]
                                              (str s (aget e 0) ": " (aget e 1) ";"))
                                            "" (js/Object.entries v))]
                                 ;; set/get attribute is faster to set, get
                                 ;; and compare (in patch)than setting
                                 ;; individual props and using cssText
                                 (aset modified-attrs "style" style))
                               (property? k) (aset modified-props k v)
                               :else (when v
                                       ;; not adding means it will be removed on new render
                                       (aset modified-attrs k v))))))))
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

(declare patch)

(defn- node-key [^js dom]
  (when-let [vn (aget dom vnode-key)]
    (aget vn key-key)))

(defn- has-key? [new-children]
  (let [n (alength new-children)]
    (loop [i 0]
      (if (< i n)
        (if (aget (aget new-children i) key-key) true (recur (inc i)))
        false))))

(defn- patch-node
  ;; patch `old` in place toward `new-vnode` when compatible, else build a fresh
  ;; node. returns the node to use, `old` when reused.
  [^js old ^js new-vnode root]
  (let [^js old-vnode (aget old vnode-key)
        txt-old (aget old-vnode "text")
        txt (aget new-vnode "text")
        new-tag (aget new-vnode "tag")]
    (cond
      (and txt-old txt)
      (do (when-not (identical? txt txt-old)
            (set! (.-textContent old) txt))
          (aset old vnode-key new-vnode)
          old)

      (and old-vnode new-vnode (identical? new-tag (aget old-vnode "tag")))
      (let [^js old-props (aget old-vnode props-key)
            ^js old-attrs (aget old-vnode attrs-key)
            ^js new-props (aget new-vnode props-key)
            ^js new-attrs (aget new-vnode attrs-key)
            old-prop-names (js/Object.getOwnPropertyNames old-props)
            old-attr-names (js/Object.getOwnPropertyNames old-attrs)
            new-attr-names (js/Object.getOwnPropertyNames new-attrs)
            new-prop-names (js/Object.getOwnPropertyNames new-props)]
        (dotimes [i (alength old-prop-names)]
          (let [o (aget old-prop-names i)]
            (when-not (js-in o new-props) (aset old o nil))))
        (dotimes [i (alength old-attr-names)]
          (let [o (aget old-attr-names i)]
            (when-not (js-in o new-attrs) (.removeAttribute old o))))
        (dotimes [i (alength new-attr-names)]
          (let [n (aget new-attr-names i)
                new-attr (aget new-attrs n)]
            (when-not (identical? new-attr (aget old-attrs n))
              (.setAttribute old n new-attr))))
        (dotimes [i (alength new-prop-names)]
          (let [n (aget new-prop-names i)
                new-prop (let [v (aget new-props n)] (if (undefined? v) nil v))]
            (when-not (identical? (aget old-props n) new-prop)
              (aset old n new-prop))))
        (when-let [nc (aget new-vnode "children")]
          (patch old nc root))
        (aset old vnode-key new-vnode)
        old)

      :else (create-node new-vnode root))))

(defn- get-sequence
  ;; indices of a longest increasing subsequence of `arr`; 0 entries are holes
  ;; (new nodes) and never included. used to keep the largest already-ordered run
  ;; in place and move only the rest.
  [^js arr]
  (let [len (alength arr)
        p (.slice arr)
        result #js [0]]
    (dotimes [i len]
      (let [arr-i (aget arr i)]
        (when-not (identical? 0 arr-i)
          (let [j (aget result (dec (alength result)))]
            (if (< (aget arr j) arr-i)
              (do (aset p i j) (.push result i))
              (let [u (loop [u 0 v (dec (alength result))]
                        (if (< u v)
                          (let [c (quot (+ u v) 2)]
                            (if (< (aget arr (aget result c)) arr-i)
                              (recur (inc c) v)
                              (recur u c)))
                          u))]
                (when (< arr-i (aget arr (aget result u)))
                  (when (> u 0) (aset p i (aget result (dec u))))
                  (aset result u i))))))))
    (loop [u (alength result) v (aget result (dec (alength result)))]
      (when (> u 0)
        (let [u (dec u)]
          (aset result u v)
          (recur u (aget p v)))))
    result))

(defn- patch-keyed
  ;; match new children to old by key (unkeyed ones by position), drop the
  ;; unused, and move only nodes outside the longest stable run, so e.g. a swap
  ;; moves two nodes instead of cascading.
  [^js parent new-children root]
  (let [old-nodes (js/Array.from (.-childNodes parent))
        old-by-key (js/Map.)
        old-index (js/Map.)
        unkeyed #js []
        used (js/Set.)
        ptr (volatile! 0)
        target #js []
        source #js []
        cnt (count new-children)
        reuse (fn [^js ex ^js v]
                (let [^js r (patch-node ex v root)]
                  (when (identical? r ex) (.add used ex))
                  r))
        next-unkeyed (fn []
                       (loop []
                         (when (< @ptr (alength unkeyed))
                           (let [^js c (aget unkeyed @ptr)]
                             (vswap! ptr inc)
                             (if (.has used c) (recur) c)))))]
    (dotimes [oi (alength old-nodes)]
      (let [^js n (aget old-nodes oi)]
        (.set old-index n oi)
        (if-let [k (node-key n)] (.set old-by-key k n) (.push unkeyed n))))
    (dotimes [i cnt]
      (let [^js v (aget new-children i)
            k (aget v key-key)
            ^js ex (if k
                     (let [^js e (.get old-by-key k)]
                       (when (and e (not (.has used e))) e))
                     (next-unkeyed))
            ^js node (if ex (reuse ex v) (create-node v root))
            reused? (and ex (identical? node ex))]
        (.push target node)
        (.push source (if reused? (inc (.get old-index ex)) 0))))
    (dotimes [i (alength old-nodes)]
      (let [^js n (aget old-nodes i)]
        (when-not (.has used n) (.removeChild parent n))))
    (let [seq (get-sequence source)
          len (alength target)
          si (volatile! (dec (alength seq)))]
      (loop [i (dec len)]
        (when (>= i 0)
          (let [^js node (aget target i)
                ^js nxt (when (< (inc i) len) (aget target (inc i)))]
            (cond
              (identical? 0 (aget source i)) (.insertBefore parent node nxt)
              (and (>= @si 0) (identical? i (aget seq @si))) (vswap! si dec)
              :else (.insertBefore parent node nxt))
            (recur (dec i))))))))

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
      (if (has-key? new-children)
        (patch-keyed parent new-children root)
        ;; unkeyed: patch the common prefix, then add or remove the tail, reusing
        ;; nodes instead of rebuilding the whole list on a count change.
        (let [old-children (.-childNodes parent)
              new-count (count new-children)
              common (min old-children-count new-count)]
          (dotimes [i common]
            (let [^js old (aget old-children i)
                  ^js new-vnode (aget new-children i)
                  ^js result (patch-node old new-vnode root)]
              (when-not (identical? result old)
                (.replaceChild parent result old))))
          (cond
            (> new-count old-children-count)
            (loop [i common]
              (when (< i new-count)
                (.appendChild parent (create-node (aget new-children i) root))
                (recur (inc i))))

            (identical? 0 new-count)
            (set! (.-textContent parent) "")

            (> old-children-count new-count)
            (loop [i (dec old-children-count)]
              (when (>= i new-count)
                (.removeChild parent (aget old-children i))
                (recur (dec i))))))))))

(defn render [root hiccup]
  (when-not (aget root root-key)
    ;; clear all root children so we can rely on every child having a vnode
    (set! root -textContent "")
    (aset root root-key true))
  (let [new-node (create-vnode hiccup)]
    (patch root #js [new-node] root))
  (run! (fn [node]
          (let [ref (aget node on-render-key)]
            (if (.-isConnected node)
              (if (not (aget ref is-run-key))
                (let [data (ref node :mount nil)]
                  (aset ref is-run-key true)
                  (aset ref data-key data))
                (let [data (ref node :update (aget ref data-key))]
                  (aset ref data-key data)))
              (do (ref node :unmount (aget ref data-key))
                  (js-delete ref data-key)
                  (update! ref-registry root disj node)))))
        (.get ref-registry root)))
