(ns harja.tiedot.vesivaylat.urakoitsijoiden-luonti-test
  (:require [harja.tiedot.vesivaylat.urakoitsijoiden-luonti :as u]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.tuck-apurit :refer [e!]]
            [tuck.core :as tuck]))

(def tila @u/tila)


(deftest urakoitsijan-valinta
  (let [urakoitsija {:foobar 1}]
    (is (= urakoitsija (:valittu-urakoitsija (e! tila u/->ValitseUrakoitsija urakoitsija))))))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! tila u/->Nakymassa? true))))
  (is (false? (:nakymassa? (e! tila u/->Nakymassa? false)))))

(deftest uuden-urakoitsijan-luonnin-aloitus
  (is (= u/uusi-urakoitsija (:valittu-urakoitsija (e! tila u/->UusiUrakoitsija)))))

(deftest tallentamisen-aloitus
  (let [halutut #{u/->UrakoitsijaTallennettu u/->UrakoitsijaEiTallennettu}
        kutsutut (atom #{})]
    (with-redefs
      [tuck/send-async! (fn [r & _] (swap! kutsutut conj r))]
      (is (true? (:tallennus-kaynnissa? (e! {:haetut-urakoitsijat []} u/->TallennaUrakoitsija  {:id 1}))))
      (is (= halutut @kutsutut)))))

(deftest tallentamisen-valmistuminen
  (testing "Uuden urakoitsijan tallentaminen"
    (let [vanhat [{:id 1} {:id 2}]
          uusi {:id 3}
          tulos (e! {:haetut-urakoitsijat vanhat}  u/->UrakoitsijaTallennettu  uusi)]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-urakoitsija tulos)))
      (is (= (conj vanhat uusi) (:haetut-urakoitsijat tulos)))))

  (testing "Urakoitsijan muokkaaminen"
    (let [vanhat [{:id 1 :nimi :a} {:id 2 :nimi :b}]
          uusi {:id 2 :nimi :bb}
          tulos (e! {:haetut-urakoitsijat vanhat} u/->UrakoitsijaTallennettu  uusi)]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-urakoitsija tulos)))
      (is (= [{:id 1 :nimi :a} {:id 2 :nimi :bb}] (:haetut-urakoitsijat tulos))))))

(deftest tallentamisen-epaonnistuminen
  (let [tulos (e! tila u/->UrakoitsijaEiTallennettu "virhe")]
    (is (false? (:tallennus-kaynnissa? tulos)))
    (is (nil? (:valittu-urakoitsija tulos)))))

(deftest urakoitsijan-muokkaaminen-lomakkeessa
  (let [urakoitsija {:nimi :foobar}]
    (is (= urakoitsija (:valittu-urakoitsija (e! tila u/->UrakoitsijaaMuokattu urakoitsija))))))

