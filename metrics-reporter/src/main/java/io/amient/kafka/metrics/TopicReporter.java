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
import kafka.utils.VerifiableProperties;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class TopicReporter
        implements
        kafka.metrics.KafkaMetricsReporter,
        io.amient.kafka.metrics.TopicReporterMBean,
        org.apache.kafka.common.metrics.MetricsReporter {
    private static final Logger log = LoggerFactory.getLogger(TopicReporter.class);
    private KafkaMetricsProcessor underlying;
    volatile private boolean running;
    volatile private boolean initialized;
    private Properties config;
    final private Map<MetricName, KafkaMetric> kafkaMetrics = new ConcurrentHashMap<MetricName, KafkaMetric>();

    public TopicReporter() {}

    private void init() {
        if (!initialized) {
            underlying = new KafkaMetricsProcessor(Metrics.defaultRegistry(), kafkaMetrics, this.config);
            initialized = true;
            startReporter(Integer.valueOf(config.getProperty(KafkaMetricsProcessor.CONFIG_POLLING_INTERVAL_S, "10")));
        }
    }

    /*
     * kafka.metrics.KafkaMetricsProcessor and TopicReporterMBean impelemntation
     */
    public String getMBeanName() {
        return "kafka:type=io.amient.kafka.metrics.TopicReporter";
    }

    synchronized public void init(VerifiableProperties kafkaConfig) {
        if (!initialized) {
            this.config = kafkaConfig.props();
            config.put(KafkaMetricsProcessor.CONFIG_REPORTER_SERVICE, "kafka-broker-" + config.get("broker.id"));
            config.put(KafkaMetricsProcessor.CONFIG_BOOTSTRAP_SERVERS, "localhost:" + config.get("port"));
            init();
        }
    }

    synchronized public void startReporter(long pollingPeriodSecs) {
        if (initialized && !running) {
            underlying.start(pollingPeriodSecs, TimeUnit.SECONDS);
            running = true;
            log.info("Started TopicReproter instance with polling period " + pollingPeriodSecs + "  seconds");
        }
    }

    public synchronized void stopReporter() {
        if (initialized && running) {
            underlying.shutdown();
            running = false;
            log.info("Stopped TopicReproter instance");
            this.underlying = new KafkaMetricsProcessor(Metrics.defaultRegistry(), kafkaMetrics, config);
        }
    }

    /*
     * org.apache.kafka.common.metrics.MetricsReporter interface implementations follows
     */

    @Override
    public void configure(Map<String, ?> configs) {
        config = new Properties();
        config.putAll(configs);
    }

    @Override
    public void init(List<org.apache.kafka.common.metrics.KafkaMetric> metrics) {
        init();
        for (org.apache.kafka.common.metrics.KafkaMetric metric : metrics) {
            metricChange(metric);
        }
    }

    @Override
    public void metricChange(org.apache.kafka.common.metrics.KafkaMetric metric) {
        kafkaMetrics.put(metric.metricName(), metric);
    }

    @Override
    public void close() {
        stopReporter();
    }

}
