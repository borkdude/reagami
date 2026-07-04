(ns reactive
  "Prototype reagent-like layer on top of reagami. Components render into a
  fragment anchor, adding no wrapper element. A ratom derefed during a body
  render subscribes that component, swap!/reset! re-renders only subscribers."
  (:require [reagami.core :as reagami]))

(def ^:dynamic *ctx* nil)

(declare rerender!)

(deftype RAtom [^:mutable value watches ctxs]
  IDeref
  (-deref [this]
    (when-let [ctx *ctx*]
      (.add ctxs ctx)
      (.add (.-atoms ctx) this))
    value)
  IReset
  (-reset! [this new-value]
    (let [old value]
      (set! value new-value)
      (.forEach watches (fn [f k] (f k this old new-value)))
      (run! rerender! (js/Array.from ctxs))
      new-value))
  IWatchable
  (-add-watch [_this k f] (.set watches k f))
  (-remove-watch [_this k] (.delete watches k))
  (-notify-watches [this oldval newval]
    (.forEach watches (fn [f k] (f k this oldval newval)))))

(defn ratom [init]
  (->RAtom init (js/Map.) (js/Set.)))

(defn- unsubscribe! [ctx]
  (.forEach (.-atoms ctx) (fn [a] (.delete (.-ctxs a) ctx)))
  (.clear (.-atoms ctx)))

(defn- rerender! [ctx]
  (when-not (.-disposed ctx)
    (unsubscribe! ctx)
    (binding [*ctx* ctx]
      (let [hiccup (apply (.-f ctx) (.-args ctx))]
        (if (.-frag ctx)
          (reagami/render-fragment (.-node ctx) hiccup)
          (reagami/render (.-node ctx) hiccup))))))

(def ^:private instances (js/Map.))

(defn- dispose-ctx! [ctx]
  (set! (.-disposed ctx) true)
  (unsubscribe! ctx))

(defn- inside-fragment? [^js anchor ^js n]
  (boolean
   (some (fn [^js o]
           (or (identical? o n)
               (.contains o n)
               (and (reagami/fragment-nodes o) (inside-fragment? o n))))
         (reagami/fragment-nodes anchor))))

(defn- dispose! [^js node]
  (when-let [ctx (.get instances node)]
    (dispose-ctx! ctx)
    (.delete instances node))
  ;; nested component roots never get their own unmount pass, walk them
  (.forEach instances
            (fn [ctx ^js n]
              (when (if (reagami/fragment-nodes node)
                      (inside-fragment? node n)
                      (.contains node n))
                (dispose-ctx! ctx)
                (.delete instances n)))))

(defn- make-ctx [node f args frag?]
  #js {:node node :f f :args args :atoms (js/Set.) :disposed false :frag frag?})

(defn component [f]
  (fn [& args]
    [:<> {:on-render
          (fn [node lifecycle _]
            (case lifecycle
              :mount
              (let [ctx (make-ctx node f args true)]
                (.set instances node ctx)
                (rerender! ctx))
              :update
              (when-let [ctx (.get instances node)]
                (when (not= args (.-args ctx))
                  (set! (.-args ctx) args)
                  (rerender! ctx)))
              :unmount (dispose! node)))}]))

(defn render [node f & args]
  (dispose! node)
  (let [ctx (make-ctx node f args false)]
    (.set instances node ctx)
    (rerender! ctx)
    ctx))
