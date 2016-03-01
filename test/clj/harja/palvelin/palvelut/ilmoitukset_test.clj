(ns harja.palvelin.palvelut.ilmoitukset-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.domain.ilmoitukset :refer [+ilmoitustyypit+ ilmoitustyypin-nimi +ilmoitustilat+]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.ilmoitukset :refer :all]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae-ilmoitukset (component/using
                                                (->Ilmoitukset)
                                                [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-ilmoitukset-sarakkeet
  (let []
    (is (oikeat-sarakkeet-palvelussa?
          [:id :urakka :ilmoitusid :ilmoitettu :valitetty :yhteydenottopyynto :otsikko :lyhytselite :pitkaselite
           :ilmoitustyyppi :selitteet :urakkatyyppi :sijainti :uusinkuittaus :tila

           [:tr :numero] [:tr :alkuosa] [:tr :loppuosa] [:tr :alkuetaisyys] [:tr :loppuetaisyys]
           [:ilmoittaja :etunimi] [:ilmoittaja :sukunimi] [:ilmoittaja :tyopuhelin] [:ilmoittaja :matkapuhelin]
           [:ilmoittaja :sahkoposti] [:ilmoittaja :tyyppi]
           [:lahettaja :etunimi] [:lahettaja :sukunimi] [:lahettaja :puhelinnumero] [:lahettaja :sahkoposti]

           [:kuittaukset 0 :id] [:kuittaukset 0 :kuitattu] [:kuittaukset 0 :vapaateksti] [:kuittaukset 0 :kuittaustyyppi]
           [:kuittaukset 0 :kuittaaja :etunimi] [:kuittaukset 0 :kuittaaja :sukunimi] [:kuittaukset 0 :kuittaaja :matkapuhelin]
           [:kuittaukset 0 :kuittaaja :tyopuhelin] [:kuittaukset 0 :kuittaaja :sahkoposti]
           [:kuittaukset 0 :kuittaaja :organisaatio] [:kuittaukset 0 :kuittaaja :ytunnus]
           [:kuittaukset 0 :kasittelija :etunimi] [:kuittaukset 0 :kasittelija :sukunimi]
           [:kuittaukset 0 :kasittelija :matkapuhelin] [:kuittaukset 0 :kasittelija :tyopuhelin]
           [:kuittaukset 0 :kasittelija :sahkoposti] [:kuittaukset 0 :kasittelija :organisaatio]
           [:kuittaukset 0 :kasittelija :ytunnus]]

          :hae-ilmoitukset
          {:hallintayksikko nil
           :urakka nil
           :tilat nil
           :tyypit [:kysely :toimepidepyynto :ilmoitus]
           :kuittaustyypit #{:kuittaamaton :vastaanotto :aloitus :lopetus}
           :aikavali nil
           :hakuehto nil}))))

(deftest hae-ilmoituksia
  (let [parametrit {:hallintayksikko nil
                    :urakka          nil
                    :hoitokausi      nil
                    :aikavali        [(java.util.Date. 0 0 0) (java.util.Date.)]
                    :tyypit          +ilmoitustyypit+
                    :kuittaustyypit #{:kuittaamaton :vastaanotto :aloitus :lopetus}
                    :tilat           +ilmoitustilat+
                    :hakuehto        ""}
        ilmoitusten-maara-suoraan-kannasta (ffirst (q
                                                     (str "SELECT count(*) FROM ilmoitus;")))
        kuittausten-maara-suoraan-kannasta (ffirst (q
                                                     (str "SELECT count(*) FROM ilmoitustoimenpide;")))
        ilmoitusid-12347-kuittaukset-maara-suoraan-kannasta
        (ffirst (q (str "SELECT count(*) FROM ilmoitustoimenpide WHERE ilmoitusid = 12347;")))
        ilmoitukset-palvelusta (kutsu-palvelua (:http-palvelin jarjestelma)
                                               :hae-ilmoitukset +kayttaja-jvh+ parametrit)
        kuittaukset-palvelusta (mapv :kuittaukset ilmoitukset-palvelusta)
        kuittaukset-palvelusta-lkm (apply + (map count kuittaukset-palvelusta))
        ilmoitusid-12348 (first (filter #(= 12348 (:ilmoitusid %)) ilmoitukset-palvelusta))
        ilmoitusid-12348-kuittaukset (:kuittaukset ilmoitusid-12348)
        ilmoitusid-12347 (first (filter #(= 12347 (:ilmoitusid %)) ilmoitukset-palvelusta))
        ilmoitusid-12347-kuittaukset (:kuittaukset ilmoitusid-12347)
        uusin-kuittaus-ilmoitusidlle-12347 (:uusinkuittaus ilmoitusid-12347)
        uusin-kuittaus-ilmoitusidlle-12347-testidatassa (pvm/aikana (pvm/->pvm "18.12.2007") 19 17 30 000)]
    (doseq [i ilmoitukset-palvelusta]
      (is (#{:toimenpidepyynto :tiedoitus :kysely}
            (:ilmoitustyyppi i)) "ilmoitustyyppi"))
    (is (= 0 (count ilmoitusid-12348-kuittaukset)) "12348:lla ei kuittauksia")
    (is (= ilmoitusten-maara-suoraan-kannasta (count ilmoitukset-palvelusta)) "Ilmoitusten lukumäärä")
    (is (= kuittausten-maara-suoraan-kannasta kuittaukset-palvelusta-lkm) "Kuittausten lukumäärä")
    (is (= ilmoitusid-12347-kuittaukset-maara-suoraan-kannasta (count ilmoitusid-12347-kuittaukset)) "Ilmoitusidn 123347 kuittausten määrä")
    (is (= uusin-kuittaus-ilmoitusidlle-12347-testidatassa uusin-kuittaus-ilmoitusidlle-12347) "uusinkuittaus ilmoitukselle 12347")))

(deftest ilmoituksen-tyyppi
 (let [kuittaamaton-ilmoitus {:aloitettu false :lopetettu false :vastaanotettu false}
       vastaanotettu-ilmoitus {:aloitettu false :lopetettu false :vastaanotettu true}
       aloitettu-ilmoitus {:aloitettu true :lopetettu false :vastaanotettu true}
       lopetettu-ilmoitus {:aloitettu true :lopetettu true :vastaanotettu true}]
   (is (= (:tila (lisaa-ilmoituksen-tila kuittaamaton-ilmoitus)) :kuittaamaton))
   (is (= (:tila (lisaa-ilmoituksen-tila vastaanotettu-ilmoitus)) :vastaanotto))
   (is (= (:tila (lisaa-ilmoituksen-tila aloitettu-ilmoitus)) :aloitus))
   (is (= (:tila (lisaa-ilmoituksen-tila lopetettu-ilmoitus)) :lopetus))))