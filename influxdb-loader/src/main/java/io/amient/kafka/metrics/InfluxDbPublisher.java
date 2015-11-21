/*
 * Copyright 2015 Michal Harish, michal.harish@gmail.com
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.amient.kafka.metrics;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class InfluxDbPublisher implements MeasurementPublisher {

    final private InfluxDB influxDB;
    final private String dbName;
    final private String address;

    public InfluxDbPublisher(Properties config) {
        this.dbName = "metrics";
        this.address = "http://localhost:8086";
        influxDB = InfluxDBFactory.connect(address, "root", "root");
        //influxDB.createDatabase(dbName);
//        influxDB.enableBatch(2000, 100, TimeUnit.MILLISECONDS);
    }

    public void publish(Measurement m) {
        Point.Builder builder = Point.measurement(m.getName().toString()).time(m.getTimestamp(), TimeUnit.MILLISECONDS);
        builder.tag("service", m.getService().toString());
        builder.tag("host", m.getHost().toString());
        if (m.getGroup() != null) builder.tag("group", m.getGroup().toString());
        if (m.getType() != null) builder.tag("type", m.getType().toString());
        if (m.getScope() != null) builder.tag("scope", m.getScope().toString());

        for (String f : m.getFields().toString().split(",")) {
            String[] nv = f.split("=");
            builder.field(nv[0], Double.parseDouble(nv[1]));
        }

        influxDB.write(dbName, "default", builder.build());

    }

    @Override
    public void close() {

    }
}
