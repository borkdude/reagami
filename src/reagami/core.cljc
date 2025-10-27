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
              :cljs [tag (keyword->str tag)])
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
                              (js/document.createElement tag))
                       modified-attrs (js/Set.)]
                   (aset node ::attrs modified-attrs)
                   (doseq [child children]
                     (let [child-nodes (if (hiccup-seq? child)
                                         (mapv #(create-node* % in-svg?) child)
                                         [(create-node* child in-svg?)])]
                       (doseq [child-node child-nodes]
                         (.appendChild node child-node))))
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
                                       (.setAttribute node "style" style)))
                                 (property? k) (aset node k v)
                                 :else (when v (.setAttribute node k v)))))))))
                   (when (seq classes)
                     (.setAttribute node "class"
                                    (str (when-let [c (.getAttribute node "class")]
                                           (str c " "))
                                         (.join classes " ")))
                     (.add modified-attrs "class"))
                   (when id
                     (.setAttribute node "id" id)
                     (.add modified-attrs "id"))
                   node))]
      node)
    :else
    (throw (do
             (js/console.error "Invalid hiccup:" hiccup)
             (js/Error. (str "Invalid hiccup: " hiccup))))))

(defn- create-node [hiccup]
  (create-node* hiccup false))

(defn- create-node2
  [hiccup in-svg?]
  (cond
    (map? hiccup)
    (js/document.createTextNode (:text hiccup))
    (vector? hiccup)
    (let [[tag attrs children] hiccup
          _ (js/console.log :hiccup hiccup :tag tag)
          #?@(:squint []
              :cljs [tag (keyword->str tag)])
          [tag id class] (if (string? tag) (parse-tag tag) [tag])
          classes (when class (.split class "."))
          in-svg? (or in-svg? (= :svg tag))
          node (let [node (if in-svg?
                            (js/document.createElementNS svg-ns tag)
                            (js/document.createElement tag))
                     modified-attrs (js/Set.)]
                 (aset node ::attrs modified-attrs)
                 (doseq [child children]
                   (let [child-node (create-node2 child in-svg?)]
                     (.appendChild node child-node)))
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
                                     (.setAttribute node "style" style)))
                               (property? k) (aset node k v)
                               :else (when v (.setAttribute node k v)))))))))
                 (when (seq classes)
                   (.setAttribute node "class"
                                  (str (when-let [c (.getAttribute node "class")]
                                         (str c " "))
                                       (.join classes " ")))
                   (.add modified-attrs "class"))
                 (when id
                   (.setAttribute node "id" id)
                   (.add modified-attrs "id"))
                 node)]
      node)
    :else
    (throw (do
             (js/console.error "Invalid hiccup:" hiccup)
             (js/Error. (str "Invalid hiccup: " hiccup))))))

#_(defn- normalize-hiccup [hiccup in-svg?]
  (cond
    (or (nil? hiccup)
        (string? hiccup)
        (number? hiccup)
        (boolean? hiccup))
    {:type :text :value (str hiccup)}
    (vector? hiccup)
    (let [[tag & children] hiccup
          #?@(:squint []
              :cljs [tag (keyword->str tag)])
          in-svg? (or in-svg? (= :svg tag))
          [tag id class] (if (string? tag) (parse-tag tag) [tag])
          classes (when class (.split class "."))
          [attrs children] (if (map? (first children))
                             [(first children) (rest children)]
                             [nil children])
          hiccup (if (fn? tag)
                 (let [res (apply tag (if attrs
                                        (cons attrs children)
                                        children))]
                   (normalize-hiccup res in-svg?))
                 (let [children (into [] (mapcat (fn [child]
                                                   (if (hiccup-seq? child)
                                                     (mapv #(normalize-hiccup % in-svg?) child)
                                                     [(normalize-hiccup child in-svg?)]))) children)]
                   {:type :element :tag tag :attrs attrs :children children :classes classes :id id}))]
      hiccup)
    :else
    (throw (do
             (js/console.error "Invalid hiccup:" hiccup)
             (js/Error. (str "Invalid hiccup: " hiccup))))))

(defn- patch [^js parent new-children]
  (let [old-children (.-childNodes parent)]
    (if (not= (alength old-children) (alength new-children))
      (.apply parent.replaceChildren parent new-children)
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
          :else (.replaceChild parent new old))))))

