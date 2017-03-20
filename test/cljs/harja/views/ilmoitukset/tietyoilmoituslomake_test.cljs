(ns harja.views.ilmoitukset.tietyoilmoituslomake-test
  (:require [tuck.core :refer [tuck]]
            [cljs.test :as t :refer-macros [deftest is]]
            [reagent.core :as r]
            [clojure.string :as str]
            [harja.ui.grid :as g]
            [harja.testutils :refer [fake-palvelut-fixture fake-palvelukutsu jvh-fixture]]
            [harja.testutils.shared-testutils :as u]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tietyoilmoitukset-tiedot]
            [harja.views.ilmoitukset.tietyoilmoituslomake :as tietyoilmoituslomake-view]
            #_[harja.views.ilmoitukset.tietyoilmoitukset :as tietyoilmoitukset-view]
            )
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]
                   [cljs.core.async.macros :refer [go]]))

(t/use-fixtures :each u/komponentti-fixture fake-palvelut-fixture jvh-fixture)

(def mock-ilmoitus {:harja.domain.tietyoilmoitukset/kaistajarjestelyt
               {:harja.domain.tietyoilmoitukset/jarjestely "ajokaistaSuljettu"},
               :harja.domain.tietyoilmoitukset/loppusijainnin-kuvaus
               "Jossain Kiimingissä",
               :harja.domain.tietyoilmoitukset/viivastys-ruuhka-aikana 30,
               :harja.domain.tietyoilmoitukset/kunnat "Oulu, Kiiminki",
               :harja.domain.tietyoilmoitukset/tien-nimi "Kuusamontie",
               :harja.domain.tietyoilmoitukset/ilmoittaja
               {:harja.domain.tietyoilmoitukset/etunimi "Uuno",
                :harja.domain.tietyoilmoitukset/sukunimi "Urakoitsija",
                :harja.domain.tietyoilmoitukset/sahkoposti "yit_pk2@example.org",
                :harja.domain.tietyoilmoitukset/matkapuhelin "43223123"},
               :harja.domain.tietyoilmoitukset/tilaajayhteyshenkilo
               {:harja.domain.tietyoilmoitukset/sukunimi "Toripolliisi",
                :harja.domain.tietyoilmoitukset/matkapuhelin "0405127232",
                :harja.domain.tietyoilmoitukset/sahkoposti
                "tero.toripolliisi@example.com",
                :harja.domain.tietyoilmoitukset/etunimi "Tero"},
               :harja.domain.tietyoilmoitukset/pysaytysten-loppu
               #inst "2017-07-07T07:07:07.000000000-00:00",
               :harja.domain.tietyoilmoitukset/tilaajan-nimi "Pohjois-Pohjanmaa",
               :harja.domain.tietyoilmoitukset/vaikutussuunta "molemmat",
               :harja.domain.tietyoilmoitukset/huomautukset ["avotuli"],
               :harja.domain.tietyoilmoitukset/ajoittaiset-pysatykset true,
               :harja.domain.tietyoilmoitukset/tyoajat
               [{:harja.domain.tietyoilmoitukset/alkuaika
                 #object[java.time.LocalTime 0x3c760b34 "08:00"],
                 :harja.domain.tietyoilmoitukset/loppuaika
                 #object[java.time.LocalTime 0x472398d9 "17:00"],
                 :harja.domain.tietyoilmoitukset/paivat
                 ["maanantai" "tiistai" "keskiviikko"]}
                {:harja.domain.tietyoilmoitukset/alkuaika
                 #object[java.time.LocalTime 0x27c1ade9 "07:00"],
                 :harja.domain.tietyoilmoitukset/loppuaika
                 #object[java.time.LocalTime 0xe6b274f "21:00"],
                 :harja.domain.tietyoilmoitukset/paivat ["lauantai" "sunnuntai"]}],
               :harja.domain.tietyoilmoitukset/nopeusrajoitukset
               [{:harja.domain.tietyoilmoitukset/rajoitus "30",
                 :harja.domain.tietyoilmoitukset/matka 100}],
               :harja.domain.tietyoilmoitukset/alku
               #inst "2017-01-01T01:01:01.000000000-00:00",
               :harja.domain.tietyoilmoitukset/tienpinnat
               [{:harja.domain.tietyoilmoitukset/materiaali "paallystetty",
                 :harja.domain.tietyoilmoitukset/matka 100}],
               :harja.domain.tietyoilmoitukset/tilaajayhteyshenkilo-id 1,
               :harja.domain.tietyoilmoitukset/lisatietoja "Tämä on testi-ilmoitus",
               :harja.domain.tietyoilmoitukset/luotu
               #inst "2017-01-01T01:01:01.000000000-00:00",
               :harja.domain.tietyoilmoitukset/loppu
               #inst "2017-07-07T07:07:07.000000000-00:00",
               :harja.domain.tietyoilmoitukset/liikenteenohjaaja "liikennevalot",
               :harja.domain.tietyoilmoitukset/urakka-id 4,
               :harja.domain.tietyoilmoitukset/ajoittain-suljettu-tie true,
               :harja.domain.tietyoilmoitukset/alkusijainnin-kuvaus
               "Kuusamontien alussa",
               :harja.domain.tietyoilmoitukset/urakoitsijayhteyshenkilo
               {:harja.domain.tietyoilmoitukset/sahkoposti "yit_pk2@example.org",
                :harja.domain.tietyoilmoitukset/sukunimi "Urakoitsija",
                :harja.domain.tietyoilmoitukset/etunimi "Uuno",
                :harja.domain.tietyoilmoitukset/matkapuhelin "43223123"},
               :harja.domain.tietyoilmoitukset/tilaaja-id 9,
               :harja.domain.tietyoilmoitukset/liikenteenohjaus
               "ohjataanVuorotellen",
               :harja.domain.tietyoilmoitukset/tyovaiheet
               [{:harja.domain.tietyoilmoitukset/kaistajarjestelyt
                 {:harja.domain.tietyoilmoitukset/jarjestely "ajokaistaSuljettu"},
                 :harja.domain.tietyoilmoitukset/loppusijainnin-kuvaus
                 "Ylikiimingintien risteys",
                 :harja.domain.tietyoilmoitukset/viivastys-ruuhka-aikana 30,
                 :harja.domain.tietyoilmoitukset/kunnat "Oulu, Kiiminki",
                 :harja.domain.tietyoilmoitukset/tien-nimi "Kuusamontie",
                 :harja.domain.tietyoilmoitukset/ilmoittaja
                 {:harja.domain.tietyoilmoitukset/matkapuhelin "43223123",
                  :harja.domain.tietyoilmoitukset/etunimi "Uuno",
                  :harja.domain.tietyoilmoitukset/sukunimi "Urakoitsija",
                  :harja.domain.tietyoilmoitukset/sahkoposti "yit_pk2@example.org"},
                 :harja.domain.tietyoilmoitukset/tilaajayhteyshenkilo
                 {:harja.domain.tietyoilmoitukset/matkapuhelin "0405127232",
                  :harja.domain.tietyoilmoitukset/etunimi "Tero",
                  :harja.domain.tietyoilmoitukset/sahkoposti
                  "tero.toripolliisi@example.com",
                  :harja.domain.tietyoilmoitukset/sukunimi "Toripolliisi"},
                 :harja.domain.tietyoilmoitukset/pysaytysten-loppu
                 #inst "2017-07-07T07:07:07.000000000-00:00",
                 :harja.domain.tietyoilmoitukset/tilaajan-nimi "Pohjois-Pohjanmaa",
                 :harja.domain.tietyoilmoitukset/vaikutussuunta "molemmat",
                 :harja.domain.tietyoilmoitukset/huomautukset ["avotuli"],
                 :harja.domain.tietyoilmoitukset/ajoittaiset-pysatykset true,
                 :harja.domain.tietyoilmoitukset/tyoajat
                 [{:harja.domain.tietyoilmoitukset/alkuaika
                   #object[java.time.LocalTime 0x248b21d "06:00"],
                   :harja.domain.tietyoilmoitukset/loppuaika
                   #object[java.time.LocalTime 0x59f617dd "18:15"],
                   :harja.domain.tietyoilmoitukset/paivat
                   ["maanantai" "tiistai" "keskiviikko"]}
                  {:harja.domain.tietyoilmoitukset/alkuaika
                   #object[java.time.LocalTime 0x73f247ef "20:00"],
                   :harja.domain.tietyoilmoitukset/loppuaika
                   #object[java.time.LocalTime 0x24744781 "23:00"],
                   :harja.domain.tietyoilmoitukset/paivat ["lauantai" "sunnuntai"]}],
                 :harja.domain.tietyoilmoitukset/nopeusrajoitukset
                 [{:harja.domain.tietyoilmoitukset/rajoitus "30",
                   :harja.domain.tietyoilmoitukset/matka 100}],
                 :harja.domain.tietyoilmoitukset/alku
                 #inst "2017-06-01T01:01:01.000000000-00:00",
                 :harja.domain.tietyoilmoitukset/tienpinnat
                 [{:harja.domain.tietyoilmoitukset/materiaali "paallystetty",
                   :harja.domain.tietyoilmoitukset/matka 100}],
                 :harja.domain.tietyoilmoitukset/tilaajayhteyshenkilo-id 1,
                 :harja.domain.tietyoilmoitukset/lisatietoja
                 "Tämä on testi-ilmoitus",
                 :harja.domain.tietyoilmoitukset/luotu
                 #inst "2017-01-01T01:01:01.000000000-00:00",
                 :harja.domain.tietyoilmoitukset/loppu
                 #inst "2017-06-20T07:07:07.000000000-00:00",
                 :harja.domain.tietyoilmoitukset/liikenteenohjaaja "liikennevalot",
                 :harja.domain.tietyoilmoitukset/urakka-id 4,
                 :harja.domain.tietyoilmoitukset/ajoittain-suljettu-tie true,
                 :harja.domain.tietyoilmoitukset/alkusijainnin-kuvaus
                 "Vaalantien risteys",
                 :harja.domain.tietyoilmoitukset/urakoitsijayhteyshenkilo
                 {:harja.domain.tietyoilmoitukset/sahkoposti "yit_pk2@example.org",
                  :harja.domain.tietyoilmoitukset/etunimi "Uuno",
                  :harja.domain.tietyoilmoitukset/matkapuhelin "43223123",
                  :harja.domain.tietyoilmoitukset/sukunimi "Urakoitsija"},
                 :harja.domain.tietyoilmoitukset/tilaaja-id 9,
                 :harja.domain.tietyoilmoitukset/liikenteenohjaus
                 "ohjataanVuorotellen",
                 :harja.domain.tietyoilmoitukset/paatietyoilmoitus 1,
                 :harja.domain.tietyoilmoitukset/kiertotien-mutkaisuus
                 "loivatMutkat",
                 :harja.domain.tietyoilmoitukset/urakkatyyppi "hoito",
                 :harja.domain.tietyoilmoitukset/urakoitsijayhteyshenkilo-id 6,
                 :harja.domain.tietyoilmoitukset/viivastys-normaali-liikenteessa 15,
                 :harja.domain.tietyoilmoitukset/tyotyypit
                 [{:harja.domain.tietyoilmoitukset/tyyppi "Tienrakennus",
                   :harja.domain.tietyoilmoitukset/kuvaus "Rakennetaan tietä"}],
                 :harja.domain.tietyoilmoitukset/luoja 6,
                 :harja.domain.tietyoilmoitukset/urakoitsijan-nimi "YIT Rakennus Oy",
                 :harja.domain.tietyoilmoitukset/osoite
                 {:harja.domain.tierekisteri/aet 1,
                  :harja.domain.tierekisteri/geometria nil,
                  :harja.domain.tierekisteri/tie 20,
                  :harja.domain.tierekisteri/let 1,
                  :harja.domain.tierekisteri/aosa 3,
                  :harja.domain.tierekisteri/losa 4},
                 :harja.domain.tietyoilmoitukset/urakan-nimi
                 "Oulun alueurakka 2014-2019",
                 :harja.domain.tietyoilmoitukset/ilmoittaja-id 6,
                 :harja.domain.tietyoilmoitukset/ajoneuvorajoitukset
                 {:harja.domain.tietyoilmoitukset/max-leveys 3M,
                  :harja.domain.tietyoilmoitukset/max-korkeus 4M,
                  :harja.domain.tietyoilmoitukset/max-paino 4000M,
                  :harja.domain.tietyoilmoitukset/max-pituus 10M},
                 :harja.domain.tietyoilmoitukset/id 2,
                 :harja.domain.tietyoilmoitukset/kiertotienpinnat
                 [{:harja.domain.tietyoilmoitukset/materiaali "murske",
                   :harja.domain.tietyoilmoitukset/matka 100}],
                 :harja.domain.tietyoilmoitukset/pysaytysten-alku
                 #inst "2017-01-01T01:01:01.000000000-00:00"}],
               :harja.domain.tietyoilmoitukset/kiertotien-mutkaisuus "loivatMutkat",
               :harja.domain.tietyoilmoitukset/urakkatyyppi "hoito",
               :harja.domain.tietyoilmoitukset/urakoitsijayhteyshenkilo-id 6,
               :harja.domain.tietyoilmoitukset/viivastys-normaali-liikenteessa 15,
               :harja.domain.tietyoilmoitukset/tyotyypit
               [{:harja.domain.tietyoilmoitukset/tyyppi "Tienrakennus",
                 :harja.domain.tietyoilmoitukset/kuvaus "Rakennetaan tietä"}],
               :harja.domain.tietyoilmoitukset/luoja 6,
               :harja.domain.tietyoilmoitukset/urakoitsijan-nimi "YIT Rakennus Oy",
               :harja.domain.tietyoilmoitukset/osoite
               {:harja.domain.tierekisteri/aosa 1,
                :harja.domain.tierekisteri/geometria nil,
                :harja.domain.tierekisteri/losa 5,
                :harja.domain.tierekisteri/tie 20,
                :harja.domain.tierekisteri/let 1,
                :harja.domain.tierekisteri/aet 1},
               :harja.domain.tietyoilmoitukset/urakan-nimi "Oulun alueurakka 2014-2019",
               :harja.domain.tietyoilmoitukset/ilmoittaja-id 6,
               :harja.domain.tietyoilmoitukset/ajoneuvorajoitukset
               {:harja.domain.tietyoilmoitukset/max-korkeus 4M,
                :harja.domain.tietyoilmoitukset/max-paino 4000M,
                :harja.domain.tietyoilmoitukset/max-pituus 10M,
                :harja.domain.tietyoilmoitukset/max-leveys 3M},
               :harja.domain.tietyoilmoitukset/id 1,
               :harja.domain.tietyoilmoitukset/kiertotienpinnat
               [{:harja.domain.tietyoilmoitukset/materiaali "murske",
                 :harja.domain.tietyoilmoitukset/matka 100}],
               :harja.domain.tietyoilmoitukset/pysaytysten-alku
               #inst "2017-01-01T01:01:01.000000000-00:00"}
  )

(defn lomake-mock-komponentti [e! app]
  (let [valittu-ilmoitus (:valittu-ilmoitus app)
        kayttajan-urakat [5]])
  [tietyoilmoituslomake-view/lomake e! valittu-lmoitus kayttajan-urakat])


(defn query-selector [q]
  (js/document.querySelector q))

(deftest lomake-muodostuu (let [
        app (atom {:valittu-ilmoitus mock-ilmoitus})]


    (komponenttitesti
     [tuck lomake-mock-komponentti]
     --
     (is (pos?) (query-selector "label[for]"))
     ;; (<! haku)

     ;; (is (nil? (u/grid-solu "tietyoilmoitushakutulokset" 0 0)))
     ;; (is (= "Ei löytyneitä tietoja" (u/text :.tyhja)))
     ;; --

     ;; "Avataan aikahaku"
     ;; (u/click :button.nappi-alasveto)
     ;; --
     ;; (u/click ".harja-alasvetolistaitemi:nth-child(1) > a")
     ;; (<! haku)

     )

    )
  )
