-- name: hae-tietyoilmoitukset
SELECT
  tti.id,
  tti.tloik_id,
  tti.paatietyoilmoitus,
  tti.tloik_paatietyoilmoitus_id,
  tti.luotu,
  tti.luoja,
  tti.muokattu,
  tti.muokkaaja,
  tti.poistettu,
  tti.poistaja,
  tti.ilmoittaja,
  tti.ilmoittaja_etunimi,
  tti.ilmoittaja_sukunimi,
  tti.ilmoittaja_matkapuhelin,
  tti.ilmoittaja_sahkoposti,
  tti.urakka,
  tti.urakka_nimi,
  tti.urakoitsijayhteyshenkilo,
  tti.urakoitsijayhteyshenkilo_etunimi,
  tti.urakoitsijayhteyshenkilo_sukunimi,
  tti.urakoitsijayhteyshenkilo_matkapuhelin,
  tti.urakoitsijayhteyshenkilo_sahkoposti,
  tti.tilaaja,
  tti.tilaajan_nimi,
  tti.tilaajayhteyshenkilo,
  tti.tilaajayhteyshenkilo_etunimi,
  tti.tilaajayhteyshenkilo_sukunimi,
  tti.tilaajayhteyshenkilo_matkapuhelin,
  tti.tilaajayhteyshenkilo_sahkoposti,
  tti.luvan_diaarinumero,
  tti.tr_numero,
  tti.tr_alkuosa,
  tti.tr_alkuetaisyys,
  tti.tr_loppuosa,
  tti.tr_loppuetaisyys,
  tti.tien_nimi,
  tti.kunnat,
  tti.alkusijainnin_kuvaus,
  tti.loppusijainnin_kuvaus,
  tti.alku,
  tti.loppu,
  tti.viivastys_normaali_liikenteessa,
  tti.viivastys_ruuhka_aikana,
  tti.ajoneuvo_max_korkeus,
  tti.ajoneuvo_max_leveys,
  tti.ajoneuvo_max_pituus,
  tti.ajoneuvo_max_paino,
  tti.ajoittaiset_pysatykset,
  tti.ajoittain_suljettu_tie,
  tti.pysaytysten_alku,
  tti.pysaytysten_loppu,
  tti.lisatietoja,
  tti.urakkatyyppi,
  tti.tyotyypit,
  tti.sijainti,
  tti.tyoajat,
  tti.vaikutussuunta,
  tti.kaistajarjestelyt,
  tti.nopeusrajoitukset,
  tti.tienpinnat,
  tti.kiertotien_mutkaisuus,
  tti.kiertotienpinnat,
  tti.liikenteenohjaus,
  tti.liikenteenohjaaja,
  tti.huomautukset
FROM tietyoilmoitus tti
 WHERE (:alku BETWEEN alku AND loppu) OR (:loppu BETWEEN alku AND loppu)
  AND (tti.urakka IS NULL OR tti.urakka IN (:urakat))
ORDER BY tti.luotu DESC
LIMIT :max-maara :: INTEGER;
