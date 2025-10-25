(ns reagami
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

(defn- create-node
  ([hiccup] (create-node hiccup false))
  ([hiccup in-svg?]
   (cond
     (or (nil? hiccup)
         (string? hiccup)
         (number? hiccup)
         (boolean? hiccup))
     (js/document.createTextNode (str hiccup))
     (vector? hiccup)
     (let [[tag & children] hiccup
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
                    (create-node res in-svg?))
                  (let [node (if in-svg?
                               (js/document.createElementNS svg-ns tag)
                               (js/document.createElement tag))]
                    (doseq [child children]
                      (let [child-nodes (if (and (seq? child)
                                                 (not (vector? child)))
                                          (mapv #(create-node % in-svg?) child)
                                          [(create-node child in-svg?)])]
                        (doseq [child-node child-nodes]
                          (.appendChild node child-node))))
                    (let [attrs
                          (reduce (fn [acc [k v]]
                                    (cond
                                      (and (= :style k) (map? v))
                                      (do (doseq [[k v] v]
                                            (aset (.-style node) k v))
                                          (conj acc :style))
                                      (.startsWith k "on")
                                      (let [event (-> k
                                                      (.replaceAll "-" "")
                                                      (.toLowerCase))]
                                        (aset node event v)
                                        (conj acc event))
                                      :else (do (if in-svg?
                                                  (.setAttribute node k v)
                                                  (aset node k v))
                                                (conj acc k))))
                                  #{} attrs)
                          attrs (if-let [class-list (seq (.-classList node))]
                                 (do (doseq [clazz classes]
                                       (.add class-list clazz))
                                     (conj attrs "classList"))
                                 attrs)
                          attrs (if id
                                  (do (set! node -id id)
                                      (conj attrs id))
                                  attrs)]
                      (aset node ::attrs attrs))
                    node))]
       node)
     :else
     (throw (do
              (js/console.error "Invalid hiccup:" hiccup)
              (js/Error. (str "Invalid hiccup: " hiccup)))))))

(defn- patch [parent new-children]
  (let [old-children (.-childNodes parent)]
    (if (not= (count old-children) (count new-children))
      (parent.replaceChildren.apply parent new-children)
      (doseq [[old new] (mapv vector old-children new-children)]
        (cond
          (and old new (= (.-nodeName old) (.-nodeName new)))
          (if (= 3 (.-nodeType old))
            (let [txt (.-textContent new)]
              (set! (.-textContent old) txt))
            (do
              (let [old-attrs (aget old ::attrs)
                    new-attrs (aget new ::attrs)
                    svg? (= svg-ns (.-namespaceURI old))]
                (doseq [o old-attrs]
                  (if svg?
                    (.removeAttribute old (name o))
                    (aset old o nil)))
                (doseq [n new-attrs]
                  (if svg?
                    (.setAttribute old n (.getAttribute new n))
                    (if
                      (= :style n)
                      (set! (.-style.cssText old) (.-style.cssText (aget new n)))
                      (aset old n (aget new n))))))
              (when-let [new-children (.-childNodes new)]
                (patch old new-children))))
          :else (.replaceChild parent new old))))))

(defn render [root hiccup]
  (let [new-node (create-node hiccup)]
    (patch root [new-node])))
