(ns harja.tiedot.urakka.aikataulu
  "Ylläpidon urakoiden aikataulu"
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as urakka]
            [harja.domain.tierekisteri :as tr-domain])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce aikataulu-nakymassa? (atom false))

(defn hae-aikataulut [urakka-id sopimus-id vuosi]
  (k/post! :hae-aikataulut {:urakka-id  urakka-id
                            :sopimus-id sopimus-id
                            :vuosi vuosi}))

(defn hae-tiemerkinnan-suorittavat-urakat [urakka-id]
  (k/post! :hae-tiemerkinnan-suorittavat-urakat {:urakka-id urakka-id}))

(defn merkitse-kohde-valmiiksi-tiemerkintaan [kohde-id tiemerkintapvm urakka-id sopimus-id]
  (k/post! :merkitse-kohde-valmiiksi-tiemerkintaan {:kohde-id kohde-id
                                                    :tiemerkintapvm tiemerkintapvm
                                                    :urakka-id urakka-id
                                                    :sopimus-id sopimus-id}))

(defonce aikataulurivit
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               vuosi @urakka/valittu-urakan-vuosi
               [valittu-sopimus-id _] @u/valittu-sopimusnumero
               nakymassa? @aikataulu-nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                (go
                  (sort-by tr-domain/tiekohteiden-jarjestys
                           (<! (hae-aikataulut valittu-urakka-id
                                               valittu-sopimus-id vuosi)))))))

(defonce tiemerkinnan-suorittavat-urakat
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               nakymassa? @aikataulu-nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id nakymassa?)
                (hae-tiemerkinnan-suorittavat-urakat valittu-urakka-id))))

(defn luokittele-valmiuden-mukaan
  "Luokittelee annetun aikataulurivin valmiuden mukaan kolmeen kategoriaan:
  :valmis, :kesken, :aloittamatta."
  [{valmis :aikataulu-kohde-valmis
    aloitettu :aikataulu-kohde-alku :as aikataulurivi}]
  (cond
    (some? valmis) :valmis
    (some? aloitettu) :kesken
    :default :aloittamatta))

(defonce aikataulurivit-valmiuden-mukaan
  (reaction (group-by luokittele-valmiuden-mukaan @aikataulurivit)))

(defn tallenna-yllapitokohteiden-aikataulu [urakka-id sopimus-id kohteet]
  (go
    (let [vastaus (<! (k/post! :tallenna-yllapitokohteiden-aikataulu
                               {:urakka-id  urakka-id
                                :sopimus-id sopimus-id
                                :kohteet    kohteet}))]
      (reset! aikataulurivit vastaus))))