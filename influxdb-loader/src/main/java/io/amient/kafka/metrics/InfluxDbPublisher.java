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

    static final String COFNIG_INFLUXDB_DATABASE = "kafka.metrics.influxdb.database";
    static final String COFNIG_INFLUXDB_URL = "kafka.metrics.influxdb.url";
    static final String COFNIG_INFLUXDB_USERNAME = "kafka.metrics.influxdb.username";
    static final String COFNIG_INFLUXDB_PASSWORD = "kafka.metrics.influxdb.password";
    final private InfluxDB influxDB;
    final private String dbName;
    final private String address;

    public InfluxDbPublisher(Properties config) {
        this.dbName = config.getProperty(COFNIG_INFLUXDB_DATABASE, "metrics");
        this.address = config.getProperty(COFNIG_INFLUXDB_URL, "http://localhost:8086");
        String username = config.getProperty(COFNIG_INFLUXDB_USERNAME, "root");
        String password = config.getProperty(COFNIG_INFLUXDB_PASSWORD, "root");
        influxDB = InfluxDBFactory.connect(address, username, password);
        influxDB.enableBatch(5000, 1000, TimeUnit.MILLISECONDS);
    }

    public void publish(MeasurementV1 m) {
        Point.Builder builder = Point.measurement(m.getName().toString()).time(m.getTimestamp(), TimeUnit.MILLISECONDS);
        builder.tag("service", m.getService().toString());
        builder.tag("host", m.getHost().toString());
        for(java.util.Map.Entry<CharSequence, CharSequence> tag: m.getTags().entrySet()) {
            builder.tag(tag.getKey().toString(), tag.getValue().toString());
        }
        for(java.util.Map.Entry<CharSequence, Double> fiekd: m.getFields().entrySet()) {
            builder.field(fiekd.getKey().toString(), fiekd.getValue());
        }
        influxDB.write(dbName, "default", builder.build());

    }

    public void close() {

    }
}
