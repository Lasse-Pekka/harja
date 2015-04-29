(ns harja.kyselyt.urakan-toimenpiteet-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.kyselyt.urakan-toimenpiteet :as urakan-toimenpiteet]
            [taoensso.timbre :as log]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-oulun-urakan-toimenpiteet-ja-tehtavat-tasot []
    (let [db (apply tietokanta/luo-tietokanta testitietokanta)
         urakka-id @oulun-alueurakan-id
         response (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat-tasot db urakka-id)]
    (is (not (nil? response)))
    (is (= (count response) 4))

    (mapv (fn [rivi]
              (is (= (:taso (first rivi)) 1))
              (is (= (:koodi (first rivi)) "23000")))
              response)

    (mapv (fn [rivi] (is (= (:taso (nth rivi 1)) 2))) response)
    (mapv (fn [rivi] (is (= (:taso (nth rivi 2)) 3))) response)
    (mapv (fn [rivi] (is (= (:taso (nth rivi 3)) 4))) response)))

(deftest hae-pudun-urakan-toimenpiteet-ja-tehtavat-tasot []
    (let [db (apply tietokanta/luo-tietokanta testitietokanta)
       urakka-id @pudasjarven-alueurakan-id
       response (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat-tasot db urakka-id)]
     (is (not (nil? response)))
     (is (= (count response) 2))))