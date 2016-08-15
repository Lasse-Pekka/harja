(ns harja.ui.kartta.ikonit
  "Määrittelee kartan käyttämät ikonit alustariippumattomasti"
  #?(:cljs (:require [harja.ui.dom :refer [ie?]])))

(def ikonikansio #?(:cljs "images/tuplarajat/"
                    :clj "public/images/tuplarajat/"))

#?(:cljs
   (defn karttakuva [perusnimi]
     (str perusnimi (if ie? ".png" ".svg")))

   :clj
   (defn karttakuva [perusnimi]
     (str perusnimi ".png")))

(defn assertoi-ikonin-vari [vari]
  (assert #{"punainen"
            "oranssi"
            "keltainen"
            "lime"
            "vihrea"
            "turkoosi"
            "syaani"
            "sininen"
            "tummansininen"
            "violetti"
            "magenta"
            "pinkki"

            "musta"
            "vaaleanharmaa"
            "harmaa"
            "tummanharmaa"} vari))

(defn sijainti-ikoni
  "Palauttaa sijaintia kuvaavan ikonin, jonka ulko- ja sisäreunan väri voidaan itse asettaa.
   Jos annetaan vain sisäreuna, palautetaan tällä sisäreunalla varustettu ikoni, jolla on musta ulkoreuna."
  ([vari-sisareuna] (sijainti-ikoni vari-sisareuna "musta"))
  ([vari-sisareuna vari-ulkoreuna]
   (assertoi-ikonin-vari vari-ulkoreuna)
   (assertoi-ikonin-vari vari-sisareuna)
   (karttakuva (str ikonikansio"sijainnit/sijainti-"vari-ulkoreuna"-"vari-sisareuna))))

(defn nuoli-ikoni [vari-str]
  (assertoi-ikonin-vari vari-str)
  (karttakuva (str ikonikansio"nuolet/nuoli-"vari-str)))

(defn pinni-ikoni [vari-str]
  (assertoi-ikonin-vari vari-str)
  (karttakuva (str ikonikansio"pinnit/pinni-"vari-str)))
