(ns harja.views.urakka.aikataulu
  "Ylläpidon urakoiden aikataulunäkymä"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.aikataulu :as tiedot]
            [harja.ui.grid :as grid]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :refer [vihje]]
            [harja.ui.lomake :as lomake]
            [cljs-time.core :as t]
            [harja.ui.napit :as napit]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.paallystysilmoitus :as pot]
            [harja.ui.yleiset :as yleiset]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.tiedot.urakka.yllapito :as yllapito-tiedot]
            [harja.ui.viesti :as viesti]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.aikajana :as aikajana]
            [harja.domain.aikataulu :as aikataulu]
            [harja.ui.upotettu-raportti :as upotettu-raportti]
            [harja.tiedot.raportit :as raportit]
            [harja.ui.kentat :as kentat])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn valmis-tiemerkintaan [{:keys [kohde-id urakka-id vuosi paallystys-valmis? suorittava-urakka-annettu?]}]
  (let [valmis-tiemerkintaan-lomake (atom nil)
        valmis-tallennettavaksi? (reaction (some? (:valmis-tiemerkintaan @valmis-tiemerkintaan-lomake)))]
    (fn [{:keys [kohde-id urakka-id paallystys-valmis? suorittava-urakka-annettu?]}]
      [:div
       {:title (cond (not paallystys-valmis?) "Päällystys ei ole valmis."
                     (not suorittava-urakka-annettu?) "Tiemerkinnän suorittava urakka puuttuu."
                     :default nil)}
       [:button.nappi-ensisijainen.nappi-grid
        {:type "button"
         :disabled (or (not paallystys-valmis?)
                       (not suorittava-urakka-annettu?))
         :on-click
         (fn []
           (modal/nayta!
             {:otsikko "Kohteen merkitseminen valmiiksi tiemerkintään"
              :luokka "merkitse-valmiiksi-tiemerkintaan"
              :sulje-fn #(reset! valmis-tiemerkintaan-lomake nil) ; FIXME ei toimi?
              :footer [:div
                       [:span [:button.nappi-toissijainen
                               {:type "button"
                                :on-click #(do (.preventDefault %)
                                               (reset! valmis-tiemerkintaan-lomake nil)
                                               (modal/piilota!))}
                               "Peruuta"]
                        [napit/palvelinkutsu-nappi
                         "Merkitse"
                         #(do (log "[AIKATAULU] Merkitään kohde valmiiksi tiemerkintää")
                              (tiedot/merkitse-kohde-valmiiksi-tiemerkintaan
                                {:kohde-id kohde-id
                                 :tiemerkintapvm (:valmis-tiemerkintaan @valmis-tiemerkintaan-lomake)
                                 :urakka-id urakka-id
                                 :sopimus-id (first @u/valittu-sopimusnumero)
                                 :vuosi vuosi}))
                         {;:disabled (not @valmis-tallennettavaksi?) ; FIXME Ei päivity
                          :luokka "nappi-myonteinen"
                          :kun-onnistuu (fn [vastaus]
                                          (log "[AIKATAULU] Kohde merkitty valmiiksi tiemerkintää")
                                          (reset! tiedot/aikataulurivit vastaus)
                                          (modal/piilota!))}]]]}
             [:div
              [vihje "Toimintoa ei voi perua. Päivämäärän asettamisesta lähetetään sähköpostilla tieto tiemerkintäurakan urakanvalvojalle ja vastuuhenkilölle."]
              [lomake/lomake {:otsikko ""
                              :muokkaa! (fn [uusi-data]
                                          (reset! valmis-tiemerkintaan-lomake uusi-data))}
               [{:otsikko "Tiemerkinnän saa aloittaa"
                 :nimi :valmis-tiemerkintaan
                 :pakollinen? true
                 :tyyppi :pvm}]
               @valmis-tiemerkintaan-lomake]]))}
        "Aseta päivä\u00ADmäärä"]])))

