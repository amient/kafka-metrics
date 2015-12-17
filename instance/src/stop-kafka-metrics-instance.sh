#!/usr/bin/env bash

BASE_DIR=@BASE_DIR@
INSTALL_DIR="$BASE_DIR/.install"

killtree() {
    local _sig=$1
    local _pid=$2
    for _child in $(ps -o pid -o ppid | grep -e "${_pid}$" | sed 's/^ *//;s/ *$//' | cut -d' ' -f1); do
        killtree ${_sig} ${_child}
    done
    kill ${_sig} ${_pid}
}

stop() {
    INSTANCE=$1

    PID_FILE="$BASE_DIR/.pid/$INSTANCE.pid"
    if [ ! -f "$PID_FILE" ]
    then
      echo "$INSTANCE process not found (file $PID_FILE missing)"
    else
      PID=$(cat "$PID_FILE")
      killtree -9 $PID
      rm "$PID_FILE"
      echo "$INSTANCE stopped, PID=$PID"
    fi
}


stop influxdb
stop grafana
