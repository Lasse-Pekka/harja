(ns harja.domain.organisaatio
  "Määrittelee organisaation nimiavaruuden specit"
  #?@(:clj [(:require [clojure.spec :as s]
                      [harja.kyselyt.specql-db :refer [db]]
                      [specql.core :refer [define-tables]]
                      [clojure.future :refer :all])]
      :cljs [(:require [clojure.spec :as s]
                       [specql.impl.registry]
                       [specql.data-types])
             (:require-macros
               [harja.kyselyt.specql-db :refer [db]]
               [specql.core :refer [define-tables]])]))

(define-tables db ["organisaatio" ::organisaatio])

;; Haut

(s/def ::vesivayla-urakoitsijat-vastaus
  (s/coll-of (s/and ::organisaatio
                    (s/keys :req [::id ::nimi ::ytunnus ::alkupvm ::loppupvm
                                  ::katuosoite ::postinumero]))))

;; Tallennus

(s/def ::tallenna-urakoitsija-kysely ::organisaatio-insert)

(s/def ::tallenna-urakoitsija-vastaus (s/and ::organisaatio
                                             (s/keys :req [::id])))