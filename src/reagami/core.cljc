(ns reagami.core
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude []}}}}
  ;; cljs-lite compat:
  #_{:clj-kondo/ignore [:unused-excluded-var]}
  (:refer-clojure :exclude [doseq for set hash-map array-map sorted-map sorted-set
                            sorted-map-by sorted-set-by zipmap frequencies group-by
                            ex-info pr-str prn println vec map filter]))

(def svg-ns "http://www.w3.org/2000/svg")

(defn- parse-tag
  "From hiccup, thanks @weavejester"
  [^String tag]
  (let [id-index (let [index (.indexOf tag "#")] (when (pos? index) index))
        class-index (let [index (.indexOf tag ".")] (when (pos? index) index))]
    [(cond
       id-index (.substring tag 0 id-index)
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

(def ^:private tag-cache (js/Map.))

(def ^:private event-name-cache (js/Map.))

(defn- parse-tag-cached
  ;; tags come from a small fixed set, so cache the parse work (indexOf,
  ;; substring, toUpperCase, class join) per tag string
  [tag]
  (or (.get tag-cache tag)
      (let [[t id class] (parse-tag tag)
            entry #js {:tag t
                       :upper (.toUpperCase t)
                       :id id
                       :class (when (and class (pos? (.-length class)))
                                (.replaceAll class "." " "))}]
        (.set tag-cache tag entry)
        entry)))

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
  (and (not (vector? x))
       (not (string? x))
       (seq? x)))

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

(def ^:private comment-tag "#comment")

;; a fragment builds to an anchor comment plus its children, spliced flat into
;; the parent. the anchor keeps the slot when the fragment is empty, and patch
;; never sees fragments at all.
(defn- push-vnode!
  [^js arr x]
  (if ^boolean (js/Array.isArray x)
    (let [n (alength x)]
      (dotimes [i n] (.push arr (aget x i))))
    (.push arr x)))

(declare create-vnode*)

;; the select benchmark moved up to 10% with the placement of this code and of
;; fragment-vnodes below, without a semantic difference. do not chase that: see
;; the ssr archive in the benchmark repo before moving anything for speed.
(defn- splice-vnodes
  [hiccup in-svg?]
  (let [arr #js []]
    (run! (fn [x] (push-vnode! arr (create-vnode* x in-svg?))) hiccup)
    arr))

;; a fragment takes no attrs except a lone :key. the key composes into every
;; child, so the flat keyed patch moves the whole unit with zero range
;; bookkeeping.
(defn- fragment-vnodes
  [^js hiccup attr-idx children-idx first-child in-svg?]
  (let [attrs (when (identical? 1 attr-idx) first-child)
        fkey (when attrs
               #?(:squint (aget attrs "key")
                  :cljs (:key attrs)))
        arr #js [#js {:tag comment-tag}]]
    (when (and attrs
               (or (nil? fkey)
                   (not (identical?
                         1
                         #?(:squint (alength (js/Object.keys attrs))
                            :cljs (count first-child))))))
      (throw (js/Error. "A fragment takes only a :key attribute")))
    (dotimes [i (- (alength hiccup) children-idx)]
      (let [child (aget hiccup (+ i children-idx))]
        (if (hiccup-seq? child)
          (run! (fn [x] (push-vnode! arr (create-vnode* x in-svg?))) child)
          (push-vnode! arr (create-vnode* child in-svg?)))))
    (when (some? fkey)
        ;; a child's own key nests behind one separator, a
        ;; positional one behind two, so they cannot collide
      (let [ks (str fkey)]
        (dotimes [i (alength arr)]
          (let [v (aget arr i)
                ck (aget v key-key)]
            (aset v key-key
                  (if (some? ck)
                    (str ks "\u0000" ck)
                    (str ks "\u0000\u0000" i)))))))
    arr))

(defn- create-vnode*
  [hiccup in-svg?]
  (cond
    ;; a nil child renders nothing but holds its slot, so toggling a conditional
    ;; swaps one node instead of shifting its siblings. a comment is the marker
    ;; because, unlike an empty text node, it survives server rendering.
    (nil? hiccup)
    #js {:tag comment-tag}

    (or (string? hiccup)
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
          parsed (when (string? tag) (parse-tag-cached tag))
          tag (if parsed (aget parsed "tag") tag)
          first-child (aget hiccup children-idx)
          attr-idx (if (map? first-child) 1 -1)
          children-idx (if (identical? -1 attr-idx)
                         children-idx (inc children-idx))
          in-svg? (or in-svg? (identical? "svg" tag))
          node (if (fn? tag)
                 (let [;; note: .slice was even faster in benchmarks than .shift-mutating
                       res (.apply tag nil (.slice hiccup 1))]
                   (create-vnode* res in-svg?))
                 (if (identical? "<>" tag)
                   (fragment-vnodes hiccup attr-idx children-idx first-child in-svg?)
                   (let [;; innerHTML owns the subtree, so it has no vnode children
                       ;; to build or patch
                         inner-html? (and (identical? 1 attr-idx)
                                          (some? #?(:squint (aget first-child "innerHTML")
                                                    :cljs (:innerHTML first-child))))
                         new-children (when-not inner-html? #js [])
                         node #js {:svg in-svg?
                                   :tag (if in-svg?
                                          tag
                                          (aget parsed "upper"))
                                   :children new-children}
                         modified-props #js {}
                         modified-attrs #js {}]
                     (aset node props-key modified-props)
                     (aset node attrs-key modified-attrs)
                     (when new-children
                       (dotimes [i (- (alength hiccup) children-idx)]
                         (let [child (aget hiccup (+ i children-idx))]
                           (if (hiccup-seq? child)
                             (run! (fn [x] (push-vnode! new-children (create-vnode* x in-svg?))) child)
                             (push-vnode! new-children (create-vnode* child in-svg?))))))
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
                               (let [event (or (.get event-name-cache k)
                                               (let [e (.replaceAll k "-" "")]
                                                 (.set event-name-cache k e)
                                                 e))]
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
                     (when-let [tag-class (aget parsed "class")]
                       (aset modified-attrs "class"
                             (if-let [c (aget modified-attrs "class")]
                               (str c " " tag-class)
                               tag-class)))
                     (when-let [id (aget parsed "id")]
                       (aset modified-attrs "id" id))
                     node)))]
      node)
    ;; a top level seq splices, so a component can return one
    (hiccup-seq? hiccup) (splice-vnodes hiccup in-svg?)
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

(def ^:private stats #js {:created 0 :adopted 0})

(defn create-node [vnode root]
  (aset stats "created" (inc (aget stats "created")))
  (let [node (if-let [text (aget vnode "text")]
               (js/document.createTextNode text)
               (let [tag (aget vnode "tag")]
                 (if (identical? comment-tag tag)
                   (js/document.createComment "")
                   (let [node (if (aget vnode "svg")
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
                     node))))]
    (aset node vnode-key vnode)
    node))

(declare patch)

(defn- node-key [^js dom]
  (when-let [vn (aget dom vnode-key)]
    (aget vn key-key)))

(defn- adopt
  ;; a server-rendered node carries no vnode: rebuild one from the DOM so patch
  ;; can diff against it. props stay empty, so the first patch sets all of them.
  [^js dom]
  (aset stats "adopted" (inc (aget stats "adopted")))
  (let [vnode (cond
                (identical? 3 (.-nodeType dom))
                #js {:tag "#text" :text (.-data dom)}
                ;; comments carry no attributes and no text, so patch replaces
                ;; them rather than reusing one
                (not (identical? 1 (.-nodeType dom)))
                #js {:tag (.-nodeName dom)}
                :else
                (let [attrs #js {}
                      dom-attrs (.-attributes dom)
                      ;; patch only reads the length of :children
                      vnode #js {:tag (.-tagName dom)
                                 :svg (identical? svg-ns (.-namespaceURI dom))
                                 :children (js/Array. (alength (.-childNodes dom)))}]
                  (dotimes [i (alength dom-attrs)]
                    (let [a (aget dom-attrs i)]
                      (aset attrs (.-name a) (.-value a))))
                  (aset vnode attrs-key attrs)
                  (aset vnode props-key #js {})
                  vnode))]
    (aset dom vnode-key vnode)
    vnode))

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
  (let [^js old-vnode (or (aget old vnode-key) (adopt old))
        txt-old (aget old-vnode "text")
        txt (aget new-vnode "text")
        new-tag (aget new-vnode "tag")]
    (cond
      (and txt-old txt)
      (do (when-not (identical? txt txt-old)
            (set! (.-textContent old) txt))
          (aset old vnode-key new-vnode)
          old)

      ;; two markers: nothing to patch, and it has no attrs or props to read
      (and (identical? comment-tag new-tag)
           (identical? comment-tag (aget old-vnode "tag")))
      (do (aset old vnode-key new-vnode)
          old)

      (identical? new-tag (aget old-vnode "tag"))
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
        ;; an adopted node never went through create-node, so register its ref
        ;; here. only when it has none: mount state lives on the ref itself.
        (when-let [ref (aget new-vnode on-render-key)]
          (when-not (aget old on-render-key)
            (aset old on-render-key ref)
            (update! ref-registry root (fnil conj #{}) old)))
        (aset old vnode-key new-vnode)
        old)

      :else (create-node new-vnode root))))

(defn- lis-indices
  ;; Indices of a longest increasing subsequence of `arr`. Entries equal to 0
  ;; are holes for new nodes and never included.

  ;; Examples:
  ;;   [2 4 6] -> [0 1 2]   already in order, nothing moves
  ;;   [4 2 6] -> [1 2]     values 2,6 stay in place, index 0 (value 4) moves
  ;;   [2 0 6] -> [0 2]     index 1 holds a new node (value 0), skipped
  ;; The returned indices are the nodes to keep, the rest get moved.

  ;; See https://en.wikipedia.org/wiki/Longest_increasing_subsequence
  ;; and https://github.com/vuejs/core/blob/c0606e91798c8dca4f33d101e1dd836d672592c1/packages/runtime-core/src/renderer.ts#L2565
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
        ;; server children carry no vnode and so no keys. pair them by position
        ;; and let keys take over from the next render.
        hydrating? (and (pos? (alength old-nodes))
                        (not (aget (aget old-nodes 0) vnode-key)))
        old-by-key (js/Map.)
        old-index (js/Map.)
        unkeyed #js []
        used (js/Set.)
        ptr (volatile! 0)
        target #js []
        source #js []
        cnt (alength new-children)
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
    ;; index old nodes: keyed into a map, unkeyed kept in order
    (dotimes [oi (alength old-nodes)]
      (let [^js n (aget old-nodes oi)]
        (.set old-index n oi)
        (if-let [k (node-key n)] (.set old-by-key k n) (.push unkeyed n))))
    ;; reuse each new child's old node by key, else next unkeyed, else create
    (dotimes [i cnt]
      (let [^js v (aget new-children i)
            k (aget v key-key)
            ^js ex (if (and k (not hydrating?))
                     (let [^js e (.get old-by-key k)]
                       (when (and e (not (.has used e))) e))
                     (next-unkeyed))
            ^js node (if ex (reuse ex v) (create-node v root))
            reused? (and ex (identical? node ex))]
        (.push target node)
        (.push source (if reused? (inc (.get old-index ex)) 0)))) ; source = old pos + 1, or 0 for new
    ;; drop old nodes left unused
    (dotimes [i (alength old-nodes)]
      (let [^js n (aget old-nodes i)]
        (when-not (.has used n) (.removeChild parent n))))
    ;; place right to left, moving only nodes outside the stable run
    (let [lis (lis-indices source)
          len (alength target)
          si (volatile! (dec (alength lis)))]
      (loop [i (dec len)]
        (when (>= i 0)
          (let [^js node (aget target i)
                ^js nxt (when (< (inc i) len) (aget target (inc i)))]
            (cond
              (identical? 0 (aget source i)) (.insertBefore parent node nxt) ; new
              (and (>= @si 0) (identical? i (aget lis @si))) (vswap! si dec) ; in stable run, keep
              :else (.insertBefore parent node nxt)) ; reused, move
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
              new-count (alength new-children)
              common (js/Math.min old-children-count new-count)]
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

(defn render
  "Renders hiccup into root. Adopts server-rendered children already in root.
  Returns a map of :created and :adopted node counts."
  [root hiccup]
  (aset root root-key true)
  (aset stats "created" 0)
  (aset stats "adopted" 0)
  (let [new-node (create-vnode hiccup)
        new-children (if ^boolean (js/Array.isArray new-node)
                       new-node
                       #js [new-node])]
    (patch root new-children root))
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
        (.get ref-registry root))
  {:created (aget stats "created")
   :adopted (aget stats "adopted")})
