(ns harja.views.tierekisteri
  "Tierekisterin tarkastelunäkymä. Lähinnä debug käyttöön."
  (:require [reagent.core :as r]
            [harja.tyokalut.vkm :as vkm]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta]
            [harja.ui.kentat :as kentat]
            [harja.views.kartta.tasot :as tasot]
            [harja.ui.kartta.esitettavat-asiat :refer [maarittele-feature]]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.ui.tierekisteri :as tr])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce tr (r/atom {}))
(defonce sijainti (atom nil))

(defonce koordinaatti (r/atom {:x nil :y nil}))
(defonce koordinaatin-osoite (r/atom nil))

(defonce valitse-kartalla? (r/atom false))
(defonce valittu-osoite (r/atom nil))

(defn hae! []
  (tasot/poista-geometria! :tierekisteri-haettu-osoite)
  (go
    (let [tulos (<! (vkm/tieosoite->viiva @tr))]
      (reset! sijainti tulos)
      (tasot/nayta-geometria! :tierekisteri-haettu-osoite
                              {:alue (maarittele-feature
                                      (first tulos)
                                      false
                                      asioiden-ulkoasu/tr-ikoni
                                      asioiden-ulkoasu/tr-viiva)}))))

(defn kaanna! []
  (swap! tr
         (fn [{:keys [alkuosa alkuetaisyys loppuosa loppuetaisyys] :as tr}]
           (assoc tr
                  :alkuosa loppuosa
                  :alkuetaisyys loppuetaisyys
                  :loppuosa alkuosa
                  :loppuetaisyys alkuetaisyys)))
  (hae!))

(def tr-kentat [["Tie" :numero]
                ["Aosa" :alkuosa]
                ["Aet" :alkuetaisyys]
                ["Losa" :loppuosa]
                ["Let" :loppuetaisyys]])

(defn hae-tr-osoite! []
  ;; Hae TR osoite koordinaatille
  (tasot/poista-geometria! :tierekisteri-haettu-koordinaatti)
  (reset! koordinaatin-osoite nil)
  (go
    (let [{:keys [x y]} @koordinaatti
          tulos (<! (vkm/koordinaatti->trosoite [x y]))]
      (when-let [g (:geometria tulos)]
        (tasot/nayta-geometria! :tierekisteri-haettu-osoite
                                {:alue (assoc g
                                              :type :circle
                                              :radius 50)}))
      (reset! koordinaatin-osoite tulos))))

(defn tr-haku []
  [:div.tierekisteri-tr-haku
   [:table
    [:thead
     [:tr
      (for [[nimi _] tr-kentat]
        ^{:key nimi}
        [:th nimi])]]
    [:tbody
     [:tr
      (doall
       (for [[_ key] tr-kentat]
         ^{:key key}
         [:td [:input {:type "text" :on-change #(swap! tr assoc key (-> % .-target .-value))
                       :value (get @tr key)}]]))]]]

   [:div
    [:button {:on-click hae!} "Hae"]
    [:button {:on-click kaanna!} "Käännä alku/loppu"]]])

(defn koordinaatti-haku []
  [:div.tierekisteri-koordinaatti-haku
   [:table
    [:thead [:tr [:th "X"] [:th "Y"]]]
    [:tbody
     [:tr
      [:td
       [:input {:type :text
                :value (:x @koordinaatti)
                :on-change #(swap! koordinaatti assoc :x (-> % .-target .-value js/parseFloat))}]]
      [:td
       [:input {:type :text
                :value (:y @koordinaatti)
                :on-change #(swap! koordinaatti assoc :y (-> % .-target .-value js/parseFloat))}]]

      [:td
       [:button {:on-click hae-tr-osoite!} "Hae TR-osoite"]]]]]
   (when-let [osoite @koordinaatin-osoite]
     [:div (pr-str osoite)])])

(defn tierekisteri []
  (komp/luo
   (komp/avain-lippu nav/tarvitsen-isoa-karttaa :tierekisteri)
   (fn []
     [:span.tr-debug
      [kartta/kartan-paikka]
      [:div "Tervetuloa salaiseen TR osioon"]
      [tr-haku]
      [:hr]
      [koordinaatti-haku]
      [:hr]
      (if @valitse-kartalla?
        [tr/karttavalitsin {:kun-peruttu #(do
                                            (reset! valittu-osoite nil)
                                            (reset! valitse-kartalla? false))
                            :paivita #(reset! valittu-osoite %)
                            :kun-valmis #(do
                                           (when-let [g (:geometria %)]
                                             (tasot/nayta-geometria!
                                              :tierekisteri-valinta-haku
                                              {:alue g}))
                                           (reset! valittu-osoite %))}]
        [:button {:on-click #(reset! valitse-kartalla? true)}
         "Valitse kartalla"])
      (when-let [valittu @valittu-osoite]
        [:div (pr-str valittu)])])))

;; eism tie 20
;; x: 431418, y: 7213120
;; x: 445658, y: 7224320
