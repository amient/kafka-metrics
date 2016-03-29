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
import com.yammer.metrics.core.MetricsRegistry;
import kafka.utils.VerifiableProperties;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class TopicReporter implements
        kafka.metrics.KafkaMetricsReporter,
        io.amient.kafka.metrics.TopicReporterMBean,
        org.apache.kafka.common.metrics.MetricsReporter {
    private static final Logger log = LoggerFactory.getLogger(TopicReporter.class);

    final private Map<MetricName, KafkaMetric> kafkaMetrics = new ConcurrentHashMap<MetricName, KafkaMetric>();
    private KafkaMetricsProcessorBuilder builder;
    private KafkaMetricsProcessor underlying;
    private Properties config;
    volatile private boolean running;
    volatile private boolean initialized;

    public TopicReporter() {}

    /**
     * Builder for programmatic configuration into an existing Yammer Metrics registry
     * @param registry metrics registry to which to attach the reporter
     * @return a builder instance for the reporter
     */
    public static KafkaMetricsProcessorBuilder forRegistry(MetricsRegistry registry) {
        return new KafkaMetricsProcessorBuilder(registry);
    }

    /*
     * kafka.metrics.kafkaMetricsProcessor and TopicReporterMBean for Kafka Broker integration
     */
    public String getMBeanName() {
        return "kafka:type=io.amient.kafka.metrics.TopicReporter";
    }

    synchronized public void init(VerifiableProperties kafkaConfig) {
        if (!initialized) {
            this.config = kafkaConfig.props();
            this.builder = forRegistry(Metrics.defaultRegistry());
            builder.configure(config);
            underlying = builder.build();
            initialized = true;
            startReporter(underlying.getPollingIntervaSeconds());
        }
    }

    synchronized public void startReporter(long pollingPeriodSecs) {
        if (initialized && !running) {
            underlying.start(pollingPeriodSecs, TimeUnit.SECONDS);
            running = true;
            log.info("Started TopicReporter instance with polling period " + pollingPeriodSecs + "  seconds");
        }
    }

    public synchronized void stopReporter() {
        if (initialized && running) {
            running = false;
            underlying.shutdown();
            log.info("Stopped TopicReporter instance");
            underlying = builder.build();
        }
    }

    /*
     * org.apache.kafka.common.metrics.MetricsReporter interface implementation for new Kafka Producer (0.8.2+)
     * and Consumer (0.9+)
     */

    @Override
    public void configure(Map<String, ?> configs) {
        config = new Properties();
        config.putAll(configs);
    }

    @Override
    public void init(List<org.apache.kafka.common.metrics.KafkaMetric> metrics) {
        builder = forRegistry(new MetricsRegistry());
        builder.configure(config);
        builder.setKafkaMetrics(kafkaMetrics);
        for (org.apache.kafka.common.metrics.KafkaMetric metric : metrics) {
            metricChange(metric);
        }
        underlying = builder.build();
        initialized = true;
        startReporter(underlying.getPollingIntervaSeconds());
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
