(ns reagami.core
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [update!]}}}})

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
                          "value"]))

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

(defn camel->kebab [s]
  (.replace s (js/RegExp. "[A-Z]" "g")
            (fn [m]
              (str "-" (.toLowerCase m)))))

(defn hiccup-seq? [x]
  (and (not (string? x))
       (seq? x)
       (not (vector? x))))

(defn- create-vnode*
  [hiccup in-svg?]
  (cond
    (or (nil? hiccup)
        (string? hiccup)
        (number? hiccup)
        (boolean? hiccup))
    #js {:text (str hiccup)}
    (vector? hiccup)
    (let [[tag & children] hiccup
          #?@(:squint []
              :cljs [tag (if (keyword? tag)
                           (subs (str tag) 1)
                           tag)])
          [tag id class] (if (string? tag) (parse-tag tag) [tag])
          classes (when class (.split class "."))
          [attrs children] (if (map? (first children))
                             [(first children) (rest children)]
                             [nil children])
          in-svg? (or in-svg? (= :svg tag))
          node (if (fn? tag)
                 (let [res (apply tag (if attrs
                                        (cons attrs children)
                                        children))]
                   (create-vnode* res in-svg?))
                 (let [new-children #js []
                       node #js {:type :element :svg in-svg? :tag (.toUpperCase tag) :children new-children}
                       modified-attrs (js/Set.)]
                   (aset node ::attrs modified-attrs)
                   (doseq [child children]
                     (let [child-nodes (if (hiccup-seq? child)
                                         (mapv #(create-vnode* % in-svg?) child)
                                         [(create-vnode* child in-svg?)])]
                       (doseq [child-node child-nodes]
                         (.push new-children child-node))))
                   (when attrs
                     (let [#?@(:squint []
                               :cljs [attrs (clj->js attrs)])]
                       ;; make sure value goes last, since setting value before
                       ;; min and max attributes doesn't work for input range
                       (when (js-in "value" attrs)
                         (let [value (aget attrs "value")]
                           (js-delete attrs "value")
                           (aset attrs "value" value)))
                       (doseq [e (js/Object.entries attrs)]
                         (let [k (aget e 0)
                               v (aget e 1)]
                           (if (.startsWith k "on")
                             (let [event (-> k
                                             (.replaceAll "-" "")
                                             (.toLowerCase))]
                               (aset node event v)
                               (.add modified-attrs event))
                             (do
                               (.add modified-attrs k)
                               (cond
                                 (and (= "style" k) (object? v))
                                 (do (let [style (reduce
                                                  (fn [s e]
                                                    (str s (camel->kebab (aget e 0)) ": " (aget e 1) ";"))
                                                  "" (js/Object.entries v))]
                                       ;; set/get attribute is faster to set, get
                                       ;; and compare (in patch)than setting
                                       ;; individual props and using cssText
                                       (aset node "style" style)))
                                 (property? k) (aset node k v)
                                 :else (when v (aset node k v)))))))))
                   (when (seq classes)
                     (aset node "class"
                                    (str (when-let [c (aget node "class")]
                                           (str c " "))
                                         (.join classes " ")))
                     (.add modified-attrs "class"))
                   (when id
                     (aset node "id" id)
                     (.add modified-attrs "id"))
                   node))]
      node)
    :else
    (throw (do
             (js/console.error "Invalid hiccup:" hiccup)
             (js/Error. (str "Invalid hiccup: " hiccup))))))

(defn- create-vnode [hiccup]
  (create-vnode* hiccup false))

(defn create-node [vnode]
  (if-let [text (aget vnode "text")]
    (js/document.createTextNode text)
    (let [tag (aget vnode "tag")
          node (if (aget vnode "svg")
                 (js/document.createElementNS svg-ns tag)
                 (js/document.createElement tag))
          attrs (aget vnode ::attrs)]
      (doseq [attr attrs]
        (when-let [v (aget vnode attr)]
          (if (or (.startsWith attr "on")
                  (property? attr))
            (aset node attr v)
            (.setAttribute node attr v))))
      (when-let [children (aget vnode "children")]
        (when (pos? (alength children))
          (doseq [child children]
            (.appendChild node (create-node child)))))
      node)))

(defn- patch [^js parent new-children]
  (let [old-children (.-childNodes parent)]
    (if (not= (alength old-children) (alength new-children))
      (.apply parent.replaceChildren parent (mapv create-node new-children))
      (doseq [[^js old ^js new] (mapv vector (array-seq old-children) (array-seq new-children))]
        (cond
          (and old new (= (.-nodeName old) (aget new "tag")))
          (if (= 3 (.-nodeType old))
            (let [txt (aget new "text")]
              (set! (.-textContent old) txt))
            (do
              (let [^js old-attrs (aget old ::attrs)
                    ^js new-attrs (aget new ::attrs)]
                (doseq [o old-attrs]
                  (when-not (.has new-attrs o)
                    (if (or (.startsWith o "on") (property? o))
                      (aset old o nil)
                      (.removeAttribute old o))))
                (doseq [n new-attrs]
                  (if (or (.startsWith n "on")
                          (property? n))
                    (let [new-prop (aget new n)]
                      (when-not (identical? (aget old n)
                                            new-prop)
                        (aset old n new-prop)))
                    (let [new-attr (aget new n)]
                      (when-not (identical? new-attr (.getAttribute old n))
                        (.setAttribute old n new-attr))))))
              (when-let [new-children (aget new "children")]
                (patch old new-children))))
          :else (.replaceChild parent (create-node new) old))))))

(defn render [root hiccup]
  (time (dotimes [i 10]
          (let [new-node (create-vnode hiccup)]
            (patch root #js [new-node])))))
