#!/usr/bin/env bash

BASE_DIR=@BASE_DIR@
DATA_DIR="$BASE_DIR/.data"
INSTALL_DIR="$BASE_DIR/.install"

download() {
    URL=$1
    LOCAL=$2
    if [ ! -f "$LOCAL" ]; then
        echo "Downloading $(basename $URL)..."
        mkdir -p $(dirname $LOCAL)
        curl "$URL" > "${LOCAL}.tmp"
        mv "${LOCAL}.tmp" "$LOCAL"
    fi
}


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
            echo "$INSTANCE process started, pid file: $PID_FILE"
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
            echo "$INSTANCE process started, pid file: $PID_FILE"
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

wait_for_endpoint() {
    URL=$1
    EXPECTED=$2
    MAX_WAIT=$3
    while [  $MAX_WAIT -gt 0 ]; do
         echo "$URL $MAX_WAIT";
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

