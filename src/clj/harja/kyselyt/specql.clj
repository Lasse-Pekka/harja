(ns harja.kyselyt.specql
  "Määritellään yleisiä clojure.spec tyyppejä."
  (:require [specql.core :refer [define-tables]]
            [specql.data-types :as d]
            [harja.domain.tietyoilmoitukset :as t]
            [clojure.spec :as s]
            [clojure.future :refer :all]))

(def db {:connection-uri "jdbc:postgresql://localhost:5432/harja?user=harja"})

(s/def ::d/geometry any?)
