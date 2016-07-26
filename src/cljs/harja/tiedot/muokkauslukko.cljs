(ns harja.tiedot.muokkauslukko
  "Geneerisen muokkauslukon hallinta"
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.istunto :as istunto]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.loki :refer [tarkkaile!]]
            [harja.loki :refer [log tarkkaile!]]

            [cljs.core.async :refer [chan <! >! close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

; Kun tietyn näkymän lukkoa pyydetään, se asetetaan tähän atomiin.
; Oletetaan, että käyttäjä voi lukita vain yhden näkymän kerrallaan.
(def nykyinen-lukko (atom nil))
(def pollaus-kaynnissa (atom false))

(declare paivita-lukko)

(tarkkaile! "[LUKKO] Nykyinen lukko: " nykyinen-lukko)

(defn- kayttaja-omistaa-lukon? [lukko]
  (= (:kayttaja lukko) (:id @istunto/kayttaja)))

(defn nakyma-lukittu? [lukko]
  (if (nil? lukko)
    (do
      (log "[LUKKO] Nykyistä lukkoa ei ole. Näkymä ei ole lukittu.")
      false)
    (do
      (let [kayttajan-oma-lukko (kayttaja-omistaa-lukon? lukko)]
        (log (str "[LUKKO] Nykyinen käyttäjä " (:id @istunto/kayttaja)))
        (log (str "[LUKKO] Nykyinen lukko " (pr-str lukko)))
        (log (str "[LUKKO] Nykyisen lukon omistaja " (:kayttaja lukko)))
        (log "[LUKKO] Käyttäjä omistaa lukon: " kayttajan-oma-lukko)
        (false? kayttajan-oma-lukko)))))

(defn nykyinen-nakyma-lukittu? []
  (nakyma-lukittu? @nykyinen-lukko))

(defn muodosta-lukon-id
  "Ottaa näkymän ja item-id:n, joilla muodostetaan lukon id.
  nakyma Näkymän nimi, joka halutaan lukita. Esim. paallystysilmoitus.
  item-id Vapaaehtoinen lukittavan itemin id (tämä on sama id jolla item yksilöidään tietokannassa)."
  ([nakyma]
   nakyma)
  ([nakyma item-id]
   (str nakyma "_" item-id)))

(defn- hae-lukko-idlla [lukko-id]
  (log "[LUKKO] Haetaan lukko id:llä: " lukko-id)
  (k/post! :hae-lukko-idlla {:id lukko-id}))

(defn- lukitse
  "Merkitsee tietyn näkymän lukituksi, tarkoituksena että vain näkymän lukinnut käyttäjä voi muokata sitä."
  [id]
  (k/post! :lukitse {:id id}))

(defn- virkista-nykyinen-lukko [lukko-id]
  (go
    (log "[LUKKO] Virkistetään nykyinen lukko")
    (reset! nykyinen-lukko (<! (k/post! :virkista-lukko {:id lukko-id})))))

(defn vapauta-lukko [lukko-id]
  (log "[LUKKO] Vapautetaan nykyinen lukko")
  (if (kayttaja-omistaa-lukon? @nykyinen-lukko)
    (k/post! :vapauta-lukko {:id lukko-id}))
  (reset! nykyinen-lukko nil)
  (log "[LUKKO] Lukko vapautettu. Uusi lukon tila: " (pr-str @nykyinen-lukko)))

(defn- pollaa []
  (if @nykyinen-lukko
    (do
      (log "[LUKKO] Suoritetaan pollaus nykyiselle lukolle: " (pr-str @nykyinen-lukko))
      (let [lukko-id (:id @nykyinen-lukko)]
        (if (kayttaja-omistaa-lukon? @nykyinen-lukko)
          (virkista-nykyinen-lukko lukko-id)
          #_(paivita-lukko lukko-id))))
          ; Lukkoa itselleen odottava käyttäjä voisi päivittää lukon tiedot ja jos lukkoa ei ole, lukita näkymän itselleen.
          ; Tällöin pitäisi kuitenkin päivittää näkymään uudet tiedot. Tämä vaatisi tiedon siitä, että lukko vapautui ja
          ; näkymä voidaan päivittää.
    (log "[LUKKO] Ei nykyistä lukkoa, ei pollata")))

(defn- aloita-pollaus []
  (if (false? @pollaus-kaynnissa)
    (go
      (reset! pollaus-kaynnissa true)
      (loop []
        (<! (timeout 50000))
        (if (not (nil? @nykyinen-lukko))
          (do
            (pollaa)
            (recur))
          (do
            (log "[LUKKO] Lopetetaan muokkauslukon pollaus")
            (reset! pollaus-kaynnissa false)))))))

(defn paivita-lukko
  "Hakee lukon kannasta valitulla id:lla. Jos sitä ei ole, luo uuden."
  [lukko-id]
  (log "[LUKKO] Päivitetään lukko")
  (go (log "[LUKKO] Tarkistetaan lukon " lukko-id " tila tietokannasta")
      (let [vanha-lukko (<! (hae-lukko-idlla lukko-id))]
        (if vanha-lukko
          (do
            (log "[LUKKO] Vanha lukko löytyi: " (pr-str vanha-lukko))
            (reset! nykyinen-lukko vanha-lukko)
            (aloita-pollaus))
          (do
            (log "[LUKKO] Annetulla id:llä ei ole lukkoa. Lukitaan näkymä.")
            (let [uusi-lukko (<! (lukitse lukko-id))]
              (if uusi-lukko
                (do
                  (log "[LUKKO] Näkymä lukittu. Lukon tiedot: " (pr-str @nykyinen-lukko))
                  (reset! nykyinen-lukko uusi-lukko)
                  (aloita-pollaus))
                (do (log "[LUKKO] Lukitus epäonnistui, ilmeisesti joku muu ehti lukita näkymän!")
                    (paivita-lukko lukko-id)))))))))