(defn- realize-hiccup [hiccup]
  (cond
    (or (nil? hiccup)
        (string? hiccup)
        (number? hiccup)
        (boolean? hiccup))
    {:text (str hiccup)}
    (vector? hiccup)
    (let [[tag & children] hiccup
          #?@(:squint []
              :cljs [tag (keyword->str tag)])
          [attrs children] (if (map? (first children))
                             [(first children) (rest children)]
                             [nil children])
          ret (if (fn? tag)
                   (let [res (apply tag (if attrs
                                        (cons attrs children)
                                        children))]
                     (realize-hiccup res))
                   (let [children (into [] (mapcat (fn [child]
                                                   (if (hiccup-seq? child)
                                                     (mapv #(realize-hiccup %) child)
                                                     [(realize-hiccup child)]))) children)]
                     [tag attrs children]))]
      ret)
    :else
    (throw (do
             (js/console.error "Invalid hiccup:" hiccup)
             (js/Error. (str "Invalid hiccup: " hiccup))))))

(defn- patch2 [^js parent hiccups]
  (js/console.log :hiccups/patch2 hiccups)
  (let [old-children (.-childNodes parent)
        hiccups (into [] (map realize-hiccup) hiccups)]
    (if (not= (alength old-children) (count hiccups))
      (do
        (js/console.log :hiccups-replacement (mapv #(create-node2 % false) hiccups))
        (.apply parent.replaceChildren parent (mapv #(create-node2 % false) hiccups)))
      (doseq [[^js old ^js hiccup] (mapv vector (array-seq old-children) hiccups)]
        (if (map? hiccup)
          (if (= 3 (.-nodeType old))
            (let [txt (.-textContent (:text hiccup))]
              (set! (.-textContent old) txt))
            (.replaceChild parent (create-node2 hiccup false) old))
          (let [[tag attrs children] hiccup]
            (js/console.log :children children)
            (cond
              (and old hiccup (= tag (.toLowerCase (.-nodeName old))))
              (do
                (js/console.log :bingo tag)
                (let [^js old-attrs (aget old ::attrs)
                      ^js new-attrs (set (keys attrs))]
                  (aset old ::attrs new-attrs)
                  (doseq [o old-attrs]
                    (when-not (.has new-attrs o)
                      (if (or (.startsWith o "on") (property? o))
                        (aset old o nil)
                        (.removeAttribute old o))))
                  (when attrs
                   (let [#?@(:squint []
                             :cljs [attrs (clj->js attrs)])]
                     ;; make sure value goes last, since setting value before
                     ;; min and max attributes doesn't work for input range
                     (when (js-in "value" attrs)
                       (let [value (aget attrs "value")]
                         (js-delete attrs "value")
                         (aset attrs "value" value)))
                     (js/console.log :attrs attrs)
                     (doseq [e (js/Object.entries attrs)]
                       (let [k (aget e 0)
                             v (aget e 1)]
                         (js/console.log :k k :v v)
                         (if (.startsWith k "on")
                           (let [event (-> k
                                           (.replaceAll "-" "")
                                           (.toLowerCase))]
                             (aset old event v))
                           (do
                             (cond
                               (and (= "style" k) (object? v))
                               (do (let [style (reduce
                                                (fn [s e]
                                                  (str s (camel->kebab (aget e 0)) ": " (aget e 1) ";"))
                                                "" (js/Object.entries v))]
                                     ;; set/get attribute is faster to set, get
                                     ;; and compare (in patch)than setting
                                     ;; individual props and using cssText
                                     (.setAttribute old "style" style)))
                               (property? k) (do
                                               (js/console.log :set k v)
                                               (aset old k v))
                               :else (when v (.setAttribute old k v))))))))))
                (when (seq children)
                  (patch2 old children)))
              :else (.replaceChild parent (create-node2 hiccup false) old))))))))

(defn render [root hiccup]
  #_(js/console.log :realize (time (realize-hiccup hiccup)))
  (time
   (patch2 root [hiccup])
   #_(do
     (js/console.log :render)
     (let [new-node (create-node hiccup)]
         (patch root #js [new-node])))))
