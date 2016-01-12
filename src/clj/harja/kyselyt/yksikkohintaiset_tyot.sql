-- name: listaa-urakan-yksikkohintaiset-tyot
-- Hakee kaikki yksikkohintaiset-tyot
SELECT
  yt.id,
  yt.alkupvm,
  yt.loppupvm,
  yt.maara,
  yt.yksikko,
  yt.yksikkohinta,
  yt.tehtava,
  yt.urakka,
  yt.sopimus,
  tk.id   AS tehtavan_id,
  tk.nimi AS tehtavan_nimi
FROM yksikkohintainen_tyo yt
  LEFT JOIN toimenpidekoodi tk ON yt.tehtava = tk.id
WHERE urakka = :urakka;

-- name: hae-urakan-sopimuksen-yksikkohintaiset-tehtavat
-- Urakan sopimuksen yksikköhintaiset tehtävät
SELECT
  id,
  nimi,
  yksikko
FROM toimenpidekoodi
WHERE
  NOT poistettu AND
  id IN (
    SELECT DISTINCT (tehtava)
    FROM yksikkohintainen_tyo
    WHERE urakka = :urakkaid AND sopimus = :sopimusid) AND
  (kokonaishintainen IS NULL OR NOT kokonaishintainen);

-- name: paivita-urakan-yksikkohintainen-tyo!
-- Päivittää urakan hoitokauden yksikkohintaiset tyot
UPDATE yksikkohintainen_tyo
SET maara = :maara, yksikko = :yksikko, yksikkohinta = :yksikkohinta
WHERE urakka = :urakka AND sopimus = :sopimus AND tehtava = :tehtava
      AND alkupvm = :alkupvm AND loppupvm = :loppupvm;

-- name: lisaa-urakan-yksikkohintainen-tyo<!
INSERT INTO yksikkohintainen_tyo
(maara, yksikko, yksikkohinta,
 urakka, sopimus, tehtava,
 alkupvm, loppupvm)
VALUES (:maara, :yksikko, :yksikkohinta,
        :urakka, :sopimus, :tehtava,
        :alkupvm, :loppupvm);

-- name: merkitse-kustannussuunnitelmat-likaisiksi!
-- Merkitsee yksikköhintaisia töitä vastaavat kustannussuunnitelmat likaisiksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
UPDATE kustannussuunnitelma
SET likainen = TRUE
WHERE maksuera IN (SELECT m.numero
                   FROM maksuera m
                     JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
                     JOIN toimenpidekoodi emo ON emo.id = tpi.toimenpide
                     JOIN toimenpidekoodi tpk ON tpk.emo = emo.id
                   WHERE m.tyyppi = 'yksikkohintainen' AND tpi.urakka = :urakka AND tpk.id IN (:tehtavat));


-- name: hae-yksikkohintaiset-tyot-per-paiva
-- Hakee yksikköhintaiset työt annetulle urakalle ja aikavälille summattuna päivittäin.
-- Optionaalisesti voidaan antaa vain tietty toimenpide, jonka työt haetaan.
SELECT date_trunc('day', tot.alkanut) as pvm,
       t4.nimi,
       yht.yksikko,
       yht.yksikkohinta,
       yht.maara as suunniteltu_maara,
       SUM(tt.maara) as toteutunut_maara,
       (yht.maara * yksikkohinta) as suunnitellut_kustannukset,
       (SUM(tt.maara) * yksikkohinta) as toteutuneet_kustannukset
  FROM toteuma tot
       JOIN toteuma_tehtava tt ON tt.toteuma=tot.id AND tt.poistettu IS NOT TRUE
       JOIN toimenpidekoodi t4 ON tt.toimenpidekoodi=t4.id
       JOIN yksikkohintainen_tyo yht ON (tt.toimenpidekoodi=yht.tehtava AND yht.urakka=tot.urakka AND
                                         yht.alkupvm <= tot.alkanut AND yht.loppupvm >= tot.alkanut)
 WHERE tot.urakka = :urakka
       AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
       AND (:rajaa_tpi = false OR tt.toimenpidekoodi IN (SELECT tpk.id FROM toimenpidekoodi tpk WHERE tpk.emo=:tpi))

 GROUP BY pvm, t4.nimi, yht.yksikko, yht.yksikkohinta,yht.maara
 ORDER BY pvm ASC;
       
-- name: hae-yksikkohintaiset-tyot-kuukausittain-urakalle
-- Hakee yksikköhintaiset työt annetulle urakalle ja aikavälille summattuna kuukausittain
-- Optionaalisesti voidaan antaa vain tietty toimenpide, jonka työt haetaan.
SELECT
  extract(YEAR FROM tot.alkanut)::INT as vuosi,
  extract(MONTH FROM tot.alkanut)::INT as kuukausi,
  t4.nimi,
  t4.yksikko,
  yht.yksikkohinta,
  yht.maara as suunniteltu_maara,
  SUM(tt.maara) as toteutunut_maara,
  ROUND(SUM(tt.maara) / yht.maara, 2) as toteumaprosentti
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma=tot.id AND tt.poistettu IS NOT TRUE
  JOIN toimenpidekoodi t4 ON tt.toimenpidekoodi=t4.id
  JOIN yksikkohintainen_tyo yht ON (tt.toimenpidekoodi=yht.tehtava AND yht.urakka=tot.urakka AND
                                    yht.alkupvm <= tot.alkanut AND yht.loppupvm >= tot.alkanut)
