(ns reactive
  "Prototype reagent-like layer on top of reagami. Components wrap their
  body in a nested render root. A ratom derefed during a body render
  subscribes that component, swap!/reset! re-renders only subscribers."
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
  (-add-watch [this k f] (.set watches k f))
  (-remove-watch [this k] (.delete watches k)))

(defn ratom [init]
  (->RAtom init (js/Map.) (js/Set.)))

(defn- unsubscribe! [ctx]
  (.forEach (.-atoms ctx) (fn [a] (.delete (.-ctxs a) ctx)))
  (.clear (.-atoms ctx)))

(defn- rerender! [ctx]
  (when-not (.-disposed ctx)
    (unsubscribe! ctx)
    (binding [*ctx* ctx]
      (reagami/render (.-node ctx) (apply (.-f ctx) (.-args ctx))))))

(def ^:private instances (js/Map.))

(defn- dispose-ctx! [ctx]
  (set! (.-disposed ctx) true)
  (unsubscribe! ctx))

(defn- dispose! [node]
  (when-let [ctx (.get instances node)]
    (dispose-ctx! ctx)
    (.delete instances node))
  ;; nested component roots never get their own unmount pass, walk them
  (.forEach instances (fn [ctx n]
                        (when (.contains node n)
                          (dispose-ctx! ctx)
                          (.delete instances n)))))

(defn- make-ctx [node f args]
  #js {:node node :f f :args args :atoms (js/Set.) :disposed false})

(def ^:private next-id (volatile! 0))
(def ^:private pending-args (js/Map.))

(defn component
  ([f] (component :div f))
  ([tag f]
   (fn [& args]
     (let [id (str (vswap! next-id inc))]
       (.set pending-args id args)
       [tag {:data-reactive id
             :on-render
             (fn [node lifecycle _]
               (let [id (.getAttribute node "data-reactive")
                     args (when id (.get pending-args id))]
                 (case lifecycle
                   :mount
                   (do (.delete pending-args id)
                       (let [ctx (make-ctx node f args)]
                         (.set instances node ctx)
                         (rerender! ctx)))
                   :update
                   (when args
                     (.delete pending-args id)
                     (let [ctx (.get instances node)]
                       (when (and ctx (not= args (.-args ctx)))
                         (set! (.-args ctx) args)
                         (rerender! ctx))))
                   :unmount (dispose! node))))}]))))

(defn render [node f & args]
  (dispose! node)
  (let [ctx (make-ctx node f args)]
    (.set instances node ctx)
    (rerender! ctx)
    ctx))
