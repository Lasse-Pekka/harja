ALTER TYPE REIMARI_TURVALAITE RENAME ATTRIBUTE nro TO "r-nro";
ALTER TYPE REIMARI_TURVALAITE RENAME ATTRIBUTE nimi TO "r-nimi";
ALTER TYPE REIMARI_TURVALAITE RENAME ATTRIBUTE ryhma TO "r-ryhma";
ALTER TYPE REIMARI_ALUS RENAME ATTRIBUTE tunnus TO "r-tunnus";
ALTER TYPE REIMARI_ALUS RENAME ATTRIBUTE nimi TO "r-nimi";
ALTER TYPE REIMARI_VAYLA RENAME ATTRIBUTE nro TO "r-nro";
ALTER TYPE REIMARI_VAYLA RENAME ATTRIBUTE nimi TO "r-nimi";
ALTER TYPE REIMARI_VAYLA RENAME ATTRIBUTE ryhma TO "r-ryhma";

ALTER TABLE vv_vikailmoitus DROP COLUMN "toteuma-id";
ALTER TABLE vv_vikailmoitus ADD COLUMN "toimenpide-id" INTEGER REFERENCES reimari_toimenpide(id);

-- Sopparin nimessä tai sampoid:ssä ei saa olle merkkejä, joita käytetään SQL-parsinnassa
ALTER TABLE sopimus ADD CONSTRAINT sallittu_sampoid CHECK (nimi NOT LIKE '%=%');
ALTER TABLE sopimus ADD CONSTRAINT sallittu_nimi CHECK (nimi NOT LIKE '%=%');

-- Toimenpidekoodissa sama homma, kielletään SQL-parsinnassa käytetyt merkit
ALTER TABLE toimenpidekoodi ADD CONSTRAINT sallittu_nimi CHECK (nimi NOT LIKE '%^%');