(defn- paallystys-aloitettu-validointi
  "Validoinnit päällystys aloitettu -kentälle"
  [optiot]
  (as-> [[:pvm-kentan-jalkeen :aikataulu-kohde-alku
          "Päällystys ei voi alkaa ennen kohteen aloitusta."]] validointi

        ;; Päällystysnäkymässä validoidaan, että alku on annettu
        (if (= (:nakyma optiot) :paallystys)
          (conj validointi
                [:toinen-arvo-annettu-ensin :aikataulu-kohde-alku
                 "Päällystystä ei voi merkitä alkaneeksi ennen kohteen aloitusta."])
          validointi)))

(defn- oikeudet
  "Tarkistaa aikataulunäkymän tarvitsemat oikeudet"
  [urakka-id]
  (let [saa-muokata?
        (oikeudet/voi-kirjoittaa? oikeudet/urakat-aikataulu urakka-id)

        saa-asettaa-valmis-takarajan?
        (oikeudet/on-muu-oikeus? "TM-takaraja"
                                 oikeudet/urakat-aikataulu
                                 urakka-id
                                 @istunto/kayttaja)

        saa-merkita-valmiiksi?
        (oikeudet/on-muu-oikeus? "TM-valmis"
                                 oikeudet/urakat-aikataulu
                                 urakka-id
                                 @istunto/kayttaja)]
    {:saa-muokata? saa-muokata?
     :saa-asettaa-valmis-takarajan? saa-asettaa-valmis-takarajan?
     :saa-merkita-valmiiksi? saa-merkita-valmiiksi?
     :voi-tallentaa? (or saa-muokata?
                         saa-merkita-valmiiksi?
                         saa-asettaa-valmis-takarajan?)}))


(defn- otsikoi-aikataulurivit
  "Lisää väliotsikot valmiille, keskeneräisille ja aloittamatta oleville kohteille."
  [{:keys [valmis kesken aloittamatta] :as luokitellut-rivit}]
  (concat (when-not (empty? valmis)
            (into [(grid/otsikko "Valmiit kohteet")]
                  valmis))
          (when-not (empty? kesken)
            (into [(grid/otsikko "Keskeneräiset kohteet")]
                  kesken))
          (when-not (empty? aloittamatta)
            (into [(grid/otsikko "Aloittamatta olevat kohteet")]
                  aloittamatta))))





(defn- tallenna-aikataulu [urakka-id sopimus-id vuosi kohteet]
  (tiedot/tallenna-yllapitokohteiden-aikataulu
   {:urakka-id urakka-id
    :sopimus-id sopimus-id
    :vuosi vuosi
    :kohteet kohteet
    :epaonnistui-fn #(viesti/nayta! "Tallennus epäonnistui!"
                                    :warning
                                    viesti/viestin-nayttoaika-lyhyt)}))

(defn valinnat [ur]
  (let [{aikajana? :nayta-aikajana?
         jarjestys :jarjestys
         :as valinnat} @tiedot/valinnat]
    [:span.aikataulu-valinnat
     [valinnat/urakan-vuosi ur]
     [valinnat/yllapitokohteen-kohdenumero yllapito-tiedot/kohdenumero]
     [valinnat/tienumero yllapito-tiedot/tienumero]

     [kentat/tee-otsikollinen-kentta
      {:otsikko "Aikajana"
       :kentta-params {:tyyppi :toggle
                       :paalle-teksti "Näytä aikajana"
                       :pois-teksti "Piilota aikajana"
                       :toggle! tiedot/toggle-nayta-aikajana!}
       :arvo-atom tiedot/nayta-aikajana?}]

     [yleiset/pudotusvalikko
      "Järjestä kohteet"
      {:valinta jarjestys
       :valitse-fn tiedot/jarjesta-kohteet!
       :format-fn  {:aika "Aloistusajan mukaan"
                    :kohdenumero "Kohdenumeron mukaan"
                    :tr "Tieosoitteen mukaan"}}
      [:aika :kohdenumero :tr]]

     [upotettu-raportti/raportin-vientimuodot
      (raportit/urakkaraportin-parametrit (:id ur) :yllapidon-aikataulu {:jarjestys jarjestys})]]))

