(ns harja.kyselyt.specql
  "Määritellään yleisiä clojure.spec tyyppejä."
  (:require [specql.core :refer [define-tables]]
            [specql.data-types :as d]
            [harja.domain.specql-db :refer [db]]
            [harja.domain.tietyoilmoitukset :as t]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.urakka :as urakka]
            [clojure.spec :as s]
            [clojure.future :refer :all]
            [clojure.string :refer [trim]]
            [clojure.java.io :as io]))


(s/def ::d/geometry any?)

(define-tables db
  ["tr_osoite" ::tr/osoite]
  ["urakkatyyppi" ::urakka/urakkatyyppi])
