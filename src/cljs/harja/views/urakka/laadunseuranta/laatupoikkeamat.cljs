(ns harja.views.urakka.laadunseuranta.laatupoikkeamat
  "Listaa urakan laatupoikkeamat, jotka voivat olla joko tarkastukseen liittyviä tai irrallisia."
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.kommentit :as kommentit]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.tiedot.urakka.laadunseuranta.sanktiot :as sanktiot]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.napit :as napit]
            [harja.domain.roolit :as roolit]
            [harja.domain.laadunseuranta :refer [validi-laatupoikkeama?]]
            [harja.tiedot.istunto :as istunto]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]]
            [harja.views.kartta :as kartta])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defonce listaus (atom :kaikki))

(defonce urakan-laatupoikkeamat
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               [alku loppu] @tiedot-urakka/valittu-aikavali
               laadunseurannassa? @laadunseuranta/laadunseurannassa?
               valilehti (nav/valittu-valilehti :laadunseuranta)
               listaus @listaus]
              {:nil-kun-haku-kaynnissa? true}
              (log "urakka-id: " urakka-id "; alku: " alku "; loppu: " loppu "; laadunseurannassa? " laadunseurannassa? "; valilehti: " (pr-str valilehti) "; listaus: " (pr-str listaus))
              (when (and laadunseurannassa? (= :laatupoikkeamat valilehti)
                         urakka-id alku loppu)
                (laatupoikkeamat/hae-urakan-laatupoikkeamat listaus urakka-id alku loppu))))


(defonce valittu-laatupoikkeama-id (atom nil))

(defn uusi-laatupoikkeama []
  {:tekija (roolit/osapuoli @istunto/kayttaja (:id @nav/valittu-urakka))})

(defonce valittu-laatupoikkeama
  (reaction<! [id @valittu-laatupoikkeama-id]
              {:nil-kun-haku-kaynnissa? true}
              (when id
                (go (let [laatupoikkeama (if (= :uusi id)
                                           (uusi-laatupoikkeama)
                                           (<! (laatupoikkeamat/hae-laatupoikkeaman-tiedot (:id @nav/valittu-urakka) id)))]
                      (-> laatupoikkeama

                          ;; Tarvitsemme urakan liitteen linkitystä varten

                          (assoc :urakka (:id @nav/valittu-urakka))
                          (assoc :sanktiot (into {}
                                                 (map (juxt :id identity) (:sanktiot laatupoikkeama))))))))))

