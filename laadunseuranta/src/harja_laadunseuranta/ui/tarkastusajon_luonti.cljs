(ns harja-laadunseuranta.ui.tarkastusajon-luonti
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.comms :as comms]
            [harja-laadunseuranta.ui.urakkavalitsin :as urakkavalitsin]
            [harja-laadunseuranta.tiedot.tarkastusajon-luonti :as tiedot]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.tiedot.sovellus :as s])
  (:require-macros [reagent.ratom :refer [run!]]
                   [cljs.core.async.macros :refer [go]]
                   [devcards.core :refer [defcard]]))

(def urakkatyypin-urakat
  (atom nil))

(defn tarkastusajon-luontidialogi []
  (let [aloitettu (atom false)
        valittu (fn [tyyppi]
                  (reset! aloitettu true)
                  (tiedot/luo-ajo tyyppi))
        urakkavalitsimen-urakkatyyppi (atom nil)]
    (fn [_ _]
      (if @urakkavalitsimen-urakkatyyppi
        (let [cb #(do
                   (s/valitse-urakka! %)
                   (when-not (nil? %)
                     (valittu @urakkavalitsimen-urakkatyyppi))
                   (reset! urakkavalitsimen-urakkatyyppi nil))]
          [urakkavalitsin/urakkavalitsin @urakkatyypin-urakat urakkavalitsimen-urakkatyyppi cb])
        (if @aloitettu
          [:div.tarkastusajon-luonti-dialog
           [:p "Luodaan tarkastusajoa..."]]
          [:div.tarkastusajon-luonti-dialog
           [:p "Valitse tarkastusajon tyyppi"]
           [:div
            [:nav.pikavalintapainike {:on-click #(valittu :kelitarkastus)} "Talvihoito"]
            [:nav.pikavalintapainike {:on-click #(valittu :soratietarkastus)} "Kesähoito"]
            [:nav.pikavalintapainike {:on-click #(do
                                                  (go (let [urakat (:ok (<! (comms/hae-urakkatyypin-urakat "paallystys")))]
                                                        (reset! urakkatyypin-urakat urakat)
                                                        (reset! urakkavalitsimen-urakkatyyppi :paallystys))))} "Päällystys"]
            #_[:nav.pikavalintapainike {:on-click #(valittu :tiemerkinta)} "Tiemerkintä"]]
           [:nav.pikavalintapainike.peruutuspainike {:on-click #(tiedot/luonti-peruttu)}
            "Peruuta"]])))))

(defn tarkastusajon-paattamisdialogi [paattamattomia]
  (let [kylla-klikattu (atom false)]
    (fn [_ _ _]
      (if @kylla-klikattu
        [:div.tarkastusajon-luonti-dialog
         [:p "Päätetään, älä sulje selainta..."]
         [:div [:img.centered {:src kuvat/+spinner+
                      :height "32px"}]]]
        [:div.tarkastusajon-luonti-dialog
         [:p "Päätetäänkö tarkastusajo?"]
         [:div.tarkastusajon-luonti-dialog-wrap
          [:button.nappi-ensisijainen {:on-click #(when (= 0 @paattamattomia)
                                                   (reset! kylla-klikattu true)
                                                   (tiedot/paata-ajo))}
           (if (> @paattamattomia 0)
             [:div
              [:img.odotusspinneri {:src kuvat/+spinner+
                     :height "32px"}]
              "Odota..."]
             "Kyllä")]
          [:button.nappi-toissijainen {:on-click #(tiedot/paattaminen-peruttu)} "Ei"]]]))))

(defn tarkastusajon-jatkamisdialogi []
  [:div.tarkastusajon-luonti-dialog
   [:p "Jatketaanko tarkastusajoa?"]
   [:div
    [:nav.pikavalintapainike {:on-click #(tiedot/jatka-ajoa)} "Jatka"]
    [:nav.pikavalintapainike.nappi-kielteinen {:on-click #(tiedot/pakota-ajon-lopetus)} "Pakota lopetus"]]])

(defcard luontidialogi-card
  (reagent/as-element [tarkastusajon-luontidialogi #() #()]))
