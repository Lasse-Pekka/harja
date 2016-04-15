(ns harja.palvelin.raportointi.raportit.sanktio
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.pvm :as pvm]
            [harja.domain.materiaali :as materiaalidomain]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [harja.kyselyt.konversio :as konv]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [clojure.string :as str]))

(defqueries "harja/palvelin/raportointi/raportit/sanktiot.sql")

(defn talvihoito? [kantarivi]
  (= (str/lower-case (:toimenpidekoodi_taso2 kantarivi)) "talvihoito"))

(defn rivien-maara-sakkoryhmalla [kantarivit sanktioryhma]
  (let [laskettavat (filter
                      (fn [rivi]
                        (= sanktioryhma (:sakkoryhma rivi)))
                      kantarivit)]
    (count laskettavat)))

(defn rivien-urakat [rivit]
  (-> (map (fn [rivi]
             {:id (:urakka_id rivi)
              :nimi (:urakka_nimi rivi)})
           rivit)
      distinct))

(defn urakan-rivit [rivit urakka-id]
  (filter
    (fn [rivi]
      (= (:urakka_id rivi) urakka-id))
    rivit))

(defn sanktiot-raportille [kantarivit]
  (let [;; Suodatetut rivit
        talvihoito-rivit (filter talvihoito? kantarivit)
        muut-tuotteet (filter (comp not talvihoito?) kantarivit)
        ryhma-c (filter #(= (:sakkoryhma %) :C) kantarivit)
        ;; Template rivit
        template-rivien-maara-sakkoryhmalla
        (fn [otsikko rivit sakkoryhma]
          (apply conj [otsikko "kpl"] (mapv (fn [urakka]
                                                     (rivien-maara-sakkoryhmalla
                                                       (urakan-rivit rivit (:id urakka))
                                                       sakkoryhma))
                                                   (rivien-urakat rivit))))]
    [{:otsikko "Talvihoito"}
     (template-rivien-maara-sakkoryhmalla "Muistutukset" talvihoito-rivit :muistutus)
     ["Sakko A" "€" 0]
     ["- Päätiet" "€" 0]
     ["- Muut tiet" "€" 0]
     ["Sakko B" "€" 0]
     ["- Päätiet" "€" 0]
     ["- Muut tiet" "€" 0]
     ["- Talvihoito, sakot yht." "€" 0]
     ["- Talvihoito, indeksit yht." "€" 0]
     {:otsikko "Muut tuotteet"}
     (template-rivien-maara-sakkoryhmalla "Muistutukset" muut-tuotteet :muistutus)
     ["Sakko A" "€" 0]
     ["- Liikenneymp. hoito" "€" 0]
     ["- Sorateiden hoito" "€" 0]
     ["Sakko B" "€" 0]
     ["- Liikenneymp. hoito" "€" 0]
     ["- Sorateiden hoito" "€" 0]
     ["- Muut tuotteet, sakot yht." "€" 0]
     ["- Muut tuotteet, indeksit yht." "€" 0]
     {:otsikko "Ryhmä C"}
     ["Ryhmä C, sakot yht." "€" 0]
     ["Ryhmä C, indeksit yht." "€" 0]
     {:otsikko "Yhteensä"}
     (template-rivien-maara-sakkoryhmalla "Muistutukset yht." kantarivit :muistutus)
     ["Indeksit yht." "€" 0]
     ["Kaikki sakot yht." "€" 0]
     ["Kaikki yht." "€" 0]]))

(defn suorita [db user {:keys [alkupvm loppupvm
                               urakka-id hallintayksikko-id
                               urakkatyyppi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        kantarivit (into []
                         (comp
                           (map #(konv/string->keyword % :sakkoryhma))
                           (map #(konv/array->set % :sanktiotyyppi_laji keyword)))
                         (hae-sanktiot db
                                       {:urakka urakka-id
                                        :hallintayksikko hallintayksikko-id
                                        :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                                        :alku alkupvm
                                        :loppu loppupvm}))
        raportin-otsikot (apply conj
                                [{:otsikko "" :leveys 10}
                                 {:otsikko "Yks." :leveys 3}]
                                (mapv
                                  (fn [urakka]
                                    {:otsikko (:nimi urakka) :leveys 20})
                                  (rivien-urakat kantarivit)))
        raporttidata (sanktiot-raportille kantarivit)
        raportin-nimi "Sanktioraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka  (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio
                                                    db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]

    [:raportti {:nimi raportin-nimi
                :orientaatio :landscape}
     [:taulukko {:otsikko otsikko}
      raportin-otsikot
      raporttidata]]))