(defn laatupoikkeamalistaus
  "Listaa urakan laatupoikkeamat"
  []

  [:div.laatupoikkeamat
   [urakka-valinnat/urakan-hoitokausi @nav/valittu-urakka]
   [yleiset/pudotusvalikko
    "Näytä laatupoikkeamat"
    {:valinta    @listaus
     :valitse-fn #(reset! listaus %)
     :format-fn  #(case %
                   :kaikki "Kaikki"
                   :kasitellyt "Käsitellyt (päätös tehty)"
                   :selvitys "Odottaa urakoitsijan selvitystä"
                   :omat "Minun kirjaamat / kommentoimat")}

    [:kaikki :selvitys :kasitellyt :omat]]

   [urakka-valinnat/aikavali]

   (when @laatupoikkeamat/voi-kirjata?
     [napit/uusi "Uusi laatupoikkeama" #(reset! valittu-laatupoikkeama-id :uusi)])

   [grid/grid
    {:otsikko "Laatu\u00ADpoikkeamat" :rivi-klikattu #(reset! valittu-laatupoikkeama-id (:id %))
     :tyhja   "Ei laatupoikkeamia."}
    [{:otsikko "Päivä\u00ADmäärä" :nimi :aika :fmt pvm/pvm-aika :leveys 1}
     {:otsikko "Koh\u00ADde" :nimi :kohde :leveys 1}
     {:otsikko "Kuvaus" :nimi :kuvaus :leveys 3}
     {:otsikko "Tekijä" :nimi :tekija :leveys 1 :fmt laatupoikkeamat/kuvaile-tekija}
     {:otsikko "Päätös" :nimi :paatos :fmt laatupoikkeamat/kuvaile-paatos :leveys 2}]  ;; Päätös
    @urakan-laatupoikkeamat]])

(defn paatos?
  "Onko annetussa laatupoikkeamassa päätös?"
  [laatupoikkeama]
  (not (nil? (get-in laatupoikkeama [:paatos :paatos]))))


(defn tallenna-laatupoikkeama
  "Tallentaa annetun laatupoikkeaman palvelimelle. Lukee serveriltä palautuvan laatupoikkeaman ja 
   päivittää/lisää sen nykyiseen listaukseen, jos se kuuluu listauksen aikavälille."
  [laatupoikkeama]
  (let [laatupoikkeama (assoc laatupoikkeama
                              :sanktiot (vals (:sanktiot laatupoikkeama)))]
    (go
      (let [tulos (<! (laatupoikkeamat/tallenna-laatupoikkeama laatupoikkeama))]
        (if (k/virhe? tulos)
          ;; Palautetaan virhe, jotta nappi näyttää virheviestin
          tulos

          ;; Laatupoikkeama tallennettu onnistuneesti, päivitetään sen tiedot
          (let [uusi-laatupoikkeama tulos
                aika (:aika uusi-laatupoikkeama)
                [alku loppu] @tiedot-urakka/valittu-aikavali]
            (when (and (pvm/sama-tai-jalkeen? aika alku)
                       (pvm/sama-tai-ennen? aika loppu))
              ;; Kuuluu aikavälille, lisätään tai päivitetään
              (if (:id laatupoikkeama)
                ;; Päivitetty olemassaolevaa
                (swap! urakan-laatupoikkeamat
                       (fn [laatupoikkeamat]
                         (mapv (fn [h]
                                 (if (= (:id h) (:id uusi-laatupoikkeama))
                                   uusi-laatupoikkeama
                                   h)) laatupoikkeamat)))
                ;; Luotu uusi
                (swap! urakan-laatupoikkeamat
                       conj uusi-laatupoikkeama)))
            true))))))

(defn kuvaile-sanktion-sakko [{:keys [sakko? summa indeksi]}]
  (if-not sakko?
    "Muistutus"
    (str "Sakko " (fmt/euro-opt summa)
         (when indeksi (str " (" indeksi ")")))))


(defn laatupoikkeaman-sanktiot
  "Näyttää muokkaus-gridin laatupoikkeaman sanktioista. Ottaa kaksi parametria, sanktiot (muokkaus-grid muodossa)
sekä sanktio-virheet atomin, jonne yksittäisen sanktion virheet kirjoitetaan (id avaimena)"
  [_ _]
  (let [g (grid/grid-ohjaus)]
    (fn [sanktiot-atom sanktio-virheet]
      [:div.sanktiot
       [grid/muokkaus-grid
        {:tyhja        "Ei kirjattuja sanktioita."
         :lisaa-rivi   " Lisää sanktio"
         :ohjaus       g
         :uusi-rivi    (fn [rivi]
                         (assoc rivi :sakko? true))}

        [{:otsikko "Perintäpvm" :nimi :perintapvm :tyyppi :pvm :leveys 1.5
          :validoi [[:ei-tyhja "Anna sanktion päivämäärä"]]}
         {:otsikko       "Laji" :tyyppi :valinta :leveys 0.85
          :nimi          :laji
          :aseta         #(assoc %1
                           :laji %2
                           :tyyppi nil)
          :valinnat      [:A :B :C :muistutus]
          :valinta-nayta #(case %
                           :A "A"
                           :B "B"
                           :C "C"
                           "- valitse -")
          :validoi       [[:ei-tyhja "Valitse laji"]]}
         {:otsikko       "Tyyppi" :nimi :tyyppi :leveys 3
          :tyyppi        :valinta
          :aseta         (fn [sanktio {tpk :toimenpidekoodi :as  tyyppi}]
                           ;; Asetetaan uusi sanktiotyyppi sekä toimenpideinstanssi, joka tähän kuuluu
                           (log "VALITTIIN TYYPPI: " (pr-str tyyppi))
                           (assoc sanktio
                             :tyyppi tyyppi
                             :toimenpideinstanssi
                             (when tpk
                               (:tpi_id (tiedot-urakka/urakan-toimenpideinstanssi-toimenpidekoodille tpk)))))
          :valinnat-fn   #(sanktiot/lajin-sanktiotyypit (:laji %))
          :valinta-nayta :nimi
          :validoi       [[:ei-tyhja "Valitse sanktiotyyppi"]]
          }

         {:otsikko "Sakko/muistutus"
          :nimi          :sakko?
          :tyyppi        :valinta
          :hae           #(if (:sakko? %) :sakko :muistutus)
          :aseta         (fn [rivi arvo]
                           (let [sakko? (= :sakko arvo)]
                             (assoc rivi
                                    :sakko? sakko?
                                    :summa (when sakko? (:summa rivi))
                                    :toimenpideinstanssi (when sakko?
                                                           (:toimenpideinstanssi rivi)))))
          :valinnat      [:sakko :muistutus]
          :valinta-nayta #(case %
                            :sakko "Sakko"
                            :muistutus "Muistutus")
          :leveys    2}

         {:otsikko       "Toimenpide"
          :nimi          :toimenpideinstanssi
          :tyyppi        :valinta
          :valinta-arvo  :tpi_id
          :valinta-nayta :tpi_nimi
          :valinnat-fn   #(when (:sakko? %) @tiedot-urakka/urakan-toimenpideinstanssit)
          :leveys    3
          :validoi       [[:ei-tyhja "Valitse toimenpide, johon sakko liittyy"]]
          :muokattava? :sakko?}

         {:otsikko    "Sakko (€)"
          :tyyppi     :numero
          :nimi       :summa
          :leveys     1.5
          :validoi    [[:ei-tyhja "Anna sakon summa euroina"]]
          :muokattava? :sakko?}

         {:otsikko       "Indeksi" :nimi :indeksi :leveys 1.5
          :tyyppi        :valinta
          :valinnat      ["MAKU 2005" "MAKU 2010"] ;; FIXME: haetaanko indeksit tiedoista?
          :valinta-nayta #(or % "Ei sidota indeksiin")
          :palstoja 1}
         
         ]

        sanktiot-atom]])))

