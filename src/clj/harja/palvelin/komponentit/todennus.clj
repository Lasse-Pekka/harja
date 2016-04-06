(ns harja.palvelin.komponentit.todennus
  "Tämä namespace määrittelee käyttäjäidentiteetin todentamisen. Käyttäjän todentaminen
  WWW-palvelussa tehdään KOKA ympäristön antamilla header tiedoilla. Tämä komponentti ei tee
  käyttöoikeustarkistuksia, vaan pelkästään hakee käyttäjälle sallitut käyttöoikeudet
  ja tarkistaa käyttäjän identiteetin."
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.cache :as cache]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.kayttajat :as q]
            [slingshot.slingshot :refer [try+ throw+]]
            [harja.palvelin.komponentit.tapahtumat :refer [kuuntele!]]
            [harja.domain.roolit :as roolit]
            [harja.domain.oikeudet :as oikeudet]

            [clojure.string :as str]))

(defn- ryhman-rooli-ja-linkki
  "Etsii annetulle OAM ryhmälle roolin. Ryhmä voi olla suoraan roolin nimi
tai linkitetyssä roolissa muotoa <linkitetty id>_<roolin nimi>. Palauttaa
roolin tiedot ja linkitetyn id:n vektorissa, jos rooli ei ole linkitetty id
on nil."
  [roolit ryhma]
  (some (fn [{:keys [nimi linkki] :as rooli}]
          (cond
            (= nimi ryhma)
            [rooli nil]

            (and linkki (str/ends-with? ryhma (str "_" nimi)))
            [rooli (first (str/split ryhma #"_"))]))
        (vals roolit)))

(defn- yleisroolit [roolit-ja-linkit]
  (into #{}
        ;; Haetaan kaikki roolit, joilla ei ole linkkiä
        (comp (map first)
              (filter (comp empty? :linkki))
              (map :nimi))
        roolit-ja-linkit))

(defn- roolien-nimet
  [roolit]
  (into #{}
        (map (comp :nimi first))
        roolit))

(defn- urakkaroolit [urakan-id roolit-ja-linkit]
  (into {}
        (comp
         ;; Muuta key Sampo id:stä Harjan urakka id:ksi
         (map #(update-in % [0] urakan-id))

         ;; Muuta [[rooli id] ...] -> #{nimi ...}
         (map #(update-in % [1] roolien-nimet)))
        ;; Valitaan vain "urakka" linkitetyt roolit ja
        ;; ryhmitellään ne id:n perusteella
        (group-by second
                  (filter (comp #(= "urakka" %)
                                :linkki
                                first)
                          roolit-ja-linkit))))

(defn organisaatioroolit [urakoitsijan-id roolit-ja-linkit]
  (into {}
        (comp
         (map #(update-in % [0] urakoitsijan-id))
         (map #(update-in % [1] roolien-nimet)))
        (group-by second
                  (filter (comp #(= "urakoitsija" %) :linkki first)
                          roolit-ja-linkit))))
(defn kayttajan-roolit
  "Palauttaa annetun käyttäjän roolit OAM_GROUPS header arvon perusteella.
  Roolit on mäppäys roolinimestä sen tietoihin. Sähken antama urakan tai
  urakoitsijan id muutetaan harjan id:ksi kutsumalla annettuja urakan-id
  ja urakoitsijan-id funktioita."
  [urakan-id urakoitsijan-id roolit oam-groups]
  (let [roolit-ja-linkit (->> (str/split oam-groups #",")
                              (map (partial ryhman-rooli-ja-linkki roolit)))]
    {:roolit (yleisroolit roolit-ja-linkit)
     :urakkaroolit (urakkaroolit urakan-id roolit-ja-linkit)
     :organisaatioroolit (organisaatioroolit urakoitsijan-id roolit-ja-linkit)}))

;; Pidetään käyttäjätietoja muistissa vartti, jotta ei tarvitse koko ajan hakea tietokannasta
;; uudestaan. KOKA->käyttäjätiedot pitää hakea joka ikiselle HTTP pyynnölle.
(def kayttajatiedot (atom (cache/ttl-cache-factory {} :ttl (* 15 60 1000))))

(defn- koka-headerit [headerit]
  (select-keys headerit
               [;; Käyttäjätunnus ja ryhmät
                "oam_remote_user" "oam_groups"
                ;; ELY-numero (tai null) ja org nimi
                "oam_departmentnumber" "oam_organization"
                ;; Etu- ja sukunimi
                "oam_user_first_name" "oam_user_last_name"
                ;; Sähköposti ja puhelin
                "oam_user_email" "oam_user_mobile"]))

(defn- hae-kayttajalle-organisaatio
  [ely db organisaatio]
  (or
   ;; Jos ELY-numero haetaan se
   (some->> ely
            (re-matches #"\d+")
            Long/parseLong
            (q/hae-ely-numerolla db)
            first)
   ;; Muuten haetaan org. nimellä
   (first (q/hae-organisaatio-nimella db organisaatio))))

(defn- varmista-kayttajatiedot
  "Ottaa tietokannan ja käyttäjän OAM headerit. Varmistaa että käyttäjä on olemassa
ja palauttaa käyttäjätiedot"
  [db {kayttajanimi "oam_remote_user"
       ryhmat "oam_groups"
       ely "oam_departmentnumber"
       organisaatio "oam_organization"
       etunimi "oam_user_first_name"
       sukunimi "oam_user_last_name"
       sahkoposti "oam_user_email"
       puhelin "oam_user_mobile"}]

  (let [organisaatio (hae-kayttajalle-organisaatio ely db organisaatio)

        kayttaja {:kayttajanimi kayttajanimi
                  :etunimi etunimi
                  :sukunimi sukunimi
                  :sahkoposti sahkoposti
                  :puhelin puhelin
                  :organisaatio (:id organisaatio)}
        kayttaja-id (q/varmista-kayttaja
                     db
                     (assoc kayttaja
                            :organisaatio (:id organisaatio)))]

    (merge (assoc kayttaja
                  :id kayttaja-id)
           (kayttajan-roolit (partial q/hae-urakan-id-sampo-idlla db)
                             (partial q/hae-urakoitsijan-id-ytunnuksella db)
                             oikeudet/roolit
                             ryhmat))))

(defn koka->kayttajatiedot [db headerit]
  (let [oam-tiedot (koka-headerit headerit)]
    (get (swap! kayttajatiedot
                #(cache/through
                  (fn [oam-tiedot]
                    (try
                      (varmista-kayttajatiedot db oam-tiedot)
                      (catch Throwable t
                        (log/warn t "Käyttäjätietojen varmistuksessa virhe!"))))
                  %
                  oam-tiedot))
         oam-tiedot)))


(defprotocol Todennus
  "Protokolla HTTP pyyntöjen käyttäjäidentiteetin todentamiseen."
  (todenna-pyynto [this req] "Todenna annetun HTTP-pyynnön käyttäjätiedot, palauttaa uuden
req mäpin, jossa käyttäjän tiedot on lisätty avaimella :kayttaja."))

(def todennusvirhe {:virhe :todennusvirhe})

(defn testikaytto
  "Tekee mahdollisen testikäyttäjän korvaamisen. Jos testikäyttäjiä on konfiguroitu ja autentikoitu
  käyttäjä on järjestelmävastuuhenkilö ja hänellä on testikäyttäjä eväste, korvataan käyttäjätiedot
  evästeen nimeämän käyttäjätunnuksen tiedoilla."
  [db req kayttajatiedot testikayttajat]
  (if-let [testitunnus (and testikayttajat
                            (oikeudet/voi-kirjoittaa? oikeudet/testaus-testikaytto
                                                      nil kayttajatiedot)
                            (get-in req [:cookies "testikayttaja" :value]))]
    (if-let [testikayttajan-tiedot (get testikayttajat testitunnus)]
      (assoc (koka->kayttajatiedot db testikayttajan-tiedot)
             ;; asetetaan myös oikea käyttäjä talteen, käyttäjätiedot tarvitsee sitä, jotta
             ;; "su" tilanne voidaan tunnistaa
             :oikea-kayttaja kayttajatiedot)
      (do
        (log/warn "Käyttäjä " (:kayttajanimi kayttajatiedot)
                  " yritti ei-sallittua testikäyttäjää: " testitunnus)
        (throw+ todennusvirhe)))
    kayttajatiedot))

(defrecord HttpTodennus [testikayttajat]
  component/Lifecycle
  (start [this]
    (log/info "Todennetaan HTTP käyttäjä KOKA headereista.")
    (kuuntele! (:klusterin-tapahtumat this)
               :kayttaja-muokattu #(swap! kayttajatiedot cache/evict %))
    this)
  (stop [this]
    this)

  Todennus
  (todenna-pyynto [{db :db :as this} req]
    (let [headerit (:headers req)
          kayttaja-id (headerit "oam_remote_user")]

      (if (nil? kayttaja-id)
        (throw+ todennusvirhe)
        (if-let [kayttajatiedot (koka->kayttajatiedot db headerit)]
          (do (println "KÄYTTÄJÄTIEDOT: " kayttajatiedot)
              (assoc req :kayttaja
                     (testikaytto db req kayttajatiedot testikayttajat)))
          (throw+ todennusvirhe))))))

(defrecord FeikkiHttpTodennus [kayttaja]
  component/Lifecycle
  (start [this]
    (log/warn "Käytetään FEIKKI käyttäjätodennusta, käyttäjä = " (pr-str kayttaja))
    this)
  (stop [this]
    this)

  Todennus
  (todenna-pyynto [this req]
    (assoc req
      :kayttaja kayttaja)))

(defn http-todennus
  ([] (http-todennus nil))
  ([testikayttajat]
   (->HttpTodennus testikayttajat)))

(defn feikki-http-todennus [kayttaja]
  (->FeikkiHttpTodennus kayttaja))
