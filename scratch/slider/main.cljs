(ns main
  (:require
   ["../../reagami.mjs" :as r]))

(defn calc-bmi [{:keys [height weight bmi] :as data}]
  (let [h (/ height 100)]
    (if (nil? bmi)
      (assoc data :bmi (/ weight (* h h)))
      (assoc data :weight (* bmi h h)))))

(def bmi-data (atom (calc-bmi {:height 180 :weight 80})))

(defn slider [param value min max invalidates]
  [:input {:type "range"
           :value value
           :id param
           :min min
           :max max
           :style {:width "100%"}
           :on-input (fn [e]
                       (let [new-value (parse-long (.. e -target -value))]
                          (swap! bmi-data
                            (fn [data]
                                   (-> data
                                     (assoc param new-value)
                                     (dissoc invalidates)
                                     calc-bmi)))))}])

(defn bmi-component []
  (let [{:keys [weight height bmi]} @bmi-data
        [color diagnose] (cond
                           (< bmi 18.5) ["orange" "underweight"]
                           (< bmi 25) ["inherit" "normal"]
                           (< bmi 30) ["orange" "overweight"]
                           :else ["red" "obese"])]
    [:div
     [:h3 "BMI calculator"]
     [:div
      "Height: " (int height) "cm"
      [slider :height height 100 220 :bmi]]
     [:div
      "Weight: " (int weight) "kg"
      [slider :weight weight 30 150 :bmi]]
     [:div
      "BMI: " (int bmi) " "
      [:span {:style {:color color}} diagnose]
      [slider :bmi bmi 10 50 :weight]]]))

(defn render []
  (r/render (js/document.querySelector "#app") [bmi-component]))

(add-watch bmi-data ::render render)

(render)
