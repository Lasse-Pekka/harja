(ns harja-laadunseuranta.tiedot.nappaimisto
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.utils :refer [timestamp ipad?]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [cljs-time.local :as lt]
            [cljs-time.coerce :as tc]
            [harja-laadunseuranta.tiedot.math :as math]
            [harja-laadunseuranta.tiedot.fmt :as fmt]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]))

;; Syöttöjen validiusehdot

(def mittaustyypin-lahtoarvo {:kitkamittaus "0,"
                              :lumisuus ""
                              :talvihoito-tasaisuus ""})

(def syoton-max-merkkimaara {:kitkamittaus 4
                             :lumisuus 3
                             :talvihoito-tasaisuus 3})

(def syoton-rajat {:kitkamittaus [0 1]
                   :lumisuus [0 100]
                   :talvihoito-tasaisuus [0 100]})

;; Näppäimistön toiminnallisuus

(defn numeronappain-painettu!
  "Lisää syötteen syöttö-atomiin, jos se on sallittu.
   Estää liian pitkät syötteet, mutta ei tarkista esim.
   min-max rajoja ylittäviä syötteitä. Näistä on tarkoitus
   näyttää varoitus käyttöliittymässä ja käyttäjää
   hyväksymästä tällaista syötettä."
  [numero mittaustyyppi syotto-atom]
  (.log js/console "Numero syötetty: " (pr-str numero))
  (let [nykyinen-syotto (:nykyinen-syotto @syotto-atom)
        uusi-syotto (str nykyinen-syotto numero)
        suurin-sallittu-tarkkuus (mittaustyyppi syoton-max-merkkimaara)
        salli-syotto? (<= (count uusi-syotto) suurin-sallittu-tarkkuus)
        _ (.log js/console "Salli syöttö: " (pr-str salli-syotto?))
        lopullinen-syotto (if salli-syotto?
                            uusi-syotto
                            nykyinen-syotto)]
    (swap! syotto-atom assoc :nykyinen-syotto lopullinen-syotto)))

(defn alusta-mittaussyotto! [mittaustyyppi syotto-atom]
  (swap! syotto-atom assoc :nykyinen-syotto (mittaustyyppi mittaustyypin-lahtoarvo)))

(defn tyhjennyspainike-painettu! [mittaustyyppi syotto-atom]
  (alusta-mittaussyotto! mittaustyyppi syotto-atom))

(defn syotto-valmis! [mittaustyyppi syotto-atom]
  (swap! syotto-atom assoc :syotot (conj (:syotot @syotto-atom) (:nykyinen-syotto @syotto-atom)))
  (swap! syotto-atom assoc :nykyinen-syotto (mittaustyyppi mittaustyypin-lahtoarvo))
  (.log js/console "Syötöt nyt: " (pr-str (:syotot @syotto-atom))))

(defn- syotto-validi? [mittaustyyppi nykyinen-syotto]
  (let [suurin-sallittu-tarkkuus (mittaustyyppi syoton-max-merkkimaara)
        syotto-sallittu? (and (<= (count nykyinen-syotto)
                                  suurin-sallittu-tarkkuus)
                              (>= (fmt/string->numero nykyinen-syotto)
                                  (first (mittaustyyppi syoton-rajat)))
                              (<= (fmt/string->numero nykyinen-syotto)
                                  (second (mittaustyyppi syoton-rajat))))]
    (.log js/console "Syöttö sallittu? " (pr-str syotto-sallittu?))
    syotto-sallittu?))

;; Erikoisnäppäimistöt

(defn alusta-soratiemittaussyotto! [syotto-atom]
  (swap! syotto-atom assoc :tasaisuus 5)
  (swap! syotto-atom assoc :kiinteys 5)
  (swap! syotto-atom assoc :polyavyys 5))

(defn soratienappaimiston-numeronappain-painettu! [arvo mittaustyyppi syotto-atom]
  (.log js/console (pr-str "Painoit " mittaustyyppi " arvoksi " arvo))
  (swap! syotto-atom assoc mittaustyyppi arvo))

;; Arvojen kirjaaminen

(defn kirjaa-kitkamittaus! [arvo]
  (.log js/console "Kirjataan uusi kitkamittaus: " (pr-str arvo))
  (reitintallennus/kirjaa-kertakirjaus
    @s/idxdb
    {:sijainti (select-keys (:nykyinen @s/sijainti) [:lat :lon])
     :aikaleima (tc/to-long (lt/local-now))
     :tarkastusajo @s/tarkastusajo-id
     :havainnot @s/jatkuvat-havainnot
     :mittaukset {:kitkamittaus arvo}}))

(defn kirjaa-lumisuus! [arvo]
  (.log js/console "Kirjataan uusi lumisuus: " (pr-str arvo))
  (reitintallennus/kirjaa-kertakirjaus
    @s/idxdb
    {:sijainti (select-keys (:nykyinen @s/sijainti) [:lat :lon])
     :aikaleima (tc/to-long (lt/local-now))
     :tarkastusajo @s/tarkastusajo-id
     :havainnot @s/jatkuvat-havainnot
     :mittaukset {:lumisuus arvo}}))

(defn kirjaa-talvihoito-tasaisuus! [arvo]
  (.log js/console "Kirjataan uusi talvihoidon tasaisuus: " (pr-str arvo))
  (reitintallennus/kirjaa-kertakirjaus
    @s/idxdb
    {:sijainti (select-keys (:nykyinen @s/sijainti) [:lat :lon])
     :aikaleima (tc/to-long (lt/local-now))
     :tarkastusajo @s/tarkastusajo-id
     :havainnot @s/jatkuvat-havainnot
     :mittaukset {:talvihoito-tasaisuus arvo}}))