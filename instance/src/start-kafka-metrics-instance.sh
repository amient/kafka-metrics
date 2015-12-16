#!/usr/bin/env bash

BASE_DIR=@BASE_DIR@
INSTALL_DIR="$BASE_DIR/.install"
METRICS_CONFIG=$1

start() {
    INSTANCE=$1
    COMMAND="${@:2}"
    if [ ! -d "$BASE_DIR/.pid" ]; then
        mkdir -p "$BASE_DIR/.pid"
    fi
    PID_FILE="$BASE_DIR/.pid/$INSTANCE.pid"
    if [ -f $PID_FILE ]; then
        echo "pid file already exists $PID_FILE"
    else
        exec $COMMAND > /dev/null 2>&1 &
        if [ $? -eq 0 ]
        then
          if /bin/echo -n $! > "$PID_FILE"
          then
            sleep 1
            echo "$INSTANCE started, pid file: $PID_FILE"
          else
            echo "Failed to write pid file $PID_FILE for component $INSTANCE"
            exit 1
          fi
        else
          echo "$INSTANCE did not start"
          exit 1
        fi
     fi
}

start_with_output_redirect() {
    INSTANCE=$1
    COMMAND="${@:2}"
    if [ ! -d "$BASE_DIR/.pid" ]; then
        mkdir -p "$BASE_DIR/.pid"
    fi
    PID_FILE="$BASE_DIR/.pid/$INSTANCE.pid"
    if [ -f $PID_FILE ]; then
        echo "pid file already exists $PID_FILE"
    else
        exec $COMMAND 1>$STDOUT 2>$STDERR &
        if [ $? -eq 0 ]
        then
          if /bin/echo -n $! > "$PID_FILE"
          then
            sleep 1
            echo "$INSTANCE started, pid file: $PID_FILE"
          else
            echo "Failed to write pid file $PID_FILE for component $INSTANCET"
            exit 1
          fi
        else
          echo "$INSTANCE did not start"
          exit 1
        fi
     fi
}



start_influxdb() {
    INFLUXDB_CONFIG="$BASE_DIR/build/conf/influxdb.conf"
    if [ -f "$INFLUXDB_CONFIG" ]; then
        export STDOUT="$BASE_DIR/.logs/influxdb/stdout.log"
        export STDERR="$BASE_DIR/.logs/influxdb/stderr.log"
        mkdir -p "$BASE_DIR/.logs/influxdb"
        echo "starting influxdb deamon with config $INFLUXDB_CONFIG"
#        FIXME influxdb doesn't respect the STDOUT and STDERR env vars so for now we use output redirect
        start_with_output_redirect "influxdb" influxd -config $INFLUXDB_CONFIG
        sleep 2
        "$INSTALL_DIR/golang/bin/influx" -execute "CREATE DATABASE IF NOT EXISTS metrics"
    fi
}

start_grafana() {
    GRAFANA_CONFIG="$BASE_DIR/build/conf/grafana.ini"
    if [ -f "$GRAFANA_CONFIG" ]; then
        export GF_LOG_MODE="file"
        export GF_PATHS_LOGS="$BASE_DIR/.logs/grafana"
        echo "starting grafana with config $GRAFANA_CONFIG"
        cd "$INSTALL_DIR/golang/src/github.com/grafana/grafana"
        start "grafana" "./bin/grafana-server" "-config" $GRAFANA_CONFIG
    fi
}

start_influxdb_loader() {
    mkdir -p "$BASE_DIR/.logs/influxdb-loader"
    export STDOUT="$BASE_DIR/.logs/influxdb-loader/stdout.log"
    export STDERR="$BASE_DIR/.logs/influxdb-loader/stderr.log"
    start_with_output_redirect "influxdb-loader" $BASE_DIR/../influxdb-loader/build/scripts/influxdb-loader "$METRICS_CONFIG"
}

start_influxdb
start_grafana
start_influxdb_loader