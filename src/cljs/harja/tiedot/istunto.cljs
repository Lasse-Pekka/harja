(ns harja.tiedot.istunto
  "Harjan istunnon tiedot"
  (:require [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.loki :refer [log]]

            [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.modal :as modal])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def kayttaja (atom nil))

(def kayttajan-nimi
  (reaction (when-let [k @kayttaja]
              (str (:etunimi k) " " (:sukunimi k)))))

(def istunto-alkoi (atom nil))

(defn- aseta-kayttaja [k]
  (reset! kayttaja k)
  (tapahtumat/julkaise! (merge {:aihe :kayttajatiedot} k)))

(def oletuskayttoaika-ilman-kayttajasyotteita-sekunteina (* 60 60 2))

(def ajastin-kaynnissa (atom false))

(def kayttoaikaa-jaljella-sekunteina (atom oletuskayttoaika-ilman-kayttajasyotteita-sekunteina))

(defn pysayta-ajastin []
  (reset! ajastin-kaynnissa false))

; TODO Lisää tälle event listener
(defn resetoi-ajastin []
  (reset! kayttoaikaa-jaljella-sekunteina oletuskayttoaika-ilman-kayttajasyotteita-sekunteina))

(defn kirjaudu-ulos []
  ;; TODO Unmounttaa komponentit
  )

(defn kirjaudu-ulos-jos-kayttoaika-umpeutunut []
  (if (<= @kayttoaikaa-jaljella-sekunteina 0)
    (reset! ajastin-kaynnissa false)
    (kirjaudu-ulos)))

(defn nayta-kayttoaika []
  (let [minuutit (int (/ @kayttoaikaa-jaljella-sekunteina 60))
        sekunnit (- @kayttoaikaa-jaljella-sekunteina (* minuutit 60))]
    (str minuutit ":" sekunnit)))

(defn nayta-varoitus-aikakatkaisusta []
  (modal/nayta! {:otsikko "Haluatko jatkaa käyttöä?"
                 :footer  [:span
                           [:button.nappi-kielteinen {:type     "button"
                                                      :on-click #(do (.preventDefault %)
                                                                     (kirjaudu-ulos)
                                                                     (modal/piilota!))}
                            "Kirjaudu ulos"]
                           [:button.nappi-myonteinen {:type     "button"
                                                      :on-click #(do (.preventDefault %)
                                                                     (resetoi-ajastin)
                                                                     (modal/piilota!))}
                            "Jatka käyttöä"]
                           ]}
                [:div
                 (if (> @kayttoaikaa-jaljella-sekunteina 0)
                   [:span
                    [:p (str "Et ole käyttänyt Harjaa aktiivisesti pian kahteen tuntiin. Jos et jatka käyttöä, sinut kirjataan ulos. Haluatko jatkaa käyttöä?")]
                    [:p (str "Käyttöaikaa jäljellä: " (nayta-kayttoaika))]]
                 [:p (str "Harjan käyttö aikakatkaistu kahden tunnin käyttämättömyyden takia. Lataa sivu uudelleen.")])]))

(defn varoita-jos-kayttoaika-umpeutumassa []
  (if (and (< @kayttoaikaa-jaljella-sekunteina (* 60 5)))
    (nayta-varoitus-aikakatkaisusta)))

(defn kaynnista-ajastin []
  (if (false? @ajastin-kaynnissa)
    (go
      (reset! ajastin-kaynnissa true)
      (loop []
        (<! (timeout 1000))
        (if @ajastin-kaynnissa
          (do
            (reset! kayttoaikaa-jaljella-sekunteina (- @kayttoaikaa-jaljella-sekunteina 1))
            (varoita-jos-kayttoaika-umpeutumassa)
            (kirjaudu-ulos-jos-kayttoaika-umpeutunut)
            (recur)))))))