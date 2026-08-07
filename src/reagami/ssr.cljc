(ns reagami.ssr
  ;; the flag lets squint load the defmacro below at compile time; squint and
  ;; cljs both take the :cljs branch, which makes the same-file macro visible
  {:squint/compile-time true}
  #?(:cljs (:require-macros [reagami.ssr :refer [app!]]))
  (:require [clojure.string :as str]))

#?(:clj (set! *warn-on-reflection* true))

(def ^:private void-tags
  #{"area" "base" "br" "col" "embed" "hr" "img" "input"
    "link" "meta" "param" "source" "track" "wbr"})

;; mirrors reagami.core/properties: set as DOM properties on the client, so they
;; need an HTML equivalent here
(def ^:private properties
  #{"checked" "disabled" "selected" "value" "innerHTML" "indeterminate"
    "muted" "volume" "playbackRate"})

;; see indeterminate-ssr-test and media-property-ssr-test
(def ^:private client-only-properties
  #{"indeterminate" "volume" "playbackRate"})

(def ^:private boolean-properties
  #{"checked" "disabled" "selected" "muted"})

(defn- parse-tag
  "From hiccup, thanks @weavejester"
  [^String tag]
  (let [id-index    (let [index (.indexOf tag "#")] (when (pos? index) index))
        class-index (let [index (.indexOf tag ".")] (when (pos? index) index))
        t (cond
            id-index    (.substring tag 0 id-index)
            class-index (.substring tag 0 class-index)
            :else tag)]
    [t
     (when id-index
       (if class-index
         (.substring tag (inc id-index) class-index)
         (.substring tag (inc id-index))))
     (when class-index
       (let [class (.substring tag (inc class-index))]
         (when (pos? (count class))
           (str/replace class "." " "))))
     (str/includes? t "-")]))

;; tags come from code, so the cache stays small. mirrors the client's cache in
;; reagami.core. the map type follows the platform: a ConcurrentHashMap lookup
;; is 15 ns cheaper than an atom with a persistent map on the jvm, and a js Map
;; is 5 ns cheaper than an object on node. bb keeps the atom, because interop
;; costs 10x a native call there.
#?(:bb (def ^:private tag-cache (atom {}))
   :clj (def ^:private ^java.util.concurrent.ConcurrentHashMap tag-cache
          (java.util.concurrent.ConcurrentHashMap.))
   :default (def ^:private tag-cache (js/Map.)))

(defn- parse-tag-cached [tag]
  #?(:bb (or (get @tag-cache tag)
             (let [parsed (parse-tag tag)]
               (swap! tag-cache assoc tag parsed)
               parsed))
     :clj (or (.get tag-cache tag)
              (let [parsed (parse-tag tag)]
                (.put tag-cache tag parsed)
                parsed))
     :default (or (.get tag-cache tag)
                  (let [parsed (parse-tag tag)]
                    (.set tag-cache tag parsed)
                    parsed))))

(defn- entries [m]
  (if m
    #?(:squint (js/Object.entries m)
       :default (seq m))
    []))

;; bb collects pieces in a transient vector and joins once: a native conj! is
;; about 9x cheaper than an interop .append under interpretation
(defn- sb []
  #?(:bb (volatile! (transient []))
     :clj (StringBuilder.)
     :default (array "")))

