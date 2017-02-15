(ns harja.tiedot.hallinta.yhteydenpito
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.navigaatio :as nav]
            [clojure.string :as str]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def nakymassa? (atom false))

(defn- hae-yhteydenpidon-vastaanottajat []
  (log "Haetaan mailiosoitteet")
  (k/post! :yhteydenpito-vastaanottajat nil))

(def vastaanottajat (reaction<! (let [nakymassa? @nakymassa?]
                                  {:nil-kun-haku-kaynnissa? true}
                                  (when nakymassa?
                                    (hae-yhteydenpidon-vastaanottajat)))))

(defn mailto-bcc-linkki [vastaanottajat]
  (str "mailto:?bcc="
       (str/join "," (keep :sahkoposti vastaanottajat))))