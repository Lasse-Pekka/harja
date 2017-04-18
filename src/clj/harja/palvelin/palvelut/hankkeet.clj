(ns harja.palvelin.palvelut.hankkeet
  (:require [com.stuartsierra.component :as component]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.hankkeet :as q]
            [taoensso.timbre :as log]
            [harja.domain.hanke :as hanke]
            [harja.id :as id]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.konversio :as konv]))

(defn hae-harjassa-luodut-hankkeet [db user]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-vesivaylat user)
    ;; Tietomallin puolesta on mahdollista, että hankkeella on monta urakkaa
    ;; mutta käytännössä näin ei ole. Jos hankkeella on monta urakkaa, palautetaan
    ;; hanke duplikaattina, ja virhetilanne käsitellään frontilla.
    (into []
          (map konv/alaviiva->rakenne)
          (q/hae-harjassa-luodut-hankkeet db))))

(defn tallenna-hanke
  "Tallentaa yksittäisen hankkeen ja palauttaa sen tiedot"
  [db user {:keys [hanke] :as tiedot}]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)
    (jdbc/with-db-transaction [db db]
      (let [tallennus-params {:nimi (:nimi hanke)
                              :alkupvm (:alkupvm hanke)
                              :loppupvm (:loppupvm hanke)
                              :kayttaja (:id user)}
            {:keys [id nimi alkupvm loppupvm] :as tallennettu-hanke}
            (if (id/id-olemassa? hanke)
              (q/paivita-harjassa-luotu-hanke<! db (assoc tallennus-params :id (:id hanke)))
              (q/luo-harjassa-luotu-hanke<! db tallennus-params))]
        {:id id
         :nimi nimi
         :alkupvm alkupvm
         :loppupvm loppupvm}))))

(defrecord Hankkeet []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu http
                      :hae-harjassa-luodut-hankkeet
                      (fn [user _]
                        (hae-harjassa-luodut-hankkeet db user))
                      {:vastaus-spec ::hanke/hae-harjassa-luodut-hankkeet-vastaus})

    (julkaise-palvelu http
                      :tallenna-hanke
                      (fn [user tiedot]
                        (tallenna-hanke db user tiedot))
                      {:kysely-spec ::hanke/tallenna-hanke-kysely
                       :vastaus-spec ::hanke/tallenna-hanke-vastaus})
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-harjassa-luodut-hankkeet
                     :tallenna-hanke)

    this))