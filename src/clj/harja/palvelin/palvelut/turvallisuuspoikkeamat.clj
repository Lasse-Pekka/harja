(ns harja.palvelin.palvelut.turvallisuuspoikkeamat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.kyselyt.liitteet :as liitteet]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.turvallisuuspoikkeamat :as q]
            [harja.geo :as geo]
            [harja.palvelin.integraatiot.turi.turi-komponentti :as turi]
            [harja.domain.oikeudet :as oikeudet]))

(def turvallisuuspoikkeama-xf
  (comp (map #(konv/string->keyword % :korjaavatoimenpide_tila))
        (map konv/alaviiva->rakenne)
        (geo/muunna-pg-tulokset :sijainti)
        (map #(konv/array->set % :tyyppi))
        (map #(assoc % :vaaralliset-aineet (into #{} (remove nil?
                                                             [(when (:vaarallisten-aineiden-kuljetus %)
                                                                :vaarallisten-aineiden-kuljetus)
                                                              (when (:vaarallisten-aineiden-vuoto %)
                                                                :vaarallisten-aineiden-kuljetus)]))))
        (map #(dissoc % :vaarallisten-aineiden-kuljetus))
        (map #(dissoc % :vaarallisten-aineiden-vuoto))
        (map #(konv/string-set->keyword-set % :tyyppi))
        (map #(konv/array->set % :vahinkoluokittelu))
        (map #(konv/string-set->keyword-set % :vahinkoluokittelu))
        (map #(konv/array->set % :vahingoittuneetruumiinosat))
        (map #(konv/string-set->keyword-set % :vahingoittuneetruumiinosat))
        (map #(konv/array->set % :vammat))
        (map #(konv/string-set->keyword-set % :vammat))
        (map #(konv/string->keyword % :vakavuusaste))
        (map #(konv/string->keyword % :tila))
        (map #(konv/string->keyword % :vaylamuoto))
        (map #(konv/string->keyword % :tyontekijanammatti))
        (map #(konv/string-polusta->keyword % [:kommentti :tyyppi]))))

(defn hae-turvallisuuspoikkeamat [db user {:keys [urakka-id alku loppu]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-turvallisuus user urakka-id)
  (konv/sarakkeet-vektoriin
    (into []
          turvallisuuspoikkeama-xf
          (q/hae-urakan-turvallisuuspoikkeamat db urakka-id (konv/sql-date alku) (konv/sql-date loppu)))
    {:korjaavatoimenpide :korjaavattoimenpiteet}))

(defn hae-turvallisuuspoikkeama [db user {:keys [urakka-id turvallisuuspoikkeama-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-turvallisuus user urakka-id)
  (log/debug "Haetaan turvallisuuspoikkeama " turvallisuuspoikkeama-id " urakalle " urakka-id)
  (let [tulos (-> (first (konv/sarakkeet-vektoriin (into []
                                                         turvallisuuspoikkeama-xf
                                                         (q/hae-urakan-turvallisuuspoikkeama db turvallisuuspoikkeama-id urakka-id))
                                                   {:kommentti :kommentit
                                                    :korjaavatoimenpide :korjaavattoimenpiteet
                                                    :liite :liitteet}))

                  (update-in [:kommentit]
                             (fn [kommentit]
                               (sort-by :aika (map #(if (nil? (:id (:liite %)))
                                                     (dissoc % :liite)
                                                     %)
                                                   kommentit)))))]
    (log/debug "Tulos: " (pr-str tulos))
    tulos))

(defn- luo-tai-paivita-korjaavatoimenpide
  [db user tp-id {:keys [id turvallisuuspoikkeama kuvaus suoritettu vastaavahenkilo poistettu
                         otsikko tila vastuuhenkilo toteuttaja] :as korjaavatoimenpide}]

  (log/debug "Tallennetaan korjaavatoimenpide (" id ") turvallisuuspoikkeamalle " tp-id ".")
  (log/debug "TP:" (pr-str korjaavatoimenpide))
  ;; Jos tämä assertti failaa, joku on hassusti
  (assert
    (or (nil? turvallisuuspoikkeama) (= turvallisuuspoikkeama tp-id))
    "Korjaavan toimenpiteen 'turvallisuuspoikkeama' pitäisi olla joko tyhjä (uusi korjaava), tai sama kuin parametrina
    annettu turvallisuuspoikkeaman id.")

  (if-not (or (nil? id) (neg? id))
    (q/paivita-korjaava-toimenpide<!
      db
      {:otsikko  otsikko
       :tila (name tila)
       :vastuuhenkilo vastuuhenkilo ;; TODO Tee tälle frontti ennen tallennusta
       :toteuttaja toteuttaja
       :kuvaus kuvaus
       :suoritettu (konv/sql-timestamp suoritettu)
       :laatija (:id user)
       :poistettu (or poistettu false)
       :id id
       :tp tp-id})
    (q/luo-korjaava-toimenpide<! db {:tp tp-id
                                     :otsikko  otsikko
                                     :tila (name tila)
                                     :vastuuhenkilo vastuuhenkilo ;; TODO Tee tälle frontti ennen tallennusta
                                     :toteuttaja toteuttaja
                                     :kuvaus kuvaus
                                     :suoritettu (konv/sql-timestamp suoritettu)
                                     :laatija (:id user)})))

(defn- luo-tai-paivita-korjaavat-toimenpiteet [db user korjaavattoimenpiteet tp-id]
  (when-not (empty? korjaavattoimenpiteet)
    (doseq [korjaavatoimenpide korjaavattoimenpiteet]
      (log/debug "Lisätään turvallisuuspoikkeamalle korjaava toimenpide, tai muokataan sitä.")
      (luo-tai-paivita-korjaavatoimenpide db user tp-id korjaavatoimenpide))))

(def oletusparametrit {:ulkoinen_id nil
                       :ilmoittaja_etunimi nil
                       :ilmoittaja_sukunimi nil
                       :alkuosa nil
                       :numero nil
                       :alkuetaisyys nil
                       :loppuetaisyys nil
                       :loppuosa nil
                       :ilmoitukset_lahetetty nil
                       :lahde "harja-ui"})

(defn- luo-tai-paivita-turvallisuuspoikkeama
  [db user {:keys [id urakka tapahtunut kasitelty tyontekijanammatti tyontekijanammattimuu
                   kuvaus vammat sairauspoissaolopaivat sairaalavuorokaudet sijainti tr
                   vahinkoluokittelu vakavuusaste vahingoittuneetruumiinosat tyyppi
                   sairauspoissaolojatkuu seuraukset vaylamuoto toteuttaja tilaaja
                   laatijaetunimi laatijasukunimi otsikko paikan-kuvaus vaaralliset-aineet
                   turvallisuuskoordinaattorietunimi turvallisuuskoordinaattorisukunimi
                   ilmoituksetlahetetty tila]}]
  (let [sijainti (and sijainti (geo/geometry (geo/clj->pg sijainti)))
        parametrit
        (merge oletusparametrit
               tr
               {:urakka urakka
                :tapahtunut (konv/sql-timestamp tapahtunut)
                :kasitelty (konv/sql-timestamp kasitelty)
                :ammatti (some-> tyontekijanammatti name)
                :ammatti_muu tyontekijanammattimuu
                :kuvaus kuvaus
                :vammat (konv/seq->array vammat)
                :poissa sairauspoissaolopaivat
                :sairaalassa sairaalavuorokaudet
                :tyyppi (konv/seq->array tyyppi)
                :kayttaja (:id user)
                :vahinkoluokittelu (konv/seq->array vahinkoluokittelu)
                :vakavuusaste (name vakavuusaste)
                :toteuttaja toteuttaja
                :tilaaja tilaaja
                :sijainti sijainti
                :vahingoittuneet_ruumiinosat (konv/seq->array vahingoittuneetruumiinosat)
                :sairauspoissaolo_jatkuu sairauspoissaolojatkuu
                :aiheutuneet_seuraukset seuraukset
                :vaylamuoto (name vaylamuoto)
                :laatija_etunimi laatijaetunimi
                :laatija_sukunimi laatijasukunimi
                :turvallisuuskoordinaattori_etunimi turvallisuuskoordinaattorietunimi
                :turvallisuuskoordinaattori_sukunimi turvallisuuskoordinaattorisukunimi
                :tapahtuman_otsikko otsikko
                :paikan_kuvaus paikan-kuvaus
                :vaarallisten_aineiden_kuljetus
                (boolean (some #{:vaarallisten-aineiden-kuljetus}
                               vaaralliset-aineet))
                :vaarallisten_aineiden_vuoto
                (boolean (some #{:vaarallisten-aineiden-vuoto}
                               vaaralliset-aineet))
                :tila (name tila)
                :ilmoitukset_lahetetty (konv/sql-timestamp ilmoituksetlahetetty)})]
    (if id
      (do (q/paivita-turvallisuuspoikkeama! db (assoc parametrit :id id))
          id)
      (:id (q/luo-turvallisuuspoikkeama<! db parametrit)))))

(defn- tallenna-turvallisuuspoikkeaman-kommentti [db user uusi-kommentti urakka tp-id]
  (when uusi-kommentti
    (log/debug "Turvallisuuspoikkeamalle lisätään uusi kommentti.")
    (let [liite (some->> uusi-kommentti
                         :liite
                         :id
                         (liitteet/hae-urakan-liite-id db urakka)
                         first
                         :id)
          kommentti (kommentit/luo-kommentti<! db
                                               nil
                                               (:kommentti uusi-kommentti)
                                               liite
                                               (:id user))]
      (q/liita-kommentti<! db tp-id (:id kommentti)))))

(defn tallenna-turvallisuuspoikkeama-kantaan [db user tp korjaavattoimenpiteet uusi-kommentti]
  (jdbc/with-db-transaction [db db]
    (let [tp-id (luo-tai-paivita-turvallisuuspoikkeama db user tp)]
      (tallenna-turvallisuuspoikkeaman-kommentti db user uusi-kommentti (:urakka tp) tp-id)
      (luo-tai-paivita-korjaavat-toimenpiteet db user korjaavattoimenpiteet tp-id)
      tp-id)))

(defn tallenna-turvallisuuspoikkeama [turi db user {:keys [tp korjaavattoimenpiteet uusi-kommentti hoitokausi]}]
  (log/debug "Tallennetaan turvallisuuspoikkeama " (:id tp) " urakkaan " (:urakka tp))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-turvallisuus user (:urakka tp))
  (let [id (tallenna-turvallisuuspoikkeama-kantaan db user tp korjaavattoimenpiteet uusi-kommentti)]
    (when turi
      (turi/laheta-turvallisuuspoikkeama turi id)))
  (hae-turvallisuuspoikkeamat db user {:urakka-id (:urakka tp) :alku (first hoitokausi) :loppu (second hoitokausi)}))

(defrecord Turvallisuuspoikkeamat []
  component/Lifecycle
  (start [this]
    (julkaise-palvelut (:http-palvelin this)
                       :hae-turvallisuuspoikkeamat
                       (fn [user tiedot]
                         (hae-turvallisuuspoikkeamat (:db this) user tiedot))

                       :hae-turvallisuuspoikkeama
                       (fn [user tiedot]
                         (hae-turvallisuuspoikkeama (:db this) user tiedot))

                       :tallenna-turvallisuuspoikkeama
                       (fn [user tiedot]
                         (tallenna-turvallisuuspoikkeama (:turi this) (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-turvallisuuspoikkeamat
                     :tallenna-turvallisuuspoikkeama)

    this))