WHERE tot.urakka = :urakka
      AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = false OR tt.toimenpidekoodi IN (SELECT tpk.id FROM toimenpidekoodi tpk WHERE tpk.emo=:tpi))
GROUP BY t4.nimi, t4.yksikko, yht.yksikkohinta, yht.maara, vuosi, kuukausi

-- name: hae-yksikkohintaiset-tyot-kuukausittain-hallintayksikolle
-- Hakee yksikköhintaiset työt annetulle hallintayksikolle ja aikavälille summattuna kuukausittain
-- Optionaalisesti voidaan antaa vain tietty toimenpide, jonka työt haetaan.
SELECT
  extract(YEAR FROM tot.alkanut)::INT as vuosi,
  extract(MONTH FROM tot.alkanut)::INT as kuukausi,
  t4.nimi,
  t4.yksikko,
  SUM(tt.maara) as toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma=tot.id AND tt.poistettu IS NOT TRUE
  JOIN toimenpidekoodi t4 ON tt.toimenpidekoodi=t4.id
WHERE tot.urakka IN (SELECT id
                     FROM urakka
                     WHERE hallintayksikko = :hallintayksikko)
      AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = false OR tt.toimenpidekoodi IN (SELECT tpk.id FROM toimenpidekoodi tpk WHERE tpk.emo=:tpi))
GROUP BY t4.nimi, yksikko, vuosi, kuukausi

-- name: hae-yksikkohintaiset-tyot-kuukausittain-koko-maalle
-- Hakee yksikköhintaiset työt koko maalle ja aikavälille summattuna kuukausittain
-- Optionaalisesti voidaan antaa vain tietty toimenpide, jonka työt haetaan.
SELECT
  extract(YEAR FROM tot.alkanut)::INT as vuosi,
  extract(MONTH FROM tot.alkanut)::INT as kuukausi,
  t4.nimi,
  t4.yksikko,
  SUM(tt.maara) as toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma=tot.id AND tt.poistettu IS NOT TRUE
  JOIN toimenpidekoodi t4 ON tt.toimenpidekoodi=t4.id
WHERE (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = false OR tt.toimenpidekoodi IN (SELECT tpk.id FROM toimenpidekoodi tpk WHERE tpk.emo=:tpi))
GROUP BY t4.nimi, yksikko, vuosi, kuukausi

-- name: hae-yksikkohintaiset-tyot-kuukausittain-hallintayksikolle-urakoittain
-- Hakee yksikköhintaiset työt annetulle hallintayksikolle ja aikavälille summattuna kuukausittain, eriteltynä urakoittain
-- Optionaalisesti voidaan antaa vain tietty toimenpide, jonka työt haetaan.
SELECT
  extract(YEAR FROM tot.alkanut)::INT as vuosi,
  extract(MONTH FROM tot.alkanut)::INT as kuukausi,
  u.id AS urakka_id,
  u.nimi AS urakka_nimi,
  t4.nimi,
  t4.yksikko,
  SUM(tt.maara) as toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma=tot.id AND tt.poistettu IS NOT TRUE
  JOIN toimenpidekoodi t4 ON tt.toimenpidekoodi=t4.id
  JOIN urakka u ON tot.urakka = u.id
WHERE tot.urakka IN (SELECT id
                     FROM urakka
                     WHERE hallintayksikko = :hallintayksikko)
      AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = false OR tt.toimenpidekoodi IN (SELECT tpk.id FROM toimenpidekoodi tpk WHERE tpk.emo=:tpi))
GROUP BY t4.nimi, yksikko, vuosi, kuukausi, u.id

-- name: hae-yksikkohintaiset-tyot-kuukausittain-koko-maalle-urakoittain
-- Hakee yksikköhintaiset työt koko maalle ja aikavälille summattuna kuukausittain, eriteltynä urakoittain
-- Optionaalisesti voidaan antaa vain tietty toimenpide, jonka työt haetaan.
SELECT
  extract(YEAR FROM tot.alkanut)::INT as vuosi,
  extract(MONTH FROM tot.alkanut)::INT as kuukausi,
  u.id AS urakka_id,
  u.nimi AS urakka_nimi,
  t4.nimi,
  t4.yksikko,
  SUM(tt.maara) as toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma=tot.id AND tt.poistettu IS NOT TRUE
  JOIN toimenpidekoodi t4 ON tt.toimenpidekoodi=t4.id
  JOIN urakka u ON tot.urakka = u.id
WHERE (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = false OR tt.toimenpidekoodi IN (SELECT tpk.id FROM toimenpidekoodi tpk WHERE tpk.emo=:tpi))
GROUP BY t4.nimi, yksikko, vuosi, kuukausi, u.id

