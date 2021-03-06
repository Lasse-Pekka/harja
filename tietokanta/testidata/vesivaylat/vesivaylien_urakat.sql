-- URAKOITSIJA
INSERT INTO organisaatio (nimi, ytunnus, tyyppi, harjassa_luotu, luotu)
VALUES ('Saimaan huolto', '1729662-9', 'urakoitsija', true, NOW());

INSERT INTO organisaatio (nimi, ytunnus, tyyppi, harjassa_luotu, luotu)
VALUES ('Pohjanmeren venepojat', '0472549-4', 'urakoitsija', true, NOW());

-- HANKE
INSERT INTO hanke (nimi, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Saimaan kartoitus', '2014-07-07', '2015-05-05', true, NOW());

INSERT INTO hanke (nimi, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Saimaan korjaushanke', '2016-07-07', '2021-05-05', true, NOW());

INSERT INTO hanke (nimi, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Pohjanmeren hoitohanke', '2021-07-07', '2030-05-05', true, NOW());

-- URAKKA
INSERT INTO urakka (nimi, alkupvm, loppupvm, hallintayksikko, urakoitsija, hanke, tyyppi,  harjassa_luotu, luotu, luoja)
VALUES
  ('Vantaan väyläyksikön väylänhoito ja -käyttö, Itäinen SL',
   '2013-08-01', '2016-07-30',
   (SELECT id FROM organisaatio WHERE nimi = 'Meriväylät'),
   (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'),
   (SELECT id FROM hanke WHERE nimi = 'Saimaan kartoitus'),
   'vesivayla-hoito',
   true, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

INSERT INTO urakka (nimi, alkupvm, loppupvm, hallintayksikko, urakoitsija, hanke, tyyppi,  harjassa_luotu, luotu, luoja)
VALUES
  ('Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL',
   '2016-08-01', '2019-07-30',
   (SELECT id FROM organisaatio WHERE nimi = 'Meriväylät'),
   (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'),
   (SELECT id FROM hanke WHERE nimi = 'Saimaan korjaushanke'),
   'vesivayla-hoito',
   true, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

INSERT INTO urakka (nimi, alkupvm, loppupvm, tyyppi,  harjassa_luotu, luotu, luoja)
VALUES
  ('Kotkan väyläyksikön väylänhoito ja -käyttö, Itäinen SL',
   '2016-08-01', '2019-07-30',
   'vesivayla-hoito',
   true, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

INSERT INTO urakka (nimi, alkupvm, loppupvm, hallintayksikko, urakoitsija, hanke, tyyppi,  harjassa_luotu, luotu, luoja)
VALUES
  ('Turun väyläyksikön väylänhoito ja -käyttö, Itäinen SL',
   '2019-08-01', '2024-07-30',
   (SELECT id FROM organisaatio WHERE nimi = 'Meriväylät'),
   (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'),
   (SELECT id FROM hanke WHERE nimi = 'Pohjanmeren hoitohanke'),
   'vesivayla-hoito',
   true, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

-- SOPIMUS
INSERT INTO sopimus (nimi, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Kotkan väyläyksikön pääsopimus',  NULL,
        '2013-08-01', '2016-07-30', true, NOW());

INSERT INTO sopimus (nimi, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Vapaa sopimus',  NULL,
        '2013-08-01', '2016-07-30', true, NOW());

INSERT INTO sopimus (nimi, urakka, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Helsingin väyläyksikön pääsopimus',
        (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        NULL,
        '2016-08-01', '2018-07-30', true, NOW());

INSERT INTO sopimus (nimi, urakka, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Helsingin väyläyksikön sivusopimus',
        (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
        '2016-08-01', '2018-07-30', true, NOW());

INSERT INTO sopimus (nimi, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Meriväylien sopimus','2016-08-01', '2018-07-30', true, NOW());

INSERT INTO sopimus (nimi,  paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Vantaan väyläyksikön pääsopimus',
        NULL,
        '2021-08-01', '2024-07-30',
        true, NOW());

-- TOIMENPIDEKOODIT
INSERT INTO toimenpidekoodi (taso, emo, nimi)
VALUES (3, 132, 'Rannikon kauppamerenkulku');
INSERT INTO toimenpidekoodi (taso, emo, nimi)
VALUES (3, 132, 'Rannikon muut');

-- TOIMENPIDEINSTANSSIT
INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide,  alkupvm, loppupvm)
VALUES ((SELECT id
         FROM urakka
         WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        'Väylänhoito, Itäinen Suomenlahti, sopimuksen kok.hintaiset työt, rann kmrk, TP',
        (SELECT id
         FROM toimenpidekoodi
         WHERE koodi = '24104'), '2016-08-01', '2017-07-30');

INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide,  alkupvm, loppupvm)
VALUES ((SELECT id
         FROM urakka
         WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        'Väylänhoito, Itäinen Suomenlahti, sopimuksen kok.hintaiset työt, rann muu vl, TP',
        (SELECT id
         FROM toimenpidekoodi
         WHERE nimi = 'Rannikon muut'), '2016-08-01', '2017-07-30');

INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide,  alkupvm, loppupvm)
VALUES ((SELECT id
         FROM urakka
         WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        'Väylänhoito, Itäinen Suomenlahti,erikseen tilattavat työt, rann kmrk, TP',
        (SELECT id
         FROM toimenpidekoodi
         WHERE nimi = 'Rannikon kauppamerenkulku'), '2016-08-01', '2017-07-30');

INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide, sampoid, alkupvm, loppupvm)
VALUES ((SELECT id
         FROM urakka
         WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        'Väylänhoito, Itäinen Suomenlahti,erikseen tilattavat työt, rann muu vl, TP',
        (SELECT id
         FROM toimenpidekoodi
         WHERE koodi = '24103'), 'PR00008511', '2016-08-01', '2017-07-30');