(defn laatupoikkeama [asetukset laatupoikkeama]
  (let [sanktio-virheet (atom {})
        alkuperainen @laatupoikkeama]
    (komp/luo
      (fn [asetukset laatupoikkeama]
        (let [muokattava? (constantly (not (paatos? alkuperainen)))
              uusi? (not (:id alkuperainen))]

          [:div.laatupoikkeama
           [napit/takaisin "Takaisin laatupoikkeamaluetteloon" #(reset! valittu-laatupoikkeama-id nil)]
           
           [lomake/lomake
            {:otsikko "Laatupoikkeaman tiedot"
             :muokkaa!     #(reset! laatupoikkeama %)
             :voi-muokata? @laatupoikkeamat/voi-kirjata?
             :footer       [napit/palvelinkutsu-nappi
                            ;; Määritellään "verbi" tilan mukaan, jos päätöstä ei ole: Tallennetaan laatupoikkeama,
                            ;; jos päätös on tässä muokkauksessa lisätty: Lukitaan laatupoikkeama
                            (cond
                              (and (not (paatos? alkuperainen))
                                   (paatos? @laatupoikkeama))
                              "Tallenna ja lukitse laatupoikkeama"

                              :default
                              "Tallenna laatupoikkeama")

                            #(tallenna-laatupoikkeama @laatupoikkeama)
                            {:ikoni        (ikonit/tallenna)
                             :disabled     (not (validi-laatupoikkeama? @laatupoikkeama))
                             :kun-onnistuu (fn [_] (reset! valittu-laatupoikkeama-id nil))}]}

            [{:otsikko     "Päivämäärä ja aika"
              :pakollinen? true
              :tyyppi      :pvm-aika
              :nimi        :aika
              :validoi     [[:ei-tyhja "Anna laatupoikkeaman päivämäärä ja aika"]]
              :varoita     [[:urakan-aikana-ja-hoitokaudella]]
              :palstoja  1}

             
             {:otsikko     "Kohde" :tyyppi :string :nimi :kohde
              :palstoja 1
              :pakollinen? true
              :muokattava? muokattava?
              :validoi     [[:ei-tyhja "Anna laatupoikkeaman kohde"]]}

             {:otsikko       "Tekijä" :nimi :tekija
              :uusi-rivi? true
              :tyyppi        :valinta
              :valinnat      [:tilaaja :urakoitsija :konsultti]
              :valinta-nayta #(case %
                                :tilaaja "Tilaaja"
                                :urakoitsija "Urakoitsija"
                                :konsultti "Konsultti"
                                "- valitse osapuoli -")
              :palstoja 1
              :muokattava?   muokattava?
              :validoi       [[:ei-tyhja "Valitse laatupoikkeaman tehnyt osapuoli"]]}

             (when-not (= :urakoitsija (:tekija @laatupoikkeama))
               {:otsikko "Urakoitsijan selvitystä pyydetään"
                :nimi    :selvitys-pyydetty
                :tyyppi  :boolean})
             
             {:otsikko     "Kuvaus"
              :uusi-rivi? true
              :nimi :kuvaus
              :tyyppi :text
              :pakollinen? true
              :palstoja 2
              :validoi     [[:ei-tyhja "Kirjoita kuvaus"]] :pituus-max 4096
              :placeholder "Kirjoita kuvaus..." :koko [80 :auto]}


             
             {:otsikko     "Liitteet" :nimi :liitteet
              :palstoja 2
              :tyyppi :komponentti
              :komponentti [liitteet/liitteet {:urakka-id         (:id @nav/valittu-urakka)
                                               :uusi-liite-atom (r/wrap (:uusi-liite @laatupoikkeama)
                                                                        #(swap! laatupoikkeama assoc :uusi-liite %))
                                               :uusi-liite-teksti "Lisää liite laatupoikkeamaan"}
                            (:liitteet @laatupoikkeama)]}

             (when-not uusi?
               (lomake/ryhma
                "Kommentit"
                {:otsikko     "" :nimi :kommentit :tyyppi :komponentti
                 :komponentti [kommentit/kommentit {:voi-kommentoida? true
                                                    :voi-liittaa      true
                                                    :liita-nappi-teksti " Lisää liite kommenttiin"
                                                    :placeholder      "Kirjoita kommentti..."
                                                    :uusi-kommentti   (r/wrap (:uusi-kommentti @laatupoikkeama)
                                                                              #(swap! laatupoikkeama assoc :uusi-kommentti %))}
                               (:kommentit @laatupoikkeama)]}))

             ;; Päätös
             (when (:id alkuperainen)
               (lomake/ryhma
                "Käsittely ja päätös"
                 
                {:otsikko     "Käsittelyn pvm"
                 :nimi        :paatos-pvm
                 :hae         (comp :kasittelyaika :paatos) :aseta #(assoc-in %1 [:paatos :kasittelyaika] %2)
                 :tyyppi      :pvm-aika
                 :muokattava? muokattava?}

                {:otsikko       "Käsitelty" :nimi :kasittelytapa
                 :hae           (comp :kasittelytapa :paatos)
                 :aseta         #(assoc-in %1 [:paatos :kasittelytapa] %2)
                 :tyyppi        :valinta
                 :valinnat      [:tyomaakokous :puhelin :kommentit :muu]
                 :valinta-nayta #(if % (laatupoikkeamat/kuvaile-kasittelytapa %) "- valitse käsittelytapa -")
                 :palstoja 2
                 :muokattava?   muokattava?}

                (when (= :muu (:kasittelytapa (:paatos @laatupoikkeama)))
                  {:otsikko     "Muu käsittelytapa"
                   :nimi        :kasittelytapa-selite
                   :hae         (comp :muukasittelytapa :paatos)
                   :aseta       #(assoc-in %1 [:paatos :muukasittelytapa] %2)
                   :tyyppi      :string
                   :palstoja 2
                   :validoi     [[:ei-tyhja "Anna lyhyt kuvaus käsittelytavasta."]]
                   :muokattava? muokattava?})


                {:otsikko       "Päätös"
                 :nimi          :paatos-paatos
                 :tyyppi        :valinta
                 :valinnat      [:sanktio :ei_sanktiota :hylatty]
                 :hae           (comp :paatos :paatos)
                 :aseta         #(assoc-in %1 [:paatos :paatos] %2)
                 :valinta-nayta #(if % (laatupoikkeamat/kuvaile-paatostyyppi %) "- valitse päätös -")
                 :palstoja 2
                 :muokattava?   muokattava?}

                (when (:paatos (:paatos @laatupoikkeama))
                  {:otsikko     "Päätöksen selitys"
                   :nimi        :paatoksen-selitys
                   :tyyppi      :text
                   :hae         (comp :perustelu :paatos)
                   :koko        [80 :auto]
                   :palstoja 2
                   :aseta       #(assoc-in %1 [:paatos :perustelu] %2)
                   :muokattava? muokattava?
                   :validoi     [[:ei-tyhja "Anna päätöksen selitys"]]})


                (when (= :sanktio (:paatos (:paatos @laatupoikkeama)))
                  ;; FIXME: tarkista myös oikeus, urakanvalvoja... urakoitsija/konsultti EI saa päätöstä tehdä
                  {:otsikko     "Sanktiot"
                   :nimi        :sanktiot
                   :palstoja 4
                   :komponentti [laatupoikkeaman-sanktiot
                                 (r/wrap (:sanktiot @laatupoikkeama)
                                         #(swap! laatupoikkeama assoc :sanktiot %))
                                 sanktio-virheet]})
                ))]

            @laatupoikkeama]])))))

(defn laatupoikkeamat []
  (komp/luo
    (komp/sisaan-ulos #(do
                        (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                        (nav/vaihda-kartan-koko! :M))
                      #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))
    (fn []
     [:span.laatupoikkeamat
      [kartta/kartan-paikka]
      (if @valittu-laatupoikkeama
        [laatupoikkeama {} valittu-laatupoikkeama]
        [laatupoikkeamalistaus])])))
