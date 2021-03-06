(ns harja.palvelin.integraatiot.reimari.toimenpidehaku-test
  (:require [harja.palvelin.integraatiot.reimari.toimenpidehaku :as tohaku]
            [harja.palvelin.integraatiot.reimari.reimari-komponentti :as reimari]
            [com.stuartsierra.component :as component]
            [harja.testi :as ht]
            [clojure.test :as t]))

(def jarjestelma-fixture
  (ht/laajenna-integraatiojarjestelmafixturea
   "yit"
   :reimari (component/using
           (reimari/->Reimari "https://www.example.com/reimari/" "reimarikayttaja" "reimarisalasana" nil nil nil)
           [:db :integraatioloki])))

(t/use-fixtures :each (t/compose-fixtures ht/tietokanta-fixture jarjestelma-fixture))

(t/deftest kasittele-vastaus-kantatallennus
  (ht/tarkista-map-arvot
   (first (tohaku/kasittele-vastaus (:db ht/jarjestelma) (ht/hae-helsingin-vesivaylaurakan-id) (slurp "resources/xsd/reimari/haetoimenpiteet-vastaus.xml") ))
   {:harja.domain.vesivaylat.toimenpide/urakka-id (ht/hae-helsingin-vesivaylaurakan-id)
    :harja.domain.vesivaylat.toimenpide/suoritettu #inst "2017-04-24T09:42:04.123-00:00",
    :harja.domain.vesivaylat.toimenpide/reimari-id -123456,
    :harja.domain.vesivaylat.toimenpide/reimari-tila "1022541202",
    :harja.domain.vesivaylat.toimenpide/lisatyo? false,
    :harja.domain.vesivaylat.toimenpide/reimari-toimenpidetyyppi "1022542001"
    :harja.domain.vesivaylat.toimenpide/reimari-vayla
    {:harja.domain.vesivaylat.vayla/r-nro "12345",
     :harja.domain.vesivaylat.vayla/r-nimi "Joku väylä"},
    :harja.domain.vesivaylat.toimenpide/id 8,
    :harja.domain.vesivaylat.toimenpide/reimari-luotu
    #inst "2017-04-24T13:00:00.123-00:00",
    :harja.domain.vesivaylat.toimenpide/lisatieto
    "vaihdettiin patterit lamppuun",
    :harja.domain.vesivaylat.toimenpide/reimari-tyoluokka "1022541905",
    :harja.domain.vesivaylat.toimenpide/reimari-tyolaji "1022541802",
    :harja.domain.vesivaylat.toimenpide/reimari-urakoitsija
    {:harja.domain.vesivaylat.urakoitsija/id 2,
     :harja.domain.vesivaylat.urakoitsija/nimi "Merimiehet Oy"},
    :harja.domain.vesivaylat.toimenpide/reimari-sopimus
    {:harja.domain.vesivaylat.sopimus/r-nro -666,
     :harja.domain.vesivaylat.sopimus/r-tyyppi "1022542301",
     :harja.domain.vesivaylat.sopimus/r-nimi "Hoitosopimus"},
    :harja.domain.vesivaylat.toimenpide/reimari-tyyppi "1022542001",
    :harja.domain.vesivaylat.toimenpide/reimari-muokattu
    #inst "2017-04-24T13:30:00.123-00:00",
    :harja.domain.vesivaylat.toimenpide/reimari-komponentit
    [{:harja.domain.vesivaylat.komponentti/tila "234",
      :harja.domain.vesivaylat.komponentti/nimi "Erikoispoiju",
      :harja.domain.vesivaylat.komponentti/id 123}
     {:harja.domain.vesivaylat.komponentti/tila "345",
      :harja.domain.vesivaylat.komponentti/nimi "Erikoismerkki",
      :harja.domain.vesivaylat.komponentti/id 124}],
    :harja.domain.vesivaylat.toimenpide/reimari-alus
    {:harja.domain.vesivaylat.alus/r-tunnus "omapaatti",
     :harja.domain.vesivaylat.alus/r-nimi "MS Totally out of Gravitas"},
    :harja.domain.vesivaylat.toimenpide/reimari-turvalaite
    {:harja.domain.vesivaylat.turvalaite/r-nro "904",
     :harja.domain.vesivaylat.turvalaite/r-nimi
     "Glosholmsklacken pohjoinen",
     :harja.domain.vesivaylat.turvalaite/r-ryhma 514}}))
