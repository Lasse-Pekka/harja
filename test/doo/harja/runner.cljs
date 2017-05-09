(ns harja.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [harja.pvm-test]
            [harja.ui.dom-test]
            [harja.tiedot.urakka.toteumat.tiemerkinta-muut-kustannukset-test]
            [harja.tiedot.urakka.suunnittelu-test]
            [harja.tiedot.urakka.yhatuonti-test]
            [harja.tiedot.muokkauslukko-test]
            [harja.views.kartta.infopaneeli-test]
            [harja.views.urakka.siltatarkastukset-test]
            [harja.views.urakka.paallystysilmoitukset-test]
            [harja.views.urakka.paikkausilmoitukset-test]
            [harja.views.urakka.yllapitokohteet-test]
            [harja.views.urakka.valitavoitteet-test]
            [harja.views.urakka.yleiset-test]
            [harja.ui.historia-test]
            [harja.ui.kentat-test]
            [harja.ui.grid-test]
            [harja.ui.edistymispalkki-test]
            [harja.fmt-test]
            [harja.tiedot.urakka.siirtymat-test]
            [harja.tiedot.tierekisteri.varusteet-test]
            [harja.ui.kartta.infopaneelin-sisalto-test]
            [harja.tiedot.tilannekuva.tienakyma-test]
            [harja.tiedot.urakka.aikataulu-test]
            [harja.views.kartta-test]
            [harja.tiedot.tilannekuva.tilannekuva-test]
            [harja.views.urakka.tiemerkinnan-yksikkohintaiset-tyot-test]
            [harja.views.ilmoitukset.tietyoilmoitushakulomake-test]
            [harja.views.ilmoitukset.tietyoilmoituslomake-test]
            [harja.views.kartta.tasot-test]
            [harja.tiedot.urakka.yllapitokohteet-test]
            [harja.ui.kartta.esitettavat-asiat-test]
            [harja.views.urakka.paallystyksen-maksuerat-test]
            [harja.tiedot.vesivaylat.hallinta.urakoiden-luonti-test]
            [harja.tiedot.vesivaylat.hallinta.urakoitsijoiden-luonti-test]
            [harja.tiedot.vesivaylat.hallinta.hankkeiden-luonti-test]
            [harja.tiedot.vesivaylat.hallinta.sopimuksien-luonti-test]
            [harja.tiedot.vesivaylat.urakka.turvalaitteet-test]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset-test]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset-test]
            [harja.tiedot.hallintayksikot-test]
            [harja.tiedot.vesivaylat.urakka.laadunseuranta.sanktiot]
            [harja.tiedot.vesivaylat.urakka.laadunseuranta.viat]
            ))

(doo-tests 'harja.pvm-test
           'harja.ui.dom-test
           'harja.tiedot.urakka.toteumat.tiemerkinta-muut-kustannukset-test
           'harja.tiedot.urakka.suunnittelu-test
           'harja.tiedot.urakka.yhatuonti-test
           'harja.tiedot.muokkauslukko-test
           'harja.views.urakka.siltatarkastukset-test
           'harja.views.urakka.paallystysilmoitukset-test
           'harja.views.urakka.paikkausilmoitukset-test
           'harja.views.urakka.yllapitokohteet-test
           'harja.views.urakka.yleiset-test
           'harja.ui.historia-test
           'harja.ui.kentat-test
           'harja.ui.grid-test
           'harja.ui.edistymispalkki-test
           'harja.views.kartta.infopaneeli-test
           'harja.views.urakka.valitavoitteet-test
           'harja.fmt-test
           'harja.tiedot.urakka.siirtymat-test
           'harja.tiedot.tierekisteri.varusteet-test
           'harja.ui.kartta.infopaneelin-sisalto-test
           'harja.tiedot.tilannekuva.tienakyma-test
           'harja.tiedot.urakka.aikataulu-test
           'harja.views.kartta-test
           'harja.tiedot.tilannekuva.tilannekuva-test
           'harja.views.urakka.tiemerkinnan-yksikkohintaiset-tyot-test
           'harja.views.ilmoitukset.tietyoilmoitushakulomake-test
           'harja.views.ilmoitukset.tietyoilmoituslomake-test
           'harja.views.kartta.tasot-test
           'harja.tiedot.urakka.yllapitokohteet-test
           'harja.ui.kartta.esitettavat-asiat-test
           'harja.views.urakka.paallystyksen-maksuerat-test
           'harja.tiedot.vesivaylat.hallinta.urakoiden-luonti-test
           'harja.tiedot.vesivaylat.hallinta.urakoitsijoiden-luonti-test
           'harja.tiedot.vesivaylat.hallinta.hankkeiden-luonti-test
           'harja.tiedot.vesivaylat.hallinta.sopimuksien-luonti-test
           'harja.tiedot.vesivaylat.urakka.turvalaitteet-test
           'harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset-test
           'harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset-test
           'harja.tiedot.hallintayksikot-test
           'harja.tiedot.vesivaylat.urakka.laadunseuranta.sanktiot
           'harja.tiedot.vesivaylat.urakka.laadunseuranta.viat
           ;; uusi testi tähän
           )
