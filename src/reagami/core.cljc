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

;; see indeterminate-test and media-property-test
(def properties (js/Set. ["checked" "disabled" "selected"
                          "value" "innerHTML" "indeterminate"
                          "muted" "volume" "playbackRate"]))

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
                       :custom (.includes t "-")
                       :id id
                       :class (when (and class (pos? (.-length class)))
                                (.replaceAll class "." " "))}]
        (.set tag-cache tag entry)
        entry)))

(defn property? [^js x]
  (.has properties x))

(defn- property-for? [^js k custom?]
  (if custom?
    (identical? "innerHTML" k)
    (property? k)))


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

;; the root has no vnode of its own, so its child list lives on the node
(def ^:private children-key #?(:squint ::children
                               :cljs "reagami.core/children"))

;; set on the root for the hydration render only, so patch-keyed knows to pair
;; server children by position
(def ^:private hydrating-key #?(:squint ::hydrating
                                :cljs "reagami.core/hydrating"))

(def ^:private is-run-key #?(:squint ::is-run
                             :cljs "reagami.core/is-run"))

(def ^:private state-key #?(:squint ::state
                            :cljs "reagami.core/state"))

(def ^:private handlers-key #?(:squint ::handlers
                               :cljs "reagami.core/handlers"))

(defn- prop-name [k]
  (or (.get event-name-cache k)
      (let [e (.replaceAll k "-" "")]
        (.set event-name-cache k e)
        e)))

(defn- set-handler! [^js node k v]
  (let [hs (or (aget node handlers-key)
               (let [o #js {}]
                 (aset node handlers-key o)
                 o))]
    (when-not (js-in k hs)
      (.addEventListener node (subs k 3)
                         (fn [e] (when-let [f (aget hs k)] (f e)))))
    (aset hs k v)))

;; see custom-event-test
(defn- set-prop!
  [^js node k v]
  (if (.startsWith k "on")
    (let [p (prop-name k)]
      (if (js-in p node)
        (aset node p v)
        (set-handler! node k v)))
    (aset node k v)))

(defn- save-fn
  ;; reagami keeps whatever the hook saves and hands it back on the next call.
  ;; it makes nothing itself, so the hook picks what its state is: an atom, a
  ;; volatile, a js object, a plain value.
  [^js node]
  (fn [v] (aset node state-key v) v))

;; set when a render drops the :on-render of a node that had one
(def ^:private unhook-key #?(:squint ::unhook
                             :cljs "reagami.core/unhook"))

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

;; a seq child builds to an array of vnodes, spliced flat into the parent
(defn- push-vnode!
  [^js arr x]
  (if ^boolean (js/Array.isArray x)
    (let [n (alength x)]
      (dotimes [i n] (.push arr (aget x i))))
    (.push arr x)))

(declare create-vnode*)

