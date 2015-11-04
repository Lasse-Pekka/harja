(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.sillat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.siltatarkastukset :as s]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn paivita-silta [db silta]
  (log/debug "Päivitetään wanha silta")
  (s/paivita-silta-siltanumerolla! db
                       (:tyyppi silta)
                       (:nro silta)
                       (:nimi silta)
                       (.toString (:the_geom silta))))

(defn luo-silta [db silta]
  (log/debug "Luodaan uusi silta.")
  (s/vie-siltatauluun! db
                       (:tyyppi silta)
                       (:nro silta)
                       (:nimi silta)
                       (.toString (:the_geom silta))))

(defn luo-tai-paivita-silta [db silta]
  (if-let [silta-kannassa (first (s/hae-silta-numerolla db (:nro silta)))]
    (paivita-silta db silta)
    (luo-silta db silta)))
; Mahdollisesti voisi olla aiheellista poistaa silta, jota ei enää ole.
; Sillalle saattaa kuitenkin olla kirjattuna tarkastus.

(defn vie-silta-entry [db silta]
  (if (:the_geom silta)
    (luo-tai-paivita-silta db silta)
    (log/warn "Siltaa ei voida tuoda ilman geometriaa. Virheviesti: " (:loc_error silta))))

(defn vie-sillat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan sillat kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [transaktio db]
        (doseq [silta (shapefile/tuo shapefile)]
          (vie-silta-entry transaktio silta)))
      (log/debug "Siltojen tuonti kantaan valmis."))
    (log/debug "Siltojen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