(defn aikataulu
  [urakka optiot]
  (komp/luo
    (komp/lippu tiedot/aikataulu-nakymassa?)
    (fn [urakka optiot]
      (let [{urakka-id :id :as ur} @nav/valittu-urakka
            sopimus-id (first @u/valittu-sopimusnumero)
            aikataulurivit @tiedot/aikataulurivit-suodatettu
            urakkatyyppi (:tyyppi urakka)
            vuosi @u/valittu-urakan-vuosi
            {:keys [voi-tallentaa? saa-muokata?
                    saa-asettaa-valmis-takarajan?
                    saa-merkita-valmiiksi?]} (oikeudet urakka-id)


            otsikoidut-aikataulurivit (if (= :aika (:jarjestys @tiedot/valinnat))
                                        (otsikoi-aikataulurivit
                                         (tiedot/aikataulurivit-valmiuden-mukaan aikataulurivit urakkatyyppi))
                                        aikataulurivit)

            voi-muokata-paallystys? #(and (= (:nakyma optiot) :paallystys)
                                          saa-muokata?)
            voi-muokata-tiemerkinta? #(and (= (:nakyma optiot) :tiemerkinta)
                                           saa-merkita-valmiiksi?
                                           (:valmis-tiemerkintaan %))
            aikajana? (:nayta-aikajana? @tiedot/valinnat)]
        [:div.aikataulu

         [valinnat ur]

         (when aikajana?
           [aikajana/aikajana
            {:muuta! #(tallenna-aikataulu
                       urakka-id sopimus-id vuosi
                       (aikataulu/raahauksessa-paivitetyt-aikataulurivit aikataulurivit %))}
            (map #(aikataulu/aikataulurivi-jana voi-muokata-paallystys? voi-muokata-tiemerkinta? %)
                 aikataulurivit)])

         [grid/grid
          {:otsikko "Kohteiden aikataulu"
           :voi-poistaa? (constantly false)
           :voi-lisata? false
           :piilota-toiminnot? true
           :salli-valiotsikoiden-piilotus? true
           :tyhja (if (nil? @tiedot/aikataulurivit)
                    [yleiset/ajax-loader "Haetaan kohteita..."] "Ei kohteita")
           :tallenna (if voi-tallentaa?
                       #(tallenna-aikataulu urakka-id sopimus-id vuosi %)
                       :ei-mahdollinen)}
          [{:otsikko "Koh\u00ADde\u00ADnu\u00ADme\u00ADro" :leveys 3 :nimi :kohdenumero :tyyppi :string
            :pituus-max 128 :muokattava? voi-muokata-paallystys?}
           {:otsikko "Koh\u00ADteen nimi" :leveys 7 :nimi :nimi :tyyppi :string :pituus-max 128
            :muokattava? voi-muokata-paallystys?}
           {:otsikko "Tie\u00ADnu\u00ADme\u00ADro" :nimi :tr-numero
            :tyyppi :positiivinen-numero :leveys 3 :tasaa :oikea
            :muokattava? (constantly false)}
           {:otsikko "Ajo\u00ADrata"
            :nimi :tr-ajorata
            :muokattava? (constantly false)
            :tyyppi :string :tasaa :oikea
            :fmt #(pot/arvo-koodilla pot/+ajoradat-numerona+ %)
            :leveys 3}
           {:otsikko "Kais\u00ADta"
            :muokattava? (constantly false)
            :nimi :tr-kaista
            :tyyppi :string
            :tasaa :oikea
            :fmt #(pot/arvo-koodilla pot/+kaistat+ %)
            :leveys 3}
           {:otsikko "Aosa" :nimi :tr-alkuosa :leveys 3
            :tyyppi :positiivinen-numero
            :tasaa :oikea
            :muokattava? (constantly false)}
           {:otsikko "Aet" :nimi :tr-alkuetaisyys :leveys 3
            :tyyppi :positiivinen-numero
            :tasaa :oikea
            :muokattava? (constantly false)}
           {:otsikko "Losa" :nimi :tr-loppuosa :leveys 3
            :tyyppi :positiivinen-numero
            :tasaa :oikea
            :muokattava? (constantly false)}
           {:otsikko "Let" :nimi :tr-loppuetaisyys :leveys 3
            :tyyppi :positiivinen-numero
            :tasaa :oikea
            :muokattava? (constantly false)}
           {:otsikko "YP-lk"
            :nimi :yllapitoluokka :leveys 4 :tyyppi :string
            :fmt yllapitokohteet-domain/yllapitoluokkanumero->lyhyt-nimi
            :muokattava? (constantly false)}
           (when (= (:nakyma optiot) :paallystys) ;; Asiakkaan mukaan ei tarvi näyttää tiemerkkareille
             {:otsikko "Koh\u00ADteen aloi\u00ADtus" :leveys 8 :nimi :aikataulu-kohde-alku
              :tyyppi :pvm :fmt pvm/pvm-opt
              :muokattava? voi-muokata-paallystys?})
           {:otsikko "Pääl\u00ADlystyk\u00ADsen aloi\u00ADtus" :leveys 8 :nimi :aikataulu-paallystys-alku
            :tyyppi :pvm :fmt pvm/pvm-opt
            :muokattava? voi-muokata-paallystys?
            :validoi (paallystys-aloitettu-validointi optiot)}
           {:otsikko "Pääl\u00ADlystyk\u00ADsen lope\u00ADtus" :leveys 8 :nimi :aikataulu-paallystys-loppu
            :tyyppi :pvm :fmt pvm/pvm-opt
            :muokattava? voi-muokata-paallystys?
            :validoi [[:toinen-arvo-annettu-ensin :aikataulu-paallystys-alku
                       "Päällystystä ei ole merkitty aloitetuksi."]
                      [:pvm-kentan-jalkeen :aikataulu-paallystys-alku
                       "Valmistuminen ei voi olla ennen aloitusta."]
                      [:ei-tyhja-jos-toinen-arvo-annettu :valmis-tiemerkintaan
                       "Arvoa ei voi poistaa, koska kohde on merkitty valmiiksi tiemerkintään"]
                      [:ei-tyhja-jos-toinen-arvo-annettu :aikataulu-paallystys-alku
                       "Anna päällystyksen valmistumisen aika tai aika-arvio."]]}
           (when (= (:nakyma optiot) :paallystys)
             {:otsikko "Tie\u00ADmer\u00ADkin\u00ADnän suo\u00ADrit\u00ADta\u00ADva u\u00ADrak\u00ADka"
              :leveys 10 :nimi :suorittava-tiemerkintaurakka
              :tyyppi :valinta
              :fmt (fn [arvo]
                     (:nimi (some
                             #(when (= (:id %) arvo) %)
                             @tiedot/tiemerkinnan-suorittavat-urakat)))
              :valinta-arvo :id
              :valinta-nayta #(if % (:nimi %) "- Valitse urakka -")
              :valinnat @tiedot/tiemerkinnan-suorittavat-urakat
              :nayta-ryhmat [:sama-hallintayksikko :eri-hallintayksikko]
              :ryhmittely #(if (= (:hallintayksikko %) (:id (:hallintayksikko urakka)))
                             :sama-hallintayksikko
                             :eri-hallintayksikko)
              :ryhman-otsikko #(case %
                                 :sama-hallintayksikko "Hallintayksikön tiemerkintäurakat"
                                 :eri-hallintayksikko "Muut tiemerkintäurakat")
              :muokattava? (fn [rivi] (and saa-muokata? (:tiemerkintaurakan-voi-vaihtaa? rivi)))})
           {:otsikko "Val\u00ADmis tie\u00ADmerkin\u00ADtään" :leveys 10
            :fmt pvm/pvm-opt
            :nimi :valmis-tiemerkintaan :tyyppi :komponentti :muokattava? (constantly saa-muokata?)
            :komponentti (fn [rivi {:keys [muokataan?]}]
                           (if (:valmis-tiemerkintaan rivi)
                             [:span (pvm/pvm-opt (:valmis-tiemerkintaan rivi))]
                             (if (= (:nakyma optiot) :paallystys)
                               ;; Voi merkitä valmiiksi tiemerkintään vain päällystysurakassa
                               ;; Ei kuitenkaan jos gridi on muokkaustilassa, sillä päivämäärän asettaminen
                               ;; dialogista resetoi muokkaustilan.
                               (if muokataan?
                                 [:span]
                                 [valmis-tiemerkintaan
                                  {:kohde-id (:id rivi)
                                   :urakka-id urakka-id
                                   :vuosi vuosi
                                   :paallystys-valmis? (some? (:aikataulu-paallystys-loppu rivi))
                                   :suorittava-urakka-annettu? (some? (:suorittava-tiemerkintaurakka rivi))}])
                               [:span "Ei"])))}
           {:otsikko "Tie\u00ADmerkin\u00ADtä val\u00ADmis vii\u00ADmeis\u00ADtään"
            :leveys 6 :nimi :aikataulu-tiemerkinta-takaraja :tyyppi :pvm
            :fmt pvm/pvm-opt
            :muokattava? (fn [rivi]
                           (and saa-asettaa-valmis-takarajan?
                                (:valmis-tiemerkintaan rivi)))}
           {:otsikko "Tiemer\u00ADkinnän aloi\u00ADtus"
            :leveys 6 :nimi :aikataulu-tiemerkinta-alku :tyyppi :pvm
            :fmt pvm/pvm-opt
            :muokattava? voi-muokata-tiemerkinta?}
           {:otsikko "Tiemer\u00ADkinnän lope\u00ADtus"
            :leveys 6 :nimi :aikataulu-tiemerkinta-loppu :tyyppi :pvm
            :fmt pvm/pvm-opt
            :muokattava? voi-muokata-tiemerkinta?
            :validoi [[:toinen-arvo-annettu-ensin :aikataulu-tiemerkinta-alku
                       "Tiemerkintää ei ole merkitty aloitetuksi."]
                      [:pvm-kentan-jalkeen :aikataulu-tiemerkinta-alku
                       "Valmistuminen ei voi olla ennen aloitusta."]
                      [:ei-tyhja-jos-toinen-arvo-annettu :aikataulu-tiemerkinta-alku
                       "Anna tiemerkinnän valmistumisen aika tai aika-arvio."]]}
           {:otsikko "Pääl\u00ADlystys\u00ADkoh\u00ADde val\u00ADmis" :leveys 6 :nimi :aikataulu-kohde-valmis :tyyppi :pvm
            :fmt pvm/pvm-opt
            :muokattava? voi-muokata-paallystys?
            :validoi [[:pvm-kentan-jalkeen :aikataulu-kohde-alku
                       "Kohde ei voi olla valmis ennen kuin se on aloitettu."]]}

           (when (istunto/ominaisuus-kaytossa? :tietyoilmoitukset)
             {:otsikko "Tie\u00ADtyö\u00ADilmoi\u00ADtus"
              :leveys 6
              :nimi :tietyoilmoitus
              :tyyppi :komponentti
              :komponentti (fn [{tietyoilmoitus-id :tietyoilmoitus-id :as kohde}]
                             [:button.nappi-toissijainen.nappi-grid
                              {:on-click #(siirtymat/avaa-tietyoilmoitus kohde)}
                              (if tietyoilmoitus-id
                                [ikonit/ikoni-ja-teksti (ikonit/livicon-eye) " Avaa"]
                                [ikonit/ikoni-ja-teksti (ikonit/livicon-plus) " Lisää"])])})]
          otsikoidut-aikataulurivit]
         (if (= (:nakyma optiot) :tiemerkinta)
           [vihje "Tiemerkinnän valmistumisesta lähetetään sähköpostilla tieto päällystysurakan urakanvalvojalle ja vastuuhenkilölle."])]))))
