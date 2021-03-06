(ns harja.views.urakka.laadunseuranta.sanktiot
  "Sanktioiden listaus"
  (:require [reagent.core :refer [atom]]
            [harja.pvm :as pvm]
            [harja.views.urakka.valinnat :as urakka-valinnat]

            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.urakka.laadunseuranta.sanktiot :as tiedot]
            [harja.tiedot.navigaatio :as nav]

            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :as yleiset]

            [harja.loki :refer [log]]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.views.kartta :as kartta]
            [harja.tiedot.urakka.laadunseuranta.sanktiot :as sanktiot]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.ui.modal :as modal]
            [harja.ui.viesti :as viesti]
            [harja.fmt :as fmt]
            [harja.domain.yllapitokohde :as yllapitokohde-domain])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(defn sanktion-tiedot
  [optiot]
  (let [muokattu (atom @tiedot/valittu-sanktio)
        _ (log "muokattu sanktio: " (pr-str muokattu))
        voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-sanktiot
                                               (:id @nav/valittu-urakka))
        tallennus-kaynnissa (atom false)]
    (fn [optiot]
      (let [yllapitokohteet (conj (:yllapitokohteet optiot) {:id nil})
            mahdolliset-sanktiolajit @tiedot-urakka/urakkatyypin-sanktiolajit
            yllapito? (:yllapito? optiot)
            yllapitokohdeurakka? @tiedot-urakka/yllapitokohdeurakka?]
        [:div
         [napit/takaisin "Takaisin sanktioluetteloon" #(reset! tiedot/valittu-sanktio nil)]
         ;; Vaadi tarvittavat tiedot ennen rendausta
         (if (and mahdolliset-sanktiolajit
                  (or (not yllapitokohdeurakka?)
                      (and yllapitokohdeurakka? yllapitokohteet)))
           [lomake/lomake
            {:otsikko (if (:id @muokattu)
                        (if (:suorasanktio @muokattu)
                          "Muokkaa suoraa sanktiota"
                          "Muokkaa laatupoikkeaman kautta tehtyä sanktiota")
                        "Luo uusi suora sanktio")
             :luokka :horizontal
             :muokkaa! #(reset! muokattu %)
             :voi-muokata? voi-muokata?
             :footer-fn (fn [tarkastus]
                          [:span.nappiwrappi
                           [napit/palvelinkutsu-nappi
                            "Tallenna sanktio"
                            #(tiedot/tallenna-sanktio @muokattu (:id @nav/valittu-urakka))
                            {:luokka "nappi-ensisijainen"
                             :ikoni (ikonit/tallenna)
                             :kun-onnistuu #(reset! tiedot/valittu-sanktio nil)
                             :disabled (or (not voi-muokata?)
                                           (not (lomake/voi-tallentaa? tarkastus)))}]
                           (when (and voi-muokata? (:id @muokattu))
                             [:button.nappi-kielteinen
                              {:class (when @tallennus-kaynnissa "disabled")
                               :on-click
                               (fn [e]
                                 (.preventDefault e)
                                 (yleiset/varmista-kayttajalta
                                   {:otsikko "Sanktion poistaminen"
                                    :sisalto (str "Haluatko varmasti poistaa sanktion "
                                                  (or (str (:summa @muokattu) "€") "")
                                                  " päivämäärällä "
                                                  (pvm/pvm (:perintapvm @muokattu)) "?")
                                    :hyvaksy "Poista"
                                    :hyvaksy-ikoni (ikonit/livicon-trash)
                                    :hyvaksy-napin-luokka "nappi-kielteinen"
                                    :toiminto-fn #(do
                                                    (let [res (tiedot/tallenna-sanktio
                                                                (assoc @muokattu
                                                                  :poistettu true)
                                                                (:id @nav/valittu-urakka))]
                                                      (do (viesti/nayta! "Sanktio poistettu")
                                                          (reset! tiedot/valittu-sanktio nil))))}))}
                              (ikonit/livicon-trash) " Poista sanktio"])])}
            [{:otsikko "Tekijä" :nimi :tekijanimi
              :hae (comp :tekijanimi :laatupoikkeama)
              :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :tekijanimi] arvo))
              :leveys 1 :tyyppi :string
              :muokattava? (constantly false)}

             (lomake/ryhma {:rivi? true}
                           {:otsikko "Havaittu" :nimi :laatupoikkeamaaika
                            :pakollinen? true
                            :hae (comp :aika :laatupoikkeama)
                            :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :aika] arvo))
                            :fmt pvm/pvm-aika :leveys 1 :tyyppi :pvm
                            :validoi [[:ei-tyhja "Valitse päivämäärä"]]
                            :huomauta [[:urakan-aikana-ja-hoitokaudella]]}
                           {:otsikko "Käsitelty" :nimi :kasittelyaika
                            :pakollinen? true
                            :hae (comp :kasittelyaika :paatos :laatupoikkeama)
                            :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :kasittelyaika] arvo))
                            :fmt pvm/pvm-aika :leveys 1 :tyyppi :pvm
                            :validoi [[:ei-tyhja "Valitse päivämäärä"]
                                      [:pvm-kentan-jalkeen (comp :aika :laatupoikkeama) "Ei voi olla ennen havaintoa"]]}
                           {:otsikko "Perintäpvm" :nimi :perintapvm
                            :pakollinen? true
                            :fmt pvm/pvm-aika :leveys 1 :tyyppi :pvm
                            :validoi [[:ei-tyhja "Valitse päivämäärä"]
                                      [:pvm-kentan-jalkeen (comp :aika :laatupoikkeama)
                                       "Ei voi olla ennen havaintoa"]]})

             (if yllapitokohdeurakka?
               {:otsikko "Ylläpitokohde" :tyyppi :valinta :nimi :yllapitokohde
                :palstoja 1 :pakollinen? false :muokattava? (constantly voi-muokata?)
                :valinnat yllapitokohteet :jos-tyhja "Ei valittavia kohteita"
                :valinta-nayta (fn [arvo voi-muokata?]
                                 (if (:id arvo)
                                   (yllapitokohde-domain/yllapitokohde-tekstina
                                     arvo
                                     {:osoite {:tr-numero (:tr-numero arvo)
                                               :tr-alkuosa (:tr-alkuosa arvo)
                                               :tr-alkuetaisyys (:tr-alkuetaisyys arvo)
                                               :tr-loppuosa (:tr-loppuosa arvo)
                                               :tr-loppuetaisyys (:tr-loppuetaisyys arvo)}})
                                   (if (and voi-muokata? (not arvo))
                                     "- Valitse kohde -"
                                     (if (and voi-muokata? (nil? (:id arvo)))
                                       "Ei liity kohteeseen"
                                       ""))))}
               {:otsikko "Kohde" :tyyppi :string :nimi :kohde
                :hae (comp :kohde :laatupoikkeama)
                :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :kohde] arvo))
                :palstoja 1
                :pakollinen? true
                :muokattava? (constantly voi-muokata?)
                :validoi [[:ei-tyhja "Anna sanktion kohde"]]})

             {:otsikko "Käsitelty" :nimi :kasittelytapa
              :pakollinen? true
              :hae (comp :kasittelytapa :paatos :laatupoikkeama)
              :aseta #(assoc-in %1 [:laatupoikkeama :paatos :kasittelytapa] %2)
              :tyyppi :valinta
              :valinnat [:tyomaakokous :puhelin :kommentit :muu]
              :valinta-nayta #(if % (case %
                                      :tyomaakokous "Työmaakokous"
                                      :puhelin "Puhelimitse"
                                      :kommentit "Harja-kommenttien perusteella"
                                      :muu "Muu tapa"
                                      nil) "- valitse käsittelytapa -")
              :palstoja 1}

             (when yllapito?
               {:otsikko "Puute tai laiminlyönti"
                :nimi :vakiofraasi
                :tyyppi :valinta
                :valinta-arvo first
                :valinta-nayta second
                :valinnat sanktio-domain/+yllapidon-sanktiofraasit+
                :palstoja 2}
               )
             {:otsikko "Perustelu" :nimi :perustelu
              :pakollinen? true
              :hae (comp :perustelu :paatos :laatupoikkeama)
              :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :perustelu] arvo))
              :palstoja 2 :tyyppi :text :koko [80 :auto]
              :validoi [[:ei-tyhja "Anna perustelu"]]}

             (when (= :muu (get-in @muokattu [:laatupoikkeama :paatos :kasittelytapa]))
               {:otsikko "Muu käsittelytapa" :nimi :muukasittelytapa
                :hae (comp :muukasittelytapa :paatos :laatupoikkeama)
                :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :muukasittelytapa] arvo))
                :leveys 2 :tyyppi :string
                :validoi [[:ei-tyhja "Anna lyhyt kuvaus käsittelytavasta."]]})

             {:otsikko "Laji" :tyyppi :valinta :pakollinen? true
              :palstoja 1 :uusi-rivi? true :nimi :laji
              :hae (comp keyword :laji)
              :aseta (fn [rivi arvo]
                       (let [paivitetty (assoc rivi :laji arvo :tyyppi nil)]
                         (if-not (sanktio-domain/sakko? paivitetty)
                           (assoc paivitetty :summa nil :toimenpideinstanssi nil :indeksi nil)
                           paivitetty)))
              :valinnat mahdolliset-sanktiolajit
              :valinta-nayta #(case %
                                :A "Ryhmä A"
                                :B "Ryhmä B"
                                :C "Ryhmä C"
                                :muistutus "Muistutus"
                                :yllapidon_muistutus "Muistutus"
                                :yllapidon_sakko "Sakko"
                                :yllapidon_bonus "Bonus"
                                "- valitse laji -")
              :validoi [[:ei-tyhja "Valitse laji"]]}

             (when-not yllapito?
               {:otsikko "Tyyppi" :tyyppi :valinta
                :palstoja 1
                :pakollinen? true
                :nimi :tyyppi
                :aseta (fn [sanktio {tpk :toimenpidekoodi :as tyyppi}]
                         (assoc sanktio
                           :tyyppi tyyppi
                           :toimenpideinstanssi
                           (when tpk
                             (:tpi_id (tiedot-urakka/urakan-toimenpideinstanssi-toimenpidekoodille tpk)))))
                ;; TODO: Kysely ei palauta sanktiotyyppien lajeja, joten tässä se pitää dissocata. Onko ok? Laatupoikkeamassa käytetään.
                :valinnat-fn (fn [_] (map #(dissoc % :laji) (sanktiot/lajin-sanktiotyypit (:laji @muokattu))))
                :valinta-nayta #(if % (:nimi %) " - valitse tyyppi -")
                :validoi [[:ei-tyhja "Valitse sanktiotyyppi"]]})

             (when (sanktio-domain/sakko? @muokattu)
               {:otsikko "Summa" :nimi :summa :palstoja 1 :tyyppi :positiivinen-numero
                :hae #(when (:summa %) (Math/abs (:summa %)))
                :pakollinen? true :uusi-rivi? true :yksikko "€"
                :validoi [[:ei-tyhja "Anna summa"] [:rajattu-numero 0 999999999 "Anna arvo väliltä 0 - 999 999 999"]]})

             (when (and (sanktio-domain/sakko? @muokattu) (urakka/indeksi-kaytossa?))
               {:otsikko "Indeksi" :nimi :indeksi :leveys 2
                :tyyppi :valinta
                :valinnat ["MAKU 2005" "MAKU 2010"]
                :valinta-nayta #(or % "Ei sidota indeksiin")
                :palstoja 1})

             (when (and (sanktio-domain/sakko? @muokattu))
               {:otsikko "Toimenpide"
                :pakollinen? true
                :nimi :toimenpideinstanssi
                :tyyppi :valinta
                :valinta-arvo :tpi_id
                :valinta-nayta #(if % (:tpi_nimi %) " - valitse toimenpide -")
                :valinnat @tiedot-urakka/urakan-toimenpideinstanssit
                :palstoja 1
                :validoi [[:ei-tyhja "Valitse toimenpide, johon sanktio liittyy"]]})]
            @muokattu]
           [ajax-loader "Ladataan..."])]))))

