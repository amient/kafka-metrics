#!/usr/bin/env bash
#    Copyright 2015 Michal Harish, michal.harish@gmail.com
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.


DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BASE_DIR=$(dirname $DIR)

if [ "x$1" == "x" ]; then
    echo "Usage run-influxdb-loader.sh <PROPERTIES_FILE>"
    exit 1;
fi

$BASE_DIR/build/scripts/influxdb-loader "$@"
