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
                          ;; we don't include value here since this causes issues with Ohm's calculator
                          ;; https://gist.github.com/borkdude/ff65e3c5a10cb657e7df68aec35defc6
                          ]))

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

(defn- create-node*
  [hiccup in-svg?]
  (cond
    (or (nil? hiccup)
        (string? hiccup)
        (number? hiccup)
        (boolean? hiccup))
    (js/document.createTextNode (str hiccup))
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
                   (create-node* res in-svg?))
                 (let [node (if in-svg?
                              (js/document.createElementNS svg-ns tag)
                              (js/document.createElement tag))]
                   (doseq [child children]
                     (let [child-nodes (if (and (seq? child)
                                                (not (vector? child)))
                                         (mapv #(create-node* % in-svg?) child)
                                         [(create-node* child in-svg?)])]
                       (doseq [child-node child-nodes]
                         (.appendChild node child-node))))
                   (let [modified-attrs (js/Set.)]
                     (doseq [[k v] attrs]
                       (let [k (name k)
                             #?@(:squint [] :cljs [v (keyword->str v)])]
                         (cond (.startsWith k "on")
                           (let [event (-> k
                                           (.replaceAll "-" "")
                                           (.toLowerCase))]
                             (aset node event v)
                             (.add modified-attrs event))
                           (= "ref" k)
                           (aset node ::ref v)
                           :else
                           (do
                             (.add modified-attrs k)
                             (cond
                               (and (= "style" k) (map? v))
                               (do (let [style (reduce-kv
                                                (fn [s k v]
                                                  (str s (camel->kebab (name k)) ": " (name v) ";"))
                                                "" v)]
                                     ;; set/get attribute is faster to set, get
                                     ;; and compare (in patch)than setting
                                     ;; individual props and using cssText
                                     (.setAttribute node "style" style)))
                               (property? k) (aset node k v)
                               :else (when v (.setAttribute node k v)))))))
                     (when (seq classes)
                       (.setAttribute node "class"
                                      (str (when-let [c (.getAttribute node "class")]
                                             (str c " "))
                                           (.join classes " ")))
                       (.add modified-attrs "class"))
                     (when id
                       (.setAttribute node "id" id)
                       (.add modified-attrs "id"))
                     (aset node ::attrs modified-attrs))
                   node))]
      node)
    :else
    (throw (do
             (js/console.error "Invalid hiccup:" hiccup)
             (js/Error. (str "Invalid hiccup: " hiccup))))))

(defn- create-node [hiccup]
  (create-node* hiccup false))

(defn call-ref [node removed?]
  (when-let [ref (aget node ::ref)]
    (if removed? (ref nil) (ref node))))

(defn- patch [^js parent new-children]
  (let [old-children (.-childNodes parent)]
    (if (not= (alength old-children) (alength new-children))
      (do (.apply parent.replaceChildren parent new-children)
          (run! #(call-ref % true) old-children)
          (run! #(call-ref % false) (.-childNodes parent)))
      (doseq [[^js old ^js new] (mapv vector (array-seq old-children) (array-seq new-children))]
        (cond
          (and old new (= (.-nodeName old) (.-nodeName new)))
          (if (= 3 (.-nodeType old))
            (let [txt (.-textContent new)]
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
                    (let [new-attr (.getAttribute new n)]
                      (when-not (identical? new-attr (.getAttribute old n))
                        (.setAttribute old n new-attr))))))
              (when-let [new-children (.-childNodes new)]
                (patch old new-children))))
          :else (do
                  (.replaceChild parent new old)
                  (call-ref old true)
                  (call-ref new false)
                  #_(call-ref old true)))))))

(defn render [root hiccup]
  (let [new-node (create-node hiccup)]
    (patch root #js [new-node])))
