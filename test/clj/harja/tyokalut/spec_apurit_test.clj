(ns harja.tyokalut.spec-apurit-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [harja.domain.urakka :as u]
    [harja.tyokalut.spec-apurit :refer [poista-nil-avaimet]]
    [harja.testi :refer :all]))

(deftest nil-arvojen-poisto-mapista-toimii
  (is (= (poista-nil-avaimet {:a "1" :b nil}) {:a "1"}))
  (is (= (poista-nil-avaimet {::u/id 27} {::u/id 27})))
  (is (= (poista-nil-avaimet {:a "1" :b "2"}) {:a "1" :b "2"}))
  (is (= (poista-nil-avaimet {:a "1" :b {:c "3"}}) {:a "1" :b {:c "3"}}))
  (is (= (poista-nil-avaimet {:a "1" :b {:c nil}}) {:a "1"}))
  (is (= (poista-nil-avaimet {:a nil :b {:c "3"}}) {:b {:c "3"}}))
  (is (= (poista-nil-avaimet {:a nil :b {:c nil}} true) nil))
  (is (= (poista-nil-avaimet {:a nil :b {:c nil} :d 1} false) {:b {} :d 1}))
  (is (= (poista-nil-avaimet {:a nil :b {:c nil} :d 1} true) {:d 1}))
  (is (= (poista-nil-avaimet {:a nil :b {:c {:d nil}} :e 1} false) {:b {:c {}} :e 1})))
