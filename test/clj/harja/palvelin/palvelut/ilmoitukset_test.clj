(ns harja.palvelin.palvelut.ilmoitukset-test
  (:require [clojure.test :refer :all]
            [harja.domain.tieliikenneilmoitukset :refer [+ilmoitustyypit+ ilmoitustyypin-nimi +ilmoitustilat+]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.ilmoitukset :as ilmoitukset]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.set :as set])
  (:import (java.util Date)))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae-ilmoitukset (component/using
                                           (ilmoitukset/->Ilmoitukset)
                                           [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-ilmoitukset-sarakkeet
  (let []
    (is (oikeat-sarakkeet-palvelussa?
          [:id :urakka :urakkanimi :ilmoitusid :ilmoitettu :valitetty :otsikko :ilmoitustyyppi :selitteet :sijainti
           :uusinkuittaus :tila :urakkatyyppi :tila

           [:tr :numero] [:tr :alkuosa] [:tr :loppuosa] [:tr :alkuetaisyys] [:tr :loppuetaisyys]

           [:kuittaukset 0 :id] [:kuittaukset 0 :kuitattu] [:kuittaukset 0 :kuittaustyyppi]
           [:kuittaukset 0 :kuittaaja :etunimi] [:kuittaukset 0 :kuittaaja :sukunimi]]

          :hae-ilmoitukset
          {:hallintayksikko nil
           :urakka nil
           :tilat [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
           :tyypit [:kysely :toimepidepyynto :ilmoitus]
           :aikavali nil
           :aloituskuittauksen-ajankohta :kaikki
           :hakuehto nil}))))

(def hae-ilmoitukset-parametrit
  {:hallintayksikko nil
   :urakka nil
   :hoitokausi nil
   :aikavali [(Date. 0 0 0) (Date.)]
   :tyypit +ilmoitustyypit+
   :tilat [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
   :aloituskuittauksen-ajankohta :kaikki
   :hakuehto ""})

(defn hae [parametrit]
  (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-ilmoitukset +kayttaja-jvh+ parametrit))

(deftest hae-ilmoituksia
  (let [parametrit hae-ilmoitukset-parametrit
        ilmoitusten-maara-suoraan-kannasta (ffirst (q
                                                     (str "SELECT count(*) FROM ilmoitus;")))
        kuittausten-maara-suoraan-kannasta (ffirst (q
                                                     (str "SELECT count(*) FROM ilmoitustoimenpide;")))
        ilmoitusid-12347-kuittaukset-maara-suoraan-kannasta
        (ffirst (q (str "SELECT count(*) FROM ilmoitustoimenpide WHERE ilmoitusid = 12347;")))
        ilmoitukset-palvelusta (hae parametrit)
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

(deftest hae-ilmoitukset-tyypin-mukaan
  (let [hoito-ilmoitukset (hae (assoc hae-ilmoitukset-parametrit
                                      :urakkatyyppi :hoito))
        paallystys-ilmoitukset (hae (assoc hae-ilmoitukset-parametrit
                                           :urakkatyyppi :paallystys))
        kaikki-ilmoitukset (hae (assoc hae-ilmoitukset-parametrit
                                       :urakkatyyppi :kaikki))

        idt #(into #{} (map :id) %)]

    ;; urakkatyypitön ilmoitus tulee aina, joten näitä on 2
    (is (= 2 (count paallystys-ilmoitukset)))

    (is (< (count paallystys-ilmoitukset)
           (count hoito-ilmoitukset)
           (count kaikki-ilmoitukset)))

    (is (= (set/union (idt hoito-ilmoitukset)
                      (idt paallystys-ilmoitukset))
           (idt kaikki-ilmoitukset)))))

(deftest tallenna-ilmoitustoimenpide
  (let [parametrit [{:ilmoittaja-sukunimi "Vastaava"
                     :ilmoittaja-tyopuhelin "0400123123"
                     :ilmoittaja-etunimi "Järjestelmän"
                     :ilmoittaja-organisaatio "Liikennevirasto"
                     :ilmoittaja-ytunnus nil
                     :ilmoittaja-sahkoposti "jvh@example.com"
                     :ilmoituksen-id 1
                     :ulkoinen-ilmoitusid 123
                     :ilmoittaja-matkapuhelin "0400123123"
                     :vapaatesti "TESTI123"
                     :tyyppi :lopetus}]

        ilmoitusten-maara-ennen (ffirst (q
                                          (str "SELECT count(*) FROM ilmoitus;")))
        kuittausten-maara-ennen (ffirst (q
                                          (str "SELECT count(*) FROM ilmoitustoimenpide;")))
        ilmoituksen-1-kuittaukset-maara-ennen
        (ffirst (q (str "SELECT count(*) FROM ilmoitustoimenpide WHERE ilmoitus = 1;")))
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :tallenna-ilmoitustoimenpiteet
                          +kayttaja-jvh+
                          parametrit)
        ilmoitusten-maara-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM ilmoitus;")))
        kuittausten-maara-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM ilmoitustoimenpide;")))
        ilmoituksen-1-kuittaukset-maara-jalkeen
        (ffirst (q (str "SELECT count(*) FROM ilmoitustoimenpide WHERE ilmoitus = 1;")))]

    ;; Ilmoituksia sama määrä, kuittausten määrä kannassa nousi yhdellä
    (is (= ilmoitusten-maara-ennen ilmoitusten-maara-jalkeen))
    (is (= (+ kuittausten-maara-ennen 1) kuittausten-maara-jalkeen))
    (is (= (+ ilmoituksen-1-kuittaukset-maara-ennen 1) ilmoituksen-1-kuittaukset-maara-jalkeen))

    (u "DELETE FROM ilmoitustoimenpide WHERE vapaateksti = 'TESTI123';")))

(deftest tallenna-ilmoitustoimenpide-ilman-oikeuksia
  (let [parametrit [{:ilmoittaja-sukunimi "Vastaava"
                     :ilmoittaja-tyopuhelin "0400123123"
                     :ilmoittaja-etunimi "Järjestelmän"
                     :ilmoittaja-organisaatio "Liikennevirasto"
                     :ilmoittaja-ytunnus nil
                     :ilmoittaja-sahkoposti "jvh@example.com"
                     :ilmoituksen-id 1
                     :ulkoinen-ilmoitusid 123
                     :ilmoittaja-matkapuhelin "0400123123"
                     :vapaatesti "TESTI123"
                     :tyyppi :aloitus}]]
    (is (thrown-with-msg? Exception #"EiOikeutta"
         (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-ilmoitustoimenpiteet
                                +kayttaja-tero+
                                parametrit)))))

(deftest hae-ilmoituksia-tienumerolla
  (let [oletusparametrit {:hallintayksikko nil
                          :urakka nil
                          :hoitokausi nil
                          :aikavali [(Date. 0 0 0) (Date.)]
                          :tyypit +ilmoitustyypit+
                          :tilat [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
                          :aloituskuittauksen-ajankohta :kaikki
                          :hakuehto ""}
        hae (fn [parametrit]
              (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-ilmoitukset +kayttaja-jvh+
                              (merge oletusparametrit parametrit)))

        ilmoitukset-kaikille-teille (hae {:tr-numero nil})
        ilmoitukset-tielle-6 (hae {:tr-numero 6})
        ilmoitukset-olemattomalle-tielle (hae {:tr-numero 9999999})]
    (is (> (count ilmoitukset-kaikille-teille) (count ilmoitukset-tielle-6))
        "Haku ilman tierajausta löytää enemmän ilmoituksia")
    (is (> (count ilmoitukset-tielle-6) 0) "Tielle 6 on ilmoituksia")

    (is (some #(= 6 (get-in % [:tr :numero])) ilmoitukset-kaikille-teille)
        "Tien 6 ilmoituksia löytyy myös kaikista ilmoituksista")

    (is (every? #(= 6 (get-in % [:tr :numero])) ilmoitukset-tielle-6)
        "Vain tien 6 ilmoituksia on rajatussa hakujoukossa")

    (is (zero? (count ilmoitukset-olemattomalle-tielle))
        "Olemattomalle tielle rajattu haku ei löydy ilmoituksia")))


(deftest ilmoitus-myohassa-ilman-kuittauksia
  (let [myohastynyt-kysely {:ilmoitustyyppi :kysely :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/days 7))) :kuittaukset []}
        myohastynyt-toimenpidepyynto {:ilmoitustyyppi :toimenpidepyynto :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/days 7))) :kuittaukset []}
        myohastynyt-tiedoitus {:ilmoitustyyppi :tiedoitus :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/days 7))) :kuittaukset []}]
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-kysely)))
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-toimenpidepyynto)))
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-tiedoitus)))))