;; appends every piece to the string builder. each platform gets its cheapest
;; shape: the JS targets concatenate into one cell (V8 rope strings beat array
;; push and join), bb conjes onto a transient (native calls beat interop), the
;; JVM appends to a StringBuilder.
(defmacro app! #_{:clj-kondo/ignore [:unused-binding]} [b & xs]
  #?(:squint
     (let [tmpl (apply str (interpose " + " (repeat (inc (count xs)) "~{}")))]
       `(aset ~b 0 (~'js* ~tmpl (aget ~b 0) ~@xs)))
     :bb
     (if (= 1 (count xs))
       `(vreset! ~b (conj! (deref ~b) ~(first xs)))
       `(vreset! ~b (conj! (deref ~b) (str ~@xs))))
     :clj
     (if (:ns &env)
       ;; expanding for cljs
       (let [tmpl (apply str (interpose " + " (repeat (inc (count xs)) "~{}")))]
         `(aset ~b 0 (~'js* ~tmpl (aget ~b 0) ~@xs)))
       (let [sb (with-meta (gensym "sb") {:tag 'StringBuilder})]
         `(let [~sb ~b]
            ~@(map (fn [x] (list '.append sb x)) xs))))))

(defn- sb->str [b]
  #?(:bb (str/join (persistent! @b))
     :clj (.toString ^StringBuilder b)
     :default (aget b 0)))

#?(:clj
   (defn- num->str
     ;; match how JS stringifies a number, so an integral double is not "1.0"
     ;; here and "1" in the browser
     [^double v]
     (if (and (== v (Math/floor v)) (< (Math/abs v) 1e21))
       (str (long v))
       (str v))))

(defn- ->str [v]
  (cond
    (string? v) v
    (keyword? v) (name v)
    #?@(:clj [(float? v) (num->str v)])
    :else (str v)))

;; most strings contain nothing to escape, so scan before paying for the
;; replace passes and their intermediate strings. on the jvm a char loop wins
;; under ~32 chars on call overhead, and the simd indexOf wins above it.
;; interleaved a/b runs against a plain str/index-of chain show 8% per render
;; on an attribute heavy page and 5% on a text heavy one, so keep it.
;; mode 1 is text, 0 is attribute.
#?(:clj
   (defn- clean? [^String s ^long mode]
     (let [n (.length s)]
       (if (< n 32)
         (loop [i 0]
           (if (< i n)
             (let [c (unchecked-int (.charAt s i))]
               (if (or (== c 38) (== c 160)
                       (if (== mode 1)
                         (or (== c 60) (== c 62))
                         (== c 34)))
                 false
                 (recur (unchecked-inc i))))
             true))
         (and (neg? (.indexOf s 38))
              (neg? (.indexOf s 160))
              (if (== mode 1)
                (and (neg? (.indexOf s 60)) (neg? (.indexOf s 62)))
                (neg? (.indexOf s 34))))))))

(defn- escape-text [s]
  (if #?(:bb (or (str/index-of s "&")
                 (str/index-of s "\u00a0")
                 (str/index-of s "<")
                 (str/index-of s ">"))
         :clj (not (clean? s 1))
         :default
         (or (str/index-of s "&")
             (str/index-of s "\u00a0")
             (str/index-of s "<")
             (str/index-of s ">")))
    (-> s
        (str/replace "&" "&amp;")
        (str/replace "\u00a0" "&nbsp;")
        (str/replace "<" "&lt;")
        (str/replace ">" "&gt;"))
    s))

(defn- escape-attr [s]
  (if #?(:bb (or (str/index-of s "&")
                 (str/index-of s "\u00a0")
                 (str/index-of s "\""))
         :clj (not (clean? s 0))
         :default
         (or (str/index-of s "&")
             (str/index-of s "\u00a0")
             (str/index-of s "\"")))
    (-> s
        (str/replace "&" "&amp;")
        (str/replace "\u00a0" "&nbsp;")
        (str/replace "\"" "&quot;"))
    s))

(defn- pair-get [pairs k]
  (some (fn [pair] (when (= k (nth pair 0)) (nth pair 1))) pairs))

(defn- put
  ;; replace in place when present, so the emitted order matches the DOM
  [pairs k v]
  (let [n (count pairs)]
    (loop [i 0]
      (cond
        (= i n) (conj pairs [k v])
        (= k (nth (nth pairs i) 0)) (assoc pairs i [k v])
        :else (recur (inc i))))))

(defn- style->str [m]
  (reduce (fn [s pair]
            (str s (->str (nth pair 0)) ": " (->str (nth pair 1)) ";"))
          "" (entries m)))

(defn- hiccup-seq? [x]
  (and (not (vector? x))
       (not (string? x))
       (seq? x)))

(declare ->html)

(defn- children->html [xs b]
  (run! (fn [child]
          (if (hiccup-seq? child)
            (run! (fn [x] (->html x b)) child)
            (->html child b)))
        xs))

;; see custom-element-ssr-test
(defn- property-for? [k custom?]
  (if custom?
    (= "innerHTML" k)
    (contains? properties k)))

(defn- split-attrs [attrs custom?]
  (reduce
   (fn [acc pair]
     (let [^String k (->str (nth pair 0))
           v (nth pair 1)]
       (cond
         (= "key" k) acc
         (= "on-render" k) acc
         (.startsWith k "on") acc
         (.startsWith k "default")
         (update acc 0 put (str/replace (subs k 7) "-" "") v)
         (and (= "style" k) (map? v))
         (update acc 0 put "style" (style->str v))
         (property-for? k custom?) (update acc 1 put k v)
         :else (if v (update acc 0 put k v) acc))))
   [[] []]
   (entries attrs)))

(defn- element->html [hiccup b]
  (let [[^String tag id class custom?] (parse-tag-cached (->str (nth hiccup 0)))
        attrs (nth hiccup 1 nil)
        attrs? (map? attrs)
        children (if attrs? (subvec hiccup 2) (subvec hiccup 1))
        [attr-pairs prop-pairs] (split-attrs (when attrs? attrs) custom?)
        attr-pairs (if class
                     (let [c (pair-get attr-pairs "class")]
                       (put attr-pairs "class"
                            (if c (str (->str c) " " class) class)))
                     attr-pairs)
        attr-pairs (if id (put attr-pairs "id" id) attr-pairs)]
    (app! b "<")
    (app! b tag)
    (run! (fn [pair]
            (app! b " " (nth pair 0) "=\""
                  (escape-attr (->str (nth pair 1))) "\""))
          attr-pairs)
    (run! (fn [pair]
            (let [k (nth pair 0)
                  v (nth pair 1)]
              (cond
                (= "innerHTML" k) nil
                (contains? client-only-properties k) nil
                (contains? boolean-properties k)
                (when v (app! b " " k "=\"\""))
                (some? v)
                (app! b " " k "=\"" (escape-attr (->str v)) "\""))))
          prop-pairs)
    (app! b ">")
    (when-not (contains? void-tags tag)
      (let [html (pair-get prop-pairs "innerHTML")]
        (if (some? html)
          (app! b (->str html))
          (children->html children b)))
      (app! b "</" tag ">"))))

(defn- ->html [x b]
  (cond
    ;; reagami.core gives a nil child a comment node, so emit the same marker
    (nil? x) (app! b "<!---->")
    (string? x) (app! b (escape-text x))
    (vector? x) (let [tag (nth x 0)]
                  (cond
                    (fn? tag) (->html (apply tag (rest x)) b)
                    :else (element->html x b)))
    (or (number? x) (boolean? x)) (app! b (escape-text (str x)))
    (hiccup-seq? x) (run! (fn [child] (->html child b)) x)
    :else (throw (ex-info (str "Invalid hiccup: " x) {:hiccup x}))))

(defn render
  "Renders hiccup to an HTML string."
  [hiccup]
  (let [b (sb)]
    (->html hiccup b)
    (sb->str b)))
