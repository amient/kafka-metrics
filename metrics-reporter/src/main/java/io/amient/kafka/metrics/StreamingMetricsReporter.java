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

import com.yammer.metrics.Metrics;
import kafka.metrics.KafkaMetricsReporter;
import kafka.utils.VerifiableProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class StreamingMetricsReporter implements KafkaMetricsReporter, StreamingMetricsReporterMBean {
    private static final Logger log = LoggerFactory.getLogger(StreamingMetricsReporter.class);
    private StreamingReporter underlying;
    volatile private boolean running;
    volatile private boolean initialized;
    private Properties config;

    public String getMBeanName() {
        return "kafka:type=io.amient.kafka.metrics.StreamingMetricsReporter";
    }

    synchronized public void init(VerifiableProperties kafkaConfig) {
        if (!initialized) {
            this.config = kafkaConfig.props();
            this.config.put(StreamingReporter.CONFIG_REPORTER_SERVICE, "kafka-broker-" + kafkaConfig.getInt("broker.id"));
            this.config.put(StreamingReporter.CONFIG_BOOTSTRAP_SERVERS, "localhost:" + kafkaConfig.getInt("port", 9092));
            this.underlying = new StreamingReporter(Metrics.defaultRegistry(), this.config);
            initialized = true;
            startReporter(10);
        }
    }

    synchronized public void startReporter(long pollingPeriodSecs) {
        if (initialized && !running) {
            underlying.start(pollingPeriodSecs, TimeUnit.SECONDS);
            running = true;
            log.info("Started StreamingReporter instance with polling period " + pollingPeriodSecs + "  seconds");
        }
    }

    public synchronized void stopReporter() {
        if (initialized && running) {
            underlying.shutdown();
            running = false;
            log.info("Stopped StreamingReporter instance");
            this.underlying = new StreamingReporter(Metrics.defaultRegistry(), config);
        }
    }
}
