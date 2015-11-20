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
import kafka.metrics.KafkaMetricsReporterMBean;
import kafka.utils.VerifiableProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

interface StreamingMetricsReporterMBean extends KafkaMetricsReporterMBean {}

public class StreamingMetricsReporter implements KafkaMetricsReporter, StreamingMetricsReporterMBean {
    private static final Logger log = LoggerFactory.getLogger(StreamingMetricsReporter.class);
    private StreamingKafkaMetricsConfig metricsConfig;
    private StreamingReporter underlying;
    volatile private boolean running;
    volatile private boolean initialized;

    public String getMBeanName() {
        return "kafka:type=io.amient.kafka.metrics.StreamingMetricsReporter";
    }

    synchronized public void init(VerifiableProperties kafkaConfig) {
        if (!initialized) {
            this.metricsConfig = new StreamingKafkaMetricsConfig(kafkaConfig);
            this.underlying = new StreamingReporter(Metrics.defaultRegistry(), metricsConfig);
            initialized = true;
            startReporter(metricsConfig.pollingIntervalSecs());
        }
    }

    synchronized public void startReporter(long pollingPeriodSecs) {
        if (initialized && !running) {
            underlying.start(pollingPeriodSecs, TimeUnit.SECONDS);
            running = true;
            log.info(String.format("Started Kafka Topic metrics reporter with polling period %d seconds", pollingPeriodSecs));
        }
    }

    public synchronized void stopReporter() {
        if (initialized && running) {
            underlying.shutdown();
            running = false;
            log.info("Stopped Kafka Topic metrics reporter");
            this.underlying = new StreamingReporter(Metrics.defaultRegistry(), metricsConfig);
        }
    }
}
