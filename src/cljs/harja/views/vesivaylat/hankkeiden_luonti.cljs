(ns harja.views.vesivaylat.hankkeiden-luonti
  (:require [harja.ui.komponentti :as komp]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.hankkeiden-luonti :as tiedot]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
            [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ikonit]
            [harja.domain.oikeudet :as oikeudet]))

(defn luontilomake [e! {:keys [valittu-hanke tallennus-kaynnissa?] :as app}]
      [:div
       [napit/takaisin "Takaisin luetteloon"
        #(e! (tiedot/->ValitseHanke nil))
        {:disabled tallennus-kaynnissa?}]
       [harja.ui.debug/debug valittu-hanke]
       [lomake/lomake
        {:otsikko (if (:id valittu-hanke)
                    "Muokkaa hanketta"
                    "Luo uusi hanke")
         :muokkaa! #(e! (tiedot/->HankettaMuokattu (lomake/ilman-lomaketietoja %)))
         :voi-muokata? #(oikeudet/hallinta-vesivaylat)
         :footer-fn (fn [hanke]
                      [napit/tallenna
                       "Tallenna hanke"
                       #(e! (tiedot/->TallennaHanke (lomake/ilman-lomaketietoja hanke)))
                       {:ikoni (ikonit/tallenna)
                        :disabled (or tallennus-kaynnissa?
                                      (not (lomake/voi-tallentaa? hanke)))
                        :tallennus-kaynnissa? tallennus-kaynnissa?}])}
        [{:otsikko "Nimi" :nimi :nimi :tyyppi :string}]
        valittu-hanke]])

(defn hankegrid [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeHankkeet)))
    (fn [e! {:keys [haetut-hankkeet hankkeiden-haku-kaynnissa?] :as app}]
      [:div
       [napit/uusi "Lisää hanke"
        #(e! (tiedot/->UusiHanke))
        {:disabled (nil? haetut-hankkeet)}]
       [grid/grid
        {:otsikko (if (and (some? haetut-hankkeet) hankkeiden-haku-kaynnissa?)
                    [ajax-loader-pieni "Päivitetään listaa"] ;; Listassa on jo jotain, mutta sitä päivitetään
                    "Harjaan perustetut vesiväylähankkeet")
         :tunniste :id
         :tyhja (if (nil? haetut-hankkeet)
                  [ajax-loader "Haetaan hankkeita"]
                  "Hankkeita ei löytynyt")
         :rivi-klikattu #(e! (tiedot/->ValitseHanke %))}
        [{:otsikko "Nimi" :nimi :nimi}
         {:otsikko "Alku" :nimi :alkupvm}
         {:otsikko "Loppu" :nimi :loppupvm}]
        haetut-hankkeet]])))

(defn vesivaylahankkeiden-luonti* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! {valittu-hanke :valittu-hanke :as app}]
      (if valittu-hanke
        [luontilomake e! app]
        [hankegrid e! app]))))

(defn vesivaylahankkeiden-luonti []
      [tuck tiedot/tila vesivaylahankkeiden-luonti*])