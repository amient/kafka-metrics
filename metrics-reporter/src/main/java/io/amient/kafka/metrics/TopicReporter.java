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

public class TopicReporter
        implements
        kafka.metrics.KafkaMetricsReporter,
        io.amient.kafka.metrics.TopicReporterMBean,
        org.apache.kafka.common.metrics.MetricsReporter {
    private static final Logger log = LoggerFactory.getLogger(TopicReporter.class);

    private static final String CONFIG_METRICS_TOPIC = "kafka.metrics.topic";
    private static final String CONFIG_POLLING_INTERVAL = "kafka.metrics.polling.interval";
    private static final String CONFIG_BOOTSTRAP_SERVERS = "kafka.metrics.bootstrap.servers";

    private static final String CONFIG_REPORTER_TAG_PREFIX = "kafka.metrics.tag.";
    private static final String CONFIG_REPORTER_TAG_SERVICE = CONFIG_REPORTER_TAG_PREFIX + "service";
    private Map<String, String> fixedTags = new HashMap<String,String>();

    private KafkaMetricsProcessor underlying;
    volatile private boolean running;
    volatile private boolean initialized;
    private Properties config;
    final private Map<MetricName, KafkaMetric> kafkaMetrics = new ConcurrentHashMap<MetricName, KafkaMetric>();
    private MeasurementPublisher publisher;

    public TopicReporter() {}

    /**
     * Builder for programatic configuration into exesting Yammer Metrics registry
     * @param registry
     * @return
     */
    public static KafkaMetricsProcessorBuilder forRegistry(MetricsRegistry registry) {
        return new KafkaMetricsProcessorBuilder(registry);
    }

    private void init() {
        if (!initialized) {
            log.info("Initializing TopicReporter");
            HashMap<String, String> fixedTags = new HashMap<String, String>();
            for (Enumeration<Object> e = config.keys(); e.hasMoreElements(); ) {
                Object propKey = e.nextElement();
                String propName = ((String) propKey);
                String propValue = (String) config.get(propKey);
                if (propName.startsWith(CONFIG_REPORTER_TAG_PREFIX)) {
                    fixedTags.put(propName.substring(CONFIG_REPORTER_TAG_PREFIX.length()), propValue);
                    log.info("Initializing TopicReporter tag: " + propName.substring(CONFIG_REPORTER_TAG_PREFIX.length()) + "=" + propValue);
                }
            }
            log.info("Initializing TopicReporter " + CONFIG_BOOTSTRAP_SERVERS + "=" + config.getProperty(CONFIG_BOOTSTRAP_SERVERS));
            log.info("Initializing TopicReporter " + CONFIG_METRICS_TOPIC + "=" + config.getProperty(CONFIG_METRICS_TOPIC, "_metrics"));
            underlying = forRegistry(Metrics.defaultRegistry())
                    .setKafkaMetrics(kafkaMetrics)
                    .setBootstrapServers(config.getProperty(CONFIG_BOOTSTRAP_SERVERS))
                    .setTopic(config.getProperty(CONFIG_METRICS_TOPIC, "_metrics"))
                    .setTags(fixedTags)
                    .build();
            Integer pollingIntervalSeconds;
            String interval = config.getProperty(CONFIG_POLLING_INTERVAL, "10s");
            if (interval == "1s") pollingIntervalSeconds = 1;
            else if (interval == "10s") pollingIntervalSeconds = 10;
            else if (interval == "1m") pollingIntervalSeconds = 60;
            else throw new IllegalArgumentException("Illegal configuration value for "
                        + CONFIG_POLLING_INTERVAL + ", allowed values are: 1s, 10s, 1m");
            log.info("Initializing TopicReporter polling interval (seconds): " + pollingIntervalSeconds);
            initialized = true;
            startReporter(pollingIntervalSeconds);
        }
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
            if (!config.containsKey(CONFIG_REPORTER_TAG_SERVICE)) {
                config.put(CONFIG_REPORTER_TAG_SERVICE, "kafka-broker"
                    + (config.containsKey("broker.id") ? "-" + config.get("broker.id") : ""));
            }
            if (!config.containsKey(CONFIG_BOOTSTRAP_SERVERS) && config.containsKey("port")) {
                config.put(CONFIG_BOOTSTRAP_SERVERS, "localhost:" + config.get("port"));
            }
            init();
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
            underlying.shutdown();
            running = false;
            log.info("Stopped TopicReproter instance");
            this.underlying = new KafkaMetricsProcessor(
                    Metrics.defaultRegistry(),
                    kafkaMetrics,
                    publisher,
                    fixedTags);
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
        if (config.containsKey("bootstrap.servers") && !config.containsKey(CONFIG_BOOTSTRAP_SERVERS)) {
            config.put(CONFIG_BOOTSTRAP_SERVERS, config.getProperty("bootstrap.servers"));
        }
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
