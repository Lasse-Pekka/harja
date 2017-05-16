(ns harja.kyselyt.vesivaylat.toimenpiteet
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch]]
            [specql.op :as op]
            [specql.rel :as rel]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]
            [clojure.future :refer :all]
            [harja.kyselyt.specql-db :refer [define-tables]]

            [harja.domain.muokkaustiedot :as m]
            [harja.domain.toteuma :as tot]
            [harja.domain.vesivaylat.urakoitsija :as vv-urakoitsija]
            [harja.domain.vesivaylat.toimenpide :as vv-toimenpide]
            [harja.domain.vesivaylat.vayla :as vv-vayla]
            [clojure.future :refer :all]))

(defn hae-toimenpiteet [db {:keys [alku loppu vikakorjaukset?
                                   tyyppi luotu-alku luotu-loppu urakoitsija-id] :as tiedot}]
  (let [urakka-id (::tot/urakka-id tiedot)
        sopimus-id (::vv-toimenpide/sopimus-id tiedot)
        vaylatyyppi (::vv-toimenpide/vaylatyyppi tiedot)
        vayla-id (::vv-toimenpide/vayla-id tiedot)
        tyolaji (::vv-toimenpide/tyolaji tiedot)
        tyoluokka (::vv-toimenpide/tyoluokka tiedot)
        toimenpide (::vv-toimenpide/toimenpide tiedot)]
    (fetch db ::vv-toimenpide/toimenpide (clojure.set/union
                                           vv-toimenpide/perustiedot
                                           vv-toimenpide/viittaukset
                                           vv-toimenpide/reimari-kentat
                                           vv-toimenpide/metatiedot)
           (op/and
             (merge {}
                    {::m/poistettu? false}
                    {::vv-toimenpide/toteuma {:harja.domain.toteuma/urakka-id urakka-id}}
                    (when (and luotu-alku luotu-loppu)
                      {::m/reimari-luotu (op/between luotu-alku luotu-loppu)})
                    (when urakoitsija-id
                      {::vv-toimenpide/reimari-urakoitsija {::vv-urakoitsija/r-id urakoitsija-id}})
                    (when (= :kokonaishintainen tyyppi)
                      {::vv-toimenpide/toteuma {:harja.domain.toteuma/tyyppi "vv-kokonaishintainen"}})
                    (when (= :yksikkohintainen tyyppi)
                      {::vv-toimenpide/toteuma {:harja.domain.toteuma/tyyppi "vv-yksikkohintainen"}})
                    (when sopimus-id {::vv-toimenpide/sopimus-id sopimus-id})
                    (when (and alku loppu)
                      {::vv-toimenpide/reimari-luotu (op/between alku loppu)})
                    (when (and vaylatyyppi (not vayla-id))
                      {::vv-toimenpide/vayla {::vv-vayla/tyyppi vaylatyyppi}})
                    (when vayla-id
                      {::vv-toimenpide/vayla {::vv-vayla/id vayla-id}})
                    (when (and tyolaji (not tyoluokka) (not toimenpide))
                      {::vv-toimenpide/reimari-tyolaji tyolaji})
                    (when (and tyoluokka (not toimenpide))
                      {::vv-toimenpide/reimari-tyoluokka tyoluokka})
                    (when toimenpide
                      {::vv-toimenpide/reimari-toimenpide toimenpide})
                    (when (false? vikakorjaukset?)
                      (op/null? ::vv-toimenpide/vikailmoitukset))
                    (when (true? vikakorjaukset?)
                      (op/not-null? ::vv-toimenpide/vikailmoitukset)))))))
