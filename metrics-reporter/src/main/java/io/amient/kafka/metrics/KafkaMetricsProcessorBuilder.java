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

import com.yammer.metrics.core.MetricsRegistry;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class KafkaMetricsProcessorBuilder {

    private static final Logger log = LoggerFactory.getLogger(KafkaMetricsProcessorBuilder.class);

    private static final String CONFIG_POLLING_INTERVAL = "kafka.metrics.polling.interval";
    private static final String CONFIG_REPORTER_TAG_PREFIX = "kafka.metrics.tag.";

    private MetricsRegistry registry;
    private Map<MetricName, KafkaMetric> kafkaMetrics = null;
    private Map<String, String> tags = new HashMap<String, String>();
    private String topic = "metrics";
    private String bootstrapServers;
    private Integer pollingIntervalSeconds = 10;

    public KafkaMetricsProcessorBuilder(MetricsRegistry registry) {
        this.registry = registry;
    }

    public KafkaMetricsProcessorBuilder configure(Properties config) {
        if (!config.containsKey(ProducerPublisher.CONFIG_BOOTSTRAP_SERVERS) && config.containsKey("port")) {
            //if this is plugged into kafka broker itself we can use it for metrics producer itself
            config.put(ProducerPublisher.CONFIG_BOOTSTRAP_SERVERS, "localhost:" + config.get("port"));
        }
        if (config.containsKey("bootstrap.servers") && !config.containsKey(ProducerPublisher.CONFIG_BOOTSTRAP_SERVERS)) {
            //if plugged into kafka producer and bootstrap servers not specified, re-use the wrapping producer's ones
            config.put(ProducerPublisher.CONFIG_BOOTSTRAP_SERVERS, config.getProperty("bootstrap.servers"));
        }

        for (Enumeration<Object> e = config.keys(); e.hasMoreElements(); ) {
            Object propKey = e.nextElement();
            configure((String) propKey, config.get(propKey).toString());
        }
        return this;
    }

    public KafkaMetricsProcessorBuilder configure(String propName, String propValue) {
        if (propName.startsWith(CONFIG_REPORTER_TAG_PREFIX)) {
            String tag = propName.substring(CONFIG_REPORTER_TAG_PREFIX.length());
            return setTag(tag, propValue);
        } else if (propName.equals(ProducerPublisher.CONFIG_METRICS_TOPIC)) {
            return setTopic(propValue);
        } else if (propName.equals(ProducerPublisher.CONFIG_BOOTSTRAP_SERVERS)) {
            setBootstrapServers(propValue);
        } else if (propName.equals(CONFIG_POLLING_INTERVAL)) {
            setPollingInterval(propValue);
        }
        return this;
    }

    public KafkaMetricsProcessorBuilder setTopic(String topic) {
        this.topic = topic;
        return this;
    }

    public KafkaMetricsProcessorBuilder setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers =  bootstrapServers;
        return this;
    }

    public KafkaMetricsProcessorBuilder setTag(String tagName, String tagValue) {
        tags.put(tagName, tagValue);
        return this;
    }

    public KafkaMetricsProcessorBuilder setKafkaMetrics(Map<MetricName,KafkaMetric> kafkaMetrics) {
        this.kafkaMetrics = kafkaMetrics;
        return this;
    }

    public KafkaMetricsProcessorBuilder setTags(HashMap<String,String> tags) {
        this.tags = tags;
        return this;
    }

    public KafkaMetricsProcessorBuilder setPollingInterval(String interval) {
        if (interval.equals("1s")) pollingIntervalSeconds = 1;
        else if (interval.equals("10s")) pollingIntervalSeconds = 10;
        else if (interval.equals("1m")) pollingIntervalSeconds = 60;
        else throw new IllegalArgumentException("Illegal configuration value for "
                    + CONFIG_POLLING_INTERVAL + " = `" + interval  + "`, allowed values are: 1s, 10s, 1m");
        return this;
    }

    public void decorateConfig(Properties config) {
        config.put("kafka.metrics.reporters", TopicReporter.class.getName());
        config.put(ProducerPublisher.CONFIG_METRICS_TOPIC, topic);
        config.put(ProducerPublisher.CONFIG_BOOTSTRAP_SERVERS, bootstrapServers);
        config.put(CONFIG_POLLING_INTERVAL, pollingIntervalSeconds.toString() + "s");
        for(Map.Entry<String,String> tag: tags.entrySet()) {
            config.put(CONFIG_REPORTER_TAG_PREFIX + tag.getKey(), tag.getValue());
        }
    }

    /**
     * generate properties for New Kafka Client Producer ( 0.8.2+) and Consumer (0.9+)
     * @param kafkaClientConfig
     */
    public void decorateKafkaClientConfig(Properties kafkaClientConfig) {
        kafkaClientConfig.put("metric.reporters", TopicReporter.class.getName());
        decorateConfig(kafkaClientConfig);
    }

    public KafkaMetricsProcessor build() {
        log.info("Building TopicReporter: " + ProducerPublisher.CONFIG_METRICS_TOPIC + "=" + topic);
        log.info("Building TopicReporter: " + ProducerPublisher.CONFIG_BOOTSTRAP_SERVERS + "=" + bootstrapServers);
        log.info("Building TopicReporter: " + CONFIG_POLLING_INTERVAL + pollingIntervalSeconds);
        for(Map.Entry<String,String> tag: tags.entrySet()) {
            log.info("Building TopicReporter with tag: " + tag.getKey() + "=" + tag.getValue());
        }

        MeasurementPublisher publisher = new ProducerPublisher(bootstrapServers, topic);
        return new KafkaMetricsProcessor(registry, kafkaMetrics, publisher, tags, pollingIntervalSeconds);
    }

}
