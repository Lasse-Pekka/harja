-- Tiemerkintäurakan yks. hint. työt -näkymän muutokset

-- yllapitokohde_tiemerkinta taulua käytettiin liittämään ylläpitokohteeseen tiemerkintään liittyvät
-- asiat. Käytännössä taulua käytettiin ja käytetään jatkossakin pelkästään yks. hint. työt -näkymän
-- toteumien tallentamiseen, joten nimetään taulu uudelleen.
ALTER TABLE yllapitokohde_tiemerkinta RENAME TO tiemerkinnan_yksikkohintainen_toteuma;

ALTER TABLE tiemerkinnan_yksikkohintainen_toteuma
  ADD COLUMN urakka integer REFERENCES urakka (id) NOT NULL,
  ADD COLUMN selite VARCHAR(512),
  ADD COLUMN tr_numero INTEGER,
  ADD COLUMN yllapitoluokka INTEGER,
  ADD COLUMN pituus INTEGER,
  ADD CONSTRAINT pituus_ei_neg CHECK (pituus >= 0),
    -- Jos linkattu ylläpitokohteeseen, ei voi olla omia kohdetietoja.
    ADD CONSTRAINT jos_linkattu_yllapitokohteeseen_ei_omia_kohdetietoja CHECK
      (yllapitokohde IS NULL
        OR (yllapitokohde IS NOT NULL AND tr_numero IS NULL AND yllapitoluokka IS NULL AND pituus IS NULL));

-- Jatkossa tiemerkinnän yks. hint. toteumat sidotaan suoraan urakkaan, koska ylläpitokohde-linkitys
-- on vapaaehtoinen. Migratoidaan vanha data (tehdään urakka-linkki ylläpitokohteen kautta)
UPDATE tiemerkinnan_yksikkohintainen_toteuma tyt
SET urakka = (SELECT suorittava_tiemerkintaurakka FROM yllapitokohde WHERE id = tyt.yllapitokohde);

-- Päivitä hintatyyppi-type
ALTER TYPE yllapitokohde_tiemerkinta_hintatyyppi RENAME TO tiemerkinta_toteuma_hintatyyppi;