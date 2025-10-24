# Reagami

Fold your state into the DOM!

A minimal [Reagent](https://github.com/reagent-project/reagent)-like in 100 lines of [squint](https://github.com/squint-cljs/squint).

## Usage

Reagemi is intended to be used with [Squint](https://github.com/squint-cljs/squint).

Quickstart example:

``` clojure
(ns my-app
  (:require ["https://esm.sh/reagami@0.0.8" :as reagemi]))

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

Reagami supports:

- Building small reactive apps with the only dependency being squint. Smallest app after minification is around 3.5kb gzip.
- Rendering [hiccup](https://github.com/weavejester/hiccup) into a container DOM node. The only public function is `render`.
- Event handlers via `:on-click`, `:on-input`, etc. These get translated to `(.addEventListener node "click" f)`.
- Id and class short notation: `[:div#foo.class1.class2]`
- Disabling properties with `false`: `[:button {:disabled (not true)}]`
- `:style` maps: `{:style {:background-color :green}}`

Reagami does NOT support:

- Auto-rerendering by watching custom atoms. Instead you use `add-watch` on regular atoms! :)
- Local state and form-2 components
- React hooks (it doesn't use React)

Reagami uses a very basic patching algorithm explained in [this](TODO) blog
post. It may become more advanced in the future, but the (fun) point of this
library at this point is that it's small, underengineered and thus suited for
educational purposes.

For a more fully feature version of Reagent in squint, check out [Eucalypt](https://github.com/chr15m/eucalypt).

## Examples

Examples on the Squint playground:

- [Input field + counter](https://squint-cljs.github.io/squint/?src=gzip%3AH4sIAAAAAAAAE41TO2%2FbMBDe%2FSs%2BMyhADn5MHTg0AYqsXTIKRsGI54ipRDLkyYZh%2BL8X1KOCPVUcRB6%2Fx93pJH1GZ5xfAVIn%2BupdIlSiYY5Z73aUu21udonMh%2Bncy367334X0CZjCh2UWq2kpSMyGyZIw6HDVdeh90wJe%2BjchDM49XSbsR69Q3VYAZW27rRC2bz3zMHjqoPf1K2r%2F%2BBJ5rOJ60m5j7a8RjkfWN0KD%2BKtCednCMjx5mVAqyIOeW7IP1wMpNEXV5350tKQbhsS9Eci8rdRuIBiIsiYNpnTnfBw6XzsGVd9Mm1PkNP5zmZ5SlUj4L4qk3OoMXHl5ge%2BQbNJH8QYhZW6LZb%2F26O5%2B87Xaq4G4ufA6GgtZkXpjpJO5J8hB8pjFWqpo9IR4vVEHgNi0Zj79NhMkciKf70s%2Fr8Co7itxUGVNQ1OCoHL%2BIUE%2BZl3NtR9R563Xz2lyxu1VHORezIxijEfaQOHe3CdyDC9tlROENadxJy7zMRrbJyFWCRwT38P9rKNiSJ5q9Qyp4m8pTTOqpxGfjcFS96o%2BukXMNZuzobrZvoUWo%2BwQjx6VL8xrEORKfHRZNr%2FBf022K2GAwAA)
- [Boring crud table](https://squint-cljs.github.io/squint/?src=gzip%3AH4sIAAAAAAAAE71WS28bNxC%2B61dMaBSggKwc9FJgAyQu2lx7aI5b1qDEkcR6l1yTs3IEwf%2B94GOftpz0UgkS9jHzceabb4bkxkMjtVkB8NLhY6cdQsWORK0vb2%2FRNxt%2FvHUoD7LRDErpId%2BI9Xq14gr3sLeuKbTRBJfSyAaBsRUsPiUqTdocPgO5Dp%2BzqzU7BE%2BSELgk28CllEY3svZweX6BkZHCcuOaz30YBqRSRXJ%2FB5UIGfkn2b7rF9gbqOKleInMaySoYvC8%2BJQ90koxpfWVYEAr4J4ccCeNsk3RdVqt16%2BsAMCl93ZXaJPhqyFXrURPXfwrtQKtntfh0yfnsLEnHPOTLxLsWhWj7lGVDusBD2hygpTs3kIaAh1DzCgiIPVAe421KrRpOyqO0qga3QqA%2FYnUOeNBQn4IdJSUl%2FVwknWHoA3Egjeyfb8C6IxCBw94fg9Pmo7ZaO9sA9b8dpTmgIAnNBSUVUXPBzzHwENZc0lzEuF1LkDMJJjme77ZAEJB0h2QoIjLTFlOyZxkrdVnqKLIBslwj4%2FAU5nGNxPnIHG5rTFRAlXirtAqeus98ANSoPVuKYDBcuwTkSRXlQnsUno61wiXcmtd4Ir93H4Db2utYFvL3QNb9EtJ5xaBEX6jRTeWidwfCSbkKhbaL4eC3HxPNEuk2Fs%2Fwabnf9MX4FnkZNv%2FEJaYdH565%2BxTL%2BfYz%2Bnxdche1GOGw5Ti48BK1us%2BRHLJuipJQbWoeY8opjbltiOyBj5%2B3GzJhF%2FROt1Id960XV0XTh%2BONCX5UirtA6oCbiwBn8kydOCyILXePaRWeHX0ROk%2FHdEMCV6zAuCL%2BZBVfhX25Qh6dXJMlA3G0no902vojoFw9lWekAH7ojSxq1xCJHMkMPKqgjDdnMuenhu%2BmKG5sLNA2F%2Fdhw%2B%2F%2F8KEENPZ65GK0POvbSxpxiy2pRfajK%2Bjcy%2BhkND%2F3ePjjvZKW2cNX23rifMbnSxWy2L9kOrf1jxPi98NI3ce%2BO77%2BueT08E1MfNJoWcCZb8qxcbMhFhU1%2FeVVfqUjUIqm8l%2F4cnpFvuK0xGlGoTdKzY8B%2FaHbJCJfDO5EMMI2lqVdzTeyDYlXt2DFPPs%2F76UD3hOBxQ2zsiCDYeCuaCqyRyVo0D40Mt3s2FQTaQtxIQUZy0VO9u01qChBTc391XZOgTeusBJjylgNUL6ASxihY6zDvg%2F%2FlbZXdegoc1jh%2B78FWvckXXAbmTbshQXV5bs3HjnUBJ%2BqTHcAVP6xPrsuEd6B2FbYSMEzN0D3ZvWYYtGzc9k8diSRkI%2BFt%2FmhyFuqOZMpJEShPgkaXfsj5llcukPM%2FcQvyJAhudpwXz9L76X4DewCwAA)
- [Snake game](https://squint-cljs.github.io/squint/?src=gzip%3AH4sIAAAAAAAAE41WW4%2BjNhR%2Bz684y6iS2YoJTLsvZGenlbqqql2pUi9PiFYOPiSeGJu1HRI6yn%2BvbJNAZpnVECngw%2BfP534g0kBDuVwAkFzjlz3XCEW0tbY1%2BXKJprk126VGuqENjyCnBoZFGceLBWFYg%2BH%2FIdylMaxWsNGcBQG5S493aRwgFQoBWYAgrbZBcA9Z2h4Di5IVgrHUolOFWtXAU24k3SEUxTvI0hKKH8PtB3crYQVccsupgABbK9Yv4HzljGsoMkgdsFEdlxvQfLO1E0itFIMiC%2BwjnRNPUFTwDh%2FA6j2ezjZL0FQy1SSBolwAFMSJEi6ttz%2BGZ%2BvysrVRHSaDaW4nMQfavrkYD0BqCcVTvsPeQBGAzhp%2FVtCm9JEw9lQOihJeA5HKDu%2Fji%2F5mNJgItFBskTIgHVZQkO%2BBSLsFUnNtbHBjDGkcpO7INB6Z5q95jmzCkcXO9pmt1OIDkHvwGjnjZlESD4OzvNKkUtKELc5mzzG8Xu%2BtoBcd4nnVj0ExT5DOIvoJIptFbLlNDlSIByBKA3kPR%2B%2B099C%2FQHnlsA%2F3cDynyId76MPziwcZFPUDEKMahJuzt75z6YXGjt6J4%2FI5g3MV%2B5p2oj01RlVg7CXJayoMzqgyUeTVe0J4R3iI0hjOUH5kUkhzTshRGPwGTRyuxWK1gr94gxqEUu2CPJqlQfubtKg7KqY1d5emAf4J%2B7WimkGlpNVKmAW5pYx97FDaz9xYlKjh0SwPXDJ1gGiHPVMHGS2GCsXg8VBWO%2ByB3CbuhpdQTCsb9i1zN9eYXs4RT8y4%2FiqY14GlBmGH%2FbdzLfpZa3X4u41Cd7j31VikkJXx%2BTHJ5kvzOckvzu5nNMmE55U0n7G21zSuQQ8siX9%2BDc0fro9f8yQTolfwMK7PeRN6sptHicbKQlEcoS%2BhUkL5KBS5Fz%2FlRyBv4eiRMeS9W%2FVhNTkrP3Bmh%2FGWb9Ep6hdTSM2FCPyncShsaINJSMcwFHxWXQ%2BB6QA4wU8%2BrUKiFDnjXTijyE23gadBEfI2TONB60GjK%2BmVp3Jje4HwlK%2BVZqghytojGCU4g7Wg1S6a9Wu%2BptVuo9VeMohuEOvodBqAq1Xox%2BeKqJWGoqV6aNNjnv%2FjbQVirAb3Pj5BMUbF74g2GlFGl%2BCuVtNZPUF7R0UaWVSOUOdhUB1q%2FyfouXrIYYtyfngWucVjiP271MU8S1PXt6RNho%2BeIZpRcM4Jol%2FdKb93qN9EZVyOH0laKTeJ%2FcB4NEumqn2D0t5%2B2aPu%2F0SBlVUaohvatlFQgDBl1TW40kgtfhToVhAx3kXxpdWgfQMJZxCNFHC93X0h3bYaW5RsmvwapYt1yLvh8245CJ3eUIzZGSyijCUHaqvt0NvyPMDPrfFf8L%2FS0Tl5OGx4%2Fh8hQm1YdAoAAA%3D%3D)
- [Draggable button](https://squint-cljs.github.io/squint/?src=gzip%3AH4sIAAAAAAAAE5VWXY%2FjNBR9n19xCS%2BOtEm7EiDkIO0K2DfgAYQEiqqVG98k3nHtrO10Ekb978gfyfRr0dA%2BpI3P%2FfC5xychysKBCfUAQKjBz6MwCHXWOzdYutmgPZS23xhkHTuI99tyW36XAWUW0q1dnj88EI4t7CVrHoteSywGbeGZTvDNdgt09pfTgrKOOQTCnD4EyNsICRcmxRHfgTMjnnzaqoI%2FnZDCzdCOqnFCKxuyKGiktvgO6gnmnW%2BdKQ7kByC%2FMtdv2N4CKWACQqertvI8h2%2B3%2BQMA3OBnIHS%2Bj%2FfdpModuqKRApUrDDYOajy60IJEB7UBUnboftSj4kJ1PwXg7x5HysIx06EDPLo834UWnqnENqyFq8nfUKeHgNUDmHyh4cMRlYOeKS7RLBwc9GixOOgjFmkFat22FmM%2FrVp7W7qb%2FDZJmfr%2FK3QSSIpheeQlfuYL7N8Ldl6xuwQmogWS5jHB%2FJKDGLTovkojf17G2zJp8fRl2ATTWRs3HzrD%2FJ%2FrlyrKF30ufI3DGVsq0LfS9XHlq6rAoF8D12OMDP%2BksA4VmkhqGTFhOr%2BkFfhkN09Ccf0E2RqXQSp10w7XT%2BqlodjKJ7tptLJaYil1BzQgPdCTVlVegrBPCoMgQt2GPheBSTygcqson%2BkjzhbqoDGnh93pNSo9U0MceZjNlYBy8Enzm4HQG%2FnkvnJ%2BWpFVBRxboRBmPRoI7C48kFmPwKRBxmfo2RFBnNVIVAK5cwCSONchMs6%2FPD3G%2Bf8Z3U3OcVgzguuZS5Kxl5p5bbFxyJYtnWn0RTercLhhXcf2Eov96JxWUPvt1pSLo69V0%2F4tkMEU1hl4H05V5KOmEZ%2Bcx7pZ%2BvP2TyEUxwm%2BvxwiHbQV3nIhY3ur5egwu0JE7%2FJl7hgtZMOU5VBdxQR7iyE3XhtDrgL2rHnsjFcqZI2W2tAYVmWnKKbsN%2B2gR4MwSGQWs7BZ8tSjArK4QeIh5r4g4hVUvIaMGzpSxfs0XBNxAV7PCA3DT2f%2Fjl8swOxnwzo4YLbLd6tKjNauaPRh0Mo%2FO6JGrqWzwAPa%2B4U2wX64bkZvIeXnEc38B0psnDaQfc2GIU2IcO30JbgxyBx%2BiO4DGRfHdZokWHwhOGQvKeAyfK%2F5XA4GB1T8XO8GFffmGLwxvXZs0k3fN9SXe42vJIzz4om5pk8PFkpjyOr1EL47n9LfjwXT738B1bnPCBYJAAA%3D)
- [CSS transition](https://squint-cljs.github.io/squint/?src=gzip%3AH4sIAAAAAAAAE4VTuW7jMBDt9RUTpqGAlewU2YJVmgW2TykIAU2OLGZ5hRzZMYz8%2B0Ky4mNXSMRC5PDN45uL%2BwxOGl8AcJHwbTAJoWE9UcxitcLs6tyvEsqtdOZpXa%2FrnwyEzDCb2rIsCq6xg0ySELik4OAo%2BrDDBJ20GT8%2BIR424b1SwcXg0RM0bQHQCG12cBSZDhbhKPZGUw%2FsYb2O76yAfz%2FRo9n29AVgI9WfbQqD15UKNiTgpgM%2BC3qaVJbAEmoGbGMHZOUCCSXpsyETPLD%2FCNf1YwaUGX%2FAhOtCchfjkqYLbFFMVtIif6gfS3Y%2BlIu6tMnRygOwzuJi9NKara8MocvAFHrCtAR7HTKZ7lCp4GksxRfQU8xs3xtajE0NKY%2BAGMxE8XHBiOArF4aMEznc87yX8W7ulCHq8TfnwgcqFxwtyh1%2B6zg9yX5PZ4esPfdbCoEWG250aERMCDymKtO5GO10c9On7SffRDcOythUr3mlgxoceqrfBkyHZ7SoaMzEvYzxXD2uA4VbuEooCX9ZdFPqtdld1ZpnpDuojAZ2TQO3FJugD3VMGNHrsrwMWEKvMZ0C5fOIrmbjqB6a25ScxldqXe0lqX7OsBAnl%2Btq885D8wLTakfuEXB6ed7%2FBcFoSnxLBAAA)

## License

MIT
