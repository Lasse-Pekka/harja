(ns harja.kyselyt.tietyoilmoitukset
  (:require [jeesql.core :refer [defqueries]]
            [harja.domain.tietyoilmoitukset :as t]
            [harja.domain.tierekisteri :as tr]
            [specql.core :refer [define-tables fetch]]
            [specql.op :as op]
            [specql.rel :as rel]
            [clojure.spec :as s]
            [harja.kyselyt.specql :refer [db]]))

(defqueries "harja/kyselyt/tietyoilmoitukset.sql")

(define-tables db
  ["tr_osoite" ::tr/tr-osoite]
  ["tietyon_henkilo" ::t/henkilo]
  ["tietyon_ajoneuvorajoitukset" ::t/ajoneuvorajoitukset*]
  ["tietyotyyppi" ::t/tietyotyyppi]
  ["tietyon_tyypin_kuvaus" ::t/tietyon-tyyppi]
  ["viikonpaiva" ::t/viikonpaiva]
  ["tietyon_tyoaika" ::t/tyoaika]
  ["tietyon_vaikutussuunta" ::t/vaikutussuunta*]
  ["tietyon_kaistajarjestelytyyppi" ::t/kaistajarjestelytyyppi]
  ["tietyon_kaistajarjestelyt" ::t/kaistajarjestelyt*]
  ["nopeusrajoitus" ::t/nopeusrajoitusvalinta]
  ["tietyon_nopeusrajoitus" ::t/nopeusrajoitus]
  ["tietyon_pintamateriaalit" ::t/pintamateriaalit]
  ["tietyon_tienpinta" ::t/tien-pinta]
  ["tietyon_mutkat" ::t/mutkat]
  ["tietyon_liikenteenohjaaja" ::t/liikenteenohjaaja*]
  ["tietyon_huomautukset" ::t/huomautustyypit]
  ["tietyoilmoitus" ::t/ilmoitus
   {::t/paailmoitus (rel/has-one ::t/paatietyoilmoitus
                                 ::t/ilmoitus
                                 ::t/id)
    ::t/tyovaiheet (rel/has-many ::t/id
                                 ::t/ilmoitus
                                 ::t/paatietyoilmoitus)}]
  ["tietyoilmoitus_pituus" ::t/ilmoitus+pituus
   {::t/paailmoitus (rel/has-one ::t/paatietyoilmoitus
                                 ::t/ilmoitus+pituus
                                 ::t/id)}]
  )

(def kaikki-ilmoituksen-kentat
  #{::t/id
    ::t/tloik-id
    ::t/paatietyoilmoitus
    ::t/tloik-paatietyoilmoitus-id
    ::t/luotu
    ::t/luoja
    ::t/muokattu
    ::t/muokkaaja
    ::t/poistettu
    ::t/poistaja
    ::t/ilmoittaja-id
    ::t/ilmoittaja
    ::t/urakka-id
    ::t/urakan-nimi
    ::t/urakkatyyppi
    ::t/urakoitsijan-nimi
    ::t/urakoitsijayhteyshenkilo-id
    ::t/urakoitsijayhteyshenkilo
    ::t/tilaaja-id
    ::t/tilaajan-nimi
    ::t/tilaajayhteyshenkilo-id
    ::t/tilaajayhteyshenkilo
    ::t/tyotyypit
    ::t/luvan-diaarinumero
    ::t/osoite
    ::t/tien-nimi
    ::t/kunnat
    ::t/alkusijainnin-kuvaus
    ::t/loppusijainnin-kuvaus
    ::t/alku
    ::t/loppu
    ::t/tyoajat
    ::t/vaikutussuunta
    ::t/kaistajarjestelyt
    ::t/nopeusrajoitukset
    ::t/tienpinnat
    ::t/kiertotien-mutkaisuus
    ::t/kiertotienpinnat
    ::t/liikenteenohjaus
    ::t/liikenteenohjaaja
    ::t/viivastys-normaali-liikenteessa
    ::t/viivastys-ruuhka-aikana
    ::t/ajoneuvorajoitukset
    ::t/huomautukset
    ::t/ajoittaiset-pysatykset
    ::t/ajoittain-suljettu-tie
    ::t/pysaytysten-alku
    ::t/pysaytysten-loppu
    ::t/lisatietoja})

;; Hakee pääilmoituksen kaikki kentät, sekä siihen liittyvät työvaiheet
(def kaikki-ilmoituksen-kentat-ja-tyovaiheet
  (conj kaikki-ilmoituksen-kentat
        [::t/tyovaiheet kaikki-ilmoituksen-kentat]))

(def ilmoitus-pdf-kentat
  (conj kaikki-ilmoituksen-kentat
        ::t/pituus
        [::t/paailmoitus (conj kaikki-ilmoituksen-kentat ::t/pituus)]))

(def ilmoituslomakkeen-kentat
  #{[::t/paailmoitus #{::t/urakan-nimi ::t/urakoitsijayhteyshenkilo ::t/tilaajayhteyshenkilo
                       ::t/ilmoittaja
                       ::t/osoite ::t/tien-nimi ::t/kunnat
                       ::t/alkusijainnin-kuvaus ::t/loppusijainnin-kuvaus
                       ::t/tyoajat
                       ::t/alku ::t/loppu}]
    ::t/urakka-id
    ::t/urakan-nimi
    ::t/ilmoittaja
    ::t/urakoitsijayhteyshenkilo
    ::t/tilaajayhteyshenkilo
    ::t/tyotyypit
    ::t/luvan-diaarinumero
    ::t/osoite
    ::t/tien-nimi
    ::t/kunnat
    ::t/alkusijainnin-kuvaus
    ::t/loppusijainnin-kuvaus
    ::t/alku
    ::t/loppu
    ::t/tyoajat
    ::t/vaikutussuunta
    ::t/kaistajarjestelyt
    ::t/nopeusrajoitukset
    ::t/tienpinnat
    ::t/kiertotien-mutkaisuus
    ::t/kiertotienpinnat
    ::t/liikenteenohjaus
    ::t/liikenteenohjaaja
    ::t/viivastys-normaali-liikenteessa
    ::t/viivastys-ruuhka-aikana
    ::t/ajoneuvorajoitukset
    ::t/huomautukset
    ::t/ajoittaiset-pysatykset
    ::t/ajoittain-suljettu-tie
    ::t/pysaytysten-alku
    ::t/pysaytysten-loppu
    ::t/lisatietoja})

(defn intersects? [threshold geometry]
  (reify op/Op
    (to-sql [this value-accessor]
      [(str "ST_Intersects(ST_Buffer(?,?), " value-accessor ")")
       [geometry threshold]])))

(defn hae-ilmoitukset [db {:keys [luotu-alku
                                  luotu-loppu
                                  kaynnissa-alku
                                  kaynnissa-loppu
                                  urakat
                                  organisaatio
                                  kayttaja-id
                                  sijainti]}]
  (fetch db ::t/ilmoitus kaikki-ilmoituksen-kentat-ja-tyovaiheet
         (op/and
           (merge {::t/paatietyoilmoitus op/null?}
                  (when (and luotu-alku luotu-loppu)
                    {::t/luotu (op/between luotu-alku luotu-loppu)})
                  (when kayttaja-id
                    {::t/luoja kayttaja-id})
                  (when sijainti
                    {::t/osoite {::t/geometria (intersects? 100 sijainti)}}))
           (if (and kaynnissa-alku kaynnissa-loppu)
             (op/and {::t/alku (op/<= kaynnissa-alku)}
                     {::t/loppu (op/>= kaynnissa-loppu)})
             {::t/id op/not-null?})
           (if organisaatio
             (op/or
               {::t/urakka-id (op/or op/null? (op/in urakat))}
               {::t/urakoitsija-id organisaatio})
             {::t/urakka-id (op/or op/null? (op/in urakat))}))))
