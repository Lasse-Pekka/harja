(ns harja.palvelin.palvelut.vesivaylat.vaylat-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [harja.domain.vesivaylat.toimenpide :as toi]
            [clojure.string :as str]
            [harja.palvelin.palvelut.vesivaylat.vaylat :as va]
            [harja.domain.vesivaylat.vayla :as va-d]
            [clojure.spec.alpha :as s]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :vv-vaylat (component/using
                                                (va/->Vaylat)
                                                [:db :http-palvelin])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest vaylien-haku-toimii
  (let [params {}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-vaylat +kayttaja-jvh+
                                {})]
    (is (s/valid? ::va-d/hae-vaylat-kysely params))
    (is (>= (count vastaus) 2))
    (is (s/valid? ::va-d/hae-vaylat-vastaus vastaus))))

(deftest vaylien-haku-toimii-hakutekstilla
  (let [params {:hakuteksti "hie"}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-vaylat +kayttaja-jvh+
                                params)]
    (is (s/valid? ::va-d/hae-vaylat-kysely params))
    (is (= (count vastaus) 1))
    (is (::va-d/nimi (first vastaus) "Hietasaaren läntinen väylä"))
    (is (s/valid? ::va-d/hae-vaylat-vastaus vastaus))))