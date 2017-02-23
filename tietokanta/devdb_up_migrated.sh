#!/bin/sh

set -e

echo "Käynnistetään valmiiksi migratoitu harjadb Docker-image"
docker run -p 5432:5432 --name harjadb -dit jarzka/harjadb

echo "Odotetaan, että PostgreSQL on käynnissä ja vastaa yhteyksiin portissa 5432"
while ! nc -z localhost 5432; do
    sleep 0.5;
done;

sh devdb_migrate.sh
