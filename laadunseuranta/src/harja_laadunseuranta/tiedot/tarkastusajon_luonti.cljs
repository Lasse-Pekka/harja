(ns harja-laadunseuranta.tiedot.tarkastusajon-luonti
  (:require [harja-laadunseuranta.ui.paanavigointi :refer [paanavigointi]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.tiedot.comms :as comms]
            [cljs.core.async :refer [<! timeout]]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]
            [harja-laadunseuranta.utils :as utils])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn- pysayta-tarkastusajo [sovellus]
  (assoc sovellus
    ;; Tarkastusajon perustiedot
    :valittu-urakka nil
    :tarkastusajo-id nil
    :tallennus-kaynnissa false
    :tarkastusajo-paattymassa false
    ;; Ajonaikaiset tiedot
    :reittipisteet []
    :tr-tiedot {:tr-osoite {:tie nil
                            :aosa nil
                            :aet nil}
                :talvihoitoluokka nil}
    ;; Havainnot
    :jatkuvat-havainnot #{}
    ;; Mittaukset
    :mittaussyotto {:nykyinen-syotto nil
                    :syotot []}
    :soratiemittaussyotto {:tasaisuus 5
                           :kiinteys 5
                           :polyavyys 5}
    :mitttaustyyppi nil
    ;; Lomake
    :havaintolomake-auki? false
    :havaintolomakedata {:kayttajanimi nil
                         :tr-osoite nil
                         :aikaleima nil
                         :laadunalitus? false
                         :kuvaus ""
                         :kuva nil}
    ;; Kartta
    :kirjauspisteet []
    ;; Muut
    :ilmoitukset []))

(defn- tarkastusajo-kayntiin [sovellus ajo-id]
  (assoc sovellus
    :tarkastusajo-id ajo-id
    :reittipisteet []
    :kirjauspisteet []
    :tallennus-kaynnissa true))

(defn- tarkastusajo-kayntiin! [ajo-id]
  (swap! s/sovellus #(tarkastusajo-kayntiin % ajo-id)))

(defn- pysayta-tarkastusajo! []
  (swap! s/sovellus pysayta-tarkastusajo))

(defn luo-ajo! []
  (go-loop []
    (if-let [id (-> (<! (comms/luo-ajo!)) :ok :id)]
      (tarkastusajo-kayntiin! id)
      ;; yritä uudleleen kunnes onnistuu
      (do (<! (timeout 1000))
          (recur)))))

(defn paattaminen-peruttu! []
  (reset! s/tarkastusajo-paattymassa false))

(defn aseta-ajo-paattymaan! []
  (reset! s/tarkastusajo-paattymassa true))

(defn paata-ajo! []
  (go-loop []
    (if (<! (comms/paata-ajo! @s/tarkastusajo-id @s/valittu-urakka))
      (pysayta-tarkastusajo!)

      ;; yritä uudelleen kunnes onnistuu, spinneri pyörii
      (do (<! (timeout 1000))
          (recur)))))

(defn jatka-ajoa! []
  (let [ajo @s/palautettava-tarkastusajo]
    (js/console.log "Tarkastusajo palautetaan: " (pr-str ajo))
    (reset! s/reittipisteet (mapv utils/keywordize-map (js->clj (get ajo "reittipisteet"))))
    (reset! s/kirjauspisteet (mapv utils/keywordize-map (js->clj (get ajo "tarkastuspisteet"))))
    (reset! s/tarkastusajo-id (get ajo "tarkastusajo"))
    (reset! s/tallennus-kaynnissa true))
  (reset! s/palautettava-tarkastusajo nil))

(defn pakota-ajon-lopetus! []
  (let [ajo @s/palautettava-tarkastusajo]
    (reitintallennus/poista-tarkastusajo @s/idxdb (get ajo "tarkastusajo"))
    (reitintallennus/tyhjenna-reittipisteet @s/idxdb))
  (reset! s/palautettava-tarkastusajo nil))