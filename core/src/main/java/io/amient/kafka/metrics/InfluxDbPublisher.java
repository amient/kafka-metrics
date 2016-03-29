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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class InfluxDbPublisher implements MeasurementPublisher {

    static private final Logger log = LoggerFactory.getLogger(InfluxDbPublisher.class);
    static final String COFNIG_INFLUXDB_DATABASE = "influxdb.database";
    static final String COFNIG_INFLUXDB_URL = "influxdb.url";
    static final String COFNIG_INFLUXDB_USERNAME = "influxdb.username";
    static final String COFNIG_INFLUXDB_PASSWORD = "influxdb.password";
    private static final int DEFAULT_BACK_OFF_MS = 15000;
    final private String dbName;
    final private String address;
    private final String username;
    private final String password;

    private InfluxDB influxDB = null;

    volatile private long failureTimestamp = 0;

    public InfluxDbPublisher(Properties config) {
        this.dbName = config.getProperty(COFNIG_INFLUXDB_DATABASE, "metrics");
        this.address = config.getProperty(COFNIG_INFLUXDB_URL, "http://localhost:8086");
        this.username = config.getProperty(COFNIG_INFLUXDB_USERNAME, "root");
        this.password = config.getProperty(COFNIG_INFLUXDB_PASSWORD, "root");
    }

    public void publish(MeasurementV1 m) {
        Long time = m.getTimestamp();
        if (failureTimestamp > 0) {
            if (failureTimestamp + DEFAULT_BACK_OFF_MS < time) return; else failureTimestamp = 0;
        }
        try {
            tryPublish(m);
        } catch (Throwable e) {
            log.warn("Failed to publish measurement to InfluxDB, will retry...", e);
            influxDB = null;
            failureTimestamp = time;
        }
    }

    public void tryPublish(MeasurementV1 m) {
        if (influxDB == null) {
            influxDB = InfluxDBFactory.connect(address, username, password);
            influxDB.enableBatch(1000, 100, TimeUnit.MILLISECONDS);
        }
        Point.Builder builder = Point.measurement(m.getName().toString()).time(m.getTimestamp(), TimeUnit.MILLISECONDS);
        for (java.util.Map.Entry<String, String> tag : m.getTags().entrySet()) {
            builder.tag(tag.getKey().toString(), tag.getValue().toString());
        }
        for (java.util.Map.Entry<String, Double> field : m.getFields().entrySet()) {
            builder.field(field.getKey().toString(), field.getValue());
        }
        influxDB.write(dbName, "default", builder.build());
    }



    public void close() {

    }
}
