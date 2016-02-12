(ns harja.ui.valinnat
  "Yleisiä valintoihin liittyviä komponentteja.
  Refaktoroitu vanhasta harja.views.urakka.valinnat namespacesta."
  (:require [reagent.core :refer [atom] :as r]

            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [livi-pudotusvalikko]]
            [harja.fmt :as fmt]
            [clojure.string :as str]
            [cljs-time.core :as t]))

(defn urakan-sopimus
  [ur valittu-sopimusnumero-atom valitse-fn]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Sopimusnumero"]
   [livi-pudotusvalikko {:valinta    @valittu-sopimusnumero-atom
                         :format-fn  second
                         :valitse-fn valitse-fn
                         :class      "suunnittelu-alasveto"
                         }
    (:sopimukset ur)]])

(defn urakan-hoitokausi
  [ur hoitokaudet valittu-hoitokausi-atom valitse-fn]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko (if (= :hoito (:tyyppi ur)) "Hoitokausi" "Sopimuskausi")]
   [livi-pudotusvalikko {:valinta    @valittu-hoitokausi-atom
                         :format-fn  #(if % (fmt/pvm-vali-opt %) "Valitse")
                         :valitse-fn valitse-fn
                         :class      "suunnittelu-alasveto"
                         }
    @hoitokaudet]])

(defn hoitokausi
  ([hoitokaudet valittu-hoitokausi-atom]
   (hoitokausi {} hoitokaudet valittu-hoitokausi-atom #(reset! valittu-hoitokausi-atom %)))
  ([hoitokaudet valittu-hoitokausi-atom valitse-fn]
   (hoitokausi {} hoitokaudet valittu-hoitokausi-atom valitse-fn))
  ([{:keys [disabled]} hoitokaudet valittu-hoitokausi-atom valitse-fn]
   [:div.label-ja-alasveto
    [:span.alasvedon-otsikko "Hoitokausi"]
    [livi-pudotusvalikko {:valinta    @valittu-hoitokausi-atom
                          :disabled disabled
                          :format-fn  #(if % (fmt/pvm-vali-opt %) "Valitse")
                          :valitse-fn valitse-fn
                          :class      "suunnittelu-alasveto"
                          }
     hoitokaudet]]))

(defn kuukausi [{:keys [disabled nil-valinta]} kuukaudet valittu-kuukausi-atom]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Kuukausi"]
   [livi-pudotusvalikko {:valinta    @valittu-kuukausi-atom
                         :disabled disabled
                         :format-fn  #(if %
                                       (let [[alkupvm _] %
                                             kk-teksti (pvm/kuukauden-nimi (pvm/kuukausi alkupvm))]
                                         (str (str/capitalize kk-teksti) " " (pvm/vuosi alkupvm)))
                                       (or nil-valinta "Kaikki"))
                         :valitse-fn #(reset! valittu-kuukausi-atom %)
                         :class      "suunnittelu-alasveto"
                         }
    kuukaudet]])

(defn hoitokauden-kuukausi
  [hoitokauden-kuukaudet valittu-kuukausi-atom valitse-fn]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Kuukausi"]
   [livi-pudotusvalikko {:valinta    @valittu-kuukausi-atom
                         :format-fn  #(if %
                                       (let [[alkupvm _] %
                                             kk-teksti (pvm/kuukauden-nimi (pvm/kuukausi alkupvm))]
                                         (str (str/capitalize kk-teksti) " " (pvm/vuosi alkupvm)))
                                       "Koko hoitokausi")
                         :valitse-fn valitse-fn
                         :class      "suunnittelu-alasveto"
                         }
    hoitokauden-kuukaudet]])

(defn urakan-hoitokausi-ja-kuukausi
  [ur
   hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn
   hoitokauden-kuukaudet valittu-kuukausi-atom valitse-kuukausi-fn]
  [:span
   [urakan-hoitokausi ur hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]
   [hoitokauden-kuukausi hoitokauden-kuukaudet valittu-kuukausi-atom valitse-kuukausi-fn]])

