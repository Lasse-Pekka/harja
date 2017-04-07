(ns harja.views.vesivaylat.hallinta
  (:require [harja.ui.bootstrap :as bs]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.vesivaylat.urakoiden-luonti :as vu]
            [harja.views.vesivaylat.hankkeiden-luonti :as vhu]
            [harja.views.vesivaylat.urakoitsijoiden-luonti :as vuu]
            [harja.views.vesivaylat.sopimuksien-luonti :as vsu]
            [harja.tiedot.istunto :as istunto]))

(defn vesivayla-hallinta
  []
  [bs/tabs {:style :tabs :classes "tabs-taso2"
            :active (nav/valittu-valilehti-atom :vesivayla-hallinta)}
   "Urakoiden luonti"
   :vesivaylaurakoiden-luonti
   (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
                     (oikeudet/hallinta-vesivaylat))
     ^{:key "vesivaylaurakat"}
     [vu/vesivaylaurakoiden-luonti])

   "Hankkeiden luonti"
   :vesivaylahankkeiden-luonti
   (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
                     (oikeudet/hallinta-vesivaylat))
     ^{:key "vesivaylahankkeet"}
     [vhu/vesivaylahankkeiden-luonti])

   "Urakoitsijoiden luonti"
   :vesivaylaurakoitsijoiden-luonti
   (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
              (oikeudet/hallinta-vesivaylat))
     ^{:key "vesivaylaurakoitsijat"}
     [vuu/vesivaylaurakoitsijoiden-luonti])

   "Sopimuksien luonti"
   :vesivaylasopimuksien-luonti
   (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
              (oikeudet/hallinta-vesivaylat))
     ^{:key "vesivaylasopimukset"}
     [vsu/vesivaylasopimuksien-luonti])])
