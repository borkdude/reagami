(ns ssr-corpus)

;; :html is what reagami.ssr/render emits. :dom, when present, is what the
;; client renders instead, for attributes reagami.core sets as DOM properties.
(def cases
  [{:hiccup [:div "hello"]
    :html "<div>hello</div>"}
   {:hiccup [:div#myid.class1.class2 {:class :myclass} "hello"]
    :html "<div class=\"myclass class1 class2\" id=\"myid\">hello</div>"}
   {:hiccup [:div.class1.class2 "hello"]
    :html "<div class=\"class1 class2\">hello</div>"}
   {:hiccup [:div {:style {:color :blue}} "hello"]
    :html "<div style=\"color: blue;\">hello</div>"}
   {:hiccup [:div {:style {:border "1px solid black" :color "red"}}]
    :html "<div style=\"border: 1px solid black;color: red;\"></div>"}
   {:hiccup [:br]
    :html "<br>"}
   {:hiccup [:img {:src "a.png"}]
    :html "<img src=\"a.png\">"}
   {:hiccup [:div {:hidden true}]
    :html "<div hidden=\"true\"></div>"}
   {:hiccup [:div {:hidden false}]
    :html "<div></div>"}
   {:hiccup [:div {:tabindex 1}]
    :html "<div tabindex=\"1\"></div>"}
   {:hiccup [:div {:on-click identity :key "k" :title "t"}]
    :html "<div title=\"t\"></div>"}
   {:hiccup [:input {:default-value "x"}]
    :html "<input value=\"x\">"}
   {:hiccup [:input {:value "x"}]
    :html "<input value=\"x\">"
    :dom "<input>"}
   {:hiccup [:input {:value nil}]
    :html "<input>"}
   ;; disabled reflects the property back to an attribute, checked does not
   {:hiccup [:input {:disabled true}]
    :html "<input disabled=\"\">"}
   {:hiccup [:input {:checked true}]
    :html "<input checked=\"\">"
    :dom "<input>"}
   {:hiccup [:input {:checked false}]
    :html "<input>"}
   ;; :hydrate false because setting the innerHTML prop wipes the children patch
   ;; is about to diff. reagami.core loses them on any re-render, not just here.
   {:hiccup [:div {:innerHTML "<b>x</b>"} [:i "y"]]
    :html "<div><b>x</b><i>y</i></div>"
    :hydrate false}
   {:hiccup [:p "a & b < c > d"]
    :html "<p>a &amp; b &lt; c &gt; d</p>"}
   {:hiccup [:div {:title "he said \"hi\" & left"}]
    :html "<div title=\"he said &quot;hi&quot; &amp; left\"></div>"}
   {:hiccup [:div nil "x" nil]
    :html "<div>x</div>"}
   {:hiccup [:div 1 " " false]
    :html "<div>1 false</div>"}
   {:hiccup [:ul (list [:li "a"] [:li "b"])]
    :html "<ul><li>a</li><li>b</li></ul>"}
   {:hiccup [:svg {:viewBox "0 0 1 1"} [:path {:d "M0 0"}]]
    :html "<svg viewBox=\"0 0 1 1\"><path d=\"M0 0\"></path></svg>"}
   {:hiccup [:div [:span "a"] [:span "b"]]
    :html "<div><span>a</span><span>b</span></div>"}])
