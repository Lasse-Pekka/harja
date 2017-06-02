(ns harja.ui.leijuke
  "Yleinen leijuke -komponentti. Leijuke on muun sisällön päälle tuleva absoluuttisesti
  positioitu pieni elementti, esim. lyhyt lomake."
  (:require [reagent.core :as r]
            [harja.ui.napit :as napit]
            [harja.ui.komponentti :as komp]
            [harja.ui.dom :as dom]
            [harja.fmt :as fmt]
            [goog.events.EventType :as EventType]
            [harja.loki :refer [log]]))


(def avautumissuunta-tyyli
  {:alas-vasen {:top "calc(100% - 1px)"
                :right "0"
                :bottom "auto"}

   :alas-oikea {:top "calc(100% - 1px)"
                :bottom "auto"}

   :ylos-vasen {:bottom "calc(100% - 1px)"
                :right "0"
                :top "auto"}

   :ylos-oikea {:bottom "calc(100% - 1px)"
                :top "auto"}})

(defn- maarita-suunta [komponentti]
  (let [wrapper-node (r/dom-node komponentti)
        komponentti-node (.-firstChild wrapper-node)
        [x y _ _] (dom/sijainti wrapper-node)
        [_ _ leveys korkeus :as sij] (dom/sijainti komponentti-node)
        viewport-korkeus @dom/korkeus
        etaisyys-alareunaan (dom/elementin-etaisyys-viewportin-alareunaan komponentti-node)
        etaisyys-ylareunaan (dom/elementin-etaisyys-viewportin-ylareunaan komponentti-node)
        etaisyys-oikeaan-reunaan (dom/elementin-etaisyys-viewportin-oikeaan-reunaan komponentti-node)
        suunta (if (< viewport-korkeus (+ y korkeus))
                 (if (< etaisyys-oikeaan-reunaan leveys)
                   :ylos-vasen
                   :ylos-oikea)
                 (if (< etaisyys-oikeaan-reunaan leveys)
                   :alas-vasen
                   :alas-oikea))]
    suunta))


(defn leijuke
  "Leijuke komponentti annetulla sisällöllä.
  Optiot:

  :sulje!   funktio, jota kutsutaan kun leijukkeen sulje-raksia tai ESC-näppäintä painetaan
  :luokka   optionaalinen lisäluokka
  "
  [optiot sisalto]
  (let [suunta (r/atom nil)
        paivita-suunta! (fn [this _]
                                     (reset! suunta
                                             (maarita-suunta this)))
        sulje-esc-napilla! (fn [_ event]
                             (when (= 27 (.-keyCode event))
                               ((:sulje! optiot))))]
    (komp/luo
     (komp/piirretty paivita-suunta!)
     (komp/dom-kuuntelija js/window
                          EventType/SCROLL paivita-suunta!
                          EventType/RESIZE paivita-suunta!
                          EventType/KEYUP sulje-esc-napilla!)
     (fn [{:keys [luokka sulje!] :as optiot} sisalto]
       (let [suunta @suunta]
         [:div.leijuke-wrapper
          [:div.leijuke {:class luokka
                         :style
                         (if suunta
                           (avautumissuunta-tyyli suunta)
                           (merge
                            (avautumissuunta-tyyli :alas-vasen)
                            {:visibility "hidden"}))}
           [napit/sulje-ruksi sulje!]
           sisalto]])))))
