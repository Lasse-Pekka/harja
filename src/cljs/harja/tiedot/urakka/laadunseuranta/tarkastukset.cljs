(ns harja.tiedot.urakka.laadunseuranta.tarkastukset
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.loki :refer [log tarkkaile!]]
            [harja.domain.laadunseuranta.tarkastus :as tarkastukset]
            [cljs.core.async :refer [<!]])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def +tarkastustyyppi->nimi+ tarkastukset/+tarkastustyyppi->nimi+)

(defonce tienumero (atom nil))                              ;; tienumero, tai kaikki
(defonce tarkastustyyppi (atom nil))                        ;; nil = kaikki, :tiesto, :talvihoito, :soratie

(def +naytettevat-tarkastukset-valinnat+
  [[nil "Kaikki"]
   [:havaintoja-sisaltavat "Havaintoja sisältävät"]
   [:laadunalitukset "Vain laadunalitukset"]])

(defonce naytettavat-tarkastukset (atom (first +naytettevat-tarkastukset-valinnat+)))

(defonce vain-laadunalitukset? (reaction (case (first @naytettavat-tarkastukset)
                                           :laadunalitukset true
                                           false)))

(defonce havaintoja-sisaltavat? (reaction (case (first @naytettavat-tarkastukset)
                                            :havaintoja-sisaltavat true
                                            false)))

(defn hae-tarkastus
  "Hakee tarkastuksen kaikki tiedot urakan id:n ja tarkastuksen id:n perusteella. Tähän liittyy laatupoikkeamat sekä niiden reklamaatiot."
  [urakka-id tarkastus-id]
  (k/post! :hae-tarkastus {:urakka-id    urakka-id
                           :tarkastus-id tarkastus-id}))

(defn tallenna-tarkastus
  "Tallentaa tarkastuksen urakalle."
  [urakka-id tarkastus nakyma]
  (k/post! :tallenna-tarkastus {:urakka-id urakka-id
                                :tarkastus (as-> tarkastus t
                                                 (if-not (some #(= nakyma %) [:paallystys :paikkaus :tiemerkinta])
                                                   (dissoc t :yllapitokohde)
                                                   (if (integer? (:yllapitokohde t))
                                                     t
                                                     (assoc t :yllapitokohde (get-in t [:yllapitokohde :id])))))}))

(defn hae-urakan-tarkastukset
  "Hakee annetun urakan tarkastukset urakka id:n ja ajan perusteella."
  [parametrit]
  (k/post! :hae-urakan-tarkastukset parametrit))

(defn naytettava-aikavali [urakka-kaynnissa? kuukausi aikavali]
  (if urakka-kaynnissa?
    aikavali
    (or kuukausi aikavali)))

(defn kasaa-haun-parametrit [urakka-kaynnissa? urakka-id kuukausi aikavali tienumero tyyppi
                             havaintoja-sisaltavat? vain-laadunalitukset?]
  (let [[alkupvm loppupvm] (naytettava-aikavali urakka-kaynnissa? kuukausi aikavali)]
    {:urakka-id urakka-id
     :alkupvm   alkupvm
     :loppupvm  loppupvm
     :tienumero tienumero
     :tyyppi    tyyppi
     :havaintoja-sisaltavat? havaintoja-sisaltavat?
     :vain-laadunalitukset? vain-laadunalitukset?}))

(defonce valittu-aikavali (atom nil))

(def urakan-tarkastukset
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               urakka-kaynnissa? @tiedot-urakka/valittu-urakka-kaynnissa?
               kuukausi @tiedot-urakka/valittu-hoitokauden-kuukausi
               aikavali @valittu-aikavali
               laadunseurannassa? @laadunseuranta/laadunseurannassa?
               valilehti (nav/valittu-valilehti :laadunseuranta)
               tienumero @tienumero
               tyyppi @tarkastustyyppi
               havaintoja-sisaltavat? @havaintoja-sisaltavat?
               vain-laadunalitukset? @vain-laadunalitukset?]
              {:odota 500
               :nil-kun-haku-kaynnissa? true}
              (let [parametrit (kasaa-haun-parametrit urakka-kaynnissa? urakka-id
                                                      kuukausi aikavali tienumero tyyppi
                                                      havaintoja-sisaltavat?
                                                      vain-laadunalitukset?)]
                (when (and laadunseurannassa? (= :tarkastukset valilehti)
                           (:urakka-id parametrit) (:alkupvm parametrit) (:loppupvm parametrit))
                  (go (into [] (<! (hae-urakan-tarkastukset parametrit))))))))

(defonce valittu-tarkastus (atom nil))

(defn paivita-tarkastus-listaan!
  "Päivittää annetun tarkastuksen urakan-tarkastukset listaan, jos se on valitun aikavälin sisällä."
  [{:keys [aika id] :as tarkastus}]
  (let [[alkupvm loppupvm] (naytettava-aikavali @tiedot-urakka/valittu-urakka-kaynnissa?
                                                @tiedot-urakka/valittu-hoitokauden-kuukausi
                                                @tiedot-urakka/valittu-aikavali)
        sijainti-listassa (first (keep-indexed (fn [i {tarkastus-id :id}]
                                                 (when (= id tarkastus-id) i))
                                               @urakan-tarkastukset))]
    (if (pvm/valissa? aika alkupvm loppupvm)
      ;; Tarkastus on valitulla välillä: päivitetään
      (if sijainti-listassa
        (swap! urakan-tarkastukset assoc sijainti-listassa tarkastus)
        (swap! urakan-tarkastukset conj tarkastus))

      ;; Ei pvm välillä, poistetaan listasta jos se aiemmin oli välillä
      (when sijainti-listassa
        (swap! urakan-tarkastukset (fn [tarkastukset]
                                     (into []
                                           (remove #(= (:id %) id))
                                           tarkastukset)))))))


(defn lisaa-laatupoikkeama
  "Lisää tarkastukselle laatupoikkeaman"
  [tarkastus]
  (go
    (if (nil? (:laatupoikkeamaid tarkastus))
      (assoc tarkastus
        :laatupoikkeamaid (<! (k/post! :lisaa-tarkastukselle-laatupoikkeama
                                        {:urakka-id (:id @nav/valittu-urakka)
                                         :tarkastus-id (:id tarkastus)})))
      tarkastus)))
