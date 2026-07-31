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
   {:hiccup [:div {:innerHTML "<b>x</b>"}]
    :html "<div><b>x</b></div>"}
   ;; innerHTML owns the subtree, so the [:i "y"] child is dropped
   {:hiccup [:div {:innerHTML "<b>x</b>"} [:i "y"]]
    :html "<div><b>x</b></div>"}
   {:hiccup [:p "a & b < c > d"]
    :html "<p>a &amp; b &lt; c &gt; d</p>"}
   {:hiccup [:div {:title "he said \"hi\" & left"}]
    :html "<div title=\"he said &quot;hi&quot; &amp; left\"></div>"}
   ;; a nil child is a comment marker on both sides, so it keeps its slot and
   ;; hydration finds the same number of children the server wrote
   {:hiccup [:div nil "x" nil]
    :html "<div><!---->x<!----></div>"}
   {:hiccup [:div 1 " " false]
    :html "<div>1 false</div>"}
   {:hiccup [:ul (list [:li "a"] [:li "b"])]
    :html "<ul><li>a</li><li>b</li></ul>"}
   {:hiccup [:svg {:viewBox "0 0 1 1"} [:path {:d "M0 0"}]]
    :html "<svg viewBox=\"0 0 1 1\"><path d=\"M0 0\"></path></svg>"}
   {:hiccup [:div [:span "a"] [:span "b"]]
    :html "<div><span>a</span><span>b</span></div>"}])

;; rendered by reagami.ssr only. the DOM rejects the injected names, so these
;; cannot go through the client comparisons the cases above are used for.
(def escaping-cases
  [{:hiccup [:p "<script>alert(1)</script>"]
    :html "<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>"}
   {:hiccup [:div {:title "\" onmouseover=alert(1) x=\""}]
    :html "<div title=\"&quot; onmouseover=alert(1) x=&quot;\"></div>"}
   ;; angle brackets stay, matching how a browser serializes an attribute. a
   ;; double quoted value cannot break out on them.
   {:hiccup [:div {:title "<img src=x>"}]
    :html "<div title=\"<img src=x>\"></div>"}
   ;; names are not escaped, so an attacker controlled key or tag injects
   {:hiccup [:div {(keyword "x\" onload=\"alert(1)") "y"}]
    :html "<div x\" onload=\"alert(1)=\"y\"></div>"}
   {:hiccup [(keyword "div onload=alert(1)") "hi"]
    :html "<div onload=alert(1)>hi</div onload=alert(1)>"}
   {:hiccup [:a {:href "javascript:alert(1)"} "x"]
    :html "<a href=\"javascript:alert(1)\">x</a>"}
   ;; innerHTML is raw, so it can close the element it sits in
   {:hiccup [:script {:innerHTML "var s = '</script>'"}]
    :html "<script>var s = '</script>'</script>"}])
