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
    #js {:tag "#text"
         :text (str hiccup)}
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
          in-svg? (or in-svg? (= "svg" tag))
          node (if (fn? tag)
                 (let [res (apply tag (if attrs
                                        (cons attrs children)
                                        children))]
                   (create-vnode* res in-svg?))
                 (let [new-children #js []
                       node #js {:type :element :svg in-svg?
                                 :tag (if in-svg?
                                        tag
                                        (.toUpperCase tag))
                                 :children new-children}
                       modified-props #js {}
                       modified-attrs #js {}]
                   (aset node ::props modified-props)
                   (aset node ::attrs modified-attrs)
                   (doseq [child children]
                     (if (hiccup-seq? child)
                       (doseq [x child]
                         (.push new-children (create-vnode* x in-svg?)))
                       (.push new-children (create-vnode* child in-svg?))))
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
                           (cond
                             (= "on-render" k) (aset node ::on-render v)
                             (.startsWith k "on")
                             (let [event (-> k
                                             (.replaceAll "-" "")
                                             (.toLowerCase))]
                               (aset modified-props event v))
                             (.startsWith k "default")
                             (let [default-attr (-> (subs k 7)
                                                    (.replaceAll "-" "")
                                                    (.toLowerCase))]
                               (aset modified-attrs default-attr v))
                             :else
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
                                       (aset modified-attrs "style" style)))
                                 (property? k) (aset modified-props k v)
                                 :else (when v
                                         ;; not adding means it will be removed on new render
                                         (aset modified-attrs k v)))))))))
                   (when (seq classes)
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
                    props (aget vnode ::props)
                    attrs (aget vnode ::attrs)]
                ;; always make sure to first set attrs, then props because value should go last
                (doseq [n (js/Object.getOwnPropertyNames attrs)]
                  (let [new-attr (aget attrs n)]
                    (.setAttribute node n new-attr)))
                (doseq [n (js/Object.getOwnPropertyNames props)]
                  (let [new-prop (aget props n)
                        new-prop (if (undefined? new-prop) nil new-prop)]
                    (aset node n new-prop)))
                (when-let [children (aget vnode "children")]
                  (when (pos? (alength children))
                    (doseq [child children]
                      (.appendChild node (create-node child root)))))
                (when-let [ref (aget vnode ::on-render)]
                  (aset node ::on-render ref)
                  (update! ref-registry root (fnil conj #{}) node))
                node))]
    (aset node ::vnode vnode)
    node))

(defn- patch [^js parent new-children root]
  (let [parent-vnode (aget parent ::vnode)
        old-children-count (cond (and parent-vnode
                                      ;; other render root
                                      (not (aget parent ::root)))
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
                  ^js old-vnode (aget old ::vnode)
                  ^js new-vnode (aget new-children i)
                  txt-old (aget old-vnode "text")
                  txt (aget new-vnode "text")]
              (let [new-tag (aget new-vnode "tag")]
                (cond
                  (and txt-old txt)
                  (when-not (identical? txt txt-old)
                    (do (set! (.-textContent old) txt)
                        (aset old ::vnode new-vnode)))
                  (and old old-vnode new-vnode (identical? new-tag (aget old-vnode "tag")))
                  (let [^js old-props (aget old-vnode ::props)
                        ^js old-attrs (aget old-vnode ::attrs)
                        ^js new-props (aget new-vnode ::props)
                        ^js new-attrs (aget new-vnode ::attrs)]
                    (doseq [o (js/Object.getOwnPropertyNames old-props)]
                      (when-not (js-in o new-props)
                        (aset old o nil)))
                    (doseq [o (js/Object.getOwnPropertyNames old-attrs)]
                      (when-not (js-in o new-attrs)
                        (.removeAttribute old o)))
                    ;; always make sure to first set attrs, then props because value should go last
                    (doseq [n (js/Object.getOwnPropertyNames new-attrs)]
                      (let [new-attr (aget new-attrs n)]
                        (when-not (identical? new-attr (aget old-attrs n))
                          (.setAttribute old n new-attr))))
                    (doseq [n (js/Object.getOwnPropertyNames new-props)]
                      (let [new-prop (aget new-props n)
                            new-prop (if (undefined? new-prop) nil new-prop)]
                        (when-not (identical? (aget old-props n) new-prop)
                          (aset old n new-prop))))
                    (when-let [new-children (aget new-vnode "children")]
                      (patch old new-children root))
                    ;; it's important that we set the vnode of the old node last
                    ;; since while patching children, the old vnode should
                    ;; remain in place since it's used at the top for reading the amount of children
                    (aset old ::vnode new-vnode))
                  :else (let [new-node (create-node new-vnode root)]
                          (.replaceChild parent new-node old)))))))))))

(defn render [root hiccup]
  (when-not (aget root ::root)
    ;; clear all root children so we can rely on every child having a vnode
    (set! root -textContent "")
    (aset root ::root true))
  (let [new-node (create-vnode hiccup)]
    (patch root #js [new-node] root))
  (doseq [node (.get ref-registry root)]
    (let [ref (aget node ::on-render)]
      (if (.-isConnected node)
        (if (not (aget ref ::is-run))
          (do (let [data (ref node :mount nil)]
                (aset ref ::is-run true)
                (aset ref ::data data)))
          (let [data (ref node :update (aget ref ::data))]
            (aset ref ::data data)))
        (do (ref node :unmount (aget ref ::data))
            (js-delete ref ::data)
            (update! ref-registry root disj node))))))
