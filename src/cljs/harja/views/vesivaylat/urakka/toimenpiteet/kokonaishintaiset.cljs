(ns harja.views.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.ui.otsikkopaneeli :refer [otsikkopaneeli]]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log]]
            [harja.ui.debug :refer [debug]]
            [harja.ui.kentat :as kentat]
            [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- ryhmittele-toimenpiteet-alueella [toimenpiteet]
  (let [toimenpiteet-ryhmiteltyna (group-by ::to/alue toimenpiteet)
        turvalaitetyypit (keys toimenpiteet-ryhmiteltyna)]
    (vec (mapcat #(-> (cons (grid/otsikko %)
                            (get toimenpiteet-ryhmiteltyna %))) turvalaitetyypit))))

(defn- toimenpiteet-turvalaitetyypilla [toimenpiteet turvalaitetyyppi]
  (filterv #(= (::to/turvalaitetyyppi %) turvalaitetyyppi) toimenpiteet))

(defn- suodata-ja-ryhmittele-toimenpiteet-gridiin [toimenpiteet turvalaitetyyppi]
  (-> toimenpiteet
      (toimenpiteet-turvalaitetyypilla turvalaitetyyppi)
      (ryhmittele-toimenpiteet-alueella)))

(defn- alueen-toimenpiteet [toimenpiteet alue]
  (filter #(= (::to/alue %) alue) toimenpiteet))

(defn- paneelin-otsikon-sisalto [sijainnit e!]
  [grid/grid
   {:tunniste ::to/id
    :tyhja (if (nil? sijainnit)
             [ajax-loader "Haetaan toimenpiteitä"]
             "Ei toimenpiteitä")}
   [{:otsikko "Työluokka" :nimi ::to/tyoluokka :leveys 10}
    {:otsikko "Toimenpide" :nimi ::to/toimenpide :leveys 10}
    {:otsikko "Päivämäärä" :nimi ::to/pvm :fmt pvm/pvm-opt :leveys 10}
    {:otsikko "Turvalaite" :nimi ::to/turvalaite :leveys 10}
    {:otsikko "Vikakorjaus" :nimi ::to/vikakorjaus :fmt fmt/totuus :leveys 5}
    {:otsikko "Valitse" :nimi :valinta :tyyppi :komponentti :tasaa :keskita
     :komponentti (fn [rivi]
                    ;; TODO Olisi kiva jos otettaisiin click koko solun alueelta
                    ;; Siltatarkastuksissa käytetty radio-elementti expandoi labelin
                    ;; koko soluun. Voisi ehkä käyttää myös checkbox-elementille
                    ;; Täytyy kuitenkin varmistaa, ettei mikään mene rikki
                    [kentat/tee-kentta
                     {:tyyppi :checkbox}
                     (r/wrap (:valittu? rivi)
                             (fn [uusi]
                               (e! (tiedot/->ValitseToimenpide {:id (::to/id rivi)
                                                                :valinta uusi}))))])
     :leveys 5}]
   sijainnit])

(defn- paneelin-otsikko [otsikko maara]
  (str otsikko
       " ("
       maara
       (when (not= maara 0)
         "kpl")
       ")"))

(defn- luo-otsikkorivit [toimenpiteet e!]
  (let [turvalaitetyypit (keys (group-by ::to/turvalaitetyyppi toimenpiteet))]
    (vec (mapcat
           (fn [turvalaitetyyppi]
             [turvalaitetyyppi
              (paneelin-otsikko turvalaitetyyppi
                                (count (toimenpiteet-turvalaitetyypilla
                                         toimenpiteet
                                         turvalaitetyyppi)))
              (fn [] [paneelin-otsikon-sisalto
                      (suodata-ja-ryhmittele-toimenpiteet-gridiin
                        toimenpiteet
                        turvalaitetyyppi)
                      e!])])
           turvalaitetyypit))))

(defn kokonaishintaiset-toimenpiteet* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! {:keys [toimenpiteet] :as app}]
      [:div
       [debug app]

       [:div {:style {:padding "10px"}}
        [:img {:src "images/harja_favicon.png"}]
        [:div {:style {:color "orange"}} "Työmaa"]]

       (into [otsikkopaneeli {:paneelikomponentit [;; FIXME Ei osu täysin kohdalleen eri taulukon leveyksillä :(
                                                   {:sijainti "94.3%"
                                                    :sisalto (fn [{:keys [tunniste]}]
                                                               (let [turvalaitetyypin-toimenpiteet (toimenpiteet-turvalaitetyypilla toimenpiteet tunniste)
                                                                     kaikki-valittu? (every? true? (map :valittu? turvalaitetyypin-toimenpiteet))]
                                                                 [kentat/tee-kentta
                                                                  {:tyyppi :checkbox}
                                                                  ;; TODO Lisää välitila jos osa valittu
                                                                  ;; TODO Mahdollista kaikkien valinta tästä
                                                                  (atom kaikki-valittu?)]))}]}]
             (luo-otsikkorivit toimenpiteet e!))])))

(defn kokonaishintaiset-toimenpiteet []
  [tuck tiedot/tila kokonaishintaiset-toimenpiteet*])