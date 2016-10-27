(ns harja.palvelin.palvelut.yllapitokohteet.viestinta
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :refer :all]
            [harja.palvelin.palvelut.yllapitokohteet.yllapitokohteet :refer :all]
            [harja.testi :refer :all]
            [clojure.core.match :refer [match]]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]))

(defn- viesti-kohde-valmis-merkintaan [{:keys [paallystysurakka-nimi kohde-nimi kohde-osoite
                                              kohde-valmis-tiemerkintaan-pvm ilmoittaja
                                              tiemerkintaurakka-nimi] :as tiedot}]
  (html
    [:div
     [:p (format "Kohde '%s' on ilmoitettu olevan valmis tiemerkintään %s."
                 (or kohde-nimi (tierekisteri/tierekisteriosoite-tekstina kohde-osoite))
                 (fmt/pvm kohde-valmis-tiemerkintaan-pvm))]
     (html-tyokalut/taulukko [["Kohde" kohde-nimi]
                              ["TR-osoite" (tierekisteri/tierekisteriosoite-tekstina
                                             kohde-osoite
                                             {:teksti-tie? false})]
                              ["Valmis tiemerkintään" (fmt/pvm kohde-valmis-tiemerkintaan-pvm)]
                              ["Tiemerkinnän suorittaja" tiemerkintaurakka-nimi]
                              ["Ilmoittaja" ilmoittaja]
                              ["Ilmoittajan urakka" paallystysurakka-nimi]])]))

(defn sahkoposti-tiemerkintaurakkaan-kohde-valmis-merkintaan
  [{:keys [fim email
           paallystysurakka-nimi kohde-nimi kohde-osoite
           kohde-valmis-tiemerkintaan-pvm ilmoittaja
           tiemerkintaurakka-id tiemerkintaurakka-nimi
           tiemerkintaurakka-sampo-id] :as tiedot}]
  (let [ilmoituksen-saajat (fim/hae-urakan-kayttajat-jotka-roolissa
                             fim
                             tiemerkintaurakka-sampo-id
                             #{"ely urakanvalvoja" "urakan vastuuhenkilö"})]
    (if-not (empty? ilmoituksen-saajat)
      (doseq [henkilo ilmoituksen-saajat]
        (sahkoposti/laheta-viesti!
          email
          (sahkoposti/vastausosoite email)
          (:sahkoposti henkilo)
          (format "Harja: Kohteen '%s' tiemerkinnän voi aloittaa %s"
                  (or kohde-nimi (tierekisteri/tierekisteriosoite-tekstina kohde-osoite))
                  (fmt/pvm kohde-valmis-tiemerkintaan-pvm))
          (viesti-kohde-valmis-merkintaan {:paallystysurakka-nimi paallystysurakka-nimi
                                          :kohde-nimi kohde-nimi
                                          :kohde-osoite kohde-osoite
                                          :kohde-valmis-tiemerkintaan-pvm kohde-valmis-tiemerkintaan-pvm
                                          :ilmoittaja ilmoittaja
                                          :tiemerkintaurakka-nimi tiemerkintaurakka-nimi})))
      (log/warn (format "Tiemerkintäurakalle %s ei löydy FIM:stä henkiöä, jolle ilmoittaa kohteen valmiudesta tiemerkintään."
                        tiemerkintaurakka-id)))))


(defn- viesti-tiemerkinta-valmis [{:keys [paallystysurakka-nimi kohde-nimi kohde-osoite
                                               tiemerkinta-valmis ilmoittaja] :as tiedot}]
  (html
    [:div
     [:p (format "Kohteen '%s' tiemerkintä on valmistunut %s."
                 (or kohde-nimi (tierekisteri/tierekisteriosoite-tekstina kohde-osoite))
                 (fmt/pvm kohde-valmis-tiemerkintaan-pvm))]
     (html-tyokalut/taulukko [["Kohde" kohde-nimi]
                              ["TR-osoite" (tierekisteri/tierekisteriosoite-tekstina
                                             kohde-osoite
                                             {:teksti-tie? false})]
                              ["Tiemerkintä valmistunut" (fmt/pvm tiemerkinta-valmis)]
                              ["Ilmoittaja" ilmoittaja]
                              ["Ilmoittajan urakka" paallystysurakka-nimi]])]))

(defn sahkoposti-paallystysurakkaan-tiemerkinta-valmis
  [{:keys [fim email
           paallystysurakka-nimi kohde-nimi kohde-osoite
           tiemerkinta-valmis ilmoittaja
           tiemerkintaurakka-id
           paallystysurakka-sampo-id] :as tiedot}]
  (let [ilmoituksen-saajat (fim/hae-urakan-kayttajat-jotka-roolissa
                             fim
                             paallystysurakka-sampo-id
                             #{"ely urakanvalvoja" "urakan vastuuhenkilö"})]
    (if-not (empty? ilmoituksen-saajat)
      (doseq [henkilo ilmoituksen-saajat]
        (sahkoposti/laheta-viesti!
          email
          (sahkoposti/vastausosoite email)
          (:sahkoposti henkilo)
          (format "Harja: Kohteen '%s' tiemerkintä on valmistunut %s"
                  (or kohde-nimi (tierekisteri/tierekisteriosoite-tekstina kohde-osoite))
                  (fmt/pvm tiemerkinta-valmis))
          (viesti-tiemerkinta-valmis {:paallystysurakka-nimi paallystysurakka-nimi
                                           :kohde-nimi kohde-nimi
                                           :kohde-osoite kohde-osoite
                                           :tiemerkinta-valmis tiemerkinta-valmis
                                           :ilmoittaja ilmoittaja})))
      (log/warn (format "Tiemerkintäurakalle %s ei löydy FIM:stä henkiöä, jolle ilmoittaa kohteen valmiudesta tiemerkintään."
                        tiemerkintaurakka-id)))))
