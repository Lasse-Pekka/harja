(ns harja.views.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.ui.otsikkokomponentti :refer [otsikot]]
            [harja.domain.vesivaylat.toimenpide :as t]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def testidata [{::t/id 0
                 ::t/tyoluokka "Asennus ja huolto"
                 ::t/toimenpide "Huoltotyö"
                 ::t/pvm (pvm/nyt)
                 ::t/turvalaite "Siitenluoto (16469)"}
                {::t/id 1
                 ::t/tyoluokka "Asennus ja huolto"
                 ::t/toimenpide "Huoltotyö"
                 ::t/pvm (pvm/nyt)
                 ::t/turvalaite "Siitenluoto (16469)"}
                {::t/id 2
                 ::t/tyoluokka "Asennus ja huolto"
                 ::t/toimenpide "Huoltotyö"
                 ::t/pvm (pvm/nyt)
                 ::t/turvalaite "Siitenluoto (16469)"}
                {::t/id 3
                 ::t/tyoluokka "Asennus ja huolto"
                 ::t/toimenpide "Huoltotyö"
                 ::t/pvm (pvm/nyt)
                 ::t/turvalaite "Siitenluoto (16469)"}
                {::t/id 4
                 ::t/tyoluokka "Asennus ja huolto"
                 ::t/toimenpide "Huoltotyö"
                 ::t/pvm (pvm/nyt)
                 ::t/turvalaite "Siitenluoto (16469)"}])

(def ryhmitelty-testidata [(grid/otsikko "Varkaus, Kuopion väylä")
                {::t/id 0
                 ::t/tyoluokka "Asennus ja huolto"
                 ::t/toimenpide "Huoltotyö"
                 ::t/pvm (pvm/nyt)
                 ::t/turvalaite "Siitenluoto (16469)"}
                {::t/id 1
                 ::t/tyoluokka "Asennus ja huolto"
                 ::t/toimenpide "Huoltotyö"
                 ::t/pvm (pvm/nyt)
                 ::t/turvalaite "Siitenluoto (16469)"}
                {::t/id 2
                 ::t/tyoluokka "Asennus ja huolto"
                 ::t/toimenpide "Huoltotyö"
                 ::t/pvm (pvm/nyt)
                 ::t/turvalaite "Siitenluoto (16469)"}
                (grid/otsikko "Kopio, Iisalmen väylä")
                {::t/id 3
                 ::t/tyoluokka "Asennus ja huolto"
                 ::t/toimenpide "Huoltotyö"
                 ::t/pvm (pvm/nyt)
                 ::t/turvalaite "Siitenluoto (16469)"}
                {::t/id 4
                 ::t/tyoluokka "Asennus ja huolto"
                 ::t/toimenpide "Huoltotyö"
                 ::t/pvm (pvm/nyt)
                 ::t/turvalaite "Siitenluoto (16469)"}])

(defn- otsikon-sisalto [sijainnit]
  [grid/grid
   {:tunniste ::t/id
    ;; TODO Piilota otsikkosarivi (th) kokonaan?
    :tyhja (if (nil? sijainnit)
             [ajax-loader "Haetaan toimenpiteitä"]
             "Ei toimenpiteitä")}
   [{:nimi ::t/tyoluokka}
    {:nimi ::t/toimenpide}
    {:nimi ::t/pvm :fmt pvm/pvm-opt}
    {:nimi ::t/turvalaite}]
   sijainnit])

(defn- toimenpidepaneelin-otsikko [otsikko maara]
  (str otsikko
       " ("
       maara
       (when (not= maara 0)
         "kpl")
       ")"))

(defn kokonaishintaiset-toimenpiteet* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! app]
      [:div
       [:div {:style {:padding "10px"}}
        [:img {:src "images/harja_favicon.png"}]
        [:div {:style {:color "orange"}} "Työmaa"]]

       [otsikot [(toimenpidepaneelin-otsikko "Viitat" (count testidata))
                 (fn [] [otsikon-sisalto ryhmitelty-testidata])
                 (toimenpidepaneelin-otsikko "Poljut" 0)
                 (fn [] [otsikon-sisalto []])
                 (toimenpidepaneelin-otsikko "Tykityöt" 0)
                 (fn [] [otsikon-sisalto []])]]])))

(defn kokonaishintaiset-toimenpiteet []
  [tuck tiedot/tila kokonaishintaiset-toimenpiteet*])