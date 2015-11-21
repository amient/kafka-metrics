#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [ -z "$JAVA_HOME" ]; then
  JAVA="java"
else
  JAVA="$JAVA_HOME/bin/java"
fi

JAR="$(dirname $DIR)/target/influxdb-loader-main-${project.version}.jar"

"$JAVA" -jar "$JAR"
