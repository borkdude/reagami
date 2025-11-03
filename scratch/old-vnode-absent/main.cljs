(ns main
  (:require ["../../reagami.mjs" :as reagami]))

(def state
  (atom
   {:tab :appointments
    :search ""
    :selected-patient nil
    :patients
    [{:id 1 :name "Alice Vermeer" :age 31 :condition "Checkup"}
     {:id 2 :name "Bas de Groot" :age 47 :condition "Cavity"}
     {:id 3 :name "Carla Janssen" :age 29 :condition "Teeth whitening"}
     {:id 4 :name "David Bakker" :age 56 :condition "Crown replacement"}
     {:id 5 :name "Els de Vries" :age 40 :condition "Root canal"}]
    :appointments
    [{:id 1 :patient "Bas de Groot" :time "10:00" :dentist "Dr. Maria"}
     {:id 2 :patient "Alice Vermeer" :time "10:30" :dentist "Dr. Koen"}
     {:id 3 :patient "Carla Janssen" :time "11:00" :dentist "Dr. Maria"}]}))

(defn appointments []
  [:table {:style {:width "100%" :border-collapse "collapse"}}
   [:thead
    [:tr {:style {:background "#f0f0f0"}}
     [:th {:style {:text-align "left" :padding "6px"}} "Time"]
     [:th {:style {:text-align "left" :padding "6px"}} "Patient"]
     [:th {:style {:text-align "left" :padding "6px"}} "Dentist"]]]
   [:tbody
    (for [{:keys [id time patient dentist]} (:appointments @state)]
      [:tr {:key id :style {:border-bottom "1px solid #ddd"}}
       [:td {:style {:padding "6px"}} time]
       [:td {:style {:padding "6px"}} patient]
       [:td {:style {:padding "6px"}} dentist]])]])

(defn ui []
  [:div
   ;; Main area
   [:div {:style {:padding "16px"}}
    (case (:tab @state)
      #_#_:patients
      [:div
       [:h2 "Patient Records"]
       [patient-list]
       [patient-details]]

      :appointments
      [:div
       [:h2 "Appointments"]
       [appointments]])]])

(defn render []
  (prn :render)
  (reagami/render (js/document.querySelector "#app") [ui]))

;; Reactivity
(add-watch state ::render (fn [_ _ _ _] (render)))

;; --- Live updates simulation ---

(def dentist-names ["Dr. Maria" "Dr. Koen" "Dr. Lisa" "Dr. Ahmed"])
(def patient-conditions ["Cleaning" "Cavity" "Implant" "Checkup" "Root canal" "X-ray"])

(defn random-patient []
  (rand-nth (:patients @state)))

(defn random-time []
  (let [h (+ 9 (rand-int 7))
        m (rand-nth ["00" "15" "30" "45"])]
    (str h ":" m)))

(defn add-random-appointment! []
  (let [p (random-patient)
        new {:id (inc (count (:appointments @state)))
             :patient (:name p)
             :time (random-time)
             :dentist (rand-nth dentist-names)}]
    (swap! state update :appointments conj new)))

(defn random-update-condition! []
  (swap! state update :patients
         (fn [ps]
           (let [i (rand-int (count ps))
                 c (rand-nth patient-conditions)]
             (assoc-in ps [i :condition] c)))))

;; Add updates every few seconds

#_(js/setInterval add-random-appointment! 5000)
#_(js/setInterval random-update-condition! 7000)

;; --- Mount ---
(render)

(add-random-appointment!)
(add-random-appointment!)
(add-random-appointment!)
