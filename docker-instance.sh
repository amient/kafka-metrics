#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

wait_for_endpoint() {
    URL=$1
    EXPECTED=$2
    MAX_WAIT=$3
    while [  $MAX_WAIT -gt 0 ]; do
         echo -en "\r$URL $MAX_WAIT";
         RESPONSE_STATUS=$(curl --stderr /dev/null -X GET -i "$URL" | head -1 | cut -d' ' -f2)
         if [ ! -z $RESPONSE_STATUS ] ; then
            if [ $RESPONSE_STATUS == $EXPECTED ]; then
                return 1
            else
                echo "UNEXPECTED RESPONSE_STATUS $RESPONSE_STATUS FOR $URL"
                return 0
            fi
         fi
         let MAX_WAIT=MAX_WAIT-1
         sleep 1
     done
     return 0
}

cd $DIR

docker-compose up &

terminate() {
    cd $DIR
    docker-compose down
}

trap terminate EXIT INT

GRAFANA_URL="http://admin:admin@localhost:3000"

INFLUXDB_URL="http://localhost:8086"

wait_for_endpoint "$INFLUXDB_URL/ping" 204 1800
if [ $? == 1 ]; then
    echo "influxdb endpoind check successful"
    curl -G "$INFLUXDB_URL/query" --data-urlencode "q=CREATE DATABASE metrics"
else
    echo "influxdb endpoint check failed"
    exit 2;
fi

wait_for_endpoint "$GRAFANA_URL/api/login/ping" 401 30
if [ $? == 1 ]; then
    echo "grafana endpoind check successful"
    echo "configuring 'Kafka Metrics InfluxDB' datasource -> $INFLUXDB_URL in the provided Grafana instance @ $GRAFANA_URL"
    curl "$GRAFANA_URL/api/datasources" -s -X POST -H 'Content-Type: application/json;charset=UTF-8' --data-binary '{"name": "Kafka Metrics InfluxDB", "type": "influxdb", "access": "direct", "url": "'$INFLUXDB_URL'", "password": "none", "user": "kafka-metrics", "database": "metrics", "isDefault": true}'
    echo ""
else
    exit 1;
fi


tail -f "$DIR/.data/grafana/logs/grafana.log"



