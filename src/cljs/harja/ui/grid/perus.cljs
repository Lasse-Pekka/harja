(ns harja.ui.grid.perus
  "Harjan käyttöön soveltuva geneerinen muokattava ruudukkokomponentti."
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log tarkkaile! logt] :refer-macros [mittaa-aika]]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje vihje] :as y]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :refer [tee-kentta nayta-arvo vain-luku-atomina]]
            [harja.ui.validointi :as validointi]
            [harja.ui.skeema :as skeema]
            [goog.events :as events]
            [goog.events.EventType :as EventType]

            [cljs.core.async :refer [<! put! chan]]
            [clojure.string :as str]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid.protokollat :refer
             [Grid vetolaatikko-auki? sulje-vetolaatikko!
              muokkauksessa-olevat-gridit
              avaa-vetolaatikko! muokkaa-rivit! otsikko?
              lisaa-rivi! vetolaatikko-rivi vetolaatikon-tila
              aseta-grid +rivimaara-jonka-jalkeen-napit-alaskin+]]
            [harja.ui.dom :as dom]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.makrot :refer [fnc]]))

(defn tayta-tiedot-alas
  "Täyttää rivin tietoja alaspäin."
  [rivit s lahtorivi tayta-fn]
  (let [tayta-fn (or tayta-fn

                     ;; Oletusfunktio kopioi tiedon sellaisenaan
                     (let [nimi (:nimi s)
                           lahtoarvo ((or (:hae s) nimi) lahtorivi)
                           aseta (or (:aseta s)
                                     (fn [rivi arvo]
                                       (assoc rivi nimi arvo)))]
                       (fn [_ taytettava]
                         (aseta taytettava lahtoarvo))))]
    (loop [uudet-rivit (list)
           alku false
           [rivi & rivit] rivit]
      (if-not rivi
        uudet-rivit
        (if (= lahtorivi rivi)
          (recur (conj uudet-rivit rivi) true rivit)
          (if-not alku
            (recur (conj uudet-rivit rivi) false rivit)
            (recur (conj uudet-rivit
                         (tayta-fn lahtorivi rivi))
                   true
                   rivit)))))))

