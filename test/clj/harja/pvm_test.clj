(ns harja.pvm-test
  "Harjan pvm-namespacen backendin testit"
  (:require
    [clojure.test :refer [deftest is use-fixtures testing]]
    [harja.pvm :as pvm]
    [clj-time.core :as t]))

(def nyt (t/now))

(deftest ennen?
  (is (false? (pvm/ennen? nil nil)))
  (is (false? (pvm/ennen? nyt nil)))
  (is (false? (pvm/ennen? nil nyt)))
  (is (false? (pvm/ennen? nyt nyt)))
  (is (false? (pvm/ennen? (t/plus nyt (t/hours 4))
                          nyt)))
  (is (true? (pvm/ennen? nyt
                         (t/plus nyt (t/hours 4))))))

(deftest sama-tai-ennen?
  (is (false? (pvm/sama-tai-ennen? nil nil)))
  (is (false? (pvm/sama-tai-ennen? nyt nil)))
  (is (false? (pvm/sama-tai-ennen? nil nyt)))
  (is (false? (pvm/sama-tai-ennen? (t/plus nyt (t/hours 4))
                                   nyt
                                   false)))
  (is (true? (pvm/sama-tai-ennen? (t/local-date 2005 10 10)
                                  (t/local-date 2005 10 10))))
  (is (true? (pvm/sama-tai-ennen? (t/local-date-time 2005 10 10 11 11 11)
                                  (t/local-date-time 2005 10 10 11 11 11)
                                  false)))
  (is (true? (pvm/sama-tai-ennen? (t/plus nyt (t/hours 4))
                                  nyt)))
  (is (true? (pvm/sama-tai-ennen? nyt
                                  nyt)))
  (is (true? (pvm/sama-tai-ennen? nyt
                                  (t/plus nyt (t/hours 4))))))

(deftest jalkeen?
  (is (false? (pvm/jalkeen? nil nil)))
  (is (false? (pvm/jalkeen? nyt nil)))
  (is (false? (pvm/jalkeen? nil nyt)))
  (is (false? (pvm/jalkeen? nyt nyt)))
  (is (false? (pvm/jalkeen? nyt
                            (t/plus nyt (t/hours 4)))))
  (is (true? (pvm/jalkeen? (t/plus nyt (t/hours 4))
                           nyt))))

(deftest sama-tai-jalkeen?
  (is (false? (pvm/sama-tai-jalkeen? nil nil)))
  (is (false? (pvm/sama-tai-jalkeen? nyt nil)))
  (is (false? (pvm/sama-tai-jalkeen? nil nyt)))
  (is (false? (pvm/sama-tai-jalkeen? nyt
                                     (t/plus nyt (t/hours 4))
                                     false)))
  (is (true? (pvm/sama-tai-jalkeen? (t/local-date 2005 10 10)
                                    (t/local-date 2005 10 10))))
  (is (true? (pvm/sama-tai-jalkeen? (t/local-date-time 2005 10 10 11 11 11)
                                    (t/local-date-time 2005 10 10 11 11 11)
                                    false)))
  (is (true? (pvm/sama-tai-jalkeen? nyt
                                    (t/plus nyt (t/hours 4))
                                    true)))
  (is (true? (pvm/sama-tai-jalkeen? nyt
                                    nyt)))
  (is (true? (pvm/sama-tai-jalkeen? (t/plus nyt (t/hours 4))
                                    nyt))))

(deftest valissa?
  (is (true? (pvm/valissa? nyt nyt nyt)))
  (is (true? (pvm/valissa? nyt
                           (t/minus nyt (t/minutes 5))
                           (t/plus nyt (t/minutes 5)))))
  (is (true? (pvm/valissa? (t/plus nyt (t/minutes 8))
                           (t/minus nyt (t/minutes 5))
                           (t/plus nyt (t/minutes 5)))))
  (is (true? (pvm/valissa? (t/plus nyt (t/minutes 15))
                           (t/minus nyt (t/minutes 5))
                           (t/plus nyt (t/minutes 5))
                           true)))
  (is (false? (pvm/valissa? (t/plus nyt (t/minutes 15))
                            (t/minus nyt (t/minutes 5))
                            (t/plus nyt (t/minutes 5))
                            false))))

(deftest paivia-valissa-toimii
  (is (= (pvm/paivia-aikavalien-leikkauskohdassa [nyt
                                                  (t/plus nyt (t/days 5))]
                                                 [(t/plus nyt (t/days 1))
                                                  (t/plus nyt (t/days 3))])
         2))
  (is (= (pvm/paivia-aikavalien-leikkauskohdassa [nyt
                                                  (t/plus nyt (t/days 2))]
                                                 [(t/plus nyt (t/days 1))
                                                  (t/plus nyt (t/days 5))])
         1))
  (is (= (pvm/paivia-aikavalien-leikkauskohdassa [(t/plus nyt (t/days 1))
                                                  (t/plus nyt (t/days 3))]
                                                 [nyt
                                                  (t/plus nyt (t/days 2))])
         1))
  (is (= (pvm/paivia-aikavalien-leikkauskohdassa [(t/plus nyt (t/days 1))
                                                  (t/plus nyt (t/days 3))]
                                                 [nyt
                                                  (t/plus nyt (t/days 5))])
         2))
  (is (= (pvm/paivia-aikavalien-leikkauskohdassa [nyt
                                                  (t/plus nyt (t/days 3))]
                                                 [(t/plus nyt (t/days 5))
                                                  (t/plus nyt (t/days 10))])
         0))
  (is (= (pvm/paivia-aikavalien-leikkauskohdassa [nyt
                                                  (t/plus nyt (t/days 3))]
                                                 [(t/minus nyt (t/days 3))
                                                  (t/minus nyt (t/days 2))])
         0)))

(deftest aikavalit
  (testing "Kuukauden aikaväli"
    (let [tulos (pvm/kuukauden-aikavali nyt)]
      (is (= 2 (count tulos)))
      (is (= 0 (t/hour (first tulos))))
      (is (= 0 (t/minute (first tulos))))
      (is (= 23 (t/hour (second tulos))))
      (is (= 59 (t/minute (second tulos))))))

  (testing "Edellinen kuukausi aikavälina"
    (let [tulos (pvm/ed-kk-aikavalina nyt)]
      (is (= 2 (count tulos)))
      (is (= (t/month (first tulos))
             (t/month (second tulos))
             (t/month (t/minus nyt (t/months 1)))))
      (is (= 0 (t/hour (first tulos))))
      (is (= 0 (t/minute (first tulos))))
      (is (= 23 (t/hour (second tulos))))
      (is (= 59 (t/minute (second tulos)))))))