;; the select benchmark moved up to 10% with the placement of this code, without
;; a semantic difference. do not chase that: see the ssr archive in the
;; benchmark repo before moving anything for speed.
(defn- splice-vnodes
  [hiccup in-svg?]
  (let [arr #js []]
    (run! (fn [x] (push-vnode! arr (create-vnode* x in-svg?))) hiccup)
    arr))

(defn- comment-vnode
  ;; a nil child renders nothing but holds its slot, so toggling a conditional
  ;; swaps one node instead of shifting its siblings. a comment is the marker
  ;; because, unlike an empty text node, it survives server rendering. empty
  ;; props and attrs let the regular patch branch handle it.
  []
  (let [vnode #js {:tag comment-tag :dom nil}]
    (aset vnode props-key #js {})
    (aset vnode attrs-key #js {})
    vnode))

(defn- create-vnode*
  [hiccup in-svg?]
  (cond
    (nil? hiccup)
    (comment-vnode)

    (or (string? hiccup)
        (number? hiccup)
        (boolean? hiccup))
    #js {:tag "#text"
         :text (str hiccup)
         :dom nil}
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
                 (let [custom? (and parsed (aget parsed "custom"))
                       new-children #js []
                       node #js {:svg in-svg?
                                 :tag (if in-svg?
                                        tag
                                        (aget parsed "upper"))
                                 :children new-children
                                 :dom nil}
                       modified-props #js {}
                       modified-attrs #js {}]
                   (aset node props-key modified-props)
                   (aset node attrs-key modified-attrs)
                   (dotimes [i (- (alength hiccup) children-idx)]
                     (let [child (aget hiccup (+ i children-idx))]
                       (if (hiccup-seq? child)
                         (run! (fn [x] (push-vnode! new-children (create-vnode* x in-svg?))) child)
                         (push-vnode! new-children (create-vnode* child in-svg?)))))
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
                             (aset modified-props k v)
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
                               (property-for? k custom?) (aset modified-props k v)
                               :else (when v
                                     ;; not adding means it will be removed on new render
                                       (aset modified-attrs k v))))))
                     ;; innerHTML owns the subtree: drop the children, so patch
                     ;; never touches what it sets
                     (when (some? (aget modified-props "innerHTML"))
                       (aset node "children" nil))))
                   (when-let [tag-class (aget parsed "class")]
                     (aset modified-attrs "class"
                           (if-let [c (aget modified-attrs "class")]
                             (str c " " tag-class)
                             tag-class)))
                   (when-let [id (aget parsed "id")]
                     (aset modified-attrs "id" id))
                   node))]
      node)
    ;; a top level seq splices, so a component can return one
    (hiccup-seq? hiccup) (splice-vnodes hiccup in-svg?)
    :else
    (throw (do
             (js/console.error "Invalid hiccup:" hiccup)
             (js/Error. (str "Invalid hiccup: " hiccup))))))

(defn- create-vnode [hiccup]
  (create-vnode* hiccup false))

(def ref-registry (js/WeakMap.))

(defn- registry-of [^js root]
  (or (.get ref-registry root) #{}))

(defn- registry-add! [^js root ^js node]
  (.set ref-registry root (conj (registry-of root) node)))

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
                         (set-prop! node n new-prop)))
                     (when-let [children (aget vnode "children")]
                       (let [len (alength children)]
                         (dotimes [i len]
                           (let [child (aget children i)]
                             (.appendChild node (create-node child root))))))
                     (when-let [ref (aget vnode on-render-key)]
                       (aset node on-render-key ref)
                       (registry-add! root node))
                     node))))]
    (aset node vnode-key vnode)
    (aset vnode "dom" node)
    node))

(declare patch)

(defn- adopt
  ;; a server-rendered node carries no vnode: rebuild one from the DOM so the
  ;; first patch can diff against it. props stay empty, so that patch sets all
  ;; of them. field order matches create-vnode*, so both sides get the same
  ;; hidden class and patching stays monomorphic.
  [^js dom]
  (aset stats "adopted" (inc (aget stats "adopted")))
  (let [vnode (cond
                (identical? 3 (.-nodeType dom))
                #js {:tag "#text" :text (.-data dom) :dom nil}
                (not (identical? 1 (.-nodeType dom)))
                (let [vnode #js {:tag (.-nodeName dom) :dom nil}]
                  (aset vnode props-key #js {})
                  (aset vnode attrs-key #js {})
                  vnode)
                :else
                (let [attrs #js {}
                      dom-attrs (.-attributes dom)
                      ;; adopt-tree fills :children with the adopted vnodes
                      vnode #js {:svg (identical? svg-ns (.-namespaceURI dom))
                                 :tag (.-tagName dom)
                                 :children #js []
                                 :dom nil}]
                  (dotimes [i (alength dom-attrs)]
                    (let [a (aget dom-attrs i)]
                      (aset attrs (.-name a) (.-value a))))
                  (aset vnode props-key #js {})
                  (aset vnode attrs-key attrs)
                  vnode))]
    (aset dom vnode-key vnode)
    (aset vnode "dom" dom)
    vnode))

(defn- adopt-tree
  ;; hydration happens once, up front: adopt every server-rendered node before
  ;; the first patch, so patching itself never has to consider hydration.
  ;; returns the adopted children, which become the parent's old child list.
  [^js parent]
  (let [children (.-childNodes parent)
        n (alength children)
        arr (js/Array. n)]
    (dotimes [i n]
      (let [^js c (aget children i)
            ^js v (adopt c)]
        (aset arr i v)
        (when (identical? 1 (.-nodeType c))
          (aset v "children" (adopt-tree c)))))
    arr))

(defn- own-children
  ;; render was called on a node whose children another render made. their
  ;; vnodes are still on them, so take those as this root's old child list
  ;; instead of starting from nothing.
  [^js parent]
  (let [children (.-childNodes parent)
        n (alength children)
        arr (js/Array. n)]
    (dotimes [i n]
      (aset arr i (aget (aget children i) vnode-key)))
    arr))

(defn- has-key? [new-children]
  (let [n (alength new-children)]
    (loop [i 0]
      (if (< i n)
        (if (aget (aget new-children i) key-key) true (recur (inc i)))
        false))))

(defn- register-ref!
  ;; the node keeps the handler of the newest render, so a hook written as an
  ;; inline closure sees this render's values instead of the first one's. the
  ;; node registers once. lifecycle state lives on the node, not on the
  ;; handler, so two nodes sharing one handler keep separate state.
  [^js old ref root]
  (when-not (aget old on-render-key)
    (registry-add! root old))
  ;; guarded: deleting a property that is not there still costs a runtime call,
  ;; and repeated deletes on a DOM node risk pushing it into dictionary mode
  (when (aget old unhook-key)
    (js-delete old unhook-key))
  (aset old on-render-key ref))

(defn- patch-node
  ;; patch `old-vnode`'s node in place toward `new-vnode` when compatible, else
  ;; build a fresh one. returns the node to use, the old one when reused.
  [^js old-vnode ^js new-vnode root]
  (let [^js old (aget old-vnode "dom")
        txt-old (aget old-vnode "text")
        txt (aget new-vnode "text")
        new-tag (aget new-vnode "tag")]
    (cond
      (and txt-old txt)
      (do (when-not (identical? txt txt-old)
            (set! (.-textContent old) txt))
          (aset old vnode-key new-vnode)
          (aset new-vnode "dom" old)
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
            (when-not (js-in o new-props) (set-prop! old o nil))))
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
              (set-prop! old n new-prop))))
        (when-let [nc (aget new-vnode "children")]
          (patch old (aget old-vnode "children") nc root))
        (let [ref (aget new-vnode on-render-key)]
          (if ref
            (register-ref! old ref root)
            ;; the hook was there and is gone now: unmount it on this render.
            ;; asked of the old vnode, which is already loaded, rather than of
            ;; the DOM node, where the read would be megamorphic
            (when (aget old-vnode on-render-key)
              (aset old unhook-key true))))
        (aset old vnode-key new-vnode)
        (aset new-vnode "dom" old)
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
  ;;
  ;; the old children are vnodes we already own, so their order is an array
  ;; index: no Array.from of a live NodeList, no map from node to position, and
  ;; used-ness is a slot in a flat array instead of a Set of DOM nodes.
  [^js parent ^js old-children new-children root]
  (let [old-count (if old-children (alength old-children) 0)
        old-by-key (js/Map.)
        unkeyed #js []
        used (js/Array. old-count)
        ptr #js [0]
        target #js []
        source #js []
        cnt (alength new-children)
        next-unkeyed (fn []
                       (loop []
                         (let [p (aget ptr 0)]
                           (if (< p (alength unkeyed))
                             (let [oi (aget unkeyed p)]
                               (aset ptr 0 (inc p))
                               (if (aget used oi) (recur) oi))
                             -1))))]
    ;; index old children: keyed into a map by position, unkeyed kept in order
    (dotimes [oi old-count]
      (let [^js ov (aget old-children oi)
            k (aget ov key-key)]
        (if k (.set old-by-key k oi) (.push unkeyed oi))))
    ;; server children carry no keys, so the loop above left them all unkeyed.
    ;; pair new keyed children with them by position, then keys take over from
    ;; the next render.
    (when (aget root hydrating-key)
      (dotimes [i (js/Math.min cnt old-count)]
        (when-let [k (aget (aget new-children i) key-key)]
          (.set old-by-key k i))))
    ;; reuse each new child's old node by key, else next unkeyed, else create
    (dotimes [i cnt]
      (let [^js v (aget new-children i)
            k (aget v key-key)
            oi (if k
                 (let [e (.get old-by-key k)]
                   (if (and (not (undefined? e)) (not (aget used e))) e -1))
                 (next-unkeyed))]
        (if (identical? -1 oi)
          (do (.push target (create-node v root))
              (.push source 0)) ; 0 marks a new node
          (let [^js ov (aget old-children oi)
                ^js ex (aget ov "dom")
                ^js node (patch-node ov v root)
                reused? (identical? node ex)]
            (when reused? (aset used oi true))
            (.push target node)
            (.push source (if reused? (inc oi) 0))))))  ; source = old pos + 1
    ;; drop old nodes left unused
    (dotimes [i old-count]
      (when-not (aget used i)
        (.removeChild parent (aget (aget old-children i) "dom"))))
    ;; place right to left, moving only nodes outside the stable run
    (let [lis (lis-indices source)
          len (alength target)]
      (loop [i (dec len)
             si (dec (alength lis))]
        (when (>= i 0)
          (let [^js node (aget target i)
                ^js nxt (when (< (inc i) len) (aget target (inc i)))
                keep? (and (not (identical? 0 (aget source i)))
                           (>= si 0)
                           (identical? i (aget lis si)))]
            (when-not keep?
              (.insertBefore parent node nxt))
            (recur (dec i) (if keep? (dec si) si))))))))

(defn- patch [^js parent ^js old-children new-children root]
  ;; a node that is some other render's root owns its own children: leave it be
  (when-not (and (aget parent root-key) (not (identical? root parent)))
    (if (has-key? new-children)
      (patch-keyed parent old-children new-children root)
      ;; unkeyed: patch the common prefix, then add or remove the tail, reusing
      ;; nodes instead of rebuilding the whole list on a count change.
      (let [old-count (if old-children (alength old-children) 0)
            new-count (alength new-children)
            common (js/Math.min old-count new-count)]
        (dotimes [i common]
          (let [^js ov (aget old-children i)
                ^js old (aget ov "dom")
                ^js new-vnode (aget new-children i)
                ^js result (patch-node ov new-vnode root)]
            (when-not (identical? result old)
              (.replaceChild parent result old))))
        (cond
          (> new-count old-count)
          (loop [i common]
            (when (< i new-count)
              (.appendChild parent (create-node (aget new-children i) root))
              (recur (inc i))))

          ;; clear only children that this render owned: an :on-render
          ;; widget can put its own DOM under a childless node
          (identical? 0 new-count)
          (when (pos? old-count)
            (set! (.-textContent parent) ""))

          (> old-count new-count)
          (loop [i (dec old-count)]
            (when (>= i new-count)
              (.removeChild parent (aget (aget old-children i) "dom"))
              (recur (dec i)))))))))

(defn render
  "Renders hiccup into root. Adopts server-rendered children already in root.
  Returns a map of :created and :adopted node counts."
  [root hiccup]
  (aset root root-key true)
  (aset stats "created" 0)
  (aset stats "adopted" 0)
  (let [^js fc (.-firstChild root)]
    (when (and fc (nil? (aget root children-key)))
      (if (aget fc vnode-key)
        (aset root children-key (own-children root))
        (do (aset root children-key (adopt-tree root))
            (aset root hydrating-key true)))))
  (let [new-node (create-vnode hiccup)
        new-children (if ^boolean (js/Array.isArray new-node)
                       new-node
                       #js [new-node])]
    (patch root (aget root children-key) new-children root)
    (aset root children-key new-children))
  (aset root hydrating-key false)
  (run! (fn [node]
          (let [ref (aget node on-render-key)
                state (aget node state-key)
                save (save-fn node)]
            (if (and (.-isConnected node) (not (aget node unhook-key)))
              (if (not (aget node is-run-key))
                (do (ref {:node node :lifecycle :mount :state state :save save})
                    (aset node is-run-key true))
                (ref {:node node :lifecycle :update :state state :save save}))
              (do (ref {:node node :lifecycle :unmount :state state :save save})
                  (js-delete node state-key)
                  (js-delete node is-run-key)
                  (js-delete node on-render-key)
                  (js-delete node unhook-key)
                  (.set ref-registry root (disj (registry-of root) node))))))
        (registry-of root))
  {:created (aget stats "created")
   :adopted (aget stats "adopted")})
