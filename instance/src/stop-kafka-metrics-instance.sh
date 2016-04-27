#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source "$DIR/kafka-metrics-common.sh"

COMPONENT=$1

if [ -z $COMPONENT ]; then
    stop influxdb
    stop grafana
else
    stop "$COMPONENT"
fi