(ns app
  "Animation of the keyed patch algorithm of Reagami: match by key, remove
  what is left over, take the longest increasing subsequence, then place the
  children right to left. The animation itself is rendered with Reagami, so
  the chips you see moving are moved by the very algorithm they show."
  (:require [reagami.core :as r]))

;; ---------------------------------------------------------------- algorithm

(defn lis-indices
  "Indices of a longest increasing subsequence of arr. Entries equal to 0 are
  holes for new nodes and are never included. Same code as reagami.core."
  [arr]
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
                          (let [c (js/Math.floor (/ (+ u v) 2))]
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

(defn parse-items
  "\"a b (u)\" -> children. A label in parens is an unkeyed child."
  [s]
  (let [out []]
    (doseq [lbl (.split (.trim s) #"\s+")]
      (when (not= "" lbl)
        (.push out {:label lbl
                    :key (when-not (.startsWith lbl "(") lbl)})))
    out))

;; ------------------------------------------------------------------- frames

;; A frame is a full snapshot, so stepping back is just an index.

(defn positions [order prev]
  (let [pos (js/Object.assign #js {} prev)]
    (dotimes [i (count order)]
      (aset pos (nth order i) i))
    pos))

(defn drop-id! [order id]
  (let [i (.indexOf order id)]
    (when-not (neg? i) (.splice order i 1))
    order))

(defn place-before!
  "What parent.insertBefore(node, ref) does to the child list."
  [order id ref]
  (drop-id! order id)
  (let [i (if ref (.indexOf order ref) -1)]
    (.splice order (if (neg? i) (count order) i) 0 id)
    order))

(defn label-of [chips id] (:label (aget chips id)))

(defn build-frames [old-str new-str]
  (let [olds (parse-items old-str)
        news (parse-items new-str)
        old-count (count olds)
        chips #js {}
        order #js []
        frames #js []
        ;; matching state, as in patch-keyed
        old-by-key (js/Map.)
        unkeyed #js []
        used (js/Array. old-count)
        ptr #js [0]
        source #js []
        target #js []
        log #js []
        next-unkeyed (fn next-unkeyed []
                       (loop []
                         (let [p (aget ptr 0)]
                           (if (< p (count unkeyed))
                             (let [oi (nth unkeyed p)]
                               (aset ptr 0 (inc p))
                               (if (aget used oi) (recur) oi))
                             -1))))
        snap (fn snap [text extra]
               (let [base {:order (.slice order)
                           :pos (positions order (:pos (last frames)))
                           :status (js/Object.assign #js {} (:status (last frames)))
                           :hl {}
                           :src (.slice source)
                           :keep nil
                           :cursor -1
                           :log (.slice log)
                           :text text}]
                 (.push frames (js/Object.assign base extra))))]
    ;; the old children, as they sit in the DOM
    (dotimes [i old-count]
      (let [it (nth olds i)
            id (str "o" i)]
        (aset chips id {:label (:label it) :key (:key it)})
        (.push order id)
        (if (:key it)
          (.set old-by-key (:key it) i)
          (.push unkeyed i))))
    (let [status #js {}]
      (doseq [id order] (aset status id "live"))
      (.push frames {:order (.slice order)
                     :pos (positions order {})
                     :status status
                     :hl {}
                     :src []
                     :keep nil
                     :cursor -1
                     :log []
                     :text (str "Old: " old-str ". New: " new-str
                                ". Every new child first looks for an old node to reuse.")}))
    ;; 1. match each new child to an old node, note that node's position + 1
    (dotimes [i (count news)]
      (let [it (nth news i)
            k (:key it)
            oi (if k
                 (let [e (.get old-by-key k)]
                   (if (and (not (undefined? e)) (not (aget used e))) e -1))
                 (next-unkeyed))
            id (if (neg? oi) (str "c" i) (str "o" oi))]
        (if (neg? oi)
          (do (aset chips id {:label (:label it) :key k})
              (.push source 0)
              (.push target id)
              (snap (str "No old node for " (:label it) ": create-node, source = 0.")
                    ;; a fresh node waits above the lane until it is inserted
                    {:hl (doto #js {} (aset id "make"))
                     :status (doto (js/Object.assign #js {} (:status (last frames)))
                               (aset id "pending"))
                     :pos (doto (positions order (:pos (last frames)))
                            (aset id i))
                     :cursor i}))
          (do (aset used oi true)
              (.push source (inc oi))
              (.push target id)
              (snap (if k
                      (str (:label it) " matches the old node with key " k
                           " at position " (inc oi) ": reuse it, source = " (inc oi) ".")
                      (str (:label it) " is unkeyed: it takes the next unused unkeyed old node,"
                           " at position " (inc oi) ", source = " (inc oi) "."))
                    {:hl (doto #js {} (aset id "match"))
                     :cursor i})))))
    ;; 2. remove the old nodes that no new child claimed
    (dotimes [i old-count]
      (when-not (aget used i)
        (let [id (str "o" i)]
          (drop-id! order id)
          (.push log (str "removeChild(" (label-of chips id) ")"))
          (snap (str "Old node " (label-of chips id) " was not matched: removeChild.")
                {:hl (doto #js {} (aset id "kill"))
                 :status (doto (js/Object.assign #js {} (:status (last frames)))
                           (aset id "gone"))}))))
    ;; 3. the longest increasing subsequence: the nodes already in relative order
    (let [lis (lis-indices source)
          len (count target)
          keep #js {}
          ;; keep / move / make per node, kept for every later frame
          roles #js {}]
      (loop [i (dec len) si (dec (alength lis))]
        (when (>= i 0)
          (let [k? (and (not (identical? 0 (nth source i)))
                        (>= si 0)
                        (identical? i (aget lis si)))]
            (when k? (aset keep (str i) true))
            (recur (dec i) (if k? (dec si) si)))))
      (let [kept (vec (filter (fn [i] (aget keep (str i))) (range len)))]
        (dotimes [i len]
          (aset roles (nth target i)
                (cond (aget keep (str i)) "keep"
                      (identical? 0 (nth source i)) "make"
                      :else "move")))
        (snap (str "Longest increasing subsequence of ["
                   (.join (vec (map (fn [s] (if (identical? 0 s) "_" s)) source)) " ")
                   "] is "
                   (.join (vec (map (fn [i] (nth source i)) kept)) " ")
                   ": " (.join (vec (map (fn [i] (label-of chips (nth target i))) kept)) ", ")
                   " are already in the right relative order and will not move.")
              {:hl roles :keep keep}))
      ;; 4. place right to left: insertBefore can only anchor on the right neighbour
      (loop [i (dec len)]
        (when (>= i 0)
          (let [id (nth target i)
                ref (when (< (inc i) len) (nth target (inc i)))
                lbl (label-of chips id)]
            (if (aget keep (str i))
              (snap (str lbl " is in the subsequence: leave it where it is.")
                    {:hl roles :cursor i :keep keep})
              (do (place-before! order id ref)
                  (.push log (str "insertBefore(" lbl ", "
                                  (if ref (label-of chips ref) "null") ")"))
                  (snap (str lbl
                             (if (identical? 0 (nth source i)) " is new" " must move")
                             ": parent.insertBefore(" lbl ", "
                             (if ref (label-of chips ref) "null")
                             (if ref ")" ") which appends it at the end."))
                        {:hl roles
                         :cursor i
                         :keep keep
                         :status (doto (js/Object.assign #js {} (:status (last frames)))
                                   (aset id "live"))})))
            (recur (dec i)))))
      (let [moved (count (filter (fn [i] (and (not (aget keep (str i)))
                                              (not (identical? 0 (nth source i)))))
                                 (range len)))
            made (count (filter (fn [s] (identical? 0 s)) source))
            gone (count (filter (fn [i] (not (aget used i))) (range old-count)))]
        (snap (str "Done: " (.join (vec (map (fn [id] (label-of chips id)) order)) " ")
                   ". " moved " node(s) moved, " made " created, " gone " removed.")
              {:hl roles :keep keep})))
    {:chips chips :frames frames :target target}))

;; ---------------------------------------------------------------------- app

(def presets
  [{:name "README" :old "a b c d z (u)" :new "a d b c (u) n (m)"}
   {:name "swap" :old "a b c d e" :new "a d c b e"}
   {:name "reverse" :old "a b c d e" :new "e d c b a"}
   {:name "prepend" :old "a b c d" :new "z a b c d"}
   {:name "remove" :old "a b c d e f" :new "a c e"}])

(def state (atom {}))
(def timer (atom nil))

(defn recompute [old-str new-str]
  (let [{:keys [chips frames target]} (build-frames old-str new-str)]
    (swap! state (fn [s] (assoc s :old old-str :new new-str :chips chips
                                :frames frames :target target :idx 0)))))

(defn stop! []
  (when-let [t @timer] (js/clearInterval t))
  (reset! timer nil)
  (swap! state assoc :playing false))

(defn step! [d]
  (let [{:keys [idx frames]} @state
        n (+ idx d)]
    (if (> n (dec (count frames)))
      (stop!)
      (swap! state assoc :idx (max 0 n)))))

(defn play! []
  (stop!)
  (let [{:keys [idx frames]} @state]
    (when (>= idx (dec (count frames))) (swap! state assoc :idx 0)))
  (swap! state assoc :playing true)
  (reset! timer (js/setInterval #(step! 1) 1800)))

(def chip-w 76)

;; a node that does not exist yet waits on the vnode row and flies up into the
;; DOM row when it is inserted
(def vnode-row 104)

(defn chip [chips id f]
  (let [pos (aget (:pos f) id)
        st (aget (:status f) id)
        hl (aget (:hl f) id)
        gone? (= "gone" st)
        pending? (= "pending" st)]
    [:div {:key id
           :class (str "chip" (when hl (str " " hl)) (when gone? " gone")
                       (when pending? " pending"))
           :style {:transform (str "translate(" (* chip-w (or pos 0)) "px,"
                                   (cond gone? "46px"
                                         pending? (str vnode-row "px")
                                         :else "0px") ")")}}
     (label-of chips id)]))

(defn vnode-chip
  "One new child, in the order the render produced it, with its source value."
  [chips f i id]
  (let [src (aget (:src f) i)
        hl (aget (:hl f) id)]
    [:div {:key (str "v" i)
           :class (str "vchip" (when hl (str " " hl))
                       (when (identical? i (:cursor f)) " cursor"))
           :style {:transform (str "translate(" (* chip-w i) "px,0)")}}
     [:span (label-of chips id)]
     ;; the source value only exists once this child has been matched
     [:span.vsrc (cond (undefined? src) "-"
                       (identical? 0 src) "0"
                       :else src)]]))

(def css "
* { box-sizing: border-box }
body { margin: 0; background: #14161c; color: #e7e9ee;
       font: 15px/1.5 ui-sans-serif, system-ui, sans-serif }
.wrap { max-width: 860px; margin: 0 auto; padding: 24px 20px 60px }
h1 { font-size: 20px; margin: 0 0 16px }
.row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; margin-bottom: 12px }
button { background: #232936; border: 1px solid #333c4e; color: #e7e9ee;
         border-radius: 6px; padding: 6px 12px; font: inherit; cursor: pointer }
button:hover { background: #2c3444 }
button.on { background: #3b82f6; border-color: #3b82f6; color: #fff }
.lane { position: relative; height: 232px; margin: 18px 0 6px;
        border: 1px dashed #303747; border-radius: 10px; padding: 30px 12px }
.lane .cap { position: absolute; top: 6px; left: 12px; font-size: 11px;
             letter-spacing: .08em; text-transform: uppercase; color: #6b7488 }
.lane .cap2 { top: 112px }
.chip { position: absolute; top: 34px; left: 12px; width: 62px; height: 42px;
        display: flex; align-items: center; justify-content: center;
        border-radius: 8px; background: #2a3140; border: 1px solid #3a4356;
        font-weight: 600; transition: transform .45s cubic-bezier(.4,0,.2,1),
        opacity .45s, background .3s, border-color .3s }
.chip.keep { background: #14432f; border-color: #2f9e6a; color: #7ff0b6 }
.chip.move { background: #4a3410; border-color: #d08a2c; color: #ffcf85 }
.chip.make { background: #14304d; border-color: #3b82f6; color: #a6cbff }
.chip.match { background: #2f2a4d; border-color: #7c6cf0; color: #c8bfff }
.chip.kill { background: #4a1620; border-color: #d0435c; color: #ff9fae }
.chip.gone { opacity: 0; }
.chip.pending { opacity: .45; border-style: dashed }
.vchip { position: absolute; top: 140px; left: 12px; width: 62px; height: 46px;
         display: flex; flex-direction: column; align-items: center;
         justify-content: center; border-radius: 8px; background: #1a1e28;
         border: 1px dashed #3a4356; font-weight: 600;
         transition: background .3s, border-color .3s, color .3s }
.vchip .vsrc { font: 400 11px/1.2 ui-monospace, monospace; color: #8ea1c0 }
.vchip.cursor { border-style: solid; box-shadow: 0 0 0 2px #3b82f6 }
.vchip.keep { color: #7ff0b6; border-color: #2f9e6a }
.vchip.move { color: #ffcf85; border-color: #d08a2c }
.vchip.make { color: #a6cbff; border-color: #3b82f6 }
.vchip.match { color: #c8bfff; border-color: #7c6cf0 }
.text { min-height: 48px; background: #1a1e28; border-left: 3px solid #3b82f6;
        padding: 10px 14px; border-radius: 0 8px 8px 0; margin: 12px 0 }
.log { font: 12px/1.7 ui-monospace, monospace; color: #8ea1c0; white-space: pre-wrap }
.step { color: #6b7488; font-size: 13px }
a { color: #7fb0ff }
")

(defn app []
  (let [{:keys [frames idx playing chips]} @state
        f (nth frames (min idx (dec (count frames))))]
    [:div.wrap
     [:style css]
     [:h1 "Reagami: the keyed patch algorithm"]
     [:div.row
      (map (fn [p]
             [:button {:key (:name p)
                       :on-click (fn [] (stop!) (recompute (:old p) (:new p)))}
              (:name p)])
           presets)]
     [:div.row
      [:button {:class (when playing "on")
                :on-click (fn [] (if playing (stop!) (play!)))}
       (if playing "pause" "play")]
      [:button {:on-click (fn [] (stop!) (step! -1))} "prev"]
      [:button {:on-click (fn [] (stop!) (step! 1))} "next"]
      [:button {:on-click (fn [] (stop!) (swap! state assoc :idx 0))} "reset"]
      [:span.step "step " (inc (min idx (dec (count frames)))) " / " (count frames)]]
     [:div.lane
      [:span.cap "children in the DOM"]
      ;; a chip without a status does not exist yet in this frame
      (map (fn [id] (chip chips id f))
           (filter (fn [id] (aget (:status f) id)) (js/Object.keys chips)))
      [:span.cap.cap2 "new children, with their source value"]
      (map-indexed (fn [i id] (vnode-chip chips f i id)) (:target @state))]
     [:div.text (:text f)]
     [:div.log (.join (:log f) "\n")]]))

(defn render []
  (r/render (js/document.querySelector "#app") [app]))

(add-watch state ::render (fn [] (render)))
(recompute (:old (first presets)) (:new (first presets)))
(render)