(defn- tayta-alas-nappi [{:keys [fokus tayta-alas fokus-id arvo tulevat-rivit hae s ohjaus rivi]}]
  (when (and (= fokus fokus-id)
             (tayta-alas arvo)

             ;; Sallitaan täyttö, vain jos tulevia rivejä on ja kaikkien niiden arvot ovat tyhjiä
             (not (empty? tulevat-rivit))
             (every? str/blank? (map hae tulevat-rivit)))

    [:div {:class (if (= :oikea (:tasaa s))
                    "pull-left"
                    "pull-right")}
     [:div {:style {:position "absolute" :display "inline-block"}}
      [:button {:class (str "nappi-toissijainen nappi-tayta" (when (:kelluta-tayta-nappi s) " kelluta-tayta-nappi"))
                :title (:tayta-tooltip s)
                :style {:position "absolute"
                        :left (when (= :oikea (:tasaa s)) 0)
                        :right (when-not (= :oikea (:tasaa s)) "100%")}
                :on-click #(muokkaa-rivit! ohjaus tayta-tiedot-alas [s rivi (:tayta-fn s)])}
       (ikonit/livicon-arrow-down) " Täytä"]]]))

(defn- muokkausrivi [{:keys [ohjaus id muokkaa! luokka rivin-virheet rivin-varoitukset rivin-huomautukset voi-poistaa? esta-poistaminen?
                             esta-poistaminen-tooltip piilota-toiminnot? tallennus-kaynnissa?
                             fokus aseta-fokus! tulevat-rivit vetolaatikot
                             voi-muokata-rivia?]}
                     skeema rivi index]
  [:tr.muokataan {:class luokka}
   (doall (for [{:keys [nimi hae aseta fmt muokattava? tasaa tyyppi komponentti] :as s} skeema]
            (if (= :vetolaatikon-tila tyyppi)
              ^{:key (str "vetolaatikontila" id)}
              [vetolaatikon-tila ohjaus vetolaatikot id]

              (let [s (assoc s :rivi rivi)
                    hae (or hae
                            #(get % nimi))
                    arvo (hae rivi)
                    kentan-virheet (get rivin-virheet nimi)
                    kentan-varoitukset (get rivin-varoitukset nimi)
                    kentan-huomautukset (get rivin-huomautukset nimi)
                    tasaus-luokka (y/tasaus-luokka tasaa)
                    fokus-id [id nimi]]

                ;; muokattava? -> voiko muokata yksittäistä saraketta
                ;; voi-muokata-riviä? -> voiko muokata yksittäistä riviä
                (if (and (not @tallennus-kaynnissa?)
                         (or (nil? voi-muokata-rivia?) (voi-muokata-rivia? rivi index))
                         (or (nil? muokattava?) (muokattava? rivi index)))

                  ^{:key (str nimi)}
                  [:td {:class (str "muokattava " tasaus-luokka (cond
                                                                  (not (empty? kentan-virheet)) " sisaltaa-virheen"
                                                                  (not (empty? kentan-varoitukset)) " sisaltaa-varoituksen"
                                                                  (not (empty? kentan-huomautukset)) " sisaltaa-huomautuksen"))}
                   (cond
                     (not (empty? kentan-virheet)) (virheen-ohje kentan-virheet)
                     (not (empty? kentan-varoitukset)) (virheen-ohje kentan-varoitukset :varoitus)
                     (not (empty? kentan-huomautukset)) (virheen-ohje kentan-huomautukset :huomautus))

                   ;; Jos skeema tukee kopiointia, näytetään kopioi alas nappi
                   (when-let [tayta-alas (:tayta-alas? s)]
                     (tayta-alas-nappi {:fokus fokus :fokus-id fokus-id
                                        :arvo arvo :tayta-alas tayta-alas
                                        :tulevat-rivit tulevat-rivit :hae hae
                                        :s s :ohjaus ohjaus :rivi rivi}))

                   (if (= tyyppi :komponentti)
                     (komponentti rivi {:index index
                                        :muokataan? true})
                     [tee-kentta (assoc s
                                   :focus (= fokus fokus-id)
                                   :on-focus #(aseta-fokus! fokus-id)
                                   :pituus-max (:pituus-max s))
                      (r/wrap
                        arvo
                        (fn [uusi]
                          (if aseta
                            (muokkaa! id (fn [rivi]
                                           (aseta rivi uusi)))
                            (muokkaa! id assoc nimi uusi))))])]

                  ^{:key (str nimi)}
                  [:td {:class (str "ei-muokattava " tasaus-luokka)}
                   ((or fmt str) (hae rivi))])))))
   (when-not piilota-toiminnot?
     [:td.toiminnot
      (when (or (nil? voi-poistaa?) (voi-poistaa? rivi))
        (if (and esta-poistaminen? (esta-poistaminen? rivi))
          [:span (ikonit/livicon-trash-disabled (esta-poistaminen-tooltip rivi))]
          [:span.klikattava {:on-click #(do (.preventDefault %)
                                            (muokkaa! id assoc :poistettu true))}
           (ikonit/livicon-trash)]))
      (when-not (empty? rivin-virheet)
        [:span.rivilla-virheita
         (ikonit/livicon-warning-sign)])])])

(defn- rivin-infolaatikko* [sisalto rivi data]
  [:div.livi-grid-infolaatikko
   [:div.livi-grid-infolaatikko-yhdistysviiva]
   [:div.livi-grid-infolaatikko-sisalto
    (sisalto rivi data)]])

(defn- kasittele-rivin-klikkaus [{:keys [rivi-klikattu rivi-valinta-peruttu infolaatikko-nakyvissa? valittu-rivi
                                         mahdollista-rivin-valinta? rivin-infolaatikko infolaatikon-tila-muuttui
                                         rivi id]}]
  ;; Rivin klikkaus
  (when rivi-klikattu
    (if (not= @valittu-rivi id)
      (rivi-klikattu rivi)))

  ;; Rivin valinta
  (when mahdollista-rivin-valinta?
    (if (= @valittu-rivi id)
      ;; Saman rivin klikkaus
      (do (reset! valittu-rivi nil)
          (when rivi-valinta-peruttu
            (rivi-valinta-peruttu rivi))
          (when rivin-infolaatikko
            (reset! infolaatikko-nakyvissa? false))
          (when infolaatikon-tila-muuttui
            (infolaatikon-tila-muuttui false)))
      ;; Eri rivin klikkaus
      (do (reset! valittu-rivi id)
          (when rivin-infolaatikko
            (reset! infolaatikko-nakyvissa? true))
          (when infolaatikon-tila-muuttui
            (infolaatikon-tila-muuttui true))))))

(defn- nayttorivi [{:keys [luokka rivi-klikattu rivi-valinta-peruttu ohjaus id infolaatikko-nakyvissa?
                           vetolaatikot tallenna piilota-toiminnot? nayta-toimintosarake? valittu-rivi
                           mahdollista-rivin-valinta? rivin-infolaatikko solun-luokka infolaatikon-tila-muuttui
                           data]}
                   skeema rivi index]
  [:tr {:class (str luokka (when (= id @valittu-rivi)
                             " rivi-valittu"))
        :on-click #(kasittele-rivin-klikkaus
                     {:rivi-klikattu rivi-klikattu
                      :rivi-valinta-peruttu rivi-valinta-peruttu
                      :infolaatikko-nakyvissa? infolaatikko-nakyvissa?
                      :valittu-rivi valittu-rivi
                      :id id
                      :mahdollista-rivin-valinta? mahdollista-rivin-valinta?
                      :rivin-infolaatikko rivin-infolaatikko
                      :infolaatikon-tila-muuttui infolaatikon-tila-muuttui
                      :rivi rivi})}

   (doall (map-indexed
            (fn [i {:keys [nimi hae fmt tasaa tyyppi komponentti
                           solu-klikattu solun-luokka huomio
                           pakota-rivitys? reunus]}]
              (let [haettu-arvo (if hae
                                  (hae rivi)
                                  (get rivi nimi))]
                (if (= :vetolaatikon-tila tyyppi)
                  ^{:key (str "vetolaatikontila" id)}
                  [vetolaatikon-tila ohjaus vetolaatikot id]
                  ^{:key (str i nimi)}
                  ;; Solu
                  [:td {:on-click (when solu-klikattu
                                    #(do
                                       (.preventDefault %)
                                       (.stopPropagation %)
                                       (solu-klikattu rivi)))
                        :class (y/luokat
                                 (y/tasaus-luokka tasaa)
                                 (when pakota-rivitys? "grid-pakota-rivitys")
                                 (case reunus
                                   :ei "grid-reunus-ei"
                                   :vasen "grid-reunus-vasen"
                                   :oikea "grid-reunus-oikea"
                                   nil)
                                 (when solun-luokka
                                   (solun-luokka haettu-arvo rivi)))}
                   ;; Solun sisältö
                   [:span
                    ;; Sijoitetaan infolaatikko suhteessa viimeiseen soluun.
                    ;; Semanttisesti sen kuuluisi olla suhteessa riviin (koska laatikko kuvaa rivin lisätietoa).
                    ;; mutta HTML:n säännöt kieltävät div-elementit suoraan tr:n lapsena
                    (when (and
                            (= id @valittu-rivi)
                            (= (inc i) (count skeema))
                            rivin-infolaatikko
                            @infolaatikko-nakyvissa?)
                      [rivin-infolaatikko* rivin-infolaatikko rivi data])

                    (if (= tyyppi :komponentti)
                      (komponentti rivi {:index index
                                         :muokataan? false})
                      (if fmt
                        (fmt haettu-arvo)
                        [nayta-arvo skeema (vain-luku-atomina haettu-arvo)]))
                    (when huomio
                      (when-let [huomion-tiedot (huomio rivi)]
                        (let [ikoni (case (:tyyppi huomion-tiedot)
                                      :varoitus (ikonit/livicon-warning-sign)
                                      (ikonit/livicon-info))
                              teksti (:teksti huomion-tiedot)]
                          [yleiset/tooltip {} [:span {:class (str "grid-huomio-"
                                                                  (name (:tyyppi huomion-tiedot)))}
                                               ikoni]
                           teksti])))]])))
            skeema))
   (when (or nayta-toimintosarake?
             (and (not piilota-toiminnot?)
                  tallenna))
     [:th.toiminnot {:width "40px"} " "])])


(def renderoi-rivia-kerralla 100)

(defn- muokkauspaneeli [{:keys [nayta-otsikko? muokataan tallenna tiedot muuta-gridia-muokataan?
                                tallennus-ei-mahdollinen-tooltip muokattu? voi-lisata? ohjaus opts
                                muokkaa-aina virheet muokatut tallennus-kaynnissa
                                tallenna-vain-muokatut nollaa-muokkaustiedot! aloita-muokkaus! peru!
                                peruuta otsikko]}]
  [:div.panel-heading
   (if-not muokataan
     [:span.pull-right.muokkaustoiminnot
      (when (and tallenna
                 (not (nil? tiedot)))
        (let [muokkaa-nappi [:button.nappi-ensisijainen
                             {:disabled (or (= :ei-mahdollinen tallenna)
                                            muuta-gridia-muokataan?)
                              :on-click #(do (.preventDefault %)
                                             (aloita-muokkaus! tiedot))}
                             [:span.grid-muokkaa
                              [ikonit/ikoni-ja-teksti [ikonit/muokkaa] "Muokkaa"]]]]
          (if (and (= :ei-mahdollinen tallenna)
                   tallennus-ei-mahdollinen-tooltip)
            [yleiset/tooltip {} muokkaa-nappi tallennus-ei-mahdollinen-tooltip]
            muokkaa-nappi)))]
     [:span.pull-right.muokkaustoiminnot
      [:button.nappi-toissijainen
       {:disabled (not muokattu?)
        :on-click #(do (.stopPropagation %)
                       (.preventDefault %)
                       (peru!))}
       [ikonit/ikoni-ja-teksti [ikonit/kumoa] " Kumoa"]]

      (when-not (= false voi-lisata?)
        [:button.nappi-toissijainen.grid-lisaa {:on-click #(do (.preventDefault %)
                                                               (lisaa-rivi! ohjaus {}))}
         [ikonit/ikoni-ja-teksti [ikonit/livicon-plus] (or (:lisaa-rivi opts) "Lisää rivi")]])

      (when-not muokkaa-aina
        [:button.nappi-myonteinen.grid-tallenna
         {:disabled (or (not (empty? @virheet))
                        @tallennus-kaynnissa
                        (not muokattu?))
          :on-click #(when-not @tallennus-kaynnissa
                       (let [kaikki-rivit (mapv second @muokatut)
                             ;; rivejä jotka ensin lisätään ja samantien poistetaan (id < 0), ei pidä lähettää
                             tallennettavat (filter (fn [rivi]
                                                      (not (and (neg-int? (:id rivi))
                                                                (:poistettu rivi))))
                                                    kaikki-rivit)
                             tallennettavat
                             (if tallenna-vain-muokatut
                               (do (log "TALLENNA VAIN MUOKATUT")
                                   (filter (fn [rivi] (not (:koskematon rivi))) tallennettavat))
                               tallennettavat)]
                         (do (.preventDefault %)
                             (reset! tallennus-kaynnissa true)
                             (go
                               (when-not (empty? tallennettavat)
                                 (<! (tallenna tallennettavat)))
                               (nollaa-muokkaustiedot!)))))}
         [ikonit/ikoni-ja-teksti (ikonit/tallenna) "Tallenna"]])

      (when-not muokkaa-aina
        [:button.nappi-kielteinen.grid-peru
         {:on-click #(do
                       (.preventDefault %)
                       (nollaa-muokkaustiedot!)
                       (when peruuta (peruuta))
                       nil)}
         [ikonit/ikoni-ja-teksti (ikonit/livicon-ban) "Peruuta"]])])
   (when nayta-otsikko? [:h6.panel-title otsikko])])

(defn- otsikkorivi [{:keys [opts skeema nayta-toimintosarake? piilota-toiminnot? tallenna]}]
  (fn []
    [:thead
     (when-let [rivi-ennen (:rivi-ennen opts)]
       [:tr
        (for [{:keys [teksti sarakkeita tasaa]} rivi-ennen]
          ^{:key teksti}
          [:th {:colSpan (or sarakkeita 1)
                :class (y/tasaus-luokka tasaa)}
           teksti])])
     [:tr
      (map-indexed
        (fn [i {:keys [otsikko leveys nimi otsikkorivi-luokka tasaa]}]
          ^{:key (str i nimi)}
          [:th {:class (y/luokat otsikkorivi-luokka
                                 (y/tasaus-luokka tasaa))
                :width (or leveys "5%")}
           otsikko]) skeema)
      (when (or nayta-toimintosarake?
                (and (not piilota-toiminnot?)
                     tallenna))
        [:th.toiminnot {:width "40px"} " "])]]))

(defn- toggle-valiotsikko [valiotsikko-id piilotetut-valiotsikot]
  (if (@piilotetut-valiotsikot valiotsikko-id)
    (swap! piilotetut-valiotsikot disj valiotsikko-id)
    (swap! piilotetut-valiotsikot conj valiotsikko-id)))

(defn rivi-piilotetun-otsikon-alla? [rivi-indeksi rivit piilotetut-otsikot]
  (assert (vector? rivit) "Rivien täytyy olla vector")
  (if (otsikko? (get rivit rivi-indeksi))
    false
    (let [otsikot (filter otsikko? rivit)
          otsikoiden-indeksit (map #(.indexOf rivit %) otsikot)
          nykyista-rivia-edeltavat-otsikkoindeksit (filter #(< % rivi-indeksi) otsikoiden-indeksit)
          nykyisen-rivin-otsikon-indeksi (when-not (empty? nykyista-rivia-edeltavat-otsikkoindeksit)
                                           (apply max nykyista-rivia-edeltavat-otsikkoindeksit))]
      (boolean (piilotetut-otsikot (get-in (get rivit nykyisen-rivin-otsikon-indeksi)
                                           [:optiot :id]))))))


(defn- valiotsikko [{:keys [otsikko-record colspan teksti salli-valiotsikoiden-piilotus?
                            piilotetut-valiotsikot]}]
  (let [valiotsikko-id (get-in otsikko-record [:optiot :id])
        otsikkokomponentit (get-in otsikko-record [:optiot :otsikkokomponentit])]
    [:tr.otsikko (when salli-valiotsikoiden-piilotus?
                   {:class "gridin-collapsoitava-valiotsikko klikattava"
                    :on-click #(toggle-valiotsikko valiotsikko-id
                                                   piilotetut-valiotsikot)})
     [:td {:colSpan colspan}
      (when salli-valiotsikoiden-piilotus?
        (if (@piilotetut-valiotsikot valiotsikko-id)
          (ikonit/livicon-plus)
          (ikonit/livicon-minus)))

      (map-indexed
        (fn [i {:keys [sijainti sisalto]}]
          ^{:key i}
          [:div.grid-valiotsikko-custom-komponentti {:style {:top -2 :left sijainti}}
           [sisalto {:id valiotsikko-id}]])
        otsikkokomponentit)

      [:h5 teksti]]]))

(defn- muokkauskayttoliittyma [{:keys [muokatut jarjestys colspan tyhja virheet varoitukset
                                       huomautukset fokus ohjaus vetolaatikot muokkaa! voi-poistaa?
                                       esta-poistaminen? tallennus-kaynnissa?
                                       salli-valiotsikoiden-piilotus?
                                       piilotetut-valiotsikot tiedot
                                       esta-poistaminen-tooltip piilota-toiminnot?
                                       voi-muokata-rivia? skeema vetolaatikot-auki]}]
  (let [muokatut @muokatut
        jarjestys @jarjestys
        tulevat-rivit (fn [aloitus-idx]
                        (map #(get muokatut %) (drop (inc aloitus-idx) jarjestys)))]
    (if (empty? muokatut)
      [:tr.tyhja [:td {:colSpan colspan} tyhja]]
      (let [kaikki-virheet @virheet
            kaikki-varoitukset @varoitukset
            kaikki-huomautukset @huomautukset
            nykyinen-fokus @fokus]
        (doall (mapcat #(keep identity %)
                       (map-indexed
                         (fn [i id]

                           (if (otsikko? id)
                             (let [teksti (:teksti id)]
                               [^{:key teksti}
                               [valiotsikko {:colspan colspan :teksti teksti :otsikko-record id
                                             :piilotetut-valiotsikot piilotetut-valiotsikot
                                             :salli-valiotsikoiden-piilotus? salli-valiotsikoiden-piilotus?}]])
                             (when-not (rivi-piilotetun-otsikon-alla? i (vec tiedot) @piilotetut-valiotsikot)
                               (let [rivi (get muokatut id)
                                     rivin-virheet (get kaikki-virheet id)
                                     rivin-varoitukset (get kaikki-varoitukset id)
                                     rivin-huomautukset (get kaikki-huomautukset id)]
                                 (when-not (or (:yhteenveto rivi) (:poistettu rivi))
                                   [^{:key id}
                                   [muokkausrivi {:ohjaus ohjaus
                                                  :vetolaatikot vetolaatikot
                                                  :muokkaa! muokkaa!
                                                  :luokka (str (if (even? (+ i 1))
                                                                 "parillinen"
                                                                 "pariton"))
                                                  :id id
                                                  :rivin-virheet rivin-virheet
                                                  :rivin-varoitukset rivin-varoitukset
                                                  :rivin-huomautukset rivin-huomautukset
                                                  :voi-poistaa? voi-poistaa?
                                                  :esta-poistaminen? esta-poistaminen?
                                                  :esta-poistaminen-tooltip esta-poistaminen-tooltip
                                                  :fokus nykyinen-fokus
                                                  :aseta-fokus! #(reset! fokus %)
                                                  :tulevat-rivit (tulevat-rivit i)
                                                  :piilota-toiminnot? piilota-toiminnot?
                                                  :voi-muokata-rivia? voi-muokata-rivia?
                                                  :tallennus-kaynnissa? tallennus-kaynnissa?}
                                    skeema rivi i]
                                    (vetolaatikko-rivi vetolaatikot vetolaatikot-auki id colspan)])))))
                         jarjestys)))))))

(defn- nayttokayttoliittyma [{:keys [renderoi-max-rivia tiedot colspan tyhja tunniste ohjaus
                                     rivin-infolaatikko infolaatikko-nakyvissa? infolaatikon-tila-muuttui
                                     vetolaatikot tallenna rivi-klikattu rivin-luokka valittu-rivi
                                     piilotetut-valiotsikot
                                     rivi-valinta-peruttu mahdollista-rivin-valinta? piilota-toiminnot?
                                     nayta-toimintosarake? skeema vetolaatikot-auki salli-valiotsikoiden-piilotus?]}]
  (let [rivit (take @renderoi-max-rivia tiedot)]
    (if (empty? rivit)
      [:tr.tyhja [:td {:col-span colspan} tyhja]]
      (doall
        (let [rivit-jarjestetty (sort-by
                                  (fn [rivi] (if (:yhteenveto rivi) 1 0)) ; Yhteenveto-rivin tulee olla aina viimeisenä
                                  rivit)]
          (mapcat #(keep identity %)
                  (map-indexed
                    (fn [i rivi]
                      (if (otsikko? rivi)
                        [^{:key (:teksti rivi)}
                        [valiotsikko {:colspan colspan :teksti (:teksti rivi)
                                      :otsikko-record rivi
                                      :piilotetut-valiotsikot piilotetut-valiotsikot
                                      :salli-valiotsikoiden-piilotus? salli-valiotsikoiden-piilotus?}]]

                        (when-not (rivi-piilotetun-otsikon-alla? i (vec rivit) @piilotetut-valiotsikot)
                          (let [id (tunniste rivi)]
                            [^{:key id}
                            [nayttorivi {:ohjaus ohjaus
                                         :vetolaatikot vetolaatikot
                                         :id id
                                         :infolaatikon-tila-muuttui infolaatikon-tila-muuttui
                                         :infolaatikko-nakyvissa? infolaatikko-nakyvissa?
                                         :tallenna tallenna
                                         :luokka (str (if (even? (+ i 1))
                                                        "parillinen "
                                                        "pariton ")
                                                      (when (or rivi-klikattu mahdollista-rivin-valinta?)
                                                        "klikattava ")
                                                      (when (:korosta rivi) "korostettu-rivi ")
                                                      (when (:lihavoi rivi) "bold ")
                                                      (when (:yhteenveto rivi) "yhteenveto ")
                                                      (when rivin-luokka
                                                        (rivin-luokka rivi)))
                                         :rivi-klikattu rivi-klikattu
                                         :rivin-infolaatikko rivin-infolaatikko
                                         :rivi-valinta-peruttu rivi-valinta-peruttu
                                         :valittu-rivi valittu-rivi
                                         :data tiedot
                                         :mahdollista-rivin-valinta? mahdollista-rivin-valinta?
                                         :piilota-toiminnot? piilota-toiminnot?
                                         :nayta-toimintosarake? nayta-toimintosarake?}
                             skeema rivi i]
                             (vetolaatikko-rivi vetolaatikot vetolaatikot-auki id (inc (count skeema)))]))))
                    rivit-jarjestetty)))))))

(defn grid
  "Taulukko, jossa tietoa voi tarkastella ja muokata. Skeema on vektori joka sisältää taulukon sarakkeet.
  Jokainen skeeman itemi on mappi, jossa seuraavat avaimet:

  :nimi                                 kentän hakufn
  :fmt                                  kentän näyttämis-fn (oletus str). Ottaa argumenttina kentän arvon.
  :hae                                  funktio, jolla voidaan näyttää arvo kentässä. Ottaa argumenttina koko rivin.
  :otsikko                              ihmiselle näytettävä otsikko

  :solun-luokka                         funktio, joka palauttaa solun luokan\n
  :tyyppi                               kentän tietotyyppi,  #{:string :puhelin :email :pvm}
  :ohjaus                               gridin ohjauskahva, joka on luotu (grid-ohjaus) kutsulla
  :tasaa                                voit antaa :oikea, :keskita jos haluat tasata kentän
                                        oikealle (esim. rahasummat) tai keskelle

  :reunus                               määrittää sarakkeen solujen reunuksen, oletuksena kaikki
                                        :ei       ei kumpaakaan reunusta
                                        :vasen    vain vasemman puolen reunus
                                        :oikea    vain oikean puolen reunus
  :solu-klikattu                        Valinnainen käsittelijä kyseisen solun klikkaamiselle,
                                        saa rivin tiedot parametrina. Jos solulle on annettu
                                        käsittelijä, ei rivi-klikattu käsittelijää kutsuta.
  :komponentti                          Jos sarakkeen tyyppi on :komponentti, tämän avaimen takana tulee olla
                                        komponentin määrittävä funktio.

  Tyypin mukaan voi olla lisäavaimia, jotka määrittelevät tarkemmin kentän validoinnin.

  Optiot on mappi optioita:
  :max-rivimaara                        montako riviä grid suostuu näyttämään ennen 'liikaa rivejä' -ilmoitusta
  :max-rivimaaran-ylitys-viesti         custom viesti :max-rivimaara -optiolle
  :voi-poistaa?                         funktio, joka kertoo, voiko rivin poistaa
  :voi-lisata?                          voiko rivin lisätä (boolean)
  :tunniste                             rivin tunnistava kenttä, oletuksena :id
  :esta-poistaminen?                    funktio, joka palauttaa true tai false. Jos palauttaa true, roskakori disabloidaan erikseen annetun tooltipin kera.
  :esta-poistaminen-tooltip             funktio, joka palauttaa tooltipin. ks. ylempi.
  :tallennus-ei-mahdollinen-tooltip     Teksti, joka näytetään jos tallennus on disabloitu
  :tallenna                             funktio, jolle kaikki muutokset, poistot ja lisäykset muokkauksen päätyttyä
                                        jos tallenna funktiota ei ole annettu, taulukon muokkausta ei sallita eikä nappia näytetään
                                        jos tallenna arvo on :ei-mahdollinen, näytetään Muokkaa-nappi himmennettynä
  :tallenna-vain-muokatut               boolean jos päällä, tallennetaan vain muokatut. Oletuksena true
  :peruuta                              funktio jota kutsutaan kun käyttäjä klikkaa Peruuta-nappia muokkausmoodissa
  :rivi-klikattu                        funktio jota kutsutaan kun käyttäjä klikkaa riviä näyttömoodissa (parametrinä rivin tiedot)
  :rivin-infolaatikko                   Funktio, jonka palauttama komponentti näytetään gridin infolaatikossa kun rivi on valittuna.
                                        Funktiota kutsutaan rivin tiedoilla.
                                        HUOM! Vaatii toimiakseen: mahdollista-rivin-valinta? true
  :mahdollista-rivin-valinta?           jos true, käyttäjä voi valita rivin gridistä. Valittu rivi korostetaan.
  :salli-valiotsikoiden-piilotus?       Jos true, väliotsikoiden sisällön voi piilottaa klikkaamalla riviä
  :valiotsikoiden-alkutila              Jos väliotsikot on sallittu piilottaa, tämä määrittää, mitkä otsikot ovat
                                        oletuksena auki / kiinni. Vaihtoehdot: :kaikki-auki / :kaikki-kiinni
  :rivi-valinta-peruttu                 funktio, joka suoritetaan kun valittua riviä klikataan uudelleen eli valinta perutaan
  :muokkaa-footer                       optionaalinen footer komponentti joka muokkaustilassa näytetään, parametrina Grid ohjauskahva
  :muokkaa-aina                         jos true, grid on aina muokkaustilassa, eikä tallenna/peruuta nappeja ole
  :muutos                               jos annettu, kaikista gridin muutoksista tulee kutsu tähän funktioon.
                                        Parametrina Grid ohjauskahva
  :prosessoi-muutos                     funktio, jolla voi prosessoida muutoksenjälkeisen datan, esim. päivittää laskettuja kenttiä.
                                        Parametrina muokkausdata, palauttaa uuden muokkausdatan
  :aloita-muokkaus-fn                   kutsutaan kun muokkaus alkaa. Kutsuva pää voi tällöin esim. muokata datasisällön eriksi muokkausta varten
  :piilota-toiminnot?                   boolean, piilotetaan toiminnot sarake jos true
  :nayta-toimintosarake?                Näyttää oikealla tyhjän sarakkeen vaikka ei oltaisi muokkaustilassa. Syy: usean taulukon alignointi
  :rivin-luokka                         funktio joka palauttaa rivin luokan
  :uusi-rivi                            jos annettu uuden rivin tiedot käsitellään tällä funktiolla
  :vetolaatikot                         {id komponentti} lisäriveistä, jotka näytetään normaalirivien välissä
                                        jos rivin id:llä on avain tässä mäpissä, näytetään arvona oleva komponentti
                                        rivin alla
  :ei-footer-muokkauspaneelia?          Jos true, muokkauspaneelia ei koskaan piirretä gridin alaosaan.
  :vetolaatikot-auki                    Ulkopuolelta annettu tila vetolaatikoille (atom, jonka arvo on setti)
  :luokat                               Päätason div-elementille annettavat lisäluokat (vectori stringejä)
  :rivi-ennen                           table rivi ennen headeria, sekvenssi mäppejä, joissa avaimet
                                         :teksti (näytettävä teksti) ja :sarakkeita (colspan)
  :id                                   mahdollinen DOM noden id, gridin pääelementille
  :tyhja                                Jos rivejä ei ole, mitä näytetään taulukon paikalla?
  :voi-muokata-rivia?                   predikaattifunktio, jolla voidaan määrittää jolla voidaan määrittää kaikille
                                        riveille yhteinen sääntö milloin rivejä saa muokata
  "
  [{:keys [otsikko tallenna tallenna-vain-muokatut peruuta tyhja tunniste voi-poistaa? voi-lisata? salli-valiotsikoiden-piilotus?
           rivi-klikattu esta-poistaminen? esta-poistaminen-tooltip muokkaa-footer muokkaa-aina muutos infolaatikon-tila-muuttui
           rivin-luokka prosessoi-muutos aloita-muokkaus-fn piilota-toiminnot? nayta-toimintosarake? rivi-valinta-peruttu
           uusi-rivi vetolaatikot luokat korostustyyli mahdollista-rivin-valinta? max-rivimaara rivin-infolaatikko
           valiotsikoiden-alkutila ei-footer-muokkauspaneelia?
           max-rivimaaran-ylitys-viesti tallennus-ei-mahdollinen-tooltip voi-muokata-rivia?] :as opts} skeema tiedot]
  (let [komponentti-id (hash (str opts skeema tiedot (t/now)))
        muokatut (atom nil) ;; muokattu datajoukko
        jarjestys (atom nil) ;; id:t indekseissä (tai otsikko)
        uusi-id (atom 0) ;; tästä dekrementoidaan aina uusia id:tä
        historia (atom [])
        virheet (atom {}) ;; validointivirheet: (:id rivi) => [virheet]
        varoitukset (atom {}) ;; validointivaroitukset: (:id rivi) => [varoitukset]
        huomautukset (atom {})
        viime-assoc (atom nil) ;; edellisen muokkauksen, jos se oli assoc-in, polku
        viimeisin-muokattu-id (atom nil)
        tallennus-kaynnissa (atom false)
        tunniste (or tunniste :id) ;; Rivin yksilöivä tunniste, optiona annettu tai oletuksena :id
        valittu-rivi (atom nil) ;; Sisältää rivin yksilöivän tunnisteen
        rivien-maara (atom (count tiedot))
        piilotetut-valiotsikot (atom #{}) ;; Setti väliotsikoita, joiden sisältö on piilossa
        valiotsikoiden-alkutila-maaritelty? (atom (boolean (not salli-valiotsikoiden-piilotus?))) ;; Määritetään kerran, kun gridi saa datan
        renderoi-max-rivia (atom renderoi-rivia-kerralla)
        skeema (keep identity skeema)
        tallenna-vain-muokatut (if (nil? tallenna-vain-muokatut)
                                 true
                                 tallenna-vain-muokatut)
        infolaatikko-nakyvissa? (atom false)
        fokus (atom nil) ;; nyt fokusoitu item [id :sarake]
        vetolaatikot-auki (or (:vetolaatikot-auki opts)
                              (atom #{}))
        validoi-ja-anna-virheet (fn [virheet uudet-tiedot tyyppi]
                                  (into {}
                                        (keep (fn [rivi]
                                                (if (:harja.ui.grid/poistettu rivi)
                                                  nil
                                                  (let [virheet (validointi/validoi-rivi uudet-tiedot rivi skeema tyyppi)]
                                                    (if (empty? virheet)
                                                      nil
                                                      [(tunniste rivi) virheet]))))
                                              (vals uudet-tiedot))))
        ohjaus (reify Grid
                 (lisaa-rivi! [this rivin-tiedot]
                   (let [id (or (tunniste rivin-tiedot) (swap! uusi-id dec))
                         vanhat-tiedot @muokatut
                         vanhat-virheet @virheet
                         vanhat-varoitukset @varoitukset
                         vanhat-huomautukset @huomautukset
                         vanha-jarjestys @jarjestys
                         uudet-tiedot (swap! muokatut assoc id
                                             ((or uusi-rivi identity)
                                               (merge rivin-tiedot {tunniste id :koskematon true})))
                         uusi-jarjestys (swap! jarjestys conj id)]
                     (swap! historia conj [vanhat-tiedot vanhat-virheet vanhat-varoitukset vanhat-huomautukset vanha-jarjestys])
                     (swap! virheet (fn [virheet]
                                      (validoi-ja-anna-virheet virheet uudet-tiedot :validoi)))
                     (swap! varoitukset (fn [varoitukset]
                                          (validoi-ja-anna-virheet varoitukset uudet-tiedot :varoita)))
                     (swap! huomautukset (fn [huomautukset]
                                           (validoi-ja-anna-virheet huomautukset uudet-tiedot :huomauta)))
                     (log "VIRHEET: " (pr-str @virheet))
                     (when muutos
                       (muutos this))))
                 (hae-muokkaustila [_]
                   @muokatut)
                 (hae-virheet [_]
                   @virheet)
                 (hae-varoitukset [_]
                   @varoitukset)
                 (hae-huomautukset [_]
                   @huomautukset)
                 (nollaa-historia! [_]
                   (reset! historia []))
                 (hae-viimeisin-muokattu-id [_]
                   @viimeisin-muokattu-id)

                 (aseta-virhe! [_ rivin-id kentta virheteksti]
                   (swap! virheet assoc-in [rivin-id kentta] [virheteksti]))
                 (poista-virhe! [_ rivin-id kentta]
                   (swap! virheet
                          (fn [virheet]
                            (let [virheet (update-in virheet [rivin-id] dissoc kentta)]
                              (if (empty? (get virheet rivin-id))
                                (dissoc virheet rivin-id)
                                virheet)))))

                 (muokkaa-rivit! [this funktio args]
                   (let [vanhat-tiedot @muokatut
                         vanhat-virheet @virheet
                         vanhat-varoitukset @varoitukset
                         vanhat-huomautukset @huomautukset
                         vanha-jarjestys @jarjestys
                         uudet-tiedot (swap! muokatut (fn [muokatut]
                                                        (let [muokatut-jarjestyksessa (map (fn [id]
                                                                                             (get muokatut id))
                                                                                           @jarjestys)]
                                                          (into {}
                                                                (map (juxt tunniste #(dissoc % :koskematon)))
                                                                (apply funktio muokatut-jarjestyksessa args)))))]
                     (when-not (= vanhat-tiedot uudet-tiedot)
                       (reset! viimeisin-muokattu-id nil) ;; bulk muutoksesta ei jätetä viimeisintä muokkausta
                       (swap! historia conj [vanhat-tiedot vanhat-virheet vanhat-varoitukset
                                             vanhat-huomautukset vanha-jarjestys])
                       (swap! virheet (fn [virheet]
                                        (validoi-ja-anna-virheet virheet uudet-tiedot :validoi)))
                       (swap! varoitukset (fn [varoitukset]
                                            (validoi-ja-anna-virheet varoitukset uudet-tiedot :varoita)))
                       (swap! huomautukset (fn [huomautukset]
                                             (validoi-ja-anna-virheet huomautukset uudet-tiedot :huomauta))))

                     (when muutos
                       (muutos this))))

                 (vetolaatikko-auki? [_ id]
                   (@vetolaatikot-auki id))

                 (avaa-vetolaatikko! [_ id]
                   (swap! vetolaatikot-auki conj id))

                 (sulje-vetolaatikko! [_ id]
                   (swap! vetolaatikot-auki disj id)))

        ;; Tekee yhden muokkauksen säilyttäen undo historian
        muokkaa! (fn [id funktio & argumentit]
                   (let [vanhat-tiedot @muokatut
                         vanhat-virheet @virheet
                         vanhat-varoitukset @varoitukset
                         vanhat-huomautukset @huomautukset
                         vanha-jarjestys @jarjestys
                         uudet-tiedot (swap! muokatut
                                             (fn [muokatut]
                                               (let [uusi-data (update-in muokatut [id]
                                                                          (fn [rivi]
                                                                            (apply funktio (dissoc rivi :koskematon) argumentit)))]
                                                 (if prosessoi-muutos
                                                   (prosessoi-muutos uusi-data)
                                                   uusi-data))))]
                     (when-not (= vanhat-tiedot uudet-tiedot)
                       ;;(log "VANHAT: " (pr-str vanhat-tiedot) "\nUUDET: " (pr-str uudet-tiedot))
                       (reset! viimeisin-muokattu-id id)
                       (swap! historia conj [vanhat-tiedot vanhat-virheet vanhat-varoitukset
                                             vanhat-huomautukset vanha-jarjestys])
                       (swap! virheet (fn [virheet]
                                        (let [uusi-rivi (get uudet-tiedot id)
                                              rivin-virheet (when-not (:poistettu uusi-rivi)
                                                              (validointi/validoi-rivi uudet-tiedot uusi-rivi skeema :validoi))]
                                          (if (empty? rivin-virheet)
                                            (dissoc virheet id)
                                            (assoc virheet id rivin-virheet)))))
                       (swap! varoitukset (fn [varoitukset]
                                            (let [uusi-rivi (get uudet-tiedot id)
                                                  rivin-varoitukset (when-not (:poistettu uusi-rivi)
                                                                      (validointi/validoi-rivi uudet-tiedot uusi-rivi skeema :varoita))]
                                              (if (empty? rivin-varoitukset)
                                                (dissoc varoitukset id)
                                                (assoc varoitukset id rivin-varoitukset)))))
                       (swap! huomautukset (fn [huomautukset]
                                             (let [uusi-rivi (get uudet-tiedot id)
                                                   rivin-huomautukset (when-not (:poistettu uusi-rivi)
                                                                        (validointi/validoi-rivi uudet-tiedot uusi-rivi skeema :huomauta))]
                                               (if (empty? rivin-huomautukset)
                                                 (dissoc huomautukset id)
                                                 (assoc huomautukset id rivin-huomautukset))))))
                     (when muutos
                       (muutos ohjaus))))
        ;; Peruu yhden muokkauksen
        peru! (fn []
                (when-not (empty? @historia)
                  (let [[muok virh var huom jarj] (peek @historia)]
                    (reset! muokatut muok)
                    (reset! virheet virh)
                    (reset! varoitukset var)
                    (reset! huomautukset huom)
                    (reset! jarjestys jarj))
                  (swap! historia pop)
                  (when muutos
                    (muutos ohjaus))))
        maarita-valiotsikoiden-alkutila! (fn []
                                           (when (and (not @valiotsikoiden-alkutila-maaritelty?)
                                                      salli-valiotsikoiden-piilotus?
                                                      (some? tiedot))
                                             (let [tila (if (= valiotsikoiden-alkutila :kaikki-kiinni)
                                                          (set (keep #(when (otsikko? %)
                                                                        (get-in % [:optiot :id]))
                                                                     tiedot))
                                                          #{})]
                                               (reset! piilotetut-valiotsikot tila)
                                               (reset! valiotsikoiden-alkutila-maaritelty? true))))
        nollaa-muokkaustiedot! (fn []
                                 (swap! muokkauksessa-olevat-gridit disj komponentti-id)
                                 (reset! virheet {})
                                 (reset! varoitukset {})
                                 (reset! huomautukset {})
                                 (reset! muokatut nil)
                                 (reset! jarjestys nil)
                                 (reset! historia nil)
                                 (reset! viime-assoc nil)
                                 (reset! uusi-id 0)
                                 ;; Valittua riviä ei nollata, koska se ei ole osa muokkaustilaa.
                                 ;; Lisäksi koska propsien muuttuminen suorittaa tämän,
                                 ;; niin valittu rivi ja näkyvissä oleva infolaatikko häviävät,
                                 ;; vaikkei gridin data oleellisesti muuttuisikaan.
                                 (reset! tallennus-kaynnissa false))
        aloita-muokkaus! (fn [tiedot]
                           (reset! vetolaatikot-auki #{}) ; sulje vetolaatikot
                           (nollaa-muokkaustiedot!)
                           (swap! muokkauksessa-olevat-gridit conj komponentti-id)
                           (loop [muok {}
                                  jarj []
                                  [r & rivit] ((or aloita-muokkaus-fn identity) tiedot)]
                             (if-not r
                               (do
                                 (reset! muokatut muok)
                                 (reset! jarjestys jarj))
                               (if (otsikko? r)
                                 (recur muok
                                        (conj jarj r)
                                        rivit)
                                 (let [id (tunniste r)]
                                   (recur (assoc muok
                                            id (assoc r :koskematon true))
                                          (conj jarj id)
                                          rivit)))))
                           nil)
        kiinnita-otsikkorivi? (atom false) ;; Jos true, otsikkorivi naulataan kiinni selaimen yläreunaan scrollatessa
        kiinnitetyn-otsikkorivin-leveys (atom 0)
        maarita-kiinnitetyn-otsikkorivin-leveys (fn [this]
                                                  (reset! kiinnitetyn-otsikkorivin-leveys (dom/elementin-leveys (r/dom-node this))))
        maarita-rendattavien-rivien-maara (fn [this]
                                            (when (and (pos? (dom/elementin-etaisyys-viewportin-alareunaan (r/dom-node this)))
                                                       (< @renderoi-max-rivia @rivien-maara))
                                              (swap! renderoi-max-rivia + renderoi-rivia-kerralla)))
        kasittele-otsikkorivin-kiinnitys (fn [this]
                                           (if (and
                                                 (empty? @vetolaatikot-auki) ;; Jottei naulattu otiskkorivi peitä sisältöä
                                                 (> (dom/elementin-korkeus (r/dom-node this)) @dom/korkeus)
                                                 (< (dom/elementin-etaisyys-viewportin-ylareunaan (r/dom-node this)) -20)
                                                 (pos? (dom/elementin-etaisyys-viewportin-ylareunaan-alareunasta (r/dom-node this))))
                                             (reset! kiinnita-otsikkorivi? true)
                                             (reset! kiinnita-otsikkorivi? false)))
        kasittele-scroll-event (fn [this _]
                                 (maarita-rendattavien-rivien-maara this)
                                 (kasittele-otsikkorivin-kiinnitys this))
        kasittele-resize-event (fn [this _]
                                 (maarita-kiinnitetyn-otsikkorivin-leveys this)
                                 (kasittele-otsikkorivin-kiinnitys this))]

    (when-let [ohj (:ohjaus opts)]
      (aseta-grid ohj ohjaus))

    (when muokkaa-aina
      (aloita-muokkaus! tiedot))

    (komp/luo
      (komp/sisaan (fn []
                     (maarita-valiotsikoiden-alkutila!)
                     (when infolaatikon-tila-muuttui
                       (infolaatikon-tila-muuttui false))))
      (komp/ulos #(when infolaatikon-tila-muuttui
                    (infolaatikon-tila-muuttui false)))
      (komp/dom-kuuntelija js/window
                           EventType/SCROLL kasittele-scroll-event
                           EventType/RESIZE kasittele-resize-event)

      {:component-will-receive-props
       (fn [this & [_ _ _ tiedot]]
         ;; jos gridin data vaihtuu, muokkaustila on peruttava, jotta uudet datat tulevat näkyviin
         (nollaa-muokkaustiedot!)
         (maarita-valiotsikoiden-alkutila!)
         (when muokkaa-aina
           (aloita-muokkaus! tiedot))
         (reset! rivien-maara (count tiedot))
         (maarita-rendattavien-rivien-maara this))

       :component-did-mount
       (fn [this _]
         (maarita-kiinnitetyn-otsikkorivin-leveys this)
         (maarita-rendattavien-rivien-maara this))

       :component-will-unmount
       (fn []
         (nollaa-muokkaustiedot!))}
      (fnc [{:keys [otsikko tallenna peruuta voi-poistaa? voi-lisata? rivi-klikattu
                    piilota-toiminnot? nayta-toimintosarake? rivin-infolaatikko mahdollista-rivin-valinta?
                    muokkaa-footer muokkaa-aina rivin-luokka uusi-rivi tyhja vetolaatikot
                    rivi-valinta-peruttu korostustyyli max-rivimaara max-rivimaaran-ylitys-viesti] :as opts}
            skeema alkup-tiedot]
        (let [skeema (skeema/laske-sarakkeiden-leveys (keep identity skeema))
              mahdollista-rivin-valinta? (or mahdollista-rivin-valinta? false)
              muuta-gridia-muokataan? (and
                                        (>= (count @muokkauksessa-olevat-gridit) 1)
                                        (not (@muokkauksessa-olevat-gridit komponentti-id)))
              colspan (if (or piilota-toiminnot? (nil? tallenna))
                        (count skeema)
                        (inc (count skeema)))
              muokataan (not (nil? @muokatut))
              tiedot (if max-rivimaara
                       (take max-rivimaara alkup-tiedot)
                       alkup-tiedot)
              luokat (if @infolaatikko-nakyvissa?
                       (conj luokat "livi-grid-infolaatikolla")
                       luokat)
              muokattu? (not (empty? @historia))]
          [:div.panel.panel-default.livi-grid {:id (:id opts)
                                               :class (clojure.string/join " " luokat)}
           (muokkauspaneeli {:nayta-otsikko? true :muokataan muokataan :tallenna tallenna
                             :tiedot tiedot :muuta-gridia-muokataan? muuta-gridia-muokataan?
                             :tallennus-ei-mahdollinen-tooltip tallennus-ei-mahdollinen-tooltip
                             :muokattu? muokattu? :voi-lisata? voi-lisata? :ohjaus ohjaus
                             :opts opts :muokkaa-aina muokkaa-aina :virheet virheet
                             :muokatut muokatut :tallennus-kaynnissa tallennus-kaynnissa
                             :tallenna-vain-muokatut tallenna-vain-muokatut
                             :nollaa-muokkaustiedot! nollaa-muokkaustiedot!
                             :aloita-muokkaus! aloita-muokkaus! :peru! peru!
                             :peruuta peruuta :otsikko otsikko})
           [:div.panel-body
            (when @kiinnita-otsikkorivi?
              ^{:key "kiinnitettyotsikko"}
              [:table.grid
               {:style {:position "fixed"
                        :top 0
                        :width @kiinnitetyn-otsikkorivin-leveys
                        :z-index 200}}
               [otsikkorivi {:opts opts :skeema skeema
                             :nayta-toimintosarake? nayta-toimintosarake? :piilota-toiminnot? piilota-toiminnot?
                             :tallenna tallenna}]])
            (if (nil? tiedot)
              (ajax-loader)
              ^{:key "taulukkodata"}
              [:table.grid
               [otsikkorivi {:opts opts :skeema skeema
                             :nayta-toimintosarake? nayta-toimintosarake? :piilota-toiminnot? piilota-toiminnot?
                             :tallenna tallenna}]
               [:tbody
                (if muokataan
                  (muokkauskayttoliittyma {:muokatut muokatut :jarjestys jarjestys :colspan colspan
                                           :tyhja tyhja :virheet virheet :varoitukset varoitukset
                                           :huomautukset huomautukset :fokus fokus :ohjaus ohjaus
                                           :vetolaatikot vetolaatikot :muokkaa! muokkaa!
                                           :voi-poistaa? voi-poistaa?
                                           :salli-valiotsikoiden-piilotus? salli-valiotsikoiden-piilotus?
                                           :esta-poistaminen? esta-poistaminen?
                                           :esta-poistaminen-tooltip esta-poistaminen-tooltip
                                           :piilota-toiminnot? piilota-toiminnot?
                                           :voi-muokata-rivia? voi-muokata-rivia?
                                           :tiedot tiedot
                                           :piilotetut-valiotsikot piilotetut-valiotsikot
                                           :skeema skeema :vetolaatikot-auki vetolaatikot-auki
                                           :tallennus-kaynnissa? tallennus-kaynnissa})
                  (nayttokayttoliittyma {:renderoi-max-rivia renderoi-max-rivia
                                         :tiedot tiedot :colspan colspan :tyhja tyhja
                                         :tunniste tunniste :ohjaus ohjaus
                                         :salli-valiotsikoiden-piilotus? salli-valiotsikoiden-piilotus?
                                         :vetolaatikot vetolaatikot :tallenna tallenna
                                         :rivi-klikattu rivi-klikattu
                                         :rivin-infolaatikko rivin-infolaatikko
                                         :rivin-luokka rivin-luokka
                                         :valittu-rivi valittu-rivi
                                         :piilotetut-valiotsikot piilotetut-valiotsikot
                                         :infolaatikon-tila-muuttui infolaatikon-tila-muuttui
                                         :rivi-valinta-peruttu rivi-valinta-peruttu
                                         :mahdollista-rivin-valinta? mahdollista-rivin-valinta?
                                         :piilota-toiminnot? piilota-toiminnot?
                                         :infolaatikko-nakyvissa? infolaatikko-nakyvissa?
                                         :nayta-toimintosarake? nayta-toimintosarake?
                                         :skeema skeema :vetolaatikot-auki vetolaatikot-auki}))]])

            (when (and max-rivimaara (> (count alkup-tiedot) max-rivimaara))
              [:div.alert-warning (or max-rivimaaran-ylitys-viesti
                                      "Liikaa hakutuloksia, rajaa hakua")])
            (when (and muokataan muokkaa-footer)
              [muokkaa-footer ohjaus])]
           ;; Taulukon allekin muokkaustoiminnot jos rivejä
           ;; yli rajamäärän (joko muokkaus- tai näyttötila), eikä tätä ole erikseen estetty
           (when (and (> (count (or @muokatut tiedot))
                         +rivimaara-jonka-jalkeen-napit-alaskin+)
                      (not ei-footer-muokkauspaneelia?))
             [:span.gridin-napit-alhaalla
              (muokkauspaneeli {:nayta-otsikko? false :muokataan muokataan :tallenna tallenna
                                :tiedot tiedot :muuta-gridia-muokataan? muuta-gridia-muokataan?
                                :tallennus-ei-mahdollinen-tooltip tallennus-ei-mahdollinen-tooltip
                                :muokattu? muokattu? :voi-lisata? voi-lisata? :ohjaus ohjaus
                                :opts opts :muokkaa-aina muokkaa-aina :virheet virheet
                                :muokatut muokatut :tallennus-kaynnissa tallennus-kaynnissa
                                :tallenna-vain-muokatut tallenna-vain-muokatut
                                :nollaa-muokkaustiedot! nollaa-muokkaustiedot!
                                :aloita-muokkaus! aloita-muokkaus! :peru! peru!
                                :peruuta peruuta :otsikko otsikko})])])))))

(defn otsikkorivin-tiedot [otsikko maara]
  (str otsikko
       " ("
       maara
       (when (not= maara 0)
         "kpl")
       ")"))