(ns harja.palvelin.palvelut.yha
  "Paikallisen kannan YHA-tietojenkäsittelyn logiikka.

  YHA on päällystysurakoiden master-järjestelmä, josta haetaan Harjaan päällystyskohteet
  ja johon ne lähetetään myöhemmin takaisin."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.yha :as yha-q]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [harja.kyselyt.paallystys :as paallystys-q]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]
            [harja.palvelin.palvelut.tierek-haku :as tr-haku]))

(defn lukitse-urakan-yha-sidonta [db urakka-id]
  (log/info "Lukitaan urakan " urakka-id " yha-sidonta.")
  (yha-q/lukitse-urakan-yha-sidonta<! db {:urakka urakka-id}))

(defn- lisaa-urakalle-yha-tiedot [db user urakka-id {:keys [yhatunnus yhaid yhanimi elyt vuodet] :as yha-tiedot}]
  (log/info "Lisätään YHA-tiedot urakalle " urakka-id ", yhatunnus: " yhatunnus " ja yhaid: " yhaid)
  (yha-q/lisaa-urakalle-yha-tiedot<! db {:urakka urakka-id
                                         :yhatunnus yhatunnus
                                         :yhaid yhaid
                                         :yhanimi yhanimi
                                         :elyt (konv/seq->array elyt)
                                         :vuodet (konv/seq->array (map str vuodet))
                                         :kayttaja (:id user)}))

(defn- poista-urakan-yha-tiedot [db urakka-id]
  (log/debug "Poistetaan urakan " urakka-id " vanhat YHA-tiedot")
  (yha-q/poista-urakan-yha-tiedot! db {:urakka urakka-id}))

(defn- poista-urakan-yllapitokohteet [db urakka-id]
  (log/debug "Poistetaan urakan " urakka-id " ylläpitokohteet")
  (yha-q/poista-urakan-yllapitokohteet! db {:urakka urakka-id})
  (yha-q/poista-urakan-yllapitokohdeosat! db {:urakka urakka-id}))

