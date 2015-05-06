(ns harja.views.urakka
  "Urakan näkymät: sisältää urakan perustiedot ja tabirakenteen"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            [harja.asiakas.tapahtumat :as t]
            [harja.tiedot.navigaatio :as nav]

            [harja.views.urakka.yleiset :as urakka-yleiset]
            [harja.views.urakka.suunnittelu :as suunnittelu]
            [harja.views.urakka.toteumat :as toteumat]
            [harja.tiedot.urakka.yhteystiedot :as yht]
            [harja.views.urakka.valitavoitteet :as valitavoitteet]))

(defn urakka
  "Urakkanäkymä"
  [ur]
  
  [bs/tabs {:active nav/urakka-valilehti}
   
    "Yleiset"
    ^{:key "yleiset"}
    [urakka-yleiset/yleiset ur]
    
    
    "Suunnittelu"
    ^{:key "suunnittelu"}
    [suunnittelu/suunnittelu ur]
    
    "Toteumat"
    ^{:key "toteumat"}
    [toteumat/toteumat ur]
    
    "Laadunseuranta"
    ^{:key "laadunseuranta"}
    [:div
     "laatua vois toki seurata"]
    
   "Siltatarkastukset"
   (when (= :hoito (:tyyppi ur))
     ^{:key "siltatarkastukset"}
     [:div
      "siltojakin voisi tarkastella"])

   "Välitavoitteet"
   (when-not (= :hoito (:tyyppi ur))
     ^{:key "valitavoitteet"}
     [valitavoitteet/valitavoitteet ur])
   ])
  
 
