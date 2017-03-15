(ns harja.palvelin.palvelut.tietyoilmoitukset-test
  (:require [clojure.test :refer :all]
            [harja.domain.ilmoitukset :refer [+ilmoitustyypit+ ilmoitustyypin-nimi +ilmoitustilat+]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.tietyoilmoitukset :as tietyoilmoitukset]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.palvelin.integraatiot.labyrintti.sms :as labyrintti]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset])
  (:import (java.util Date)))

(def kayttaja "yit-rakennus")

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :tietyoilmoitukset (component/using
                                             (tietyoilmoitukset/->Tietyoilmoitukset)
                                             [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-ilmoitukset-sarakkeet
  (let [sarakkeet [:alkusijainnin_kuvaus
                   :ajoneuvo_max_korkeus
                   :tyotyypit
                   :liikenteenohjaus
                   :sijainti
                   :ilmoittaja_matkapuhelin
                   :loppusijainnin_kuvaus
                   :urakoitsijan_nimi
                   :kiertotienpinnat
                   :liikenteenohjaaja
                   :tilaajayhteyshenkilo_sukunimi
                   :tilaajayhteyshenkilo_etunimi
                   :urakoitsijayhteyshenkilo_sukunimi
                   :ajoneuvo_max_paino
                   :lisatietoja
                   :luotu
                   :tilaajayhteyshenkilo_sahkoposti
                   :ilmoittaja_sukunimi
                   :tilaajan_nimi
                   :ajoneuvo_max_pituus
                   :ajoittaiset_pysatykset
                   :urakka_nimi
                   :luvan_diaarinumero
                   :urakoitsijayhteyshenkilo
                   :ilmoittaja_sahkoposti
                   :urakoitsijayhteyshenkilo_matkapuhelin
                   :tloik_paatietyoilmoitus_id
                   :tilaajayhteyshenkilo_matkapuhelin
                   :tloik_id
                   :tyovaiheet
                   :pysaytysten_loppu
                   :luoja
                   :urakka
                   :kaistajarjestelyt
                   :tien_nimi
                   :paatietyoilmoitus
                   :pysaytysten_alku
                   :nopeusrajoitukset
                   :tr_loppuosa
                   :ilmoittaja_etunimi
                   :tyoajat
                   :muokkaaja
                   :vaikutussuunta
                   :huomautukset
                   :viivastys_normaali_liikenteessa
                   :tr_numero
                   :viivastys_ruuhka_aikana
                   :id
                   :poistettu
                   :kiertotien_mutkaisuus
                   :ajoneuvo_max_leveys
                   :kunnat
                   :urakoitsijayhteyshenkilo_etunimi
                   :tienpinnat
                   :tr_loppuetaisyys
                   :urakoitsija
                   :tr_alkuetaisyys
                   :poistaja
                   :alku
                   :ajoittain_suljettu_tie
                   :tilaajayhteyshenkilo
                   :loppu
                   :muokattu
                   :tr_alkuosa
                   :urakkatyyppi
                   :ilmoittaja
                   :urakoitsijayhteyshenkilo_sahkoposti
                   :tilaaja]
        parametrit {:alkuaika (pvm/luo-pvm 2016 1 1)
                    :loppuaika (pvm/luo-pvm 2017 3 1)
                    :urakka nil
                    :sijainti nil
                    :vain-kayttajan-luomat nil}]
    (is (oikeat-sarakkeet-palvelussa? sarakkeet :hae-tietyoilmoitukset parametrit))))

(deftest hae-ilmoituksia
  (let [parametrit {:alkuaika (pvm/luo-pvm 2016 1 1)
                    :loppuaika (pvm/luo-pvm 2017 3 1)
                    :urakka nil
                    :sijainti nil
                    :vain-kayttajan-luomat nil}

        tietyoilmoitukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :hae-tietyoilmoitukset
                                          +kayttaja-jvh+
                                          parametrit)]
    (println (keys (first tietyoilmoitukset)))
    (is (= 1 (count tietyoilmoitukset)) "Ilmoituksia on palautunut oikea määrä")
    (is (= 1 (count (:tyovaiheet (first tietyoilmoitukset)))) "Ilmoituksella on työvaiheita oikea määrä")))

