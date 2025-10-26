(ns main
  (:require
   ["../../reagami.mjs" :as reagami]))

(defn slider [the-atom calc-fn param value min max step invalidates]
  [:input {:type "range"
           :value value
           :min min
           :max max
           :step step
           :style {:width "50%"}
           :on-input (fn [e]
                       (js/console.log :e e)
                       (let [new-value (js/parseFloat
                                         (aget (aget e "target") "value"))]
                         (js/console.log :new-value new-value)
                         (swap! the-atom
                           (fn [data]
                             (-> data
                               (assoc param new-value)
                               (dissoc invalidates)
                               calc-fn)))))}])

(defn calc-ohms [{:keys [voltage current resistance] :as data}]
  (if (nil? voltage)
    (assoc data :voltage (* current resistance))
    (assoc data :current (/ voltage resistance))))

(def ohms-data (atom {:voltage 12 :current 0.5 :resistance 24}))

(defn ohms-law-page []
  (let [{:keys [voltage current resistance]} @ohms-data]
    [:div
     [:pre (pr-str @ohms-data)]
     [:h3 "Ohm's Law Calculator"]
     [:div
      "Voltage: " (.toFixed voltage 2) "V"
      [slider ohms-data calc-ohms :voltage voltage 0 30 0.1 :current]]
     [:div
      "Current: " (.toFixed current 2) "A"
      [slider ohms-data calc-ohms :current current 0 3 0.01 :voltage]]
     [:div
      "Resistance: " (.toFixed resistance 2) ""
      [slider ohms-data calc-ohms :resistance resistance 0 100 1 :voltage]]]))

(defn render []
  (reagami/render (js/document.querySelector "#app")
    [ohms-law-page]))

(add-watch ohms-data :state (fn [_ _ _ n]
                              (prn :new n)
                              (render)))

(render)
