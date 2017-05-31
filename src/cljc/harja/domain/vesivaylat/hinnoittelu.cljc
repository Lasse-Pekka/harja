(ns harja.domain.vesivaylat.hinnoittelu
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [harja.domain.muokkaustiedot :as m]
    [harja.domain.vesivaylat.hinta :as h]
    [harja.domain.urakka :as ur]

    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]
    [specql.rel :as rel]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["vv_hinnoittelu_toimenpide" ::hinnoittelu<->toimenpide
   {#?@(:clj [::toimenpiteet (rel/has-one
                               ::toimenpide-id
                               :harja.domain.toimenpide/toimenpide
                               :harja.domain.toimenpide/id)
              ::hinnoittelut (rel/has-one
                               ::hinnoittelu-id
                               ::hinnoittelu
                               ::id)])}]
  ["vv_hinnoittelu" ::hinnoittelu
   {"muokattu" ::m/muokattu
    "hintaryhma" ::hintaryhma?
    "muokkaaja" ::m/muokkaaja-id
    "luotu" ::m/luotu
    "luoja" ::m/luoja-id
    "poistettu" ::m/poistettu?
    "poistaja" ::m/poistaja-id
    [::toimenpide-linkit (rel/has-many
                               ::id
                               ::hinnoittelu<->toimenpide
                               ::hinnoittelu-id)
         ::hinnat (rel/has-many
                    ::id
                    ::h/hinta
                    ::h/hinnoittelu-id)]}])

(def perustiedot
  #{::nimi
    ::hintaryhma?
    ::id})

(def hinnat
  #{[::hinnat h/perustiedot]})

(def hinnoittelutiedot
  (clojure.set/union perustiedot hinnat))

(def toimenpiteen-hinnoittelut
  #{[::hinnoittelut hinnoittelutiedot]})

(def hinnoittelun-toimenpiteet
  #{[::toimenpide-linkit
     #{[::toimenpiteet
        #{:harja.domain.vesivaylat.toimenpide/id
          :harja.domain.vesivaylat.toimenpide/urakka-id
          :harja.domain.vesivaylat.toimenpide/hintatyyppi
          :harja.domain.vesivaylat.toimenpide/lisatyo?}]}]})

(s/def ::hae-hinnoittelut-kysely
  (s/keys
    :req [::ur/id]))

(s/def ::hae-hinnoittelut-vastaus
  (s/coll-of
    (s/keys :req [::id ::nimi ::hintaryhma?])))

(s/def ::luo-hinnoittelu-kysely
  (s/keys
    :req [::nimi ::ur/id]))

(s/def ::luo-hinnoittelu-vastaus
  (s/keys
    :req [::nimi ::hintaryhma? ::id]))

(s/def ::liita-toimenpiteet-hinnotteluun-kysely
  (s/keys
    :req [:harja.domain.vesivaylat.toimenpide/idt
          ::id
          ::ur/id]))

#_(s/def ::hinta-elementit
  (s/coll-of
    (s/keys :req [::h/maara ::h/otsikko ::h/yleiskustannuslisa])))

(s/def ::anna-hintaryhmalle-hinta-kysely
  (s/keys
    :req [::id ::hinnat ::ur/id]))

(s/def ::anna-hintaryhmalle-hinta-vastaus ::h/hinnoittelu)

(s/def ::anna-toimenpiteelle-hinta-kysely
  (s/keys
    :req [:harja.domain.vesivaylat.toimenpide/id
          ::hinnat
          ::ur/id]))

(s/def ::anna-toimenpiteelle-hinta-vastaus ::h/hinnoittelu)