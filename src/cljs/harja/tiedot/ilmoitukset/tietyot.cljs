(ns harja.tiedot.ilmoitukset.tietyot
  (:require [reagent.core :refer [atom]]
            [harja.pvm :as pvm]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [tuck.core :as t]
            [clojure.string :as str]
            [reagent.core :as r])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))


;; Valinnat jotka riippuvat ulkoisista atomeista
(defonce valinnat
  (reaction
   {:voi-hakea? true
    :hallintayksikko (:id @nav/valittu-hallintayksikko)
    :urakka (:id @nav/valittu-urakka)
    :valitun-urakan-hoitokaudet @u/valitun-urakan-hoitokaudet
    :urakoitsija (:id @nav/valittu-urakoitsija)
    :urakkatyyppi (:arvo @nav/urakkatyyppi)
    :hoitokausi @u/valittu-hoitokausi}))

(defonce karttataso-ilmoitukset (atom false))

(def haetut-ilmoitukset-feikkidata
  [
   {:id 1 :urakkanimi "Melkonen urakka" :alkupvm #inst "2004-07-24T18:18:00"
    :loppupvm #inst "2014-07-24T18:18:00" :tyon-tyyppi "lapiointi" :ilmoittaja "Leena Lampiainen"}
   {}])

(defonce ilmoitukset (atom {:ilmoitusnakymassa? false
                            :valittu-ilmoitus nil
                            :ilmoitukset nil ;; haetut ilmoitukset
                            :valinnat {:alkuaika (pvm/tuntia-sitten 1)
                                       :loppuaika (pvm/nyt)}}))

;; Vaihtaa valinnat
(defrecord AsetaValinnat [valinnat])

;; Kun valintojen reaktio muuttuu
(defrecord YhdistaValinnat [valinnat])

(defrecord HaeIlmoitukset []) ;; laukaise ilmoitushaku
(defrecord IlmoitusHaku [tulokset]) ;; Ilmoitusten palvelinhaun tulokset


;; Valitsee ilmoituksen tarkasteltavaksi
(defrecord ValitseIlmoitus [ilmoitus])

;; Palvelimelta palautuneet ilmoituksen tiedot
(defrecord IlmoituksenTiedot [ilmoitus])

(defrecord PoistaIlmoitusValinta [])

(defn- hae-ilmoitukset [{valinnat :valinnat haku :ilmoitushaku-id :as app}]
  (log "hae-ilmoitukset" (pr-str valinnat))
  (-> app
      (assoc :ilmoitushaku-id (.setTimeout js/window
                                           (t/send-async! ->HaeIlmoitukset)
                                           1000))))

;; Kaikki mitä UI voi ilmoitusnäkymässä tehdä, käsitellään täällä
(extend-protocol t/Event
  AsetaValinnat
  (process-event [{valinnat :valinnat} app]
    (hae-ilmoitukset
      (assoc app :valinnat valinnat)))

  YhdistaValinnat
  (process-event [{valinnat :valinnat :as e} app]
    (hae-ilmoitukset
      (update-in app [:valinnat] merge valinnat)))

  HaeIlmoitukset
  (process-event [_ {valinnat :valinnat :as app}]
    (let [tulos! (t/send-async! ->IlmoitusHaku)]
      (log "HaeIlmoitukset")
      (go
        (let [haku (-> valinnat
                       ;; jos tyyppiä/tilaa ei valittu, ota kaikki
                       (update :tyypit
                               #(log "update valinnat .."))
                       )]
          (tulos!
           {:ilmoitukset (<! (k/post! :hae-tietyoilmoitukset haku))}))))
    (assoc app :ilmoitukset nil))

  IlmoitusHaku
  (process-event [{tulokset :tulokset} {valittu :valittu-ilmoitus :as app}]
    (log "IlmoitusHaku" (pr-str tulokset))
    #_(let [uudet-ilmoitusidt (set/difference (into #{} (map :id (:ilmoitukset tulokset)))
                                            (into #{} (map :id (:ilmoitukset app))))
          uudet-ilmoitukset (filter #(uudet-ilmoitusidt (:id %)) (:ilmoitukset tulokset))]

        (hae-ilmoitukset (assoc app
             ;; Uudet ilmoitukset
             :ilmoitukset (cond-> (:ilmoitukset tulokset)
                                  (:taustahaku? tulokset)
                                  (merkitse-uudet-ilmoitukset uudet-ilmoitusidt)
                                  true
                                  (jarjesta-ilmoitukset))

             ;; Jos on valittuna ilmoitus joka ei ole haetuissa, perutaan valinta
             :valittu-ilmoitus (if (some #(= (:ilmoitusid valittu) %)
                                         (map :ilmoitusid (:ilmoitukset tulokset)))
                                 valittu
                                 nil))
           60000
           true)))

  ValitseIlmoitus
  (process-event [{ilmoitus :ilmoitus} app]
    (let [tulos (t/send-async! ->IlmoituksenTiedot)]
      (go
        (tulos (<! (k/post! :hae-ilmoitukset (:id ilmoitus))))))
    (assoc app :ilmoituksen-haku-kaynnissa? true))

  IlmoituksenTiedot
  (process-event [{ilmoitus :ilmoitus} app]
    (assoc app :valittu-ilmoitus ilmoitus :ilmoituksen-haku-kaynnissa? false))

  PoistaIlmoitusValinta
  (process-event [_ app]
    (assoc app :valittu-ilmoitus nil)))