-- name: hae-yksikkohintaiset-tyot-tehtavittain-summattuna-urakalle
-- Hakee yksikköhintaiset työt annetulle urakalle ja aikavälille, summattuna tehtävittäin
SELECT
  t4.nimi,
  yht.yksikko,
  yht.yksikkohinta,
  yht.maara                      AS suunniteltu_maara,
  SUM(tt.maara)                  AS toteutunut_maara,
  (yht.maara * yksikkohinta)     AS suunnitellut_kustannukset,
  (SUM(tt.maara) * yksikkohinta) AS toteutuneet_kustannukset
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma = tot.id AND tt.poistettu IS NOT TRUE
  JOIN toimenpidekoodi t4 ON tt.toimenpidekoodi = t4.id
  JOIN yksikkohintainen_tyo yht ON (tt.toimenpidekoodi = yht.tehtava AND yht.urakka = tot.urakka AND
                                    yht.alkupvm <= tot.alkanut AND yht.loppupvm >= tot.alkanut)
WHERE tot.urakka = :urakka
      AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = FALSE OR tt.toimenpidekoodi IN (SELECT tpk.id
                                                        FROM toimenpidekoodi tpk
                                                        WHERE tpk.emo = :tpi))
GROUP BY t4.nimi, yht.yksikko, yht.yksikkohinta, yht.maara
ORDER BY toteutuneet_kustannukset;

-- name: hae-yksikkohintaiset-tyot-tehtavittain-summattuna-hallintayksikolle
-- Hakee yksikköhintaiset työt annetulle hallintayksikölle aikavälille, summattuna tehtävittäin
SELECT
  t4.nimi,
  t4.yksikko,
  SUM(tt.maara)                  AS toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma = tot.id AND tt.poistettu IS NOT TRUE
  JOIN toimenpidekoodi t4 ON tt.toimenpidekoodi = t4.id
WHERE tot.urakka IN (SELECT id
                     FROM urakka
                     WHERE hallintayksikko = :hallintayksikko)
      AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = FALSE OR tt.toimenpidekoodi IN (SELECT tpk.id
                                                        FROM toimenpidekoodi tpk
                                                        WHERE tpk.emo = :tpi))
GROUP BY t4.nimi, t4.yksikko;

-- name: hae-yksikkohintaiset-tyot-tehtavittain-summattuna-koko-maalle
-- Hakee yksikköhintaiset työt koko maasta aikavälille, summattuna tehtävittäin
SELECT
  t4.nimi,
  t4.yksikko,
  SUM(tt.maara)                  AS toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma = tot.id AND tt.poistettu IS NOT TRUE
  JOIN toimenpidekoodi t4 ON tt.toimenpidekoodi = t4.id
WHERE (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = FALSE OR tt.toimenpidekoodi IN (SELECT tpk.id
                                                        FROM toimenpidekoodi tpk
                                                        WHERE tpk.emo = :tpi))
GROUP BY t4.nimi, t4.yksikko;

-- name: hae-yksikkohintaiset-tyot-tehtavittain-summattuna-hallintayksikolle-urakoittain
-- Hakee yksikköhintaiset työt hallintayksikstä aikavälille, summattuna tehtävittäin ja eriteltynä urakoittain
SELECT
  u.id                           AS urakka_id,
  u.nimi                         AS urakka_nimi,
  t4.nimi,
  t4.yksikko,
  SUM(tt.maara)                  AS toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma = tot.id AND tt.poistettu IS NOT TRUE
  JOIN toimenpidekoodi t4 ON tt.toimenpidekoodi = t4.id
  JOIN urakka u ON tot.urakka = u.id
WHERE tot.urakka IN (SELECT id
                     FROM urakka
                     WHERE hallintayksikko = :hallintayksikko)
      AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = FALSE OR tt.toimenpidekoodi IN (SELECT tpk.id
                                                        FROM toimenpidekoodi tpk
                                                        WHERE tpk.emo = :tpi))
GROUP BY t4.nimi, t4.yksikko, u.id
ORDER BY urakka_nimi;

-- name: hae-yksikkohintaiset-tyot-tehtavittain-summattuna-koko-maalle-urakoittain
-- Hakee yksikköhintaiset työt koko maasta aikavälille, summattuna tehtävittäin ja eriteltynä urakoittain
SELECT
  u.id                           AS urakka_id,
  u.nimi                         AS urakka_nimi,
  t4.nimi,
  t4.yksikko,
  SUM(tt.maara)                  AS toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma = tot.id AND tt.poistettu IS NOT TRUE
  JOIN toimenpidekoodi t4 ON tt.toimenpidekoodi = t4.id
  JOIN urakka u ON tot.urakka = u.id
WHERE (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = FALSE OR tt.toimenpidekoodi IN (SELECT tpk.id
                                                        FROM toimenpidekoodi tpk
                                                        WHERE tpk.emo = :tpi))
GROUP BY t4.nimi, t4.yksikko, u.id
ORDER BY urakka_nimi;