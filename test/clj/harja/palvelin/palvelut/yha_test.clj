(ns harja.palvelin.palvelut.yllapitokohteet-test
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
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha-integraatio])
  (:use org.httpkit.fake))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :yha-integraatio (component/using
                                           (yha-integraatio/->Yha {:url +yha-url+})
                                           [:db :integraatioloki])
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil) [:db])
                        :yha (component/using
                               (yha/->Yha)
                               [:http-palvelin :db :yha-integraatio])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(deftest sido-yha-urakka-harja-urakkaan
  (let [urakka-id (ffirst (q "SELECT id FROM urakka WHERE nimi = 'YHA-päällystysurakka'"))
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

    (q "UPDATE yhatiedot SET sidonta_lukittu = TRUE WHERE urakka = " urakka-id ";")
    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :sido-yha-urakka-harja-urakkaan +kayttaja-jvh+
                                                   {:harja-urakka-id urakka-id
                                                    :yha-tiedot {:yhatunnus "YHATUNNUS"
                                                                 :yhaid 666
                                                                 :yhanimi "YHANIMI"}})))))

(deftest ala-sido-vajailla-tiedoilla
  (let [urakka-id (ffirst (q "SELECT id FROM urakka WHERE nimi = 'YHA-päällystysurakka'"))
        yhatiedot-ennen-testia (ffirst (q "SELECT id FROM yhatiedot WHERE urakka = " urakka-id ";"))]
    (is (nil? yhatiedot-ennen-testia) "Urakan yhatiedot on tyhjä ennen testiä")

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :sido-yha-urakka-harja-urakkaan +kayttaja-jvh+
                                           {:harja-urakka-id urakka-id
                                            :yha-tiedot {}})))))

(deftest alla-anna-sitoa-ilman-oikeuksia
  (let [urakka-id (ffirst (q "SELECT id FROM urakka WHERE nimi = 'YHA-päällystysurakka'"))]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :sido-yha-urakka-harja-urakkaan +kayttaja-ulle+
                                           {:harja-urakka-id urakka-id
                                            :yha-tiedot {:yhatunnus "YHATUNNUS"
                                                         :yhaid 666
                                                         :yhanimi "YHANIMI"}})))))

(deftest hae-yha-urakat
  (let [urakka-id (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'"))]

    (with-fake-http [urakoiden-haku-test/urakkahaku-url +onnistunut-urakoiden-hakuvastaus+]
      (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-urakat-yhasta +kayttaja-jvh+
                                    {:urakka-id urakka-id})]
        (is (= vastaus
               {:elyt ["POP"]
                :vuodet [2016]
                :yhatunnus "YHATUNNUS"
                :sampotunnus "SAMPOTUNNUS"
                :yhaid 3}))))))

(deftest hae-yha-urakan-kohteet
  (let [urakka-id (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'"))]

    (with-fake-http [urakan-kohdehaku-test/urakan-kohteet-url +onnistunut-urakan-kohdehakuvastaus+]
      (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-yha-kohteet +kayttaja-jvh+
                                    {:urakka-id urakka-id})]
        (is (= (count vastaus) 1))
        (is (every? :yha-id vastaus))))))

(deftest yha-kohteiden-haku-ei-palauta-harjassa-jo-olevia-kohteita
  (let [urakka-id (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'"))
        leppajarven-ramppi-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)]

    (u "UPDATE yllapitokohde SET yhaid = 3 WHERE id = " leppajarven-ramppi-id ";")

    (with-fake-http [urakan-kohdehaku-test/urakan-kohteet-url +onnistunut-urakan-kohdehakuvastaus+]
      (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-yha-kohteet +kayttaja-jvh+
                                    {:urakka-id urakka-id})]
        (is (= (count vastaus) 0))))))

(deftest tallenna-uudet-yha-kohteet
  (let [urakka-id (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'"))
        yhatiedot-ennen-testia (first (q-map "SELECT id, sidonta_lukittu
                                               FROM yhatiedot WHERE urakka = " urakka-id ";"))
        kohteet-ennen-testia (ffirst (q "SELECT COUNT(*) FROM yllapitokohde WHERE urakka = " urakka-id))]

    (is (integer? (:id yhatiedot-ennen-testia)) "Urakka on jo sidottu ennen testiä")
    (is (false? (:sidonta_lukittu yhatiedot-ennen-testia)) "Sidontaa ei ole lukittu ennen testiä")

    (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-uudet-yha-kohteet +kayttaja-jvh+
                    {:urakka-id urakka-id
                     :kohteet [{:alikohteet
                                [{:yha-id 1
                                  :tierekisteriosoitevali {:karttapaivamaara #inst "2017-01-01T22:00:00.000-00:00"
                                                           :ajorata 1
                                                           :kaista 1
                                                           :aosa 1
                                                           :aet 1
                                                           :losa 1
                                                           :let 2
                                                           :tienumero 20}
                                  :tunnus nil
                                  :paallystystoimenpide {:kokonaismassamaara 10
                                                         :paallystetyomenetelma 21
                                                         :kuulamylly nil
                                                         :raekoko 16
                                                         :rc-prosentti nil
                                                         :uusi-paallyste 14}}]
                                :yha-id 2
                                :tierekisteriosoitevali {:karttapaivamaara #inst "2017-01-01T22:00:00.000-00:00"
                                                         :ajorata 1
                                                         :kaista 1
                                                         :aosa 1
                                                         :aet 1
                                                         :losa 1
                                                         :let 2
                                                         :tienumero 20}
                                :yha-kohdenumero 1
                                :yllapitokohdetyyppi "paallyste"
                                :nykyinen-paallyste 14
                                :nimi "YHA-kohde"
                                :yllapitokohdetyotyyppi :paallystys
                                :yllapitoluokka 8
                                :keskimaarainen-vuorokausiliikenne 5000}]})


    (let [yhatiedot-testin-jalkeen (first (q-map "SELECT id, sidonta_lukittu
                                               FROM yhatiedot WHERE urakka = " urakka-id ";"))
          kohteet-testin-jalkeen (ffirst (q "SELECT COUNT(*) FROM yllapitokohde WHERE urakka = " urakka-id))]

      (is (true? (:sidonta_lukittu yhatiedot-testin-jalkeen)) "Sidonta lukittiin")
      (is (+ kohteet-ennen-testia 1) kohteet-testin-jalkeen))))