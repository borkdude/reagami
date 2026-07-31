(ns reagami.ssr
  (:require [clojure.string :as str]))

#?(:clj (set! *warn-on-reflection* true))

(def ^:private void-tags
  #{"area" "base" "br" "col" "embed" "hr" "img" "input"
    "link" "meta" "param" "source" "track" "wbr"})

;; mirrors reagami.core/properties: set as DOM properties on the client, so they
;; need an HTML equivalent here
(def ^:private properties
  #{"checked" "disabled" "selected" "value" "innerHTML"})

(def ^:private boolean-properties
  #{"checked" "disabled" "selected"})

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
       (let [class (.substring tag (inc class-index))]
         (when (pos? (count class))
           (str/replace class "." " "))))]))

;; tags come from code, so the cache stays small. mirrors the client's cache in
;; reagami.core.
(def ^:private tag-cache (atom {}))

(defn- parse-tag-cached [tag]
  (or (get @tag-cache tag)
      (let [parsed (parse-tag tag)]
        (swap! tag-cache assoc tag parsed)
        parsed)))

(defn- entries [m]
  (if m
    #?(:squint (js/Object.entries m)
       :default (seq m))
    []))

(defn- sb [] #?(:clj (StringBuilder.) :default (array)))

(defn- app! [b s] #?(:clj (.append ^StringBuilder b ^String s) :default (.push b s)))

(defn- sb->str [b] #?(:clj (.toString ^StringBuilder b) :default (.join b "")))

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
;; replace passes and their intermediate strings
(defn- escape-text [s]
  (if (or (str/index-of s "&")
          (str/index-of s "\u00a0")
          (str/index-of s "<")
          (str/index-of s ">"))
    (-> s
        (str/replace "&" "&amp;")
        (str/replace "\u00a0" "&nbsp;")
        (str/replace "<" "&lt;")
        (str/replace ">" "&gt;"))
    s))

(defn- escape-attr [s]
  (if (or (str/index-of s "&")
          (str/index-of s "\u00a0")
          (str/index-of s "\""))
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

(defn- split-attrs [attrs]
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
         (contains? properties k) (update acc 1 put k v)
         :else (if v (update acc 0 put k v) acc))))
   [[] []]
   (entries attrs)))

(defn- element->html [hiccup b]
  (let [[^String tag id class] (parse-tag-cached (->str (nth hiccup 0)))
        attrs (nth hiccup 1 nil)
        attrs? (map? attrs)
        children (if attrs? (drop 2 hiccup) (drop 1 hiccup))
        [attr-pairs prop-pairs] (split-attrs (when attrs? attrs))
        attr-pairs (if class
                     (let [c (pair-get attr-pairs "class")]
                       (put attr-pairs "class"
                            (if c (str (->str c) " " class) class)))
                     attr-pairs)
        attr-pairs (if id (put attr-pairs "id" id) attr-pairs)]
    (app! b "<")
    (app! b tag)
    ;; compiled targets append the pieces directly, skipping a str per
    ;; attribute. interpreted bb pays more for the extra calls than for the
    ;; str, so it keeps the single append.
    (run! (fn [pair]
            #?@(:bb [(app! b (str " " (nth pair 0) "=\""
                                 (escape-attr (->str (nth pair 1))) "\""))]
                :default
                [(app! b " ")
                 (app! b (nth pair 0))
                 (app! b "=\"")
                 (app! b (escape-attr (->str (nth pair 1))))
                 (app! b "\"")]))
          attr-pairs)
    (run! (fn [pair]
            (let [k (nth pair 0)
                  v (nth pair 1)]
              (cond
                (= "innerHTML" k) nil
                (contains? boolean-properties k)
                (when v
                  #?@(:bb [(app! b (str " " k "=\"\""))]
                      :default [(app! b " ") (app! b k) (app! b "=\"\"")]))
                (some? v)
                #?(:bb (app! b (str " " k "=\"" (escape-attr (->str v)) "\""))
                   :default
                   (do (app! b " ")
                       (app! b k)
                       (app! b "=\"")
                       (app! b (escape-attr (->str v)))
                       (app! b "\""))))))
          prop-pairs)
    (app! b ">")
    (when-not (contains? void-tags tag)
      (let [html (pair-get prop-pairs "innerHTML")]
        (if (some? html)
          (app! b (->str html))
          (children->html children b)))
      #?@(:bb [(app! b (str "</" tag ">"))]
          :default
          [(app! b "</")
           (app! b tag)
           (app! b ">")]))))

(defn- ->html [x b]
  (cond
    ;; reagami.core gives a nil child a comment node, so emit the same marker
    (nil? x) (app! b "<!---->")
    (string? x) (app! b (escape-text x))
    (vector? x) (let [tag (nth x 0)]
                  (if (fn? tag)
                    (->html (apply tag (rest x)) b)
                    (element->html x b)))
    (or (number? x) (boolean? x)) (app! b (escape-text (str x)))
    (hiccup-seq? x) (run! (fn [child] (->html child b)) x)
    :else (throw (ex-info (str "Invalid hiccup: " x) {:hiccup x}))))

(defn render
  "Renders hiccup to an HTML string."
  [hiccup]
  (let [b (sb)]
    (->html hiccup b)
    (sb->str b)))
