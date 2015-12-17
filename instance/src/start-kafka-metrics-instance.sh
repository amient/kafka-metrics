#!/usr/bin/env bash

BASE_DIR=@BASE_DIR@
INSTALL_DIR="$BASE_DIR/.install"
CONFIG_DIR=$1
LOG_DIR=$2
#    TODO create pid files with hash of the config_dir so that multiple instances running can be distinguished

if [ -z "$LOG_DIR" ]; then
    echo "Usage: ./start-kafka-metrics-instance.sh <CONFIG_DIR> <LOG_DIR>"
    exit 1;
fi

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
    INFLUXDB_CONFIG="$CONFIG_DIR/influxdb.conf"
    if [ -f "$INFLUXDB_CONFIG" ]; then
        mkdir -p "$LOG_DIR/influxdb"
        export STDOUT="$LOG_DIR/influxdb/stdout.log"
        export STDERR="$LOG_DIR/influxdb/stderr.log"
        echo "starting influxdb deamon with config $INFLUXDB_CONFIG"
#        FIXME influxdb doesn't respect the STDOUT and STDERR env vars so for now we use output redirect
        start_with_output_redirect "influxdb" influxd -config $INFLUXDB_CONFIG
        sleep 2
        "$INSTALL_DIR/golang/bin/influx" -execute "CREATE DATABASE IF NOT EXISTS metrics"
    fi
}

start_grafana() {
    GRAFANA_CONFIG="$CONFIG_DIR/grafana.ini"
    if [ -f "$GRAFANA_CONFIG" ]; then
        export GF_LOG_MODE="file"
        export GF_PATHS_LOGS="$LOG_DIR/grafana"
        echo "starting grafana with config $GRAFANA_CONFIG"
        cd "$INSTALL_DIR/golang/src/github.com/grafana/grafana"
        start "grafana" "./bin/grafana-server" "-config" $GRAFANA_CONFIG
    fi
}

start_influxdb
start_grafana
