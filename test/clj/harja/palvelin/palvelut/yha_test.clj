(ns harja.palvelin.palvelut.yha-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :refer :all]
            [harja.palvelin.integraatiot.yha.urakan-kohdehaku-test :as urakan-kohdehaku-test]
            [harja.palvelin.integraatiot.yha.urakoiden-haku-test :as urakoiden-haku-test]
            [harja.palvelin.palvelut.yllapitokohteet :refer :all]
            [harja.testi :refer :all]
            [clojure.core.match :refer [match]]
            [harja.jms-test :refer [feikki-sonja]]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [clojure.java.io :as io]
            [clojure.core.async :refer [<!! timeout]]
            [harja.palvelin.palvelut.yha :as yha]
            [harja.palvelin.integraatiot.yha.tyokalut :refer :all]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha-integraatio]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki])
  (:use org.httpkit.fake))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil) [:db])
                        :yha-integraatio (component/using
                                           (yha-integraatio/->Yha {:url +yha-url+})
                                           [:db :integraatioloki])
                        :yha (component/using
                               (yha/->Yha)
                               [:http-palvelin :db :yha-integraatio])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(deftest sido-yha-urakka-harja-urakkaan
  (let [urakka-id (hae-yha-paallystysurakan-id)
        yhatiedot-ennen-testia (ffirst (q "SELECT id FROM yhatiedot WHERE urakka = " urakka-id ";"))]
    (is (nil? yhatiedot-ennen-testia) "Urakan yhatiedot on tyhjä ennen testiä")

    (kutsu-palvelua (:http-palvelin jarjestelma)
                    :sido-yha-urakka-harja-urakkaan +kayttaja-jvh+
                    {:harja-urakka-id urakka-id
                     :yha-tiedot {:yhatunnus "YHATUNNUS"
                                  :yhaid 666
                                  :yhanimi "YHANIMI"}})

    (let [yhatiedot-testin-jalkeen (ffirst (q "SELECT id FROM yhatiedot WHERE urakka = " urakka-id ";"))]
      (is (integer? yhatiedot-testin-jalkeen) "Urakka sidottiin YHA-urakkaan oikein"))))

(deftest ala-anna-vaihtaa-lukittua-sidontaa
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        yhatiedot-ennen-testia (ffirst (q "SELECT id FROM yhatiedot WHERE urakka = " urakka-id ";"))]
    (is (integer? yhatiedot-ennen-testia) "Urakka on jo sidottu ennen testiä")

    (u "UPDATE yhatiedot SET sidonta_lukittu = TRUE WHERE urakka = " urakka-id ";")
    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :sido-yha-urakka-harja-urakkaan +kayttaja-jvh+
                                                   {:harja-urakka-id urakka-id
                                                    :yha-tiedot {:yhatunnus "YHATUNNUS"
                                                                 :yhaid 666
                                                                 :yhanimi "YHANIMI"}})))))

(deftest ala-sido-vajailla-tiedoilla
  (let [urakka-id (hae-yha-paallystysurakan-id)
        yhatiedot-ennen-testia (ffirst (q "SELECT id FROM yhatiedot WHERE urakka = " urakka-id ";"))]
    (is (nil? yhatiedot-ennen-testia) "Urakan yhatiedot on tyhjä ennen testiä")

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :sido-yha-urakka-harja-urakkaan +kayttaja-jvh+
                                           {:harja-urakka-id urakka-id
                                            :yha-tiedot {}})))))

(deftest alla-anna-sitoa-ilman-oikeuksia
  (let [urakka-id (hae-yha-paallystysurakan-id)]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :sido-yha-urakka-harja-urakkaan +kayttaja-ulle+
                                           {:harja-urakka-id urakka-id
                                            :yha-tiedot {:yhatunnus "YHATUNNUS"
                                                         :yhaid 666
                                                         :yhanimi "YHANIMI"}})))))

(deftest hae-yha-urakat
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)]

    (with-fake-http [urakoiden-haku-test/urakkahaku-url +onnistunut-urakoiden-hakuvastaus+]
      (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-urakat-yhasta +kayttaja-jvh+
                                    {:urakka-id urakka-id})]
        (is (= vastaus
               [{:elyt ["POP"]
                 :vuodet [2016]
                 :yhatunnus "YHATUNNUS"
                 :sampotunnus "SAMPOTUNNUS"
                 :yhaid 3}]))))))

