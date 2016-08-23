(ns harja.views.ilmoituksen-tiedot
  (:require [harja.ui.yleiset :as yleiset]
            [harja.pvm :as pvm]
            [harja.ui.bootstrap :as bs]
            [clojure.string :refer [capitalize]]
            [harja.tiedot.ilmoitukset :as tiedot]
            [harja.domain.ilmoitukset
             :refer [+ilmoitustyypit+ ilmoitustyypin-nimi ilmoitustyypin-lyhenne-ja-nimi
                     +ilmoitustilat+ nayta-henkilo parsi-puhelinnumero
                     +ilmoitusten-selitteet+ parsi-selitteet kuittaustyypit
                     kuittaustyypin-selite]
             :as ilmoitukset]
            [harja.views.ilmoituskuittaukset :as kuittaukset]
            [harja.ui.ikonit :as ikonit]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.tierekisteri :as tr-domain]))

(defn selitelista [{:keys [selitteet] :as ilmoitus}]
  (let [virka-apu? (ilmoitukset/virka-apupyynto? ilmoitus)]
    [:div.selitelista.inline-block
     (when virka-apu?
       [:div.selite-virkaapu
        [ikonit/livicon-warning-sign] "Virka-apupyyntö"])
     (parsi-selitteet (filter #(not= % :virkaApupyynto) selitteet))]))

(defn ilmoitus [ilmoitus]
  [:div
   [bs/panel {}
    (ilmoitustyypin-nimi (:ilmoitustyyppi ilmoitus))
    [:span
     [yleiset/tietoja {}
      "Urakka: " (:urakkanimi ilmoitus)
      "Ilmoitettu: " (pvm/pvm-aika-sek (:ilmoitettu ilmoitus))
      "Yhteydenottopyyntö:" (if (:yhteydenottopyynto ilmoitus) "Kyllä" "Ei")
      "Sijainti: " (tr-domain/tierekisteriosoite-tekstina (:tr ilmoitus))
      "Otsikko: " (:otsikko ilmoitus)
      "Paikan kuvaus: " (:paikankuvaus ilmoitus)
      "Lisatieto:  " (when (:lisatieto ilmoitus)
                         [yleiset/pitka-teksti (:lisatieto ilmoitus)])
      "Selitteet: " [selitelista ilmoitus]]

     [:br]
     [yleiset/tietoja {}
      "Ilmoittaja:" (let [henkilo (nayta-henkilo (:ilmoittaja ilmoitus))
                          tyyppi (capitalize (name (get-in ilmoitus [:ilmoittaja :tyyppi])))]
                      (if (and henkilo tyyppi)
                        (str henkilo ", " tyyppi)
                        (str (or henkilo tyyppi))))
      "Puhelinnumero: " (parsi-puhelinnumero (:ilmoittaja ilmoitus))
      "Sähköposti: " (get-in ilmoitus [:ilmoittaja :sahkoposti])]

     [:br]
     [yleiset/tietoja {}
      "Lähettäjä:" (nayta-henkilo (:lahettaja ilmoitus))
      "Puhelinnumero: " (parsi-puhelinnumero (:lahettaja ilmoitus))
      "Sähköposti: " (get-in ilmoitus [:lahettaja :sahkoposti])]]]
   [:div.kuittaukset
    [:h3 "Kuittaukset"]
    [:div
     (comment
       ;; FIXME: implement
       (if @tiedot/uusi-kuittaus-auki?
         [kuittaukset/uusi-kuittaus-lomake]
         (when (oikeudet/voi-kirjoittaa? oikeudet/ilmoitukset-ilmoitukset
                                         (:id @nav/valittu-urakka))
           [:button.nappi-ensisijainen
            {:class    "uusi-kuittaus-nappi"
             :on-click #(do
                          (tiedot/avaa-uusi-kuittaus!)
                          (.preventDefault %))}
            (ikonit/livicon-plus) " Uusi kuittaus"])))

     (when-not (empty? (:kuittaukset ilmoitus))
       [:div
        (for [kuittaus (sort-by :kuitattu pvm/jalkeen? (:kuittaukset ilmoitus))]
          (kuittaukset/kuittauksen-tiedot kuittaus))])]]])
