(ns harja.tiedot.urakka.suunnittelu
  "Tämä nimiavaruus hallinnoi urakan suunnittelun tietoja"
  (:require [reagent.core :refer [atom] :as r]
            [cljs-time.core :as time]

            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]))

(def valittu-sopimusnumero "Sopimusnumero" (atom nil))

(defn valitse-sopimusnumero! [sn]
  (reset! valittu-sopimusnumero sn))

(def valittu-hoitokausi "Hoitokausi" (atom nil))

(defn valitse-hoitokausi! [hk]
  (reset! valittu-hoitokausi hk))

(defn hoitokaudet [ur]
  (let [ensimmainen-vuosi (.getYear (:alkupvm ur))
        viimeinen-vuosi (.getYear (:loppupvm ur))]
    (mapv (fn [vuosi]
            {:alkupvm  (pvm/hoitokauden-alkupvm vuosi)
             :loppupvm (pvm/hoitokauden-loppupvm (inc vuosi))})
          (range ensimmainen-vuosi viimeinen-vuosi))))


;; rivit ryhmitelty tehtävittäin, rivissä oltava :alkupvm ja :loppupvm
(defn jaljella-olevien-hoitokausien-rivit
  "Palauttaa ne rivit joiden loppupvm on joku jaljella olevien kausien pvm:stä"
  [rivit-tehtavittain jaljella-olevat-kaudet]
  (mapv (fn [tehtavan-rivit]
                 (filter (fn [tehtavan-rivi]
                           (some #(pvm/sama-pvm? (:loppupvm %) (:loppupvm tehtavan-rivi)) jaljella-olevat-kaudet))
                         tehtavan-rivit)
          ) rivit-tehtavittain))

(defn tulevat-hoitokaudet [ur hoitokausi-josta-eteenpain]
  (drop-while #(not (pvm/sama-pvm? (:loppupvm %) (:loppupvm hoitokausi-josta-eteenpain)))
              (hoitokaudet ur)))

(defn hoitokausien-sisalto-sama?
  "Kertoo onko eri hoitokausien sisältö sama päivämääriä lukuunottamatta.
  Suunniteltu käytettäväksi mm. yks.hint. ja kok.hint. töiden sekä materiaalien suunnittelussa."
  ;; uudelleennimetään muuttujia jos tästä saadaan yleiskäyttöinen esim. kok. hintaisille ja materiaaleille
  [tyorivit-tehtavittain hoitokaudet]
  (let [tyorivit-aikajarjestyksessa (map #(sort-by :alkupvm %) tyorivit-tehtavittain)
        tyorivit-ilman-pvmia (into []
                                   (map #(map (fn [tyorivi]
                                                (dissoc tyorivi :alkupvm :loppupvm)) %) tyorivit-aikajarjestyksessa))]
      (every? #(apply = %) tyorivit-ilman-pvmia)))

(defn toiden-kustannusten-summa
  "Laskee yhteen annettujen työrivien kustannusten summan"
  [tyorivit]
  (apply + (map (fn [tyorivi]
                  (:yhteensa tyorivi))
                tyorivit)))