(defn aikavali
  ([valittu-aikavali-atom] (aikavali valittu-aikavali-atom nil))
  ([valittu-aikavali-atom {:keys [nayta-otsikko? aikavalin-rajoitus aloitusaika-pakota-suunta paattymisaika-pakota-suunta]}]
  [:span.label-ja-aikavali
   (when (or (nil? nayta-otsikko?)
             (true? nayta-otsikko?)) [:span.alasvedon-otsikko "Aikaväli"])
   [:div.aikavali-valinnat
    [tee-kentta {:tyyppi :pvm :pakota-suunta aloitusaika-pakota-suunta}
     (r/wrap (first @valittu-aikavali-atom)
             (fn [uusi-arvo]
               (let [uusi-arvo (pvm/paivan-alussa-opt uusi-arvo)]
                 (if-not aikavalin-rajoitus
                   ;; Varmista että alku ei ole lopun jälkeen
                   (swap! valittu-aikavali-atom #(pvm/varmista-aikavali [uusi-arvo (second %)] :alku))

                   (swap! valittu-aikavali-atom #(pvm/varmista-aikavali [uusi-arvo (second %)] aikavalin-rajoitus :alku))))
               (log "Uusi aikaväli: " (pr-str @valittu-aikavali-atom))))]
    [:div.pvm-valiviiva-wrap [:span.pvm-valiviiva " \u2014 "]]
    [tee-kentta {:tyyppi :pvm :pakota-suunta paattymisaika-pakota-suunta}
     (r/wrap (second @valittu-aikavali-atom)
             (fn [uusi-arvo]
               (let [uusi-arvo (pvm/paivan-lopussa-opt uusi-arvo)]
                 (if-not aikavalin-rajoitus
                   (swap! valittu-aikavali-atom #(pvm/varmista-aikavali [(first %) uusi-arvo] :loppu))

                   (swap! valittu-aikavali-atom #(pvm/varmista-aikavali [(first %) uusi-arvo] aikavalin-rajoitus :loppu))))
               (log "Uusi aikaväli: " (pr-str @valittu-aikavali-atom))))]]]))

(defn urakan-toimenpide
  [urakan-toimenpideinstanssit-atom valittu-toimenpideinstanssi-atom valitse-fn]
  (when (not (some
               #(= % @valittu-toimenpideinstanssi-atom)
               @urakan-toimenpideinstanssit-atom))
    ; Nykyisessä valintalistassa ei ole valittua arvoa, resetoidaan.
    (reset! valittu-toimenpideinstanssi-atom (first @urakan-toimenpideinstanssit-atom)))
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Toimenpide"]
   [livi-pudotusvalikko {:valinta    @valittu-toimenpideinstanssi-atom
                         :format-fn  #(if % (str (:tpi_nimi %)) "Ei toimenpidettä")
                         :valitse-fn valitse-fn}
    @urakan-toimenpideinstanssit-atom]])

(defn urakan-kokonaishintainen-tehtava
  [urakan-kokonaishintaiset-tehtavat-atom
   valittu-kokonaishintainen-tehtava-atom
   valitse-kokonaishintainen-tehtava-fn]
  [:span
   [:div.label-ja-alasveto
    [:span.alasvedon-otsikko "Tehtävä"]
    [livi-pudotusvalikko {:valinta    @valittu-kokonaishintainen-tehtava-atom
                          :format-fn  #(if % (str (:t4_nimi %)) "Ei tehtävää")
                          :valitse-fn valitse-kokonaishintainen-tehtava-fn}
     @urakan-kokonaishintaiset-tehtavat-atom]]])

(defn urakan-yksikkohintainen-tehtava
  [urakan-yksikkohintainen-tehtavat-atom
   valittu-yksikkohintainen-tehtava-atom
   valitse-yksikkohintainen-tehtava-fn]
  [:span
   [:div.label-ja-alasveto
    [:span.alasvedon-otsikko "Tehtävä"]
    [livi-pudotusvalikko {:valinta    @valittu-yksikkohintainen-tehtava-atom
                          :format-fn  #(if % (str (:nimi %)) "Ei tehtävää")
                          :valitse-fn valitse-yksikkohintainen-tehtava-fn}
     @urakan-yksikkohintainen-tehtavat-atom]]])

;; Parametreja näissä on melkoisen hurja määrä, mutta ei voi mitään
(defn urakan-sopimus-ja-hoitokausi
  [ur
   valittu-sopimusnumero-atom valitse-sopimus-fn            ;; urakan-sopimus
   hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn] ;; urakan-hoitokausi

  [:span
   [urakan-sopimus ur valittu-sopimusnumero-atom valitse-sopimus-fn]
   [urakan-hoitokausi ur hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]])

(defn urakan-sopimus-ja-toimenpide
  [ur
   valittu-sopimusnumero-atom valitse-sopimus-fn            ;; urakan-sopimus
   urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn] ;; urakan-toimenpide

  [:span
   [urakan-sopimus ur valittu-sopimusnumero-atom valitse-sopimus-fn]
   [urakan-toimenpide urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn]])



(defn urakan-sopimus-ja-hoitokausi-ja-toimenpide
  [ur
   valittu-sopimusnumero-atom valitse-sopimus-fn            ;; urakan-sopimus
   hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn ;; urakan-hoitokausi
   urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn] ;; urakan-toimenpide

  [:span
   [urakan-sopimus-ja-hoitokausi
    ur
    valittu-sopimusnumero-atom valitse-sopimus-fn
    hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]

   [urakan-toimenpide urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn]])

(defn urakan-hoitokausi-ja-toimenpide
  [ur
   hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn ;; urakan-hoitokausi
   urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn] ;; urakan-toimenpide]
  [:span
   [urakan-hoitokausi
    ur
    hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]

   [urakan-toimenpide
    urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn]])


(defn urakan-hoitokausi-ja-aikavali
  [ur
   hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn ;; urakan-hoitokausi
   valittu-aikavali-atom                                    ;; hoitokauden-aikavali
   ]

  [:span

   [urakan-hoitokausi
    ur
    hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]

   [aikavali valittu-aikavali-atom]])



(defn urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide
  [ur
   valittu-sopimusnumero-atom valitse-sopimus-fn            ;; urakan-sopimus
   hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn ;; urakan-hoitokausi
   valittu-aikavali-atom                                    ;; hoitokauden-aikavali
   urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn] ;; urakan-toimenpide

  [:span

   [urakan-sopimus-ja-hoitokausi
    ur
    valittu-sopimusnumero-atom valitse-sopimus-fn
    hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]

   [aikavali valittu-aikavali-atom]

   [urakan-toimenpide urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn]])

(defn vuosi
  ([ensimmainen-vuosi viimeinen-vuosi valittu-vuosi-atom]
   (vuosi {}
          ensimmainen-vuosi viimeinen-vuosi valittu-vuosi-atom
          #(reset! valittu-vuosi-atom %)))
  ([{:keys [disabled]} ensimmainen-vuosi viimeinen-vuosi valittu-vuosi-atom valitse-fn]
   [:span.label-ja-aikavali-lyhyt
   [:span.alasvedon-otsikko "Vuosi"]
    [livi-pudotusvalikko {:valinta @valittu-vuosi-atom
                          :disabled disabled
                          :valitse-fn valitse-fn
                          :format-fn #(if % (str %) "Valitse")
                          :class "alasveto-vuosi"}
    (range ensimmainen-vuosi (inc viimeinen-vuosi))]]))
