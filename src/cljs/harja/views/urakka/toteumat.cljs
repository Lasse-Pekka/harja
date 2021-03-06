(ns harja.views.urakka.toteumat
  "Urakan 'Toteumat' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.bootstrap :as bs]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.tiedot.urakka :as u]
            [harja.views.urakka.toteumat.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.views.urakka.toteumat.kokonaishintaiset-tyot :as kokonaishintaiset-tyot]
            [harja.views.urakka.toteumat.muut-tyot :as muut-tyot]
            [harja.views.urakka.toteumat.erilliskustannukset :as erilliskustannukset]
            [harja.views.urakka.toteumat.materiaalit :refer [materiaalit-nakyma]]
            [harja.views.urakka.toteumat.varusteet :as varusteet]
            [harja.views.urakka.toteumat.suola :refer [suolatoteumat]]

            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.oikeudet :as oikeudet])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))


(defn toteumat
  "Toteumien pääkomponentti"
  [ur]
  (komp/luo
   (komp/sisaan-ulos #(do
                        (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                        (nav/vaihda-kartan-koko! :S))
                     #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))
   (fn [{:keys [id] :as ur}]
     [bs/tabs {:style :tabs :classes "tabs-taso2"
               :active (nav/valittu-valilehti-atom :toteumat)}

      "Kokonaishintaiset työt" :kokonaishintaiset-tyot
      (when (oikeudet/urakat-toteumat-kokonaishintaisettyot id)
        [kokonaishintaiset-tyot/kokonaishintaiset-toteumat])

      "Yksikköhintaiset työt" :yksikkohintaiset-tyot
      (when (oikeudet/urakat-toteumat-yksikkohintaisettyot id)
        [yks-hint-tyot/yksikkohintaisten-toteumat])

      "Muutos- ja lisätyöt" :muut-tyot
      (when (oikeudet/urakat-toteumat-muutos-ja-lisatyot id)
        [muut-tyot/muut-tyot-toteumat])

      "Suola" :suola
      (when (and (oikeudet/urakat-toteumat-suola id)
                 (= :hoito (:tyyppi ur)))
        [suolatoteumat])

      "Materiaalit" :materiaalit
      (when (oikeudet/urakat-toteumat-materiaalit id)
        [materiaalit-nakyma ur])

      "Erilliskustannukset" :erilliskustannukset
      (when (oikeudet/urakat-toteumat-erilliskustannukset id)
        [erilliskustannukset/erilliskustannusten-toteumat])

      "Varusteet" :varusteet
      (when (and (oikeudet/urakat-toteumat-varusteet id)
                 (= :hoito (:tyyppi ur)))
        [varusteet/varusteet])])))