(defn- hae-urakan-yha-tiedot [db urakka-id]
  (log/debug "Haetaan urakan " urakka-id " yha-tiedot")
  (first (into []
               (comp
                 (map #(konv/array->vec % :vuodet))
                 (map #(konv/array->vec % :elyt))
                 (map #(clojure.set/rename-keys % {:sidonta-lukittu :sidonta-lukittu?})))
               (yha-q/hae-urakan-yhatiedot db {:urakka urakka-id}))))

(defn- sido-yha-urakka-harja-urakkaan [db user {:keys [harja-urakka-id yha-tiedot]}]
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user harja-urakka-id)
  (log/debug "Käsitellään pyyntö lisätä Harja-urakalle " harja-urakka-id " yha-tiedot: " yha-tiedot)
  (if (:sidonta-lukittu? (hae-urakan-yha-tiedot db harja-urakka-id))
    (throw (SecurityException. "Sidonta lukittu!"))
    (jdbc/with-db-transaction [db db]
      (poista-urakan-yha-tiedot db harja-urakka-id)
      (poista-urakan-yllapitokohteet db harja-urakka-id)
      (lisaa-urakalle-yha-tiedot db user harja-urakka-id yha-tiedot)
      (log/debug "YHA-tiedot sidottu. Palautetaan urakan YHA-tiedot")
      (hae-urakan-yha-tiedot db harja-urakka-id))))


(defn- hae-urakat-yhasta [db yha user {:keys [yhatunniste sampotunniste vuosi harja-urakka-id]}]
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user harja-urakka-id)
  (let [urakat (yha/hae-urakat yha yhatunniste sampotunniste vuosi)
        yhaidt (mapv :yhaid urakat)
        sidontatiedot (when (not-empty yhaidt) (yha-q/hae-urakoiden-sidontatiedot db {:yhaidt yhaidt}))
        urakat (mapv second
                     (merge-with merge
                                 (into {} (map (juxt :yhaid identity) urakat))
                                 (into {} (map (juxt :yhaid identity) sidontatiedot))))]
    urakat))

(defn- suodata-olemassaolevat-kohteet [db urakka-id kohteet]
  (let [yha-idt (into #{} (map :yhaid (yha-q/hae-urakan-kohteiden-yha-idt db {:urakkaid urakka-id})))]
    (filterv #(not (yha-idt (:yha-id %))) kohteet)))

(defn- hae-yha-kohteet
  "Hakee kohteet YHA:sta ja palauttaa vain uudet, Harjasta puuttuvat kohteet."
  [db yha user {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (log/debug "Haetaan kohteet yhasta")
  (let [yha-kohteet (yha/hae-kohteet yha urakka-id (:kayttajanimi user))
        _ (log/debug "Kohteita löytyi " (count yha-kohteet) " kpl.")
        uudet-kohteet (suodata-olemassaolevat-kohteet db urakka-id yha-kohteet)
        _ (log/debug "Uusia kohteita oli " (count uudet-kohteet) " kpl.")]
    uudet-kohteet))

(defn- merkitse-urakan-kohdeluettelo-paivitetyksi [db user harja-urakka-id]
  (log/debug "Merkitään urakan " harja-urakka-id " kohdeluettelo päivitetyksi")
  (yha-q/merkitse-urakan-yllapitokohteet-paivitetyksi<! db {:urakka harja-urakka-id
                                                            :kayttaja (:id user)}))

(defn- tallenna-kohde-ja-alikohteet [db urakka-id {:keys [tierekisteriosoitevali
                                                          tunnus yha-id yha-kohdenumero alikohteet yllapitokohdetyyppi yllapitokohdetyotyyppi
                                                          yllapitoluokka
                                                          keskimaarainen_vuorokausiliikenne
                                                          nykyinen-paallyste
                                                          nimi] :as kohde}]
  (log/debug "Tallennetaan kohde, jonka yha-id on: " yha-id)
  (let [kohde (yha-q/luo-yllapitokohde<!
                db
                {:urakka urakka-id
                 :tr_numero (:tienumero tierekisteriosoitevali)
                 :tr_alkuosa (:aosa tierekisteriosoitevali)
                 :tr_alkuetaisyys (:aet tierekisteriosoitevali)
                 :tr_loppuosa (:losa tierekisteriosoitevali)
                 :tr_loppuetaisyys (:let tierekisteriosoitevali)
                 :tr_ajorata (:ajorata tierekisteriosoitevali)
                 :tr_kaista (:kaista tierekisteriosoitevali)
                 :yhatunnus tunnus
                 :yhaid yha-id
                 :yllapitokohdetyyppi (name yllapitokohdetyyppi)
                 :yllapitokohdetyotyyppi (name yllapitokohdetyotyyppi)
                 :yllapitoluokka yllapitoluokka
                 :keskimaarainen_vuorokausiliikenne keskimaarainen_vuorokausiliikenne
                 :nykyinen_paallyste nykyinen-paallyste
                 :nimi nimi
                 :vuodet (konv/seq->array [(t/year (pvm/suomen-aikavyohykkeeseen (t/now)))])
                 :yha_kohdenumero yha-kohdenumero
                 :kohdenumero yha-kohdenumero})
        _ (yllapitokohteet-q/luo-yllapitokohteelle-tyhja-aikataulu<! db {:yllapitokohde (:id kohde)})]
    (doseq [{:keys [sijainti tierekisteriosoitevali yha-id nimi tunnus] :as alikohde} alikohteet]
      (log/debug "Tallennetaan kohteen osa, jonka yha-id on " yha-id)
      (let [uusi-kohdeosa (yha-q/luo-yllapitokohdeosa<!
                            db
                            {:yllapitokohde (:id kohde)
                             :nimi nimi
                             :tunnus tunnus
                             :tr_numero (:tienumero tierekisteriosoitevali)
                             :tr_alkuosa (:aosa tierekisteriosoitevali)
                             :tr_alkuetaisyys (:aet tierekisteriosoitevali)
                             :tr_loppuosa (:losa tierekisteriosoitevali)
                             :tr_loppuetaisyys (:let tierekisteriosoitevali)
                             :tr_ajorata (:ajorata tierekisteriosoitevali)
                             :tr_kaista (:kaista tierekisteriosoitevali)
                             :yhaid yha-id})]))))

(defn- tallenna-uudet-yha-kohteet
  "Tallentaa YHA:sta tulleet ylläpitokohteet. Olettaa, että ollaan tallentamassa vain
  uusia kohteita eli jo olemassa olevat on suodatettu joukosta pois."
  [db user {:keys [urakka-id kohteet] :as tiedot}]
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (log/debug "Tallennetaan " (count kohteet) " yha-kohdetta")
  (jdbc/with-db-transaction [db db]
    (let [kohteet+geometria (map
                              (fn [{:keys [tierekisteriosoitevali] :as kohde}]
                                (let [geometria (tr-haku/hae-tr-viiva db {:numero (:tienumero tierekisteriosoitevali)
                                                                          :alkuosa (:aosa tierekisteriosoitevali)
                                                                          :alkuetaisyys (:aet tierekisteriosoitevali)
                                                                          :loppuosa (:losa tierekisteriosoitevali)
                                                                          :loppuetaisyys (:let tierekisteriosoitevali)})]
                                  (assoc kohde :geometria geometria)))
                              kohteet)
          kohteet-geometrialla (filter :geometria kohteet+geometria)
          kohteet-ilman-geometriaa (filter (comp not :geometria) kohteet+geometria)]
      ;; Tallennetaan vain sellaiset YHA-kohteet, joille saatiin muodostettua geometria eli osoite oli
      ;; validi Harjan tieverkolla. Virheelliset kohteet palautetaan takaisin UI:lle.
      (doseq [kohde kohteet-geometrialla]
        (tallenna-kohde-ja-alikohteet db urakka-id kohde))
      (merkitse-urakan-kohdeluettelo-paivitetyksi db user urakka-id)
      (log/debug "YHA-kohteet tallennettu, päivitetään urakan geometria")
      (yy/paivita-yllapitourakan-geometria db urakka-id)
      (log/debug "Urakan geometria päivitetty.")
      {:yhatiedot (hae-urakan-yha-tiedot db urakka-id)
       :kohteet-ilman-geometriaa kohteet-ilman-geometriaa})))

(defn- tarkista-lahetettavat-kohteet
  "Tarkistaa, että kaikki annetut kohteet ovat siinä tilassa, että ne voidaan lähettää.
   Jos ei ole, heittää poikkeuksen."
  [db kohde-idt]
  (doseq [kohde-id kohde-idt]
    (let [paallystysilmoitus (first (into []
                                          (comp (map konv/alaviiva->rakenne)
                                                (map #(konv/string-poluista->keyword
                                                        %
                                                        [[:tekninen-osa :paatos]
                                                         [:tila]])))
                                          (paallystys-q/hae-paallystysilmoitus-kohdetietoineen-paallystyskohteella
                                            db
                                            {:paallystyskohde kohde-id})))]
      (when-not (and (= :hyvaksytty (get-in paallystysilmoitus [:tekninen-osa :paatos]))
                     (or (= :valmis (:tila paallystysilmoitus))
                         (= :lukittu (:tila paallystysilmoitus))))
        (throw (SecurityException. (str "Kohteen " kohde-id " päällystysilmoituksen lähetys ei ole sallittu.")))))))

(defn laheta-kohteet-yhaan
  "Lähettää annetut kohteet teknisine tietoineen YHA:n."
  [db yha user {:keys [urakka-id sopimus-id kohde-idt vuosi]}]
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (tarkista-lahetettavat-kohteet db kohde-idt)
  (log/debug (format "Lähetetään kohteet: %s YHA:n" kohde-idt))
  (let [lahetys-onnistui? (yha/laheta-kohteet yha urakka-id kohde-idt)
        paivitetyt-ilmoitukset (paallystys-q/hae-urakan-paallystysilmoitukset-kohteineen db urakka-id sopimus-id vuosi)]
    {:paallystysilmoitukset paivitetyt-ilmoitukset
     :lahetys-onnistui? lahetys-onnistui?}))

(defrecord Yha []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)
          yha (:yha-integraatio this)]
      (julkaise-palvelu http :sido-yha-urakka-harja-urakkaan
                        (fn [user tiedot]
                          (sido-yha-urakka-harja-urakkaan db user tiedot)))
      (julkaise-palvelu http :hae-urakat-yhasta
                        (fn [user tiedot]
                          (hae-urakat-yhasta db yha user tiedot)))
      (julkaise-palvelu http :hae-yha-kohteet
                        (fn [user tiedot]
                          (hae-yha-kohteet db yha user tiedot)))
      (julkaise-palvelu http :tallenna-uudet-yha-kohteet
                        (fn [user tiedot]
                          (tallenna-uudet-yha-kohteet db user tiedot)))
      (julkaise-palvelu http :laheta-kohteet-yhaan
                        (fn [user data]
                          (laheta-kohteet-yhaan db yha user data))))
    this)
  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :sido-yha-urakka-harja-urakkaan
      :hae-urakat-yhasta
      :hae-yha-kohteet
      :tallenna-uudet-yha-kohteet
      :laheta-kohteet-yhaan)
    this))
