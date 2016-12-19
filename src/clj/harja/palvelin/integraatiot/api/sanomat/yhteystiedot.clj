(ns harja.palvelin.integraatiot.api.sanomat.yhteystiedot)

(def fim-roolit
  #{"Urakan vastuuhenkilö"
    "ELY urakanvalvoja"
    "ELY laadunvalvoja"
    "ELY turvallisuusvastaava"
    "Tilaajan asiantuntija"
    "Tilaajan urakanvalvoja"
    "Tilaajan laadunvalvoja"
    "Tilaajan turvallisuusvastaava"
    "Paivystaja"
    "Laatupaallikko"
    "Kelikeskus"
    "Laadunvalvoja"})

(def harja-roolit
  #{"Sampo yhteyshenkilö"
    "Kunnossapitopäällikkö"
    "Sillanvalvoja"
    "Kelikeskus"
    "Tieliikennekeskus"})

(defn tee-yhteyshenkilo [rooli etunimi sukunimi puhelin sahkoposti organisaatio]
  {:yhteyshenkilo
   {:rooli rooli
    :nimi (str etunimi " " sukunimi)
    :puhelinnumero puhelin
    :email sahkoposti
    :organisaatio organisaatio}})

(defn yhteyshenkilot-fimissa [yhteyshenkilot]
  (let [yhteyshenkilot (filter (fn [k] some #(contains? fim-roolit %) (:roolit k)) yhteyshenkilot)]
    (apply concat
           (map
             (fn [rooli]
               (map
                 (fn [{:keys [etunimi sukunimi puhelin sahkoposti organisaatio]}]
                   (tee-yhteyshenkilo rooli etunimi sukunimi puhelin sahkoposti organisaatio))
                 (filter (fn [y] (some #(= % rooli) (:roolit y))) yhteyshenkilot)))
             fim-roolit))))

(defn yhteyshenkilot-harjassa [yhteyshenkilot]
  (let [yhteyshenkilot (filter #(contains? harja-roolit (:rooli %)) yhteyshenkilot)]
    (map
      (fn [{:keys [rooli etunimi sukunimi matkapuhelin tyopuhelin sahkoposti organisaatio_nimi]}]
        (let [puhelin (or matkapuhelin tyopuhelin)]
          (tee-yhteyshenkilo rooli etunimi sukunimi puhelin sahkoposti organisaatio_nimi)))
      yhteyshenkilot)))

(defn vastuuhenkilot-harjassa [yhteyshenkilot]
  (let [yhteyshenkilot (filter #(contains? harja-roolit (:rooli %)) yhteyshenkilot)]
    (map
      (fn [{:keys [rooli nimi puhelin sahkoposti organisaatio_nimi]}]
        (let [puhelin (or matkapuhelin tyopuhelin)]
          (tee-yhteyshenkilo rooli etunimi sukunimi puhelin sahkoposti organisaatio_nimi)))
      yhteyshenkilot)))

(defn yhteyshenkilot [fim-yhteyshenkilot harja-yhteyshenkilot harjan-vastuuhenkilot]
  (vec (concat (yhteyshenkilot-fimissa fim-yhteyshenkilot)
               (yhteyshenkilot-harjassa harja-yhteyshenkilot)
               (vastuuhenkilot-harjassa harjan-vastuuhenkilot))))

(defn urakan-yhteystiedot [{:keys [urakkanro
                                   elynumero
                                   elynimi
                                   nimi
                                   sampoid
                                   alkupvm
                                   loppupvm
                                   urakoitsija-ytunnus
                                   urakoitsija-katuosoite
                                   urakoitsija-postinumero
                                   urakoitsija-nimi]}
                           fim-yhteyshenkilot
                           harja-yhteyshenkilot
                           harjan-vastuuhenkilot]

  (let [yhteyshenkilot (yhteyshenkilot fim-yhteyshenkilot harja-yhteyshenkilot harjan-vastuuhenkilot)]
    {:urakka {
              :alueurakkanro urakkanro
              :elynro elynumero
              :elynimi elynimi
              :nimi nimi
              :sampoid sampoid
              :alkupvm alkupvm
              :loppupvm loppupvm
              :urakoitsija
              {:nimi urakoitsija-nimi
               :ytunnus urakoitsija-ytunnus
               :katuosoite urakoitsija-katuosoite
               :postinumero urakoitsija-postinumero}
              :yhteyshenkilot yhteyshenkilot}}))
