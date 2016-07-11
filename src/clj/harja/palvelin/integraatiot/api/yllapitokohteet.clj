(ns harja.palvelin.integraatiot.api.yllapitokohteet
  "Ylläpitokohteiden hallinta"
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.sanomat.yllapitokohdesanomat :as yllapitokohdesanomat]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [harja.kyselyt.paallystys :as q-paallystys]
            [harja.kyselyt.tieverkko :as q-tieverkko]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.integraatiot.api.tyokalut.palvelut :as palvelut]
            [clojure.java.jdbc :as jdbc]
            [cheshire.core :as cheshire])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(def testidata
  {:otsikko
   {:lahettaja
    {:jarjestelma "Urakoitsijan järjestelmä",
     :organisaatio {:nimi "Urakoitsija", :ytunnus "1234567-8"}},
    :viestintunniste {:id 123},
    :lahetysaika "2016-01-30T12:00:00Z"},
   :paallystysilmoitus
   {:yllapitokohde
    {:sijainti {:aosa 1, :aet 1, :losa 5, :let 16},
     :alikohteet
     [{:alikohde
       {:leveys 1.2,
        :kokonaismassamaara 12.3,
        :sijainti {:aosa 1, :aet 1, :losa 5, :let 16},
        :kivi-ja-sideaineet
        [{:kivi-ja-sideaine
          {:esiintyma "testi",
           :km-arvo "testi",
           :muotoarvo "testi",
           :sideainetyyppi "1",
           :pitoisuus 1.2,
           :lisa-aineet "lisäaineet"}}],
        :tunnus "A",
        :pinta-ala 2.2,
        :massamenekki 22,
        :kuulamylly "N14",
        :nimi "1. testialikohde",
        :raekoko 12,
        :tyomenetelma "Uraremix",
        :rc-prosentti 54,
        :paallystetyyppi "avoin asfaltti"}}]},
    :alustatoimenpiteet
    [{:alustatoimenpide
      {:sijainti {:aosa 1, :aet 1, :losa 5, :let 15},
       :kasittelymenetelma "Massanvaihto",
       :paksuus 1.2,
       :verkkotyyppi "Teräsverkko",
       :verkon-tarkoitus "Tasaukset",
       :verkon-sijainti "Päällysteessä",
       :tekninen-toimenpide "Rakentaminen"}}]}})

(defn paivita-alikohteet [db kohde alikohteet]
  (q-yllapitokohteet/poista-yllapitokohteen-kohdeosat! db {:id (:id kohde)})
  (mapv
    (fn [alikohde]
      (let [sijainti (:sijainti alikohde)
            osoite {:tie (:tr-numero kohde)
                    :aosa (:aosa sijainti)
                    :aet (:aet sijainti)
                    :losa (:losa sijainti)
                    :loppuet (:let sijainti)}
            sijainti-geometria (:tierekisteriosoitteelle_viiva (first (q-tieverkko/tierekisteriosoite-viivaksi db osoite)))
            parametrit {:yllapitokohde (:id kohde)
                        :nimi (:nimi alikohde)
                        :tunnus (:tunnus alikohde)
                        :tr_numero (:tr-numero kohde)
                        :tr_alkuosa (:aosa sijainti)
                        :tr_alkuetaisyys (:aet sijainti)
                        :tr_loppuosa (:losa sijainti)
                        :tr_loppuetaisyys (:let sijainti)
                        :tr_ajorata (:tr-ajorata kohde)
                        :tr_kaista (:tr-kaista kohde)
                        :toimenpide (:toimenpide alikohde)
                        :sijainti sijainti-geometria}]
        (assoc alikohde :id (:id (q-yllapitokohteet/luo-yllapitokohdeosa<! db parametrit)))))
    alikohteet))

(defn paivita-kohde [db kohde-id kohteen-sijainti]
  (q-yllapitokohteet/paivita-yllapitokohteen-sijainti!
    db (assoc (clojure.set/rename-keys
                kohteen-sijainti
                {:aosa :tr_alkuosa
                 :aet :tr_alkuetaisyys
                 :losa :tr_loppuosa
                 :let :tr_loppuetaisyys})
         :id
         kohde-id)))

(defn rakenna-ilmoitustiedot [paallystysilmoitus]
  ;; todo: täytyy kaivaa todennäköisesti vielä tason syvemmältä varsinaiset mäpit
  (let [data {:osoitteet
              (mapv (fn [alikohde]
                      (let [kivi (:kivi-ja-sideaine (first (:kivi-ja-sideaineet alikohde)))]
                        {:kohdeosa-id (:id alikohde)
                         :rc% (:rc-prosentti alikohde)
                         :leveys (:leveys alikohde)
                         :km-arvo (:km-arvo kivi)
                         :raekoko (:raekoko alikohde)
                         :pinta-ala (:pinta-ala alikohde)
                         :esiintyma (:esiintyma kivi)
                         :muotoarvo (:muotoarvo kivi)
                         :pitoisuus (:pitoisuus kivi)
                         ;; todo: mäppää selitteistä koodeiksi
                         :kuulamylly (:kuulamylly kivi)
                         :lisaaineet (:lisa-aineet kivi)
                         :massamenekki (:massamenekki alikohde)
                         :tyomenetelma (:tyomenetelma alikohde)
                         :sideainetyyppi (:sideainetyyppi kivi)
                         :paallystetyyppi (:paallystetyyppi alikohde)
                         :kokonaismassamaara (:kokonaismassamaara alikohde)
                         :edellinen-paallystetyyppi (:edellinen-paallystetyyppi alikohde)}))
                    (get-in paallystysilmoitus [:yllapitokohde :alikohteet]))
              :alustatoimet (mapv (fn [alustatoimi]
                                    (let [alustatoimi (:alustatoimenpide alustatoimi)
                                          sijainti (:sijainti alustatoimi)]
                                      {:aosa (:aosa sijainti)
                                       :aet (:aet sijainti)
                                       :losa (:losa sijainti)
                                       :let (:let sijainti)
                                       :paksuus (:paksuus alustatoimi)
                                       ;; todo: mäppää selitteistä koodeiksi
                                       :verkkotyyppi (:verkkotyyppi alustatoimi)
                                       :verkon-sijainti (:verkon-sijainti alustatoimi)
                                       :verkon-tarkoitus (:verkon-tarkoitus alustatoimi)
                                       :kasittelymenetelma (:kasittelymenetelma alustatoimi)
                                       :tekninen-toimenpide (:tekninen-toimenpide alustatoimi)}))
                                  (:alustatoimenpiteet paallystysilmoitus))
              :tyot (mapv (fn [tyo]
                            (let [tyo (:tyo tyo)]
                              {;; todo: mäppää selitteistä koodeiksi
                              :tyo (:tyotehtava tyo)
                              :tyyppi (:tyyppi tyo)
                              :yksikko (:yksikko tyo)
                              :yksikkohinta (:yksikkohinta tyo)
                              :tilattu-maara (:tilattu-maara tyo)
                              :toteutunut-maara (:tilattu-maara tyo)}))
                          (:tyot paallystysilmoitus))}]
    (cheshire/encode data)))

(defn paivita-paallystysilmoitus [db kayttaja kohde-id paallystysilmoitus]
  (let [ilmoitustiedot (rakenna-ilmoitustiedot paallystysilmoitus)]
    (if (q-paallystys/onko-paallystysilmoitus-olemassa-kohteelle? db {:id kohde-id})
      (q-paallystys/luo-paallystysilmoitus<!
        db
        {:paallystyskohde kohde-id
         :tila nil
         :ilmoitustiedot ilmoitustiedot
         :aloituspvm nil
         :valmispvm_kohde nil
         :valmispvm_paallystys nil
         :takuupvm nil
         :muutoshinta nil
         :kayttaja (:id kayttaja)})
      (q-paallystys/paivita-paallystysilmoituksen-ilmoitustiedot<!
        db
        {:ilmoitustiedot ilmoitustiedot
         ;; todo: tarkista pitääkö kaivaa id erikseen mäpistä
         :muokkaaja (:id kayttaja)
         :id kohde-id}))))

(defn kirjaa-paallystysilmoitus [db kayttaja {:keys [urakka-id kohde-id]} data]
  (jdbc/with-db-transaction
    [db db]
    (let [urakka-id (Integer/parseInt urakka-id)
          kohde-id (Integer/parseInt kohde-id)]
      (log/debug (format "Kirjataan urakan (id: %s) kohteelle (id: %s) päällystysilmoitus" urakka-id kohde-id))
      (clojure.pprint/pprint data)

      (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
      (validointi/tarkista-urakan-kohde db urakka-id kohde-id)
      (let [paallystysilmoitus (:paallystysilmoitus data)
            kohteen-sijainti (get-in data [:paallystysilmoitus :yllapitokohde :sijainti])
            alikohteet (mapv :alikohde (get-in data [:paallystysilmoitus :yllapitokohde :alikohteet]))
            kohde (first (q-yllapitokohteet/hae-yllapitokohde db {:id kohde-id}))
            kohteen-tienumero (:tr-numero kohde)
            alustatoimenpiteet (mapv :alustatoimenpide (get-in data [:paallystysilmoitus :alustatoimenpiteet]))]
        (validointi/tarkista-paallystysilmoitus db kohde-id kohteen-tienumero kohteen-sijainti alikohteet alustatoimenpiteet)

        (paivita-kohde db kohde-id kohteen-sijainti)

        (let [paivitetyt-alikohteet (paivita-alikohteet db kohde alikohteet)
              paallystysilmoitus (assoc-in paallystysilmoitus [:yllapitokohde :alikohteet] paivitetyt-alikohteet)
              id (paivita-paallystysilmoitus db kayttaja kohde-id paallystysilmoitus)]
          (tee-kirjausvastauksen-body {:ilmoitukset (str "Päällystysilmoitus kirjattu onnistuneesti.")
                                       :id id}))))))

(defn hae-yllapitokohteet [db parametit kayttaja]
  (let [urakka-id (Integer/parseInt (:id parametit))]
    (log/debug (format "Haetaan urakan (id: %s) ylläpitokohteet käyttäjälle: %s (id: %s)."
                       urakka-id
                       (:kayttajanimi kayttaja)
                       (:id kayttaja)))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [yllapitokohteet (into []
                                (map konv/alaviiva->rakenne)
                                (q-yllapitokohteet/hae-urakan-yllapitokohteet-alikohteineen db {:urakka urakka-id}))
          yllapitokohteet (konv/sarakkeet-vektoriin
                            yllapitokohteet
                            {:kohdeosa :alikohteet}
                            :id)]
      (yllapitokohdesanomat/rakenna-kohteet yllapitokohteet))))

(def palvelut
  [{:palvelu :hae-yllapitokohteet
    :polku "/api/urakat/:id/yllapitokohteet"
    :tyyppi :GET
    :vastaus-skeema json-skeemat/urakan-yllapitokohteiden-haku-vastaus
    :kasittely-fn (fn [parametit _ kayttaja db] (hae-yllapitokohteet db parametit kayttaja))}
   {:palvelu :kirjaa-paallystysilmoitus
    :polku "/api/urakat/:urakka-id/yllapitokohteet/:kohde-id/paallystysilmoitus"
    :tyyppi :POST
    :kutsu-skeema json-skeemat/paallystysilmoituksen-kirjaus
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db] (kirjaa-paallystysilmoitus db kayttaja parametrit data))}])

(defrecord Yllapitokohteet []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (palvelut/julkaise http db integraatioloki palvelut)
    this)
  (stop [{http :http-palvelin :as this}]
    (palvelut/poista http palvelut)
    this))