(defn sanktiolistaus
  [optiot valittu-urakka]
  (let [sanktiot (reverse (sort-by :perintapvm @tiedot/haetut-sanktiot))
        yllapito? (:yllapito? optiot)
        yhteensa (reduce + (map :summa sanktiot))
        yhteensa (when yhteensa
                   (if yllapito?
                     (- yhteensa) ; ylläpidossa sakot miinusmerkkisiä
                     yhteensa))
        yllapitokohdeurakka? @tiedot-urakka/yllapitokohdeurakka?]
    [:div.sanktiot
     [urakka-valinnat/urakan-hoitokausi valittu-urakka]
     (let [oikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-sanktiot
                                             (:id valittu-urakka))]
       (yleiset/wrap-if
         (not oikeus?)
         [yleiset/tooltip {} :%
          (oikeudet/oikeuden-puute-kuvaus :kirjoitus
                                          oikeudet/urakat-laadunseuranta-sanktiot)]
         [napit/uusi "Lisää sanktio"
          #(reset! tiedot/valittu-sanktio (tiedot/uusi-sanktio (:tyyppi valittu-urakka)))
          {:disabled (not oikeus?)}]))

     [grid/grid
      {:otsikko (if yllapito? "Sakot ja bonukset" "Sanktiot")
       :tyhja (if @tiedot/haetut-sanktiot "Ei löytyneitä tietoja" [ajax-loader "Haetaan sanktioita."])
       :rivi-klikattu #(reset! tiedot/valittu-sanktio %)}
      [{:otsikko "Päivä\u00ADmäärä" :nimi :perintapvm :fmt pvm/pvm-aika :leveys 1}
       (if yllapitokohdeurakka?
         {:otsikko "Yllä\u00ADpito\u00ADkoh\u00ADde" :nimi :kohde :leveys 2
          :hae (fn [rivi]
                 (if (get-in rivi [:yllapitokohde :id])
                   (yllapitokohde-domain/yllapitokohde-tekstina {:kohdenumero (get-in rivi [:yllapitokohde :numero])
                                                                 :nimi (get-in rivi [:yllapitokohde :nimi])})
                   "Ei liity kohteeseen"))}
         {:otsikko "Kohde" :nimi :kohde :hae (comp :kohde :laatupoikkeama) :leveys 1})
       {:otsikko "Perus\u00ADtelu" :nimi :kuvaus :hae (comp :perustelu :paatos :laatupoikkeama) :leveys 3}
       (if yllapito?
         {:otsikko "Puute tai laiminlyönti" :nimi :vakiofraasi
          :hae #(sanktio-domain/yllapidon-sanktiofraasin-nimi (:vakiofraasi %)) :leveys 3}
         {:otsikko "Tyyppi" :nimi :sanktiotyyppi :hae (comp :nimi :tyyppi) :leveys 3})
       {:otsikko "Tekijä" :nimi :tekija :hae (comp :tekijanimi :laatupoikkeama) :leveys 1}
       {:otsikko "Summa €" :nimi :summa :leveys 1 :tyyppi :numero :tasaa :oikea
        :hae #(or (when (:summa %)
                    (if yllapito?
                      (- (:summa %)) ;ylläpidossa on sakkoja ja -bonuksia, sakot miinusmerkillä
                      (:summa %)))
                  "Muistutus")}]
      sanktiot]
     (when yllapito?
       (yleiset/vihje "Huom! Sakot ovat miinusmerkkisiä ja bonukset plusmerkkisiä."))
     (when (> (count sanktiot) 0)
       [:div.pull-right.bold (str "Yhteensä " (fmt/euro-opt yhteensa))])]))

(defn sanktiot [optiot]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan-ulos #(do
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (nav/vaihda-kartan-koko! :S))
                      #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))
    (fn []
      [:span
       [kartta/kartan-paikka]
       (let [optiot (merge optiot
                           {:yllapitokohteet @laadunseuranta/urakan-yllapitokohteet-lomakkeelle
                            :yllapito? @tiedot-urakka/yllapidon-urakka?})]
         (if @tiedot/valittu-sanktio
           [sanktion-tiedot optiot]
           [sanktiolistaus optiot @nav/valittu-urakka]))])))
