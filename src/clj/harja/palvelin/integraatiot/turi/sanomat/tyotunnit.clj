(ns harja.palvelin.integraatiot.turi.sanomat.tyotunnit
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml])
  (:use [slingshot.slingshot :only [throw+]]))

(defn vaylamuoto [urakka-tyyppi]
  (if (.contains (name urakka-tyyppi) "vesivayla")
    "Vesiväylä"
    "Tie"))

(defn tyojakso [{:keys [hanke-nimi
                        hanke-sampoid
                        tilaajanvastuuhenkilo-kayttajatunnus
                        tilaajanvastuuhenkilo-etunimi
                        tilaajanvastuuhenkilo-sukunimi
                        tilaajanvastuuhenkilo-sposti
                        urakka-nimi
                        urakka-sampoid
                        urakka-loppupvm
                        urakka-tyyppi
                        urakka-ely
                        alueurakkanro]}
                vuosi
                vuosikolmannes
                tunnit]
  [:tyot:tyoaikajakso
   {:xmlns:tyot "http://restimport.xml.turi.oikeatoliot.fi/tyotunnit"}
   [:sampohankenimi hanke-nimi]
   [:sampohankeid hanke-sampoid]
   [:tilaajanvastuuhenkilokayttajatunnus tilaajanvastuuhenkilo-kayttajatunnus]
   [:tilaajanvastuuhenkiloetunimi tilaajanvastuuhenkilo-etunimi]
   [:tilaajanvastuuhenkilosukunimi tilaajanvastuuhenkilo-sukunimi]
   [:tilaajanvastuuhenkilosposti tilaajanvastuuhenkilo-sposti]
   [:sampourakkanimi urakka-nimi]
   [:sampourakkaid urakka-sampoid]
   [:urakanpaattymispvm (xml/formatoi-paivamaara urakka-loppupvm)]
   [:urakkavaylamuoto (vaylamuoto urakka-tyyppi)]
   [:urakkatyyppi urakka-tyyppi]
   (when urakka-ely
     [:elyalue (str urakka-ely " ELY")])
   [:alueurakkanro alueurakkanro]
   [:lahdejarjestelma "Harja"]
   [:vuosi vuosi]
   [:vuosikolmannes vuosikolmannes]
   [:tyotunnit tunnit]])

(defn muodosta [urakka vuosi vuosikolmannes tunnit]
  (let [sisalto (tyojakso urakka vuosi vuosikolmannes tunnit)
        xml (xml/tee-xml-sanoma sisalto)]
    (if-let [virheet (xml/validoi-xml "xsd/turi/" "tyotunnit-rest.xsd" xml)]
      (let [virheviesti (format "Työtuntien TURI-lähetyksen XML ei ole validia.\n
                                 Validointivirheet: %s\n
                                 Muodostettu sanoma:\n
                                 %s" virheet xml)]
        (log/error virheviesti)
        (throw+ {:type :invalidi-tyotunti-xml
                 :error virheviesti}))
      xml)))
