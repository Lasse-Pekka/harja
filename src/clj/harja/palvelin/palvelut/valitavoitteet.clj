(ns harja.palvelin.palvelut.valitavoitteet
  "Palvelu välitavoitteiden hakemiseksi ja tallentamiseksi."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.valitavoitteet :as q]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-urakan-valitavoitteet [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (into []
        (map konv/alaviiva->rakenne)
        (q/hae-urakan-valitavoitteet db urakka-id)))

(defn hae-valtakunnalliset-valitavoitteet [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-valitavoitteet user)
  (into []
        (q/hae-valtakunnalliset-valitavoitteet db)))

(defn merkitse-valmiiksi! [db user {:keys [urakka-id valitavoite-id valmis-pvm kommentti] :as tiedot}]
  (log/info "merkitse valmiiksi: " tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (jdbc/with-db-transaction [c db]
    (and (= 1 (q/merkitse-valmiiksi! db (konv/sql-date valmis-pvm) kommentti
                                     (:id user) urakka-id valitavoite-id))
         (hae-urakan-valitavoitteet db user urakka-id))))

(defn- poista-poistetut-urakan-valitavoitteet [db user valitavoitteet urakka-id]
  (doseq [poistettava (filter :poistettu valitavoitteet)]
    (q/poista-urakan-valitavoite! db (:id user) urakka-id (:id poistettava))))

(defn- luo-uudet-urakan-valitavoitteet [db user valitavoitteet urakka-id]
  (doseq [{:keys [takaraja nimi]} (filter
                                    #(and (< (:id %) 0)
                                          (not (:poistettu %)))
                                    valitavoitteet)]
    (q/lisaa-urakan-valitavoite<! db {:urakka urakka-id
                               :takaraja (konv/sql-date takaraja)
                               :nimi nimi
                               :luoja (:id user)})))

(defn- paivita-urakan-valitavoitteet [db user valitavoitteet urakka-id]
  (doseq [{:keys [id takaraja nimi]} (filter #(> (:id %) 0) valitavoitteet)]
    (q/paivita-urakan-valitavoite! db nimi (konv/sql-date takaraja) (:id user) urakka-id id)))

(defn tallenna-urakan-valitavoitteet! [db user {:keys [urakka-id valitavoitteet]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (log/debug "Tallenna urakan välitavoitteet " (pr-str valitavoitteet))
  (jdbc/with-db-transaction [db db]
    (poista-poistetut-urakan-valitavoitteet db user valitavoitteet urakka-id)
    (luo-uudet-urakan-valitavoitteet db user valitavoitteet urakka-id)
    (paivita-urakan-valitavoitteet db user valitavoitteet urakka-id)
    (hae-urakan-valitavoitteet db user urakka-id)))

(defn- poista-poistetut-valtakunnalliset-valitavoitteet [db user valitavoitteet]
  ;; TODO Pitää selventää mitä tämän poistamisesta seuraa
  #_(doseq [poistettava (filter :poistettu valitavoitteet)]
    (q/poista-valtakunnallinen-valitavoite! db (:id user) urakka-id (:id poistettava))))

(defn- luo-uudet-valtakunnalliset-valitavoitteet [db user valitavoitteet]
  (doseq [{:keys [takaraja nimi]} (filter
                                    #(and (< (:id %) 0)
                                          (not (:poistettu %)))
                                    valitavoitteet)]
    (q/lisaa-valtakunnallinen-valitavoite<! db {:takaraja (konv/sql-date takaraja)
                                                :nimi nimi
                                                :luoja (:id user)})))

(defn- paivita-valtakunnalliset-valitavoitteet [db user valitavoitteet]
  ;; TODO Mahdollisesti halutaan, että tämän päivittäminen vaikuttaa vain uusiin urakoihin?
  #_(doseq [{:keys [id takaraja nimi]} (filter #(> (:id %) 0) valitavoitteet)]
    (q/paivita-valtakunnallinen-valitavoite! db nimi (konv/sql-date takaraja) (:id user) urakka-id id)))


(defn tallenna-valtakunnalliset-valitavoitteet! [db user {:keys [valitavoitteet]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-valitavoitteet user)
  (log/debug "Tallenna valtakunnalliset välitavoitteet " (pr-str valitavoitteet))
  (jdbc/with-db-transaction [db db]
    (poista-poistetut-valtakunnalliset-valitavoitteet db user valitavoitteet)
    (luo-uudet-valtakunnalliset-valitavoitteet db user valitavoitteet)
    (paivita-valtakunnalliset-valitavoitteet db user valitavoitteet)
    (hae-valtakunnalliset-valitavoitteet db user)))

(defrecord Valitavoitteet []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this) :hae-urakan-valitavoitteet
                      (fn [user urakka-id]
                        (hae-urakan-valitavoitteet (:db this) user urakka-id)))
    (julkaise-palvelu (:http-palvelin this) :hae-valtakunnalliset-valitavoitteet
                      (fn [user _]
                        (hae-valtakunnalliset-valitavoitteet (:db this) user)))
    (julkaise-palvelu (:http-palvelin this) :merkitse-valitavoite-valmiiksi
                      (fn [user tiedot]
                        (merkitse-valmiiksi! (:db this) user tiedot)))
    (julkaise-palvelu (:http-palvelin this) :tallenna-urakan-valitavoitteet
                      (fn [user tiedot]
                        (tallenna-valtakunnalliset-valitavoitteet! (:db this) user tiedot)))
    (julkaise-palvelu (:http-palvelin this) :tallenna-valtakunnalliset-valitavoitteet
                      (fn [user tiedot]
                        (tallenna-valtakunnalliset-valitavoitteet! (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-valtakunnalliset-valitavoitteet
                     :hae-urakan-valitavoitteet
                     :merkitse-valitavoite-valmiiksi
                     :tallenna-urakan-valitavoitteet
                     :tallenna-valtakunnalliset-valitavoitteet)
    this))