(deftest hae-yha-urakan-kohteet
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)]

    (with-fake-http [urakan-kohdehaku-test/urakan-kohteet-url +onnistunut-urakan-kohdehakuvastaus+]
      (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-yha-kohteet +kayttaja-jvh+
                                    {:urakka-id urakka-id})]
        (is (= (count vastaus) 1))
        (is (every? :yha-id vastaus))))))

(deftest yha-kohteiden-haku-ei-palauta-harjassa-jo-olevia-kohteita
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        leppajarven-ramppi-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)]

    (u "UPDATE yllapitokohde SET yhaid = 3 WHERE id = " leppajarven-ramppi-id ";")

    (with-fake-http [urakan-kohdehaku-test/urakan-kohteet-url +onnistunut-urakan-kohdehakuvastaus+]
      (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-yha-kohteet +kayttaja-jvh+
                                    {:urakka-id urakka-id})]
        (is (= (count vastaus) 0))))))

(defn- luo-yha-kohteet [kohteen-osoite alikohteen-osoite]
  [{:alikohteet [{:yha-id 1
                  :tierekisteriosoitevali (merge {:karttapaivamaara #inst "2017-01-01T22:00:00.000-00:00"}
                                                 alikohteen-osoite)
                  :tunnus nil
                  :paallystystoimenpide {:kokonaismassamaara 10
                                         :paallystetyomenetelma 21
                                         :kuulamylly nil
                                         :raekoko 16
                                         :rc-prosentti nil
                                         :uusi-paallyste 14}}]
    :yha-id 2
    :tierekisteriosoitevali (merge {:karttapaivamaara #inst "2017-01-01T22:00:00.000-00:00"}
                                   kohteen-osoite)
    :yha-kohdenumero 1
    :yllapitokohdetyyppi "paallyste"
    :nykyinen-paallyste 14
    :nimi "YHA-kohde"
    :yllapitokohdetyotyyppi :paallystys
    :yllapitoluokka 8
    :keskimaarainen-vuorokausiliikenne 5000}])

(deftest tallenna-uudet-yha-kohteet
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        yhatiedot-ennen-testia (first (q-map "SELECT id, sidonta_lukittu
                                               FROM yhatiedot WHERE urakka = " urakka-id ";"))
        kohteet-ennen-testia (ffirst (q "SELECT COUNT(*) FROM yllapitokohde WHERE urakka = " urakka-id))]

    (is (integer? (:id yhatiedot-ennen-testia)) "Urakka on jo sidottu ennen testiä")
    (is (false? (:sidonta_lukittu yhatiedot-ennen-testia)) "Sidontaa ei ole lukittu ennen testiä")

    (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-uudet-yha-kohteet +kayttaja-jvh+
                                  {:urakka-id urakka-id
                                   :kohteet (luo-yha-kohteet {:ajorata 1
                                                              :kaista 1
                                                              :tienumero 20
                                                              :aosa 1
                                                              :aet 1
                                                              :losa 1
                                                              :let 2}
                                                             {:ajorata 1
                                                              :kaista 1
                                                              :tienumero 20
                                                              :aosa 1
                                                              :aet 1
                                                              :losa 1
                                                              :let 2})})
          yhatiedot-testin-jalkeen (first (q-map "SELECT id, sidonta_lukittu
                                               FROM yhatiedot WHERE urakka = " urakka-id ";"))
          kohteet-testin-jalkeen (ffirst (q "SELECT COUNT(*) FROM yllapitokohde WHERE urakka = " urakka-id))]

      (is (some? (:yhatiedot vastaus)))
      (is (and (vector? (:tallentamatta-jaaneet-kohteet vastaus)) (empty? (:tallentamatta-jaaneet-kohteet vastaus))))
      (is (false? (:sidonta_lukittu yhatiedot-testin-jalkeen))
          "Sidontaa ei lukittu vielä tässä vaiheessa (vaatii asioiden muokkausta)")
      (is (+ kohteet-ennen-testia 1) kohteet-testin-jalkeen))))

(deftest tallenna-uudet-yha-kohteet-epaonnistuu-alkuosa-liian-pitka
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-uudet-yha-kohteet +kayttaja-jvh+
                                {:urakka-id urakka-id
                                 :kohteet (luo-yha-kohteet {:ajorata 1
                                                            :kaista 1
                                                            :tienumero 9
                                                            :aosa 328
                                                            :aet 3060
                                                            :losa 329
                                                            :let 245}
                                                           {:ajorata 1
                                                            :kaista 1
                                                            :tienumero 9
                                                            :aosa 328
                                                            :aet 3060
                                                            :losa 329
                                                            :let 245})})]

    (is (= (count (:tallentamatta-jaaneet-kohteet vastaus)) 1))
    (is (false? (:osoite-validi? (first (:tallentamatta-jaaneet-kohteet vastaus)))))
    (is (= (:osoite-epavalidi-syy (first (:tallentamatta-jaaneet-kohteet vastaus)))
           "Alkuosan pituus 3060 ei kelpaa"))))

(deftest tallenna-uudet-yha-kohteet-epaonnistuu-alkuosaa-ei-olemassa
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-uudet-yha-kohteet +kayttaja-jvh+
                                {:urakka-id urakka-id
                                 :kohteet (luo-yha-kohteet {:ajorata 1
                                                            :kaista 1
                                                            :tienumero 20
                                                            :aosa 2
                                                            :aet 1
                                                            :losa 3
                                                            :let 1}
                                                           {:ajorata 1
                                                            :kaista 1
                                                            :tienumero 20
                                                            :aosa 2
                                                            :aet 1
                                                            :losa 3
                                                            :let 1})})]
    (is (= (count (:tallentamatta-jaaneet-kohteet vastaus)) 1))
    (is (false? (:osoite-validi? (first (:tallentamatta-jaaneet-kohteet vastaus)))))
    (is (= (:osoite-epavalidi-syy (first (:tallentamatta-jaaneet-kohteet vastaus)))
           "Alkuosaa 2 ei ole olemassa"))))

(deftest tallenna-uudet-yha-kohteet-epaonnistuu-loppuosaa-ei-olemassa
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-uudet-yha-kohteet +kayttaja-jvh+
                                {:urakka-id urakka-id
                                 :kohteet (luo-yha-kohteet {:ajorata 1
                                                            :kaista 1
                                                            :tienumero 20
                                                            :aosa 1
                                                            :aet 1
                                                            :losa 2
                                                            :let 1}
                                                           {:ajorata 1
                                                            :kaista 1
                                                            :tienumero 20
                                                            :aosa 1
                                                            :aet 1
                                                            :losa 2
                                                            :let 1})})]
    (is (= (count (:tallentamatta-jaaneet-kohteet vastaus)) 1))
    (is (false? (:osoite-validi? (first (:tallentamatta-jaaneet-kohteet vastaus)))))
    (is (= (:osoite-epavalidi-syy (first (:tallentamatta-jaaneet-kohteet vastaus)))
           "Loppuosaa 2 ei ole olemassa"))))

(deftest tallenna-uudet-yha-kohteet-epaonnistuu-kohdeosan-alkuosa-liian-pitka
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-uudet-yha-kohteet +kayttaja-jvh+
                                {:urakka-id urakka-id
                                 :kohteet (luo-yha-kohteet {:ajorata 1
                                                            :kaista 1
                                                            :tienumero 20
                                                            :aosa 1
                                                            :aet 1
                                                            :losa 3
                                                            :let 1}
                                                           {:ajorata 1
                                                            :kaista 1
                                                            :tienumero 20
                                                            :aosa 1
                                                            :aet 1
                                                            :losa 3
                                                            :let 9999999})})]
    (is (= (count (:tallentamatta-jaaneet-kohteet vastaus)) 1))
    (is (true? (:osoite-validi? (first (:tallentamatta-jaaneet-kohteet vastaus)))))
    (is (false? (:aliosoitteet-validit? (first (:tallentamatta-jaaneet-kohteet vastaus)))))
    (is (= (:osoite-epavalidi-syy (first (:tallentamatta-jaaneet-kohteet vastaus)))
           "Loppusan pituus 9999999 ei kelpaa"))))

(deftest tallenna-uudet-yha-kohteet-epaonnistuu-kohdeosan-ei-sisalla
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-uudet-yha-kohteet +kayttaja-jvh+
                                {:urakka-id urakka-id
                                 :kohteet (luo-yha-kohteet {:ajorata 1
                                                            :kaista 1
                                                            :tienumero 20
                                                            :aosa 1
                                                            :aet 1
                                                            :losa 2
                                                            :let 1}
                                                           {:ajorata 1
                                                            :kaista 1
                                                            :tienumero 20
                                                            :aosa 3
                                                            :aet 1
                                                            :losa 4
                                                            :let 1})})]
    (is (= (count (:tallentamatta-jaaneet-kohteet vastaus)) 1))
    (is (false? (:osoite-validi? (first (:tallentamatta-jaaneet-kohteet vastaus)))))
    (is (= (:osoite-epavalidi-syy (first (:tallentamatta-jaaneet-kohteet vastaus)))
           "Kohdeosa ei ole pääkohteen sisällä"))))