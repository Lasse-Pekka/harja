(ns harja.palvelin.ajastetut-tehtavat.urakan-tyotuntimuistutukset
  "Tekee ajastetun yhteysvarmistuksen API:n"
  (:require [chime :refer [chime-ch]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.palvelut.viestinta :as viestinta]
            [harja.domain.urakan-tyotunnit :as ut]
            [clj-time.core :as t]
            [harja.pvm :as pvm]
            [harja.kyselyt.urakan-tyotunnit :as q]
            [harja.fmt :as fmt]))

(defn laheta-muistutukset-urakoille [fim email urakat vuosi kolmannes kuluvan-kolmanneksen-paattymispaiva]
  (doseq [{:keys [id sampoid hallintayksikko nimi]} urakat]
    (lo/debug (format "Lähetetään muistutus urakan työtuntien kirjaamisesta urakalle %s (id: %s)" nimi id))
    (let [kuukausivali (cond
                         (= 1 kolmannes) "tammikuu - huhtikuu"
                         (= 2 kolmannes) "toukokuu - elokuu"
                         (= 3 kolmannes) "syyskuu - joulukuu"
                         :else "")
          url (format "https://extranet.liikennevirasto.fi/harja/#urakat/yleiset?&hy=%s&u=%s"
                      hallintayksikko
                      id)
          otsikko (format "Urakan '%s' työtunnit täytyy kirjata %s mennessä"
                          nimi
                          (fmt/pvm kuluvan-kolmanneksen-paattymispaiva))
          sisalto (format "Urakan %s työtunnit vuoden %s välille %s täytyy kirjata %s mennessä. Urakka Harjassa: %s"
                          nimi
                          vuosi
                          kuukausivali
                          kuluvan-kolmanneksen-paattymispaiva
                          url)
          viesti {:fim fim
                  :email email
                  :urakka-sampoid sampoid
                  :fim-kayttajaroolit #{"ely urakanvalvoja" "urakan vastuuhenkilö"}
                  :viesti-otsikko otsikko
                  :viesti-body sisalto}]
      (viestinta/laheta-sposti-fim-kayttajarooleille viesti))))

(defn urakan-tyotuntimuistutukset [{:keys [fim email db]} paivittainen-ajoaika]
  (log/debug "Ajastetaan muistutukset urakan työtunneista ajettavaksi joka päivä " paivittainen-ajoaika)
  (ajastettu-tehtava/ajasta-paivittain
    paivittainen-ajoaika
    #(let [kuluva-kolmannes (ut/kuluva-vuosikolmannes)
           vuosi (::ut/vuosi kuluva-kolmannes)
           kolmannes (::ut/vuosikolmannes kuluva-kolmannes)
           kuluvan-kolmanneksen-paattymispaiva (ut/kuluvan-vuosikolmanneksen-paattymispaiva)
           paivia-kolmanneksen-paattymiseen (pvm/paivia-valissa (t/now) kuluvan-kolmanneksen-paattymispaiva)]
       (when (= 3 paivia-kolmanneksen-paattymiseen)
         (let [tunnittomat-urakat (q/hae-urakat-joilla-puuttuu-kolmanneksen-tunnit
                                    db
                                    {:vuosi vuosi
                                     :kolmannes kolmannes})
               (laheta-muistutukset-urakoille
                 fim
                 email
                 tunnittomat-urakat
                 vuosi
                 kolmannes
                 kuluvan-kolmanneksen-paattymispaiva)])))))

(defrecord UrakanTyotuntiMuistutukset [paivittainen-ajoaika]
  component/Lifecycle
  (start [this]
    (assoc this :urakan-tyotuntimuistutukset (urakan-tyotuntimuistutukset this paivittainen-ajoaika)))
  (stop [this]
    (let [lopeta (get this :urakan-tyotuntimuistutukset)]
      (when lopeta (lopeta)))
    this))