(deftest ilmoitus-myohassa-kun-kuittaus-myohassa
  (let [myohastynyt-kysely {:ilmoitustyyppi :kysely :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/hours 73)))
                            :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :lopetus}]}
        myohastynyt-toimenpidepyynto {:ilmoitustyyppi :toimenpidepyynto :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/minutes 11)))
                                      :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :vastaanotto}]}
        myohastynyt-tiedoitus {:ilmoitustyyppi :tiedoitus :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/hours 2)))
                               :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :vastaanotto}]}]
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-kysely)))
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-toimenpidepyynto)))
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-tiedoitus)))))

(deftest ilmoitus-myohassa-kun-kuittaus-vaaraa-tyyppia
  (let [myohastynyt-kysely {:ilmoitustyyppi :kysely :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/hours 75)))
                            :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :vastaanotto}]}
        myohastynyt-toimenpidepyynto {:ilmoitustyyppi :toimenpidepyynto :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/minutes 15)))
                                      :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :aloitus}]}
        myohastynyt-tiedoitus {:ilmoitustyyppi :tiedoitus :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/hours 2)))
                               :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :aloitus}]}]
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-kysely)))
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-toimenpidepyynto)))
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-tiedoitus)))))

(deftest ilmoitus-ei-myohassa
  (let [myohastynyt-kysely {:ilmoitustyyppi :kysely :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/hours 71)))
                            :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :lopetus}]}
        myohastynyt-toimenpidepyynto {:ilmoitustyyppi :toimenpidepyynto :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/minutes 9)))
                                      :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :vastaanotto}]}
        myohastynyt-tiedoitus {:ilmoitustyyppi :tiedoitus :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/minutes 40)))
                               :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :vastaanotto}]}]
    (is (false? (ilmoitukset/ilmoitus-myohassa? myohastynyt-kysely)))
    (is (false? (ilmoitukset/ilmoitus-myohassa? myohastynyt-toimenpidepyynto)))
    (is (false? (ilmoitukset/ilmoitus-myohassa? myohastynyt-tiedoitus)))))

(deftest aloituskuittausta-ei-annettu-alle-tunnissa
  (let [ilmoitus1 {:ilmoitettu (c/to-sql-time (t/now)) :kuittaukset [{:kuitattu (c/to-sql-time (t/plus (t/now) (t/minutes 80)))
                                                                      :kuittaustyyppi :aloitus}]}
        ilmoitus2 {:ilmoitettu (c/to-sql-time (t/now)) :kuittaukset [{:kuitattu (c/to-sql-time (t/plus (t/now) (t/minutes 55)))
                                                                      :kuittaustyyppi :vastaanotto}]}
        ilmoitus3 {:ilmoitettu (c/to-sql-time (t/now)) :kuittaukset []}]
    (is (false? (#'ilmoitukset/sisaltaa-aloituskuittauksen-aikavalilla? ilmoitus1 (t/hours 1))))
    (is (false? (#'ilmoitukset/sisaltaa-aloituskuittauksen-aikavalilla? ilmoitus2 (t/hours 1))))
    (is (false? (#'ilmoitukset/sisaltaa-aloituskuittauksen-aikavalilla? ilmoitus3 (t/hours 1))))))

(deftest aloituskuittaus-annettu-alle-tunnissa
  (let [ilmoitus {:ilmoitettu (c/to-sql-time (t/now)) :kuittaukset [{:kuitattu (c/to-sql-time (t/plus (t/now) (t/minutes 25)))
                                                                     :kuittaustyyppi :aloitus}]}]
    (is (true? (#'ilmoitukset/sisaltaa-aloituskuittauksen-aikavalilla? ilmoitus (t/hours 1))))))


(deftest tarkista-aikavalihaut
  (let [alkuaika  (clj-time.core/date-time 2005 10 10 2)
        loppuaika  (clj-time.core/date-time 2005 10 10 4)
        parametrit {:hallintayksikko nil
                    :urakka nil
                    :hoitokausi nil
                    :alkuaika (c/to-date alkuaika)
                    :loppuaika (c/to-date loppuaika)
                    :tyypit +ilmoitustyypit+
                    :tilat [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
                    :aloituskuittauksen-ajankohta :kaikki
                    :hakuehto ""}
        ilmoitukset-palvelusta (kutsu-palvelua (:http-palvelin jarjestelma)
                                               :hae-ilmoitukset +kayttaja-jvh+ parametrit)
        ilmoitus (first ilmoitukset-palvelusta)]
    (is (= 1 (count ilmoitukset-palvelusta)) "Annettu aikaväli palauttaa vain yhden ilmoituksen")
    (is (t/after? (c/from-sql-time (:ilmoitettu ilmoitus)) alkuaika))
    (is (t/before? (c/from-sql-time (:ilmoitettu ilmoitus)) loppuaika))))


(deftest hae-ilmoitus-oikeudet
  (let [hae-ilmoitus-kayttajana #(kutsu-palvelua (:http-palvelin jarjestelma)
                                                 :hae-ilmoitus % 1)]
    (testing "Ilmoituksen haku oikeuksilla toimii"
      (is (= (:ilmoitusid (hae-ilmoitus-kayttajana +kayttaja-jvh+)) 12345)))

    (testing "Ilmoituksen haku ilman oikeuksia epäonnistuu"
      (is (thrown-with-msg?
           Exception #"EiOikeutta"
           (hae-ilmoitus-kayttajana +kayttaja-ulle+))))))
