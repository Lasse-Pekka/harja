(ns harja.palvelin.palvelut.vesivaylat.toimenpiteet.hinnoittelut-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.toteuma :as tot]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.hinta :as hinta]
            [harja.domain.vesivaylat.toimenpide :as toi]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.urakka :as u]
            [harja.palvelin.palvelut.vesivaylat.toimenpiteet.apurit :as apurit]
            [harja.palvelin.palvelut.vesivaylat.toimenpiteet.hinnoittelut :as hin]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [harja.palvelin.palvelut.vesivaylat.toimenpiteet.yksikkohintaiset :as yks]
            [harja.kyselyt.vesivaylat.toimenpiteet :as q]
            [clojure.spec.alpha :as s]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :vv-hinnoittelut (component/using
                                           (hin/->Hinnoittelut)
                                           [:db :http-palvelin])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-hinnoittelutiedot-toimenpiteille
  (let [toimenpide-id (hae-reimari-toimenpide-poiujen-korjaus)
        vastaus (q/hae-hinnoittelutiedot-toimenpiteille (:db jarjestelma)
                                                        #{toimenpide-id})]
    (is (number? toimenpide-id))
    (is (= (count vastaus) 1))))

(deftest tallenna-toimenpiteelle-hinta
  (testing "Uusien hintojen lisäys"
    (let [toimenpide-id (hae-reimari-toimenpide-ilman-hinnoittelua)
          urakka-id (hae-helsingin-vesivaylaurakan-id)
          hinnoittelut-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
          hinnat-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
          insert-params {::toi/urakka-id urakka-id
                         ::toi/id toimenpide-id
                         ::h/hintaelementit [{::hinta/otsikko "Testihinta 1"
                                              ::hinta/yleiskustannuslisa 0
                                              ::hinta/maara 666}
                                             {::hinta/otsikko "Testihinta 2"
                                              ::hinta/yleiskustannuslisa 12
                                              ::hinta/maara 123}]}
          insert-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :tallenna-toimenpiteelle-hinta +kayttaja-jvh+
                                         insert-params)
          hinnat-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
          hinnoittelut-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))]

      (is (s/valid? ::h/tallenna-toimenpiteelle-hinta-kysely insert-params))
      (is (s/valid? ::h/tallenna-toimenpiteelle-hinta-vastaus insert-vastaus))

      (is (= (count (::h/hinnat insert-vastaus)) 2))
      (is (some #(== (::hinta/maara %) 666) (::h/hinnat insert-vastaus)))
      (is (some #(== (::hinta/maara %) 123) (::h/hinnat insert-vastaus)))
      (is (= (+ hinnoittelut-ennen 1) hinnoittelut-jalkeen) "Toimenpiteelle luotiin hinnoittelut")
      (is (= (+ hinnat-ennen 2) hinnat-jalkeen) "Molemmat testihinnat lisättiin")

      (testing "Lisättyjen hintojen päivittäminen"
        (let [hinnoittelut-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
              hinnat-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
              update-params {::toi/urakka-id urakka-id
                             ::toi/id toimenpide-id
                             ::h/hintaelementit (mapv (fn [hinta]
                                                        (assoc hinta ::hinta/maara
                                                                     (case (::hinta/maara hinta)
                                                                       666M 555
                                                                       123M 321)))
                                                      (::h/hinnat insert-vastaus))}
              update-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-toimenpiteelle-hinta +kayttaja-jvh+
                                             update-params)
              hinnat-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
              hinnoittelut-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))]

          (is (s/valid? ::h/tallenna-toimenpiteelle-hinta-kysely update-params))
          (is (s/valid? ::h/tallenna-toimenpiteelle-hinta-vastaus update-vastaus))

          (is (= (count (::h/hinnat update-vastaus)) 2))
          (is (some #(== (::hinta/maara %) 555) (::h/hinnat update-vastaus)))
          (is (some #(== (::hinta/maara %) 321) (::h/hinnat update-vastaus)))
          (is (= hinnoittelut-ennen hinnoittelut-jalkeen))
          (is (= hinnat-ennen hinnat-jalkeen)))))))

(deftest tallenna-toimenpiteelle-hinta-ilman-kirjoitusoikeutta
  (let [toimenpide-id (hae-reimari-toimenpide-ilman-hinnoittelua)
        urakka-id (hae-muhoksen-paallystysurakan-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/id toimenpide-id
                       ::h/hintaelementit []}]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-toimenpiteelle-hinta +kayttaja-tero+
                                           kysely-params)))))

(deftest tallenna-toimenpiteelle-hinta-kun-toimenpide-ei-kuulu-urakkaan
  (let [toimenpide-id (hae-reimari-toimenpide-ilman-hinnoittelua)
        urakka-id (hae-muhoksen-paallystysurakan-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/id toimenpide-id
                       ::h/hintaelementit []}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-toimenpiteelle-hinta +kayttaja-jvh+
                                                   kysely-params)))))

(deftest tallenna-toimenpiteelle-hinta-kun-hinnat-eivat-kuulu-toimenpiteeseen
  (let [toimenpide-id (hae-reimari-toimenpide-ilman-hinnoittelua)
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/id toimenpide-id
                       ::h/hintaelementit [{::hinta/id (hae-vanhtaan-vesivaylaurakan-hinta)
                                            ::hinta/otsikko "Testihinta 1"
                                            ::hinta/yleiskustannuslisa 0
                                            ::hinta/maara 666}]}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-toimenpiteelle-hinta +kayttaja-jvh+
                                                   kysely-params)))))

(deftest tallenna-ryhmalle-hinta
  (testing "Hintojen lisääminen hintaryhmälle"
    (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja)
          urakka-id (hae-helsingin-vesivaylaurakan-id)
          hinnoittelut-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
          hinnat-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
          insert-params {::u/id urakka-id
                         ::h/id hinnoittelu-id
                         ::h/hintaelementit [{::hinta/otsikko "Testihinta 1"
                                              ::hinta/yleiskustannuslisa 0
                                              ::hinta/maara 666}
                                             {::hinta/otsikko "Testihinta 2"
                                              ::hinta/yleiskustannuslisa 12
                                              ::hinta/maara 123}]}
          insert-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :tallenna-hintaryhmalle-hinta +kayttaja-jvh+
                                         insert-params)
          hinnat-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
          hinnoittelut-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
          paivitetty-hinnoittelu (first (filter #(= (::h/id %) hinnoittelu-id) insert-vastaus))]

      (is (s/valid? ::h/tallenna-hintaryhmalle-hinta-kysely insert-params))
      (is (s/valid? ::h/tallenna-hintaryhmalle-hinta-vastaus insert-vastaus))

      (is (map? paivitetty-hinnoittelu))
      (is (= (count (::h/hinnat paivitetty-hinnoittelu)) 2))
      (is (some #(== (::hinta/maara %) 666) (::h/hinnat paivitetty-hinnoittelu)))
      (is (some #(== (::hinta/maara %) 123) (::h/hinnat paivitetty-hinnoittelu)))
      (is (= (+ hinnat-ennen 2) hinnat-jalkeen) "Molemmat testihinnat lisättiin")
      (is (= hinnoittelut-ennen hinnoittelut-jalkeen) "Hinnoittelujen määrä ei muuttunut")

      (testing "Lisättyjen hintojen päivittäminen"
        (let [hinnoittelut-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
              hinnat-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
              update-params {::u/id urakka-id
                             ::h/id hinnoittelu-id
                             ::h/hintaelementit (mapv (fn [hinta]
                                                        (assoc hinta ::hinta/maara
                                                                     (case (::hinta/maara hinta)
                                                                       666M 555
                                                                       123M 321)))
                                                      (::h/hinnat paivitetty-hinnoittelu))}
              update-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-hintaryhmalle-hinta +kayttaja-jvh+
                                             update-params)
              hinnat-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
              hinnoittelut-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
              paivitetty-hinnoittelu (first (filter #(= (::h/id %) hinnoittelu-id) update-vastaus))]

          (is (s/valid? ::h/tallenna-hintaryhmalle-hinta-kysely insert-params))
          (is (s/valid? ::h/tallenna-hintaryhmalle-hinta-vastaus insert-vastaus))

          (is (map? paivitetty-hinnoittelu))
          (is (= (count (::h/hinnat paivitetty-hinnoittelu)) 2))
          (is (some #(== (::hinta/maara %) 555) (::h/hinnat paivitetty-hinnoittelu)))
          (is (some #(== (::hinta/maara %) 321) (::h/hinnat paivitetty-hinnoittelu)))
          (is (= hinnat-ennen hinnat-jalkeen) "Hintojen määrä pystyi samana päivityksessä")
          (is (= hinnoittelut-ennen hinnoittelut-jalkeen) "Hinnoittelujen määrä ei muuttunut edelleenkään"))))))

(deftest tallenna-ryhmalle-hinta-kun-ryhma-ei-kuulu-urakkaan
  (let [hinnoittelu-id (hae-vanhtaan-vesivaylaurakan-hinnoittelu)
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::u/id urakka-id
                       ::h/id hinnoittelu-id
                       ::h/hintaelementit [{::hinta/otsikko "Testihinta 1"
                                            ::hinta/yleiskustannuslisa 0
                                            ::hinta/maara 666}
                                           {::hinta/otsikko "Testihinta 2"
                                            ::hinta/yleiskustannuslisa 12
                                            ::hinta/maara 123}]}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-hintaryhmalle-hinta +kayttaja-jvh+
                                                   kysely-params)))))

(deftest tallenna-ryhmalle-hinta-kun-hinnat-eivat-kuulu-hinnoitteluun
  (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja)
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::u/id urakka-id
                       ::h/id hinnoittelu-id
                       ::h/hintaelementit [{::hinta/id (hae-vanhtaan-vesivaylaurakan-hinta)
                                            ::hinta/otsikko "Testihinta 1"
                                            ::hinta/yleiskustannuslisa 0
                                            ::hinta/maara 666}]}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-hintaryhmalle-hinta +kayttaja-jvh+
                                                   kysely-params)))))

(deftest tallenna-ryhmalle-hinta-ilman-kirjoitusoikeutta
  (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja)
        urakka-id (hae-muhoksen-paallystysurakan-id)
        kysely-params {::u/id urakka-id
                       ::h/id hinnoittelu-id
                       ::h/hintaelementit [{::hinta/otsikko "Testihinta 1"
                                            ::hinta/yleiskustannuslisa 0
                                            ::hinta/maara 666}
                                           {::hinta/otsikko "Testihinta 2"
                                            ::hinta/yleiskustannuslisa 12
                                            ::hinta/maara 123}]}]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-hintaryhmalle-hinta +kayttaja-tero+
                                           kysely-params)))))

(deftest hae-hinnoittelut
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::u/id urakka-id}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-hinnoittelut +kayttaja-jvh+
                                kysely-params)]

    (is (s/valid? ::h/hae-hinnoittelut-kysely kysely-params))
    (is (s/valid? ::h/hae-hinnoittelut-vastaus vastaus))

    (is (>= (count vastaus) 1))
    (is (>= (count (mapcat ::h/hinnat vastaus)) 1))
    (is (every? (comp not ::m/poistettu?) (mapcat ::h/hinnat vastaus)))
    (is (some #(= (::h/nimi %) "Hietasaaren poijujen korjaus") vastaus))))

(deftest hae-hinnoittelut-ilman-oikeuksia
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::u/id urakka-id}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-hinnoittelut +kayttaja-tero+
                                           kysely-params)))))

(deftest luo-hinnoittelu
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::u/id urakka-id
                       ::h/nimi "Testi123"}]

    (testing "Luodaan uusi hinnoittelu"
      (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :luo-hinnoittelu +kayttaja-jvh+
                                    kysely-params)]

        (is (s/valid? ::h/luo-hinnoittelu-kysely kysely-params))
        (is (s/valid? ::h/luo-hinnoittelu-vastaus vastaus))

        ;; Sama hinnoittelu palautui
        (is (= (::h/urakka-id vastaus) urakka-id))
        (is (= (::h/nimi vastaus) "Testi123"))
        (is (true? (::h/hintaryhma? vastaus)))
        (is (integer? (::h/id vastaus)))))

    ;; Yritetään luoda samalla nimellä uusi hintaryhmä
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-hinnoittelut +kayttaja-tero+
                                           kysely-params))
        "Hintaryhmän nimi on jo olemassa urakassa, pitäisi tulla poikkeus")))

(deftest luo-hinnoittelu-ilman-oikeuksia
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::u/id urakka-id
                       ::h/nimi "Testi"}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :luo-hinnoittelu +kayttaja-tero+
                                           kysely-params)))))

(deftest liita-toimenpiteet-hinnoitteluun-ilman-oikeuksia
  (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja)
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::toi/idt #{1 2 3}
                       ::h/id hinnoittelu-id
                       ::u/id urakka-id}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :liita-toimenpiteet-hinnoitteluun +kayttaja-tero+
                                           kysely-params)))))
