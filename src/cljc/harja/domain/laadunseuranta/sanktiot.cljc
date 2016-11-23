(ns harja.domain.laadunseuranta.sanktiot)

(defn sakko? [sanktio]
  (not= :muistutus (:laji sanktio)))

(defn sakkoryhmasta-sakko? [sanktio]
  (not= :muistutus (:sakkoryhma sanktio)))

(defn paatos-on-sanktio? [sanktio]
  (= :sanktio (get-in sanktio [:paatos :paatos])))

(def +yllapidon-sanktiofraasit+
  (sort-by
    second
    [[:valitavoitteen-viivastyminen "Välitavoitteen viivästyminen"]
    [:laatusuunnitelman-vastainen-toiminta "Laatusuunnitelman vastainen toiminta työmaalla"]
    [:toistuva-laatusuunnitelman-vastainen-toiminta "Toistuva laatusuunnitelman vastainen toiminta työmaalla"]
    [:laadunvalvontaan-liittyvien-mittausten-ym-toimien-laiminlyonnit "Laadunvalvontaan liittyvien mittausten ym. toimien laiminlyönnit"]
    [:ymparistoasioihin-liittyvat-laiminlyonnit "Ympäristöasioihin liittyvät laiminlyönnit"]
    [:tilaajan-pistokoe-alitus-jota-ei-urakoitsijan-laatupoikkeamaraportissa "Tilaajan pistokokeella havaittu laadunalitukset, jota ei urakoitsijan laatupoikkeamaraportissa"]
    [:ei-poikkeamaraporttia-heti-poikkeaman-havaitsemisen-jalkeen "Laatupoikkeamasta ei tehty poikkeamaraporttia  havaitsemisen jälkeen, laatupoikkeama jäi seuraavassa työvaiheessa piiloon"]
    [:tyoskentelyaikaan-tai-kohteiden-yhtajaksoiseen-valmistumiseen-liittyvat-puutteet-tai-laiminlyonnit "Työskentelyaikaan tai kohteiden yhtäjaksoiseen valmistumiseen liittyvät puutteet tai laiminlyönnit"]
    [:urakoitsija-ei-ole-toimittanut-tyovaihekohtaista-laatusuunnitelmaa-ennen-tyon-aloittamista "Urakoitsija ei toimittanut työvaihekohtaista laatusuunnitelmaa ennen työn aloittamista"]
    [:tuotevaatimusten-vastainen-toiminta-joka-vaikuttaa-lopputuotteen-toimivuuteen "Tuotevaatimusten vastainen toiminta, joka vaikuttaa lopputuotteen toimivuuteen"]
    [:urakoitsijan-laatujarjestelman-mukaisessa-asiakirjassa-todennettavasti-tosiasioita-vastaamattomia-tietoja "Urakoitsijan laatujärjestelmän mukaisessa asiakirjassa todennettavasti on kirjattu tosiasioita vastaamattomia tietoja"]
    [:liikenteenhoitoon-tyonaikaisista-liikennejarjestelyista-tiedottamiseen-tai-tyoturvallisuuteen-liittyvat-puutteet-tai-laiminlyonnit "Liikenteenhoitoon, työnaikaisista liikennejärjestelyistä tiedottamiseen tai työturvallisuuteen liittyvät puutteet tai laiminlyönnit"]]))

(defn yllapidon-sanktiofraasin-nimi
  [fraasin-avain]
  (second
    (first (filter #(= (first %) (keyword fraasin-avain))
                   +yllapidon-sanktiofraasit+))))