(ns harja.views.urakka.yllapitokohteet
  "Ylläpitokohteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.fmt :as fmt]
            [harja.loki :refer [log logt tarkkaile!]]
            [clojure.string :as str]
            [cljs.core.async :refer [<!]]
            [harja.tyokalut.vkm :as vkm]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.domain.paallystysilmoitus :as pot]
            [harja.tiedot.urakka.yhatuonti :as yha]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.napit :as napit]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.yllapitokohteet :as tiedot]
            [harja.tiedot.urakka :as u]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.validointi :as validointi]
            [harja.atom :refer [wrap-vain-luku]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defn laske-sarakkeen-summa [sarake kohderivit]
  (reduce + (mapv
              (fn [rivi] (sarake rivi))
              kohderivit)))

(defn tr-virheilmoitus [tr-virheet]
  [:div.tr-virheet
   (for [virhe (into #{} (vals @tr-virheet))]
     ^{:key (hash virhe)}
     [:div.tr-virhe (ikonit/livicon-warning-sign)
      virhe])])

;; Ylläpitokohteiden sarakkeiden leveydet
(def haitari-leveys 5)
(def id-leveys 6)
(def kohde-leveys 15)
(def kvl-leveys 5)
(def yllapitoluokka-leveys 5)
(def nykyinen-paallyste-leveys 8)
(def indeksin-kuvaus-leveys 8)
(def tr-leveys 8)
(def tarjoushinta-leveys 10)
(def muutoshinta-leveys 10)
(def toteutunut-hinta-leveys 10)
(def arvonvahennykset-leveys 10)
(def bitumi-indeksi-leveys 10)
(def kaasuindeksi-leveys 10)
(def yhteensa-leveys 10)
(def yllapitokohdetyyppi-leveys 10)

;; Ylläpitokohdeosien sarakkeiden leveydet
(def nimi-leveys 20)
(def toimenpide-leveys 15)

(defn alkuosa-ei-lopun-jalkeen [aosa {losa :tr-loppuosa}]
  (when (and aosa losa (> aosa losa))
    "Al\u00ADku\u00ADo\u00ADsa ei voi olla lop\u00ADpu\u00ADo\u00ADsan jäl\u00ADkeen"))

(defn alkuetaisyys-ei-lopun-jalkeen [alkuet {aosa :tr-alkuosa
                                             losa :tr-loppuosa
                                             loppuet :tr-loppuetaisyys}]
  (when (and aosa losa alkuet loppuet
             (= aosa losa)
             (> alkuet loppuet))
    "Alku\u00ADe\u00ADtäi\u00ADsyys ei voi olla lop\u00ADpu\u00ADe\u00ADtäi\u00ADsyy\u00ADden jäl\u00ADkeen"))

(defn loppuosa-ei-alkua-ennen [losa {aosa :tr-alkuosa}]
  (when (and aosa losa (< losa aosa))
    "Lop\u00ADpu\u00ADosa ei voi olla al\u00ADku\u00ADo\u00ADsaa ennen"))

(defn loppuetaisyys-ei-alkua-ennen [loppuet {aosa :tr-alkuosa
                                             losa :tr-loppuosa
                                             alkuet :tr-alkuetaisyys}]
  (when (and aosa losa alkuet loppuet
             (= aosa losa)
             (< loppuet alkuet))
    "Lop\u00ADpu\u00ADe\u00ADtäi\u00ADsyys ei voi olla enn\u00ADen al\u00ADku\u00ADe\u00ADtäi\u00ADsyyt\u00ADtä"))

(defn tierekisteriosoite-sarakkeet [perusleveys
                                    [nimi tunnus tie ajorata kaista aosa aet losa let pituus]]
  (into []
        (remove
          nil?
          [(when nimi {:otsikko "Nimi" :nimi (:nimi nimi) :tyyppi :string
                       :leveys (+ perusleveys 5)
                       :muokattava? (or (:muokattava? nimi) (constantly true))})
           (when tunnus {:otsikko "Tunnus" :nimi (:nimi tunnus) :tyyppi :string :pituus-max 1
                         :leveys 4 :muokattava? (or (:muokattava? tunnus) (constantly true))})
           {:otsikko "Tie\u00ADnu\u00ADme\u00ADro" :nimi (:nimi tie)
            :tyyppi :positiivinen-numero :leveys perusleveys :tasaa :oikea
            :validoi [[:ei-tyhja "Anna tienumero"]]
            :muokattava? (or (:muokattava? tie) (constantly true))}
           {:otsikko "Ajo\u00ADrata"
            :nimi (:nimi ajorata)
            :muokattava? (or (:muokattava? ajorata) (constantly true))
            :tyyppi :valinta
            :tasaa :oikea
            :valinta-arvo :koodi
            :valinta-nayta (fn [arvo muokattava?]
                             (if arvo (:koodi arvo) (if muokattava?
                                                      "- Ajorata -"
                                                      "")))
            :valinnat pot/+ajoradat+
            :leveys (- perusleveys 2)}
           {:otsikko "Kais\u00ADta"
            :muokattava? (or (:muokattava? kaista) (constantly true))
            :nimi (:nimi kaista)
            :tyyppi :valinta
            :tasaa :oikea
            :valinta-arvo :koodi
            :valinta-nayta (fn [arvo muokattava?]
                             (if arvo (:koodi arvo) (if muokattava?
                                                      "- Kaista -"
                                                      "")))
            :valinnat pot/+kaistat+
            :leveys (- perusleveys 2)}
           {:otsikko "Aosa" :nimi (:nimi aosa) :leveys perusleveys :tyyppi :positiivinen-numero
            :tasaa :oikea
            :validoi (into [[:ei-tyhja "An\u00ADna al\u00ADku\u00ADo\u00ADsa"]
                            alkuosa-ei-lopun-jalkeen]
                           (:validoi aosa))
            :muokattava? (or (:muokattava? aosa) (constantly true))}
           {:otsikko "Aet" :nimi (:nimi aet) :leveys perusleveys :tyyppi :positiivinen-numero
            :tasaa :oikea
            :validoi (into [[:ei-tyhja "An\u00ADna al\u00ADku\u00ADe\u00ADtäi\u00ADsyys"]
                            alkuetaisyys-ei-lopun-jalkeen]
                           (:validoi aet))
            :muokattava? (or (:muokattava? aet) (constantly true))}
           {:otsikko "Losa" :nimi (:nimi losa) :leveys perusleveys :tyyppi :positiivinen-numero
            :tasaa :oikea
            :validoi (into [[:ei-tyhja "An\u00ADna lop\u00ADpu\u00ADo\u00ADsa"]
                            loppuosa-ei-alkua-ennen]
                           (:validoi losa))
            :muokattava? (or (:muokattava? losa) (constantly true))}
           {:otsikko "Let" :nimi (:nimi let) :leveys perusleveys :tyyppi :positiivinen-numero
            :tasaa :oikea
            :validoi (into [[:ei-tyhja "An\u00ADna lop\u00ADpu\u00ADe\u00ADtäi\u00ADsyys"]
                            loppuetaisyys-ei-alkua-ennen]
                           (:validoi let))
            :muokattava? (or (:muokattava? let) (constantly true))}
           (merge
             {:otsikko "Pit. (m)" :nimi :pituus :leveys perusleveys :tyyppi :numero :tasaa :oikea
              :muokattava? (constantly false)}
             pituus)])))

(defn tr-osoite [rivi]
  (let [arvot (map rivi [:tr-numero :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys])]
    (when (every? #(not (str/blank? %)) arvot)
      ;; Tierekisteriosoite on täytetty (ei tyhjiä kenttiä)
      (zipmap [:numero :alkuosa :alkuetaisyys :loppuosa :loppuetaisyys]
              arvot))))

(defn varmista-alku-ja-loppu [kohdeosat {tie :tr-numero aosa :tr-alkuosa losa :tr-loppuosa
                                         alkuet :tr-alkuetaisyys loppuet :tr-loppuetaisyys
                                         :as kohde}]
  (let [avaimet (sort (keys kohdeosat))
        ensimmainen (first avaimet)
        viimeinen (last avaimet)]
    (as-> kohdeosat ko
          (if (not= (tr/alku kohde) (tr/alku (get kohdeosat ensimmainen)))
            (update-in ko [ensimmainen] merge {:tr-alkuosa aosa :tr-alkuetaisyys alkuet})
            ko)
          (if (not= (tr/loppu kohde) (tr/loppu (get kohdeosat viimeinen)))
            (update-in ko [viimeinen] merge {:tr-loppuosa losa :tr-loppuetaisyys loppuet})
            ko))))

(defn validoi-tr-osoite [grid tr-sijainnit-atom tr-virheet-atom]
  (let [haetut (into #{} (keys @tr-sijainnit-atom))]
    ;; jos on tullut uusi TR osoite, haetaan sille sijainti
    (doseq [[id rivi] (grid/hae-muokkaustila grid)]
      (if (:poistettu rivi)
        (swap! tr-virheet-atom dissoc id)
        (let [osoite (tr-osoite rivi)
              virheet (grid/hae-virheet grid)]
          (when (and osoite (not (haetut osoite))
                     (empty? (get virheet id)))
            (go
              (log "Haetaan TR osoitteen sijainti: " (pr-str osoite))
              (let [sijainti (<! (vkm/tieosoite->viiva osoite))]
                (when (= (get (grid/hae-muokkaustila grid) id) rivi) ;; ettei rivi ole uudestaan muuttunut
                  (if-let [virhe (when-not (vkm/loytyi? sijainti)
                                   "Virheellinen TR-osoite")]
                    (do (swap! tr-virheet-atom assoc id virhe)
                        (doseq [kentta [:tr-numero :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys]]
                          (grid/aseta-virhe! grid id kentta "Tarkista tie")))
                    (do (swap! tr-virheet-atom dissoc id)
                        (doseq [kentta [:tr-numero :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys]]
                          (grid/poista-virhe! grid id kentta))
                        (log "sain sijainnin " (clj->js sijainti))
                        (swap! tr-sijainnit-atom assoc osoite sijainti))))))))))))

(defn- yllapitokohdeosat-grid-data [kohde kohdeosat]
  (if (empty? kohdeosat)
    {1 (select-keys kohde #{:tr-numero
                            :tr-alkuosa :tr-alkuetaisyys
                            :tr-loppuosa :tr-loppuetaisyys
                            :tr-kaista :tr-ajorata})}
    (varmista-alku-ja-loppu (zipmap (iterate inc 1)
                                    kohdeosat)
                            (tr/nouseva-jarjestys kohde))))

(defn- validoi-osa-olemassa [osan-pituus {tie :tr-numero aosa :tr-alkuosa losa :tr-loppuosa} osa]
  (when-let [osa (and osa (js/parseInt osa))]
    (cond
      (< osa aosa)
      "Osa ei voi olla ennen kohteen alkua"

      (> osa losa)
      "Osa ei voi olla kohteen lopun jälkeen"

      (not (contains? osan-pituus osa))
      (str "Tiellä " tie " ei ole osaa  " osa))))

(defn validoi-alkuetaisyys-kohteen-sisalla [{kohde-alkuosa :tr-alkuosa
                                             kohde-alkuet :tr-alkuetaisyys}
                                            alkuet
                                            {alkuosa :tr-alkuosa}]
  (when (and (= alkuosa kohde-alkuosa)
             (< alkuet kohde-alkuet))
    "Alkuetäisyys ei voi olla ennen kohteen alkua"))

(defn validoi-loppuetaisyys-kohteen-sisalla [{kohde-loppuosa :tr-loppuosa
                                              kohde-loppuet :tr-loppuetaisyys}
                                             loppuet
                                             {loppuosa :tr-loppuosa}]
  (when (and (= loppuosa kohde-loppuosa)
             (> loppuet kohde-loppuet))
    "Loppuetäisyys ei voi olla kohteen lopun jälkeen"))

(defn- validoi-osan-maksimipituus [osan-pituus key pituus rivi]
  (when (integer? pituus)
    (let [osa (get rivi key)]
      (when-let [pit (get osan-pituus osa)]
        (when (> pituus pit)
          (str "Osan " osa " maksimietäisyys on " pit))))))

(defn validoi-kohteen-osoite
  [osan-pituudet-teille kentta _ {:keys [tr-numero tr-alkuosa tr-alkuetaisyys
                                         tr-loppuosa tr-loppuetaisyys] :as kohde}]
  (let [osan-pituudet (osan-pituudet-teille tr-numero)]
    (or
      (cond
        (and (= kentta :tr-alkuosa) (not (contains? osan-pituudet tr-alkuosa)))
        (str "Tiellä " tr-numero " ei ole osaa " tr-alkuosa)

        (and (= kentta :tr-loppuosa) (not (contains? osan-pituudet tr-loppuosa)))
        (str "Tiellä " tr-numero " ei ole osaa " tr-loppuosa))

      (when (= kentta :tr-alkuetaisyys)
        (validoi-osan-maksimipituus osan-pituudet :tr-alkuosa tr-alkuetaisyys kohde))

      (when (= kentta :tr-loppuetaisyys)
        (validoi-osan-maksimipituus osan-pituudet :tr-loppuosa tr-loppuetaisyys kohde)))))

(defn yllapitokohdeosat
  [{:keys [kohdeosat-paivitetty-fn muokkaa!]}
   urakka kohdeosat
   {tie :tr-numero aosa :tr-alkuosa losa :tr-loppuosa
    alkuet :tr-alkuetaisyys loppuet :tr-loppuetaisyys
    kohdetyyppi :yllapitokohdetyyppi
    :as kohde} osan-pituus]
  (let [kirjoitusoikeus?
        (case (:tyyppi urakka)
          :paallystys
          (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystyskohteet (:id urakka))
          :paikkaus
          (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paikkauskohteet (:id urakka))
          false)
        tr-sijainnit (atom {}) ;; onnistuneesti haetut TR-sijainnit
        tr-virheet (atom {}) ;; virheelliset TR sijainnit
        resetoi-tr-tiedot (fn [] (reset! tr-sijainnit {}) (reset! tr-virheet {}))

        [sopimus-id _] @u/valittu-sopimusnumero
        urakka-id (:id urakka)

        g (grid/grid-ohjaus)
        toiminnot-komponentti
        (fn [kohdeosat-nyt muokkaa-kohdeosat!]
          (fn [_ {:keys [index]}]
            [:span
             [:button.nappi-ensisijainen.btn-xs
              {:disabled (= kohdetyyppi :sora)
               :on-click
               #(muokkaa-kohdeosat! (tiedot/lisaa-uusi-kohdeosa kohdeosat-nyt (inc index)))}
              (yleiset/ikoni-ja-teksti (ikonit/livicon-arrow-down) "Lisää")]
             [:button.nappi-kielteinen.btn-xs
              {:disabled (= 1 (count kohdeosat-nyt))
               :on-click
               #(muokkaa-kohdeosat! (tiedot/poista-kohdeosa kohdeosat-nyt (inc index)))}
              (yleiset/ikoni-ja-teksti (ikonit/livicon-trash) "Poista")]]))

        pituus (fn [osan-pituus tieosa]
                 (tr/laske-tien-pituus osan-pituus tieosa))]

    (fn [{:keys [kohdeosat-paivitetty-fn muokkaa!
                 rivinumerot? voi-muokata?] :as opts}
         urakka kohdeosat {yllapitokohde-id :id :as kohde} osan-pituus]
      (let [voi-muokata? (if (nil? voi-muokata?)
                           true
                           voi-muokata?)

            kohdeosat-nyt (yllapitokohdeosat-grid-data kohde kohdeosat)

            kohdeosia (count kohdeosat-nyt)

            skeema (into []
                         (remove
                           nil?
                           (concat
                             (tierekisteriosoite-sarakkeet
                               tr-leveys
                               [{:nimi :nimi}
                                {:nimi :tunnus}
                                {:nimi :tr-numero :muokattava? (constantly false)}
                                {:nimi :tr-ajorata :muokattava? (constantly false)}
                                {:nimi :tr-kaista :muokattava? (constantly false)}
                                {:nimi :tr-alkuosa :muokattava? (fn [_ rivi]
                                                                  (pos? rivi))
                                 :validoi [(partial validoi-osa-olemassa osan-pituus kohde)]}
                                {:nimi :tr-alkuetaisyys :muokattava? (fn [_ rivi]
                                                                       (pos? rivi))
                                 :validoi [(partial validoi-osan-maksimipituus osan-pituus :tr-alkuosa)
                                           (partial validoi-alkuetaisyys-kohteen-sisalla kohde)]}
                                {:nimi :tr-loppuosa :muokattava? (fn [_ rivi]
                                                                   (< rivi (dec kohdeosia)))
                                 :validoi [(partial validoi-osa-olemassa osan-pituus kohde)]}
                                {:nimi :tr-loppuetaisyys :muokattava? (fn [_ rivi]
                                                                        (< rivi (dec kohdeosia)))
                                 :validoi [(partial validoi-osan-maksimipituus osan-pituus :tr-loppuosa)
                                           (partial validoi-loppuetaisyys-kohteen-sisalla kohde)]}
                                {:hae (partial tr/laske-tien-pituus osan-pituus)}])
                             [{:otsikko "Toimenpide" :nimi :toimenpide :tyyppi :string
                               :leveys toimenpide-leveys}])))

            muokkaa-kohdeosat!
            (fn [kohdeosat-uudet]
              (let [uudet-tiedot (tiedot/kasittele-paivittyneet-kohdeosat
                                   kohdeosat-nyt kohdeosat-uudet)
                    uudet-virheet (into {}
                                        (keep (fn [[id rivi]]
                                                (let [rivin-virheet (validointi/validoi-rivi
                                                                      uudet-tiedot rivi skeema)]
                                                  (when-not (empty? rivin-virheet)
                                                    [id rivin-virheet])))
                                              uudet-tiedot))]
                (muokkaa! (->> uudet-tiedot
                               seq
                               (sort-by first)
                               (mapv second))
                          uudet-virheet)))


            skeema (if voi-muokata?
                     (conj skeema
                           {:otsikko "Toiminnot" :nimi :tr-muokkaus :tyyppi :komponentti :leveys 15
                            :tasaa :keskita
                            :komponentti (toiminnot-komponentti kohdeosat-nyt
                                                                muokkaa-kohdeosat!)})
                     skeema)

            grid-data (r/wrap kohdeosat-nyt
                              muokkaa-kohdeosat!)
            virheet (:virheet opts)]

        [:div
         [grid/muokkaus-grid
          {:ohjaus g
           :id "yllapitokohdeosat"
           :virheet virheet
           :rivinumerot? rivinumerot?
           :voi-muokata? voi-muokata?
           :voi-kumota? (:voi-kumota? opts)
           :nayta-virheet? :fokus
           :otsikko "Tierekisterikohteet"
           ;; Kohdeosille on toteutettu custom lisäys ja poistologiikka
           :voi-lisata? false
           :piilota-toiminnot? true
           :ulkoinen-validointi? true
           :paneelikomponentit
           (when kohdeosat-paivitetty-fn
             [(fn []
                [napit/palvelinkutsu-nappi
                 [yleiset/ikoni-ja-teksti (ikonit/tallenna) "Tallenna"]
                 (let [sijainnit @tr-sijainnit
                       osat (into []
                                  (map (fn [osa]
                                         (assoc osa :sijainti
                                                    (sijainnit (tr-osoite osa)))))
                                  (vals @grid-data))]
                   #(tiedot/tallenna-yllapitokohdeosat! urakka-id
                                                        sopimus-id
                                                        yllapitokohde-id
                                                        osat))
                 {:disabled (or (not (empty? @virheet))
                                (not kirjoitusoikeus?))
                  :luokka "nappi-myonteinen grid-tallenna"
                  :virheviesti "Tallentaminen epäonnistui."
                  :kun-onnistuu
                  (fn [vastaus]
                    (log "[KOHDEOSAT] Päivitys onnistui, vastaus: " (pr-str kohdeosat))
                    (urakka/lukitse-urakan-yha-sidonta! urakka-id)
                    (kohdeosat-paivitetty-fn vastaus)
                    (resetoi-tr-tiedot)
                    (viesti/nayta! "Kohdeosat tallennettu."
                                   :success viesti/viestin-nayttoaika-keskipitka))}])])
           :voi-poistaa? (constantly false)
           :tunniste hash
           :muokkaa-footer (fn [g]
                             [:span#kohdeosien-pituus-yht
                              "Tierekisterikohteiden pituus yhteensä: "
                              (fmt/pituus (reduce + 0 (keep (partial pituus osan-pituus)
                                                            (vals @grid-data))))
                              (when (= kohdetyyppi :sora)
                                [:p (yleiset/ikoni-ja-teksti (ikonit/livicon-info-sign) " Soratiekohteilla voi olla vain yksi alikohde")])])}
          skeema


          grid-data]]))))


(defn- aseta-uudet-kohdeosat [kohteet id kohdeosat]
  (let [kohteet (vec kohteet)
        rivi (some #(when (= (:id (nth kohteet %))
                             id)
                     %)
                   (range 0 (count kohteet)))]
    (if rivi
      (assoc-in kohteet [rivi :kohdeosat] kohdeosat)
      kohteet)))

(defn yllapitokohdeosat-kohteelle [urakka kohteet-atom
                                   {tie :tr-numero aosa :tr-alkuosa losa :tr-loppuosa :as kohde}]
  (let [osan-pituus (atom {})
        tiedot (atom {:kohdeosat (:kohdeosat kohde)
                      :virheet {}})]
    (go (reset! osan-pituus (<! (vkm/tieosien-pituudet tie aosa losa))))
    (fn [urakka kohteet-atom kohde]
      [yllapitokohdeosat
       {:muokkaa! (fn [kohdeosat virheet]
                    (swap! tiedot
                           assoc
                           :kohdeosat kohdeosat
                           :virheet virheet))
        :virheet (wrap-vain-luku (:virheet @tiedot))
        :voi-kumota? false
        :kohdeosat-paivitetty-fn
        #(swap! kohteet-atom
                (fn [kohteet kohdeosat]
                  (aseta-uudet-kohdeosat kohteet (:id kohde)
                                         kohdeosat)) %)}
       urakka
       (:kohdeosat @tiedot)
       kohde
       @osan-pituus])))

(defn kohteen-vetolaatikko [_ _ _]
  (fn [urakka kohteet-atom rivi]
    (if @grid/gridia-muokataan?
      [:span "Kohteen tierekisterikohteet ovat muokattavissa kohteen tallennuksen jälkeen."]
      [yllapitokohdeosat-kohteelle urakka kohteet-atom rivi])))

(defn hae-osan-pituudet [grid osan-pituudet-teille]
  (let [tiet (into #{} (map (comp :tr-numero second)) (grid/hae-muokkaustila grid))]
    (doseq [tie tiet :when (not (contains? @osan-pituudet-teille tie))]
      (go
        (swap! osan-pituudet-teille assoc tie (<!(vkm/tieosien-pituudet tie)))))))

(defn yllapitokohteet [urakka kohteet-atom optiot]
  (let [tr-sijainnit (atom {}) ;; onnistuneesti haetut TR-sijainnit
        tr-virheet (atom {}) ;; virheelliset TR sijainnit
        tallenna (reaction
                   (if (and @yha/yha-kohteiden-paivittaminen-kaynnissa? (:yha-sidottu? optiot))
                     :ei-mahdollinen
                     (:tallenna optiot)))
        osan-pituudet-teille (atom {20 {1 1000 2 2000 3 3000}
                                    4 {4 4000 5 5000 6 6000}})
        validoi-kohteen-osoite (fn [kentta arvo rivi]
                                 (validoi-kohteen-osoite @osan-pituudet-teille kentta arvo rivi))]
    (komp/luo
      (fn [urakka kohteet-atom optiot]
        [:div.yllapitokohteet
         [grid/grid
          {:otsikko (:otsikko optiot)
           :tyhja (if (nil? @kohteet-atom) [ajax-loader "Haetaan kohteita..."] "Ei kohteita")
           :vetolaatikot
           (into {}
                 (map (juxt
                        :id
                        (fn [rivi]
                          [kohteen-vetolaatikko urakka kohteet-atom rivi])))
                 @kohteet-atom)
           :tallenna @tallenna
           :muutos (fn [grid]
                     (hae-osan-pituudet grid osan-pituudet-teille)
                     (validoi-tr-osoite grid tr-sijainnit tr-virheet))
           :voi-lisata? (not (:yha-sidottu? optiot))
           :esta-poistaminen? (fn [rivi] (or (not (nil? (:paallystysilmoitus-id rivi)))
                                             (not (nil? (:paikkausilmoitus-id rivi)))))
           :esta-poistaminen-tooltip
           (fn [_] "Kohteelle on kirjattu ilmoitus, kohdetta ei voi poistaa.")}
          (into []
                (concat
                  [{:tyyppi :vetolaatikon-tila :leveys haitari-leveys}
                   {:otsikko "Koh\u00ADde\u00ADnu\u00ADme\u00ADro" :nimi :kohdenumero
                    :tyyppi :string :leveys id-leveys
                    :validoi [[:uniikki "Sama kohdenumero voi esiintyä vain kerran."]]}
                   {:otsikko "Koh\u00ADteen ni\u00ADmi" :nimi :nimi
                    :tyyppi :string :leveys kohde-leveys}
                   {:otsikko "Tyyppi"
                    :nimi :yllapitokohdetyyppi :tyyppi :string :leveys yllapitokohdetyyppi-leveys
                    :muokattava? (constantly false)
                    :fmt #({:paallyste "Päällyste"
                            :sora "Sora"
                            :kevytliikenne "Kevytliikenne"} %)}]
                  (tierekisteriosoite-sarakkeet
                    tr-leveys
                    [nil
                     nil
                     {:nimi :tr-numero :muokattava? (constantly (not (:yha-sidottu? optiot)))}
                     {:nimi :tr-ajorata :muokattava? (constantly (not (:yha-sidottu? optiot)))}
                     {:nimi :tr-kaista :muokattava? (constantly (not (:yha-sidottu? optiot)))}
                     {:nimi :tr-alkuosa :validoi [(partial validoi-kohteen-osoite :tr-alkuosa)]}
                     {:nimi :tr-alkuetaisyys :validoi [(partial validoi-kohteen-osoite :tr-alkuetaisyys)]}
                     {:nimi :tr-loppuosa :validoi [(partial validoi-kohteen-osoite :tr-loppuosa)]}
                     {:nimi :tr-loppuetaisyys :validoi [(partial validoi-kohteen-osoite :tr-loppuetaisyys)]}])
                  [{:otsikko "KVL"
                    :nimi :keskimaarainen-vuorokausiliikenne :tyyppi :numero :leveys kvl-leveys
                    :muokattava? (constantly (not (:yha-sidottu? optiot)))}
                   {:otsikko "YP-lk"
                    :nimi :yllapitoluokka :tyyppi :numero :leveys yllapitoluokka-leveys
                    :muokattava? (constantly (not (:yha-sidottu? optiot)))}
                   {:otsikko "Ny\u00ADkyi\u00ADnen pääl\u00ADlys\u00ADte"
                    :nimi :nykyinen-paallyste
                    :fmt #(paallystys-ja-paikkaus/hae-paallyste-koodilla %)
                    :tyyppi :valinta
                    :valinta-arvo :koodi
                    :valinnat paallystys-ja-paikkaus/+paallystetyypit+
                    :valinta-nayta :nimi
                    :leveys nykyinen-paallyste-leveys
                    :muokattava? (constantly (not (:yha-sidottu? optiot)))}
                   {:otsikko "In\u00ADdek\u00ADsin ku\u00ADvaus"
                    :nimi :indeksin-kuvaus :tyyppi :string
                    :leveys indeksin-kuvaus-leveys :pituus-max 2048}
                   (when (= (:nakyma optiot) :paallystys)
                     {:otsikko "Tar\u00ADjous\u00ADhinta" :nimi :sopimuksen-mukaiset-tyot
                      :fmt fmt/euro-opt :tyyppi :numero :leveys tarjoushinta-leveys :tasaa :oikea})
                   (when (= (:nakyma optiot) :paallystys)
                     {:otsikko "Mää\u00ADrä\u00ADmuu\u00ADtok\u00ADset" :nimi :muutoshinta :muokattava? (constantly false)
                      :fmt fmt/euro-opt :tyyppi :numero :leveys muutoshinta-leveys :tasaa :oikea})
                   (when (= (:nakyma optiot) :paikkaus)
                     {:otsikko "Toteutunut hinta" :nimi :toteutunut-hinta
                      :muokattava? (constantly false)
                      :fmt fmt/euro-opt :tyyppi :numero :leveys toteutunut-hinta-leveys
                      :tasaa :oikea})
                   {:otsikko "Ar\u00ADvon muu\u00ADtok\u00ADset" :nimi :arvonvahennykset :fmt fmt/euro-opt
                    :tyyppi :numero :leveys arvonvahennykset-leveys :tasaa :oikea}
                   {:otsikko "Bi\u00ADtumi-in\u00ADdek\u00ADsi" :nimi :bitumi-indeksi
                    :fmt fmt/euro-opt
                    :tyyppi :numero :leveys bitumi-indeksi-leveys :tasaa :oikea}
                   {:otsikko "Kaa\u00ADsu\u00ADindeksi" :nimi :kaasuindeksi :fmt fmt/euro-opt
                    :tyyppi :numero :leveys kaasuindeksi-leveys :tasaa :oikea}
                   {:otsikko (str "Ko\u00ADko\u00ADnais\u00ADhinta "
                                  "(ind\u00ADek\u00ADsit mu\u00ADka\u00ADna)")
                    :muokattava? (constantly false)
                    :nimi :kokonaishinta :fmt fmt/euro-opt :tyyppi :numero :leveys yhteensa-leveys
                    :tasaa :oikea
                    :hae (fn [rivi] (+ (:sopimuksen-mukaiset-tyot rivi)
                                       (:muutoshinta rivi)
                                       (:toteutunut-hinta rivi)
                                       (:arvonvahennykset rivi)
                                       (:bitumi-indeksi rivi)
                                       (:kaasuindeksi rivi)))}]))
          (sort-by tr/tiekohteiden-jarjestys @kohteet-atom)]
         [tr-virheilmoitus tr-virheet]]))))

(defn yllapitokohteet-yhteensa [kohteet-atom optiot]
  (let [yhteensa
        (reaction
          (let [kohteet @kohteet-atom
                sopimuksen-mukaiset-tyot-yhteensa
                (laske-sarakkeen-summa :sopimuksen-mukaiset-tyot kohteet)
                toteutunut-hinta-yhteensa (laske-sarakkeen-summa :toteutunut-hinta kohteet)
                muutoshinta-yhteensa (laske-sarakkeen-summa :muutoshinta kohteet)
                arvonvahennykset-yhteensa (laske-sarakkeen-summa :arvonvahennykset kohteet)
                bitumi-indeksi-yhteensa (laske-sarakkeen-summa :bitumi-indeksi kohteet)
                kaasuindeksi-yhteensa (laske-sarakkeen-summa :kaasuindeksi kohteet)
                kokonaishinta (+ sopimuksen-mukaiset-tyot-yhteensa
                                 toteutunut-hinta-yhteensa
                                 muutoshinta-yhteensa
                                 arvonvahennykset-yhteensa
                                 bitumi-indeksi-yhteensa
                                 kaasuindeksi-yhteensa)]
            [{:id 0
              :sopimuksen-mukaiset-tyot sopimuksen-mukaiset-tyot-yhteensa
              :muutoshinta muutoshinta-yhteensa
              :toteutunut-hinta toteutunut-hinta-yhteensa
              :arvonvahennykset arvonvahennykset-yhteensa
              :bitumi-indeksi bitumi-indeksi-yhteensa
              :kaasuindeksi kaasuindeksi-yhteensa
              :kokonaishinta kokonaishinta}]))]
    [grid/grid
     {:otsikko "Yhteensä"
      :piilota-toiminnot? true
      :tyhja (if (nil? {}) [ajax-loader "Lasketaan..."] "")}
     [{:otsikko "" :nimi :tyhja :tyyppi :string :leveys haitari-leveys}
      {:otsikko "" :nimi :kohdenumero :tyyppi :string :leveys id-leveys}
      {:otsikko "" :nimi :nimi :tyyppi :string :leveys kohde-leveys}
      {:otsikko "" :nimi :tr-numero :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr-ajorata :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr-kaista :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr-alkuosa :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr-alkuetaisyys :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr-loppuosa :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr-loppuetaisyys :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :pit :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :yllapitoluokka :tyyppi :string :leveys yllapitoluokka-leveys}
      {:otsikko "" :nimi :keskimaarainen-vuorokausiliikenne :tyyppi :string :leveys kvl-leveys}
      {:otsikko "" :nimi :nykyinen-paallyste :tyyppi :string :leveys nykyinen-paallyste-leveys}
      {:otsikko "" :nimi :indeksin-kuvaus :tyyppi :string :leveys indeksin-kuvaus-leveys}
      (when (= (:nakyma optiot) :paallystys)
        {:otsikko "Tarjous\u00ADhinta" :nimi :sopimuksen-mukaiset-tyot
         :fmt fmt/euro-opt :tyyppi :numero
         :leveys tarjoushinta-leveys :tasaa :oikea})
      (when (= (:nakyma optiot) :paallystys)
        {:otsikko "Muutok\u00ADset" :nimi :muutoshinta :fmt fmt/euro-opt :tyyppi :numero
         :leveys muutoshinta-leveys :tasaa :oikea})
      (when (= (:nakyma optiot) :paikkaus)
        {:otsikko "Toteutunut hinta" :nimi :toteutunut-hinta :fmt fmt/euro-opt :tyyppi :numero
         :leveys toteutunut-hinta-leveys :tasaa :oikea})
      {:otsikko "Arvon\u00ADväh." :nimi :arvonvahennykset :fmt fmt/euro-opt :tyyppi :numero
       :leveys arvonvahennykset-leveys :tasaa :oikea}
      {:otsikko "Bitumi-indeksi" :nimi :bitumi-indeksi :fmt fmt/euro-opt :tyyppi :numero
       :leveys bitumi-indeksi-leveys :tasaa :oikea}
      {:otsikko "Kaasu\u00ADindeksi" :nimi :kaasuindeksi :fmt fmt/euro-opt :tyyppi :numero
       :leveys kaasuindeksi-leveys :tasaa :oikea}
      {:otsikko "Kokonais\u00ADhinta (indeksit mukana)" :nimi :kokonaishinta :fmt fmt/euro-opt
       :tyyppi :numero :leveys yhteensa-leveys :tasaa :oikea}]
     @yhteensa]))