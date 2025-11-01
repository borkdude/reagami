# Reagami

![npm](https://img.shields.io/npm/v/reagami.svg)

Fold your state into the DOM!

A minimal zero-deps [Reagent](https://github.com/reagent-project/reagent)-like in [Squint](https://github.com/squint-cljs/squint) and CLJS.

## Usage

Quickstart example:

``` clojure
(ns my-app
  (:require ["https://esm.sh/reagami" :as reagami]))

(def state (atom {:counter 0}))

(defn my-component []
  [:div
   [:div "Counted: " (:counter @state)]
   [:button {:on-click #(swap! state update :counter inc)}
    "Click me!"]])

(defn render []
  (reagami/render (js/document.querySelector "#app") [my-component]))

(add-watch state ::render (fn [_ _ _ _]
                            (render)))

(render)
```

([Open this example on the Squint playground](https://squint-cljs.github.io/squint/?src=gzip%3AH4sIAAAAAAAAE3VQu27DMBDb%2FRWMssiD486aCuQTOhpGoUqX2m30iB4NgiD%2FXlhWjC7VDboTSB4pbiPMrZPeNwAXgS55DoSBTSn5KPqeojnEqQ8kP6WZGYSMqMPYtk3DNZ0Qk0wELpMzuAvlsk0U8PJ4AuyyQznjnSWbMIwNMAg9%2FzSoDdixsLQAA98kXotyO664j5ySs7gLZzt1ntU39jxepd9VA9nr5drYs1XtY6GCHQvc0I6N42YqkNUUVju8hurrI%2F%2BKvXYqG7LpcMkUbm90JpVcANtL71mL4W%2Bo9TOk1t1VJjVVR0I85U4WwztKlTT%2FHr4y2qJX%2B19OfcYnpgEAAA%3D%3D))

In ClojureScript you would add this library to your `deps.edn` `:deps` as follows:

``` clojure
io.github.borkdude/reagami {:git/sha "<latest-sha>" :git/tag "<latest-tag>"}
```

and then require it with `(:require [reagami.core :as reagami])`.

Reagami supports:

- Building small reactive apps with the only dependency being Squint or CLJS. Smallest app with Squint after minification is around 3.5kb gzip.
- Rendering [hiccup](https://github.com/weavejester/hiccup) into a container DOM node. The only public function is `render`.
- Event handlers via `:on-click`, `:on-input`, etc.
- Id and class short notation: `[:div#foo.class1.class2]`
- Disabling properties with `false`: `[:button {:disabled (not true)}]`
- `:style` maps: `{:style {:background-color :green}}`

Reagami does NOT support:

- Auto-rerendering by auto-watching custom atoms. Instead you use `add-watch` +
  `render` on regular atoms or you call `render` yourself.
- Local state and form-2 components
- React hooks (it doesn't use React)

Reagami uses a basic patching algorithm explained in [this](https://blog.michielborkent.nl/reagami.html) blog
post. It may become more advanced in the future, but the (fun) point of this
library at this point is that it's small, underengineered and thus suited for
educational purposes.

For a more fully featured version of Reagent in squint, check out [Eucalypt](https://github.com/chr15m/eucalypt).

## Examples

Examples on the Squint playground:

- [Input field + counter](https://squint-cljs.github.io/squint/?src=gzip%3AH4sIAAAAAAAAE41TO2%2FbMBDe%2FSs%2BMyhADn50Kzg0AYqsXTIKRsGIF4utRDLkyYZh%2BL8X1KOCPVUcRB6%2Fx93pJH1GZ5xfAVIn%2BuxdIlSiYY5Z73aUu21udonM0XTuZb%2Fdb79%2BE9AmY4odlFqtpKUPZDZMkIZDh6uuQ%2B%2BZEvbQuQlncOrpNmM9eofqsAIqbd1phbJ575mDx1UHv6lbV%2F%2FBk8xnE9eTch9teY1yPrC6FR7EWxPOzxCQ483LgFZFHPLckH%2B4GEijL64686WlId02JOhjIvK3UbiAYiLImDaZ053wcOl87BlXfTJtT5DT%2Bc5meUpVI%2BC%2BKpNzqDFx5eY7vkCzSUdijMJK3RbL%2F%2B3R3H3nazVXA%2FFjYHS0FrOidB%2BSTuSfIQfKYxVqqaPSEeL1RB4DYtGY%2B%2FTYTJHIin%2B9LP4%2FA6O4rcVBlTUNTgqBy%2FyFBPk772yo%2B448bz97Spc3aqnmIvdkYhRjPtIGDvfgOpFhem2pnCCsO4k5d5mJ19g4C7FI4J7%2BHuxlGxNF8lapZU4TeUtpnFU5jfxuCpa8UfXTL2Cs3ZwN1830KbQeYYX44VH9wrAORabER5Np%2FxfQFNEHhwMAAA%3D%3D)
- [Boring crud table](https://squint-cljs.github.io/squint/?src=gzip%3AH4sIAAAAAAAAE71WS28bNxC%2B61dMaBSggKwc9FJgAyQu2lx7aI5b1qDEkcR6l1yTs3IEwf%2B94GOftpz0UgkS9jHzceabb4bkxkMjtVkB8NLhY6cdQsWORK0vb2%2FRNxt%2FvHUoD7LRDErpId%2BI9Xq14gr3sLeuKbTRBJfSyAaBsRUsPiUqTdocPgO5Dp%2BzqzU7BE%2BSELgk28CllEY3svZweX6BkZHCcuOaz30YBqRSRXJ%2FB5UIGfkn2b7rF9gbqOKleInMaySoYvC8%2BJQ90koxpfWVYEAr4J4ccCeNsk3RdVqt16%2BsAMCl93ZXaJPhqyFXrURPXfwrtQKtntfh0yfnsLEnHPOTLxLsWhWj7lGVDusBD2hygpTs3kIaAh1DzCgiIPVAe421KrRpOyqO0qga3QqA%2FYnUOeNBQn4IdJSUl%2FVwknWHoA3Egjeyfb8C6IxCBw94fg9Pmo7ZaO9sA9b8dpTmgIAnNBSUVUXPBzzHwENZc0lzEuF1LkDMJJjme77ZAEJB0h2QoIjLTFlOyZxkrdVnqKLIBslwj4%2FAU5nGNxPnIHG5rTFRAlXirtAqeus98ANSoPVuKYDBcuwTkSRXlQnsUno61wiXcmtd4Ir93H4Db2utYFvL3QNb9EtJ5xaBEX6jRTeWidwfCSbkKhbaL4eC3HxPNEuk2Fs%2Fwabnf9MX4FnkZNv%2FEJaYdH565%2BxTL%2BfYz%2Bnxdche1GOGw5Ti48BK1us%2BRHLJuipJQbWoeY8opjbltiOyBj5%2B3GzJhF%2FROt1Id960XV0XTh%2BONCX5UirtA6oCbiwBn8kydOCyILXePaRWeHX0ROk%2FHdEMCV6zAuCL%2BZBVfhX25Qh6dXJMlA3G0no902vojoFw9lWekAH7ojSxq1xCJHMkMPKqgjDdnMuenhu%2BmKG5sLNA2F%2Fdhw%2B%2F%2F8KEENPZ65GK0POvbSxpxiy2pRfajK%2Bjcy%2BhkND%2F3ePjjvZKW2cNX23rifMbnSxWy2L9kOrf1jxPi98NI3ce%2BO77%2BueT08E1MfNJoWcCZb8qxcbMhFhU1%2FeVVfqUjUIqm8l%2F4cnpFvuK0xGlGoTdKzY8B%2FaHbJCJfDO5EMMI2lqVdzTeyDYlXt2DFPPs%2F76UD3hOBxQ2zsiCDYeCuaCqyRyVo0D40Mt3s2FQTaQtxIQUZy0VO9u01qChBTc391XZOgTeusBJjylgNUL6ASxihY6zDvg%2F%2FlbZXdegoc1jh%2B78FWvckXXAbmTbshQXV5bs3HjnUBJ%2BqTHcAVP6xPrsuEd6B2FbYSMEzN0D3ZvWYYtGzc9k8diSRkI%2BFt%2FmhyFuqOZMpJEShPgkaXfsj5llcukPM%2FcQvyJAhudpwXz9L76X4DewCwAA)
- [Snake game](https://squint-cljs.github.io/squint/?src=gzip%3AH4sIAAAAAAAAE41WW4%2BjNhR%2Bz684y6iS2YoJTLsvZGenlbqqql2pUi9PiFYOPiSeGJu1HRI6yn%2BvbJNAZpnVECngw%2BfP534g0kBDuVwAkFzjlz3XCEW0tbY1%2BXKJprk126VGuqENjyCnBoZFGceLBWFYg%2BH%2FIdylMaxWsNGcBQG5S493aRwgFQoBWYAgrbZBcA9Z2h4Di5IVgrHUolOFWtXAU24k3SEUxTvI0hKKH8PtB3crYQVccsupgABbK9Yv4HzljGsoMkgdsFEdlxvQfLO1E0itFIMiC%2BwjnRNPUFTwDh%2FA6j2ezjZL0FQy1SSBolwAFMSJEi6ttz%2BGZ%2BvysrVRHSaDaW4nMQfavrkYD0BqCcVTvsPeQBGAzhp%2FVtCm9JEw9lQOihJeA5HKDu%2Fji%2F5mNJgItFBskTIgHVZQkO%2BBSLsFUnNtbHBjDGkcpO7INB6Z5q95jmzCkcXO9pmt1OIDkHvwGjnjZlESD4OzvNKkUtKELc5mzzG8Xu%2BtoBcd4nnVj0ExT5DOIvoJIptFbLlNDlSIByBKA3kPR%2B%2B099C%2FQHnlsA%2F3cDynyId76MPziwcZFPUDEKMahJuzt75z6YXGjt6J4%2FI5g3MV%2B5p2oj01RlVg7CXJayoMzqgyUeTVe0J4R3iI0hjOUH5kUkhzTshRGPwGTRyuxWK1gr94gxqEUu2CPJqlQfubtKg7KqY1d5emAf4J%2B7WimkGlpNVKmAW5pYx97FDaz9xYlKjh0SwPXDJ1gGiHPVMHGS2GCsXg8VBWO%2ByB3CbuhpdQTCsb9i1zN9eYXs4RT8y4%2FiqY14GlBmGH%2FbdzLfpZa3X4u41Cd7j31VikkJXx%2BTHJ5kvzOckvzu5nNMmE55U0n7G21zSuQQ8siX9%2BDc0fro9f8yQTolfwMK7PeRN6sptHicbKQlEcoS%2BhUkL5KBS5Fz%2FlRyBv4eiRMeS9W%2FVhNTkrP3Bmh%2FGWb9Ep6hdTSM2FCPyncShsaINJSMcwFHxWXQ%2BB6QA4wU8%2BrUKiFDnjXTijyE23gadBEfI2TONB60GjK%2BmVp3Jje4HwlK%2BVZqghytojGCU4g7Wg1S6a9Wu%2BptVuo9VeMohuEOvodBqAq1Xox%2BeKqJWGoqV6aNNjnv%2FjbQVirAb3Pj5BMUbF74g2GlFGl%2BCuVtNZPUF7R0UaWVSOUOdhUB1q%2FyfouXrIYYtyfngWucVjiP271MU8S1PXt6RNho%2BeIZpRcM4Jol%2FdKb93qN9EZVyOH0laKTeJ%2FcB4NEumqn2D0t5%2B2aPu%2F0SBlVUaohvatlFQgDBl1TW40kgtfhToVhAx3kXxpdWgfQMJZxCNFHC93X0h3bYaW5RsmvwapYt1yLvh8245CJ3eUIzZGSyijCUHaqvt0NvyPMDPrfFf8L%2FS0Tl5OGx4%2Fh8hQm1YdAoAAA%3D%3D)
- [Draggable button](https://squint-cljs.github.io/squint/?src=gzip%3AH4sIAAAAAAAAE5VWy47bNhTdz1fcqhsKqOwJ0AIFVSBB2%2BzaLloUaCEYAS1ePTI0qZBXttWB%2Fz3gQxq%2FEkzthSzx3AfPPTwy0w52otcPAIxb%2FDT2FqHKOqLB8fUa3W7lurVF0Ypd%2Fy4DLhyku02ePzwwiQ1slaifis4oLAbj4Jkf4fvHR%2BCTv5xmlCNBCEyQ2QXImwgJF6H6Pb4FsiOefNqyhL%2BpVz1N0Iy6pt5oF7JoqJVx%2BBaqI0wb37XQEthPwH4X1K3F1gEr4AiMH6%2FayvMcfnjMHwDgBj8B49N9vO8mVW6Rilr1qKmwWBNUuKfQgkKCygJbtUg%2Fm1HLXre%2FBOCfHsdWBQnbIgHuKc83oYVnrrAJa%2BFq8%2B84mSFgzQA2n2l4v0dN0AktFdqZg50ZHRY7s8cirUBlmsZh7KfRS29zd0e%2FTbZK%2Ff8TOgkkxbA88hI%2F0wX23xk7LdhNArO%2BAZbmcYTpJQez6JC%2BSSN%2FnsfbCOXw9GXYEY5nbdx8%2BATTV9cvVZTP%2Bpz5GocztnSgb6Hrw8JXWYJFvwbUYYwMd6p3hBptJHUVMWE6v6UV%2BOjWh15Lc4BsicsglbppR5qDfmkotvLRrWujnVG4UqYFHpAe6EkrSy9B2CaFQRChaUKfs8AU7lDTIspn%2FoSTgypojMywOb1GpWdqiCMPs7kSUA4%2BaX4zEH4jn9xXzk8LsixBYtNrhMmMFgK7Mw9sMiMIZVHICTqxR%2BjPaiQqgd05AEmcyxCFlF%2BenpDy%2F4zuJuc4LBmBOkFJMu5SM68tNg7ZvKUzjb7oZhGOtKJtxVZhsR2JjIbKb7fist%2F7WhXv3gAbbOHIwrtwqiIfFY%2F45DyOJuXP239FryUe4cfLIfLBuN5bLmRi64waCbMrRPQuX%2BaO0UI2HLMcyquYYG8x5MZrY8hVwFbUT631SoWsNspYHsPK7BTFlP1hCDq0CINC4TALm2WHDjWw2Q0SDzH3BRGvoOI1ZNzQkSrep%2BGaiAvwckZ4GH46%2B3f8YgZmv1rRwg6zTb5ZVGKNoaI2u8Fo%2F%2B6IGrmWzgwPaO8Xxgb7kaYevYWsPo1op79QYU3GQvatGIY0ISYNmUtwbVEQvo%2FuA5ns98s0WbD4opeQvaSAy%2FCtkdNqsDiglud6t6ilN8fgjelvxzo99H1DdbnX%2BJdESFkcBNVderFwHkMWr4fw3fiU%2FnksmH5%2FBvGeevYRCQAA)
- [CSS transition](https://squint-cljs.github.io/squint/?src=gzip%3AH4sIAAAAAAAAE4VTuW7cMBDt9RVjuqGASLsu3LBKEyB9SkEIuORoRYeXydGuFwv%2Fe6DDeySCLRbikG8e31zcZ3DS%2BAKAi4Svg0kIDeuJYhabDWZX536TUO6lMwyEzLAYbVkWBdfYQSZJCFxScHAWfThggk7ajO8fEA%2B78Fap4GLw6AmatgBohDYHOItMJ4twFkejqQf2tN3GN1bAv5%2Fo0ex7%2BgSwk%2BrPPoXB60oFGxJw0wFfBH2fVJbAEmoGbGcHZOUKCSXpsyETPLD%2FCLf1cwaUGb%2FBhOtCctfDNU1X2KqYrKRF%2FlQ%2Fl%2BxilKu6tMnRyhOwzuJq9NKava8MocvAFHrCtAZ7GTKZ7lSp4GksxSfQOWZ27A2txqaGlEdADGaieL9iRPCVC0PGiRweeT7K%2BLB0yhD1%2BFty4QOVK44W5QG%2FdJyeZD8n2yFrL%2F2WQqDVhhsdGhETAo%2BpynQpRjvd3PVp%2B8E30Y0jMjbVS97ooAaHnurXAdPpF1pUNGbiUcZ4qR7XgcI9XCWUhD8suin12hxuas0z0gNURgO7pYF7il3QpzomjOh1WV4HLKHXmOZA%2BTKim%2BVwVA%2FNfUrm8ZVaV0dJql8yLMTscltt3nlofsO02pF7BMwvL%2Fu%2FoRmemkUEAAA%3D)
- [Ohm's law](https://squint-cljs.github.io/squint/?src=gzip%3AH4sIAAAAAAAAE41U24rbMBB9z1dMVcrKBSdOtvuih15Y6FOh0MK%2BGFOm9iRWK8teSU42hPx7sXwv2c3KINvjOWfOXCxu6LGWhuAmZrlzlRWrFdliafOVIdxhIRkItNC9JMFiwTPaarBKZmQgdjmF6MoCUlRpuNVQocEC9qhqgkJqKPAJrKMKpN6jkhk6sskCIBZSV7WDk3DHioAZ1DtiCxiXaEn8PrN7WqnnNnxqQs1sPmyz%2FWc9KoKTOMjM5cDuonfsPHModdhK41sNMSXTj9PFFTmINR3CViiv0FgKs7L%2Breg50AUa3JHrdgLm0OzIsQCYZ2VB8KwAAG4PWL2BvgkvBfXJZOjwBTrvF36Exu1aAhytLdOu3UMRgquwTHrcZBquYrrRCpp1HkfQm8u8sBCfxF86Woj3pXK4I0hrY0g7MGSldahTSvwYN4mdmwJwuQWupfoEHaQV0SXVuIHoyfj7C3zBBUDvxVc96wwQdMqh0Rx6CPe%2FzmkItd6MLNHyDsSIh82HczDk7ikUHsKqgcU%2BJT%2BOr6jEGT4PCtphiEUm920XYpHfAvueFzcWvuEB7lGltUJXGpb0HoMzsIc2jAAGfOnKr%2FKJsiH5TQDsof%2Bl4%2B7EGJMf%2Bzfk398juI0gWq6HaiSXgt%2B33%2BbB%2B4Sb4F9eFbxHDIWHW4iW0XqQdTH4j6Gg8%2FjTjgXAXqVggpk8RrCOIpjKSMYBMKT9%2Bes7353Oq87I%2F9hVVqZ1QdotH2syx5%2BkKHWlAfYWq4q1sxvPhqjlxiwLD%2BjSfCJUWIeO2gPkF%2FjryhnSKGqUtCPfPf8D1kkK%2FGsGAAA%3D)
- [Multi select](https://squint-cljs.github.io/squint/?src=gzip%3AH4sIAAAAAAAAE31TTa%2FTMBC891cs%2By6ORFIQQkK%2BgIA7B45RhEy8SVwldmpvWkrV%2F44cO6%2BiD5FI%2BVhPZiaza2EDTMrYHYCQno6L8QQ1DsxzkPs9hakKw96T6tVkEKQKkF%2BaHQDU7egOi6cqsDe2X9cD%2B6YodjuhqYPAigmEYjfBVQYaqWXS8HTFVjEC%2FjRe4%2B224S0MyuqRynZQtieoKcqIkRhqN7NxNoCY1HwCeVLjQiCqCghKVr4nhnIT%2BJawRbG6BBHOan6VzagQXAt3L27DPntIFaiTBP%2FiyFLLXL5m6XS984jWWVbGho8g7tVPq2iR0MUt2omEz1qLgTrRa3OKEfFlJLjKzlkuOzWZ8QIYlA1lIG86BDkrrWPW%2BNbThLeVspad89P6r4kIv2cDEjBFYDoQgY7%2F8FasgJgS%2B%2F3BGQv4GvA%2FQBTWWSowp1tnIFzltIxs5pGA%2FUIZvR0ymN8E7x6rzm7N%2Fqv1Lz7OuUzK98aW7GbAN9X7GMEDdPtiMrY8G80D4Id7UrANEqB2PQJ%2BdT02DytpNr8ofrGyzivg53hrmubeR09Wk0%2B9FHmL7HNRHMJeu3aZyHJ1XMhfUnecB3xS84wF1ItJm0ZpXZ4Vt0OeVikTSaTtLNQ%2FYD2bKBLraWzz8x8d%2Bf9YzgMAAA%3D%3D)

## License

MIT
