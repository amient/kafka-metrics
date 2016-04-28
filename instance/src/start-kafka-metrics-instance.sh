#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source "$DIR/kafka-metrics-common.sh"

CONFIG_DIR=$1
LOG_DIR=$2
GRAFANA_URL=$3

if [ -z "$LOG_DIR" ] || [ -z "$CONFIG_DIR" ]; then
    echo "Usage: ./start-kafka-metrics-instance.sh <CONFIG_DIR> <LOG_DIR>"
    echo "CONFIG_DIR provided should contain grafana.ini and influxfb.conf files"
    exit 1;
fi

start_influxdb() {
    export INFLUXDB_CONFIG="$CONFIG_DIR/influxdb.conf"
    if [ -f "$INFLUXDB_CONFIG" ]; then
        mkdir -p "$LOG_DIR/influxdb"
        mkdir -p "$DATA_DIR/influxdb"
        export STDOUT="$LOG_DIR/influxdb/stdout.log"
        export STDERR="$LOG_DIR/influxdb/stderr.log"
        echo "starting influxdb deamon with config $INFLUXDB_CONFIG"
        start_with_output_redirect "influxdb" "$INSTALL_DIR/golang/bin/influxd" -config $INFLUXDB_CONFIG
        API_HOST=`cat "$INFLUXDB_CONFIG" | grep -A 10 -e "^\[meta\]$" | grep hostname | cut -d'=' -f2 | tr -d '"' | tr -d " "`
        API_PORT=`cat "$INFLUXDB_CONFIG" | grep -A 10 -e "^\[http\]$" | grep bind-address | cut -d'=' -f2 | tr -d '"' | tr -d " "`
        INFLUXDB_URL="http://$API_HOST$API_PORT"
        wait_for_endpoint "$INFLUXDB_URL/ping?wait_for_leader=1s" 204 30
        if [ $? == 1 ]; then
            echo "influxdb endpoind check successful"
            "$INSTALL_DIR/golang/bin/influx" -execute "CREATE DATABASE IF NOT EXISTS metrics"
            if [ ! -z $GRAFANA_URL ]; then
                echo "configuring 'Kafka Metrics InfluxDB' datasource -> $INFLUXDB_URL in the provided Grafana instance @ $GRAFANA_URL"
                curl "$GRAFANA_URL/api/datasources" -s -X POST -H 'Content-Type: application/json;charset=UTF-8' --data-binary '{"name": "Kafka Metrics InfluxDB", "type": "influxdb", "access": "direct", "url": "'$INFLUXDB_URL'", "password": "none", "user": "kafka-metrics", "database": "metrics", "isDefault": true}'
                echo ""
            fi
        else
            echo "influxdb endpoint check failed"
            stop influxdb
        fi
    fi
}

start_grafana() {
    export GRAFANA_CONFIG="$CONFIG_DIR/grafana.ini"
    if [ -f "$GRAFANA_CONFIG" ]; then
        export GF_LOG_MODE="file"
        export GF_PATHS_LOGS="$LOG_DIR/grafana"
        mkdir -p "$GF_PATHS_LOGS"
        mkdir -p "$DATA_DIR/grafana/dashboards"
        echo "starting grafana with config $GRAFANA_CONFIG"
        cd "$INSTALL_DIR/golang/src/github.com/grafana/grafana"
        start "grafana" "./bin/grafana-server" "-config" $GRAFANA_CONFIG
        GRAFANA_URL="http://admin:admin@localhost:3000"
        wait_for_endpoint "$GRAFANA_URL/api/login/ping" 401 30
        if [ $? == 1 ]; then
            echo "grafana endpoind check successful"
        else
            stop grafana
        fi
    fi
}

if [ -z "$GRAFANA_URL" ]; then
    start_grafana
fi
start_influxdb

