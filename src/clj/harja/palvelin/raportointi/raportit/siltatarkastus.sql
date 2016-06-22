-- name: hae-urakan-siltatarkastukset
-- Hakee urakan kaikki sillat ja niiden annettuna vuonna tehdyn uusimman siltatarkastuksen
SELECT
  s.id,
  siltanro,
  siltanimi,
  (SELECT tarkastusaika
   FROM siltatarkastus st
   WHERE st.silta = s.id
         AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
         AND st.poistettu = FALSE
   ORDER BY tarkastusaika DESC
   LIMIT 1),
  (SELECT tarkastaja
   FROM siltatarkastus st
   WHERE st.silta = s.id
         AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
         AND st.poistettu = FALSE
   ORDER BY tarkastusaika DESC
   LIMIT 1),
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos = 'A'
         AND siltatarkastus = (SELECT id
                               FROM siltatarkastus st
                               WHERE st.silta = s.id
                                     AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                     AND st.poistettu = FALSE
                               ORDER BY tarkastusaika DESC
                               LIMIT 1)) AS "a",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos = 'B'
         AND siltatarkastus = (SELECT id
                               FROM siltatarkastus st
                               WHERE st.silta = s.id
                                     AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                     AND st.poistettu = FALSE
                               ORDER BY tarkastusaika DESC
                               LIMIT 1)) AS "b",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos = 'C'
         AND siltatarkastus = (SELECT id
                               FROM siltatarkastus st
                               WHERE st.silta = s.id
                                     AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                     AND st.poistettu = FALSE
                               ORDER BY tarkastusaika DESC
                               LIMIT 1)) AS "c",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos = 'D'
         AND siltatarkastus = (SELECT id
                               FROM siltatarkastus st
                               WHERE st.silta = s.id
                                     AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                     AND st.poistettu = FALSE
                               ORDER BY tarkastusaika DESC
                               LIMIT 1)) AS "d",
  l.id AS liite_id,
  l.tyyppi AS liite_tyyppi,
  l.koko AS liite_koko,
  l.nimi AS liite_nimi
FROM silta s
  LEFT JOIN liite l ON l.id IN (SELECT id
                                FROM
                                  liite l
                                  JOIN siltatarkastus_kohde_liite skl ON l.id = skl.liite
                                WHERE skl.siltatarkastus IN (SELECT id
                                                             FROM siltatarkastus st
                                                             WHERE st.silta = s.id
                                                                   AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                                                   AND st.poistettu = FALSE
                                                                   AND urakka = :urakka
                                                             ORDER BY tarkastusaika DESC
                                                             LIMIT 1))
WHERE s.id IN (SELECT silta
               FROM sillat_alueurakoittain
               WHERE urakka = :urakka)
ORDER BY siltanro;

-- name: hae-sillan-tarkastus
-- Hakee valitun sillan annettuna vuonna tehdyn uusimman siltatarkastuksen
SELECT
  kohde,
  tulos,
  lisatieto,
  l.id AS liite_id,
  l.tyyppi AS liite_tyyppi,
  l.koko AS liite_koko,
  l.nimi AS liite_nimi
FROM siltatarkastuskohde stk
  LEFT JOIN liite l ON l.id IN (SELECT id
                                FROM
                                  liite l
                                  JOIN siltatarkastus_kohde_liite skl ON l.id = skl.liite
                                WHERE skl.siltatarkastus IN (SELECT id
                                                             FROM siltatarkastus st
                                                             WHERE st.silta = :silta
                                                                   AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                                                   AND st.poistettu = FALSE
                                                                   AND urakka = :urakka
                                                             ORDER BY tarkastusaika DESC
                                                             LIMIT 1)
                                      AND skl.kohde = stk.kohde)
WHERE siltatarkastus = (SELECT id
                        FROM siltatarkastus st
                        WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                              AND urakka = :urakka
                              AND silta = :silta
                              AND st.poistettu = FALSE
                        ORDER BY tarkastusaika DESC
                        LIMIT 1)
ORDER BY kohde;