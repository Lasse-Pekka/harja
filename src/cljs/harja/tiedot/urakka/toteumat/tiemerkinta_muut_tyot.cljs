(ns harja.tiedot.urakka.toteumat.tiemerkinta-muut-tyot
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.urakka.suunnittelu.muut-tyot :as muut-tyot]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [tuck.core :as t]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def muut-tyot (atom {:valittu-tyo nil
                      :muut-tyot nil
                      :valinnat {:urakka nil
                                 :sopimus nil}}))

(defonce valinnat
  (reaction
    {:urakka (:id @nav/valittu-urakka)
     :sopimus (first @u/valittu-sopimusnumero)
     :sopimuskausi @u/valittu-hoitokausi}))

;; Tapahtumat

(defrecord YhdistaValinnat [valinnat])
(defrecord TyotHaettu [tulokset])
(defrecord HaeTyo [tyo])
(defrecord ValitseTyo [tyo])

(defn hae-tyot [{:keys [urakka] :as hakuparametrit}]
  (let [tulos! (t/send-async! ->TyotHaettu)]
    (go (let [tyot (<! (k/post! :hae-yllapito-toteumat {:urakka urakka}))]
          (when-not (k/virhe? tyot)
            (tulos! tyot))))))

(defn hae-tyo [id urakka]
  (let [tulos! (t/send-async! ->ValitseTyo)]
    (go (let [tyot (<! (k/post! :hae-yllapito-toteuma {:urakka urakka
                                                       :id id}))]
          (when-not (k/virhe? tyot)
            (tulos! tyot))))))

;; Tapahtumien käsittely

(extend-protocol t/Event

  YhdistaValinnat
  (process-event [{:keys [valinnat] :as e} tila]
    (hae-tyot {:urakka (:urakka valinnat)})
    (update-in tila [:valinnat] merge valinnat))

  TyotHaettu
  (process-event [{:keys [tulokset] :as e} tila]
    (assoc-in tila [:muut-tyot] tulokset))

  HaeTyo
  (process-event [{:keys [tyo] :as e} tila]
    (hae-tyo (:id tyo))
    tila)

  ValitseTyo
  (process-event [{:keys [tyo] :as e} tila]
    (assoc-in tila [:valittu-tyo] tyo)))


