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

import java.util.HashMap;
import java.util.Map;

public class KafkaMetricsProcessorBuilder {

    private static final Logger log = LoggerFactory.getLogger(KafkaMetricsProcessorBuilder.class);

    private final MetricsRegistry registry;
    private Map<String, String> tags = new HashMap<String, String>();
    private String topic;
    private String bootstrapServers;
    private Map<MetricName, KafkaMetric> kafkaMetrics = null;

    public KafkaMetricsProcessorBuilder(MetricsRegistry registry) {
        this.registry = registry;
    }

    public KafkaMetricsProcessorBuilder setTopic(String topic) {
        this.topic = topic;
        return this;
    }

    public KafkaMetricsProcessorBuilder setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers =  bootstrapServers;
        return this;
    }

    public void setTag(String tagName, String tagValue) {
        tags.put(tagName, tagValue);
    }

    public KafkaMetricsProcessor build() {

        MeasurementPublisher publisher = new ProducerPublisher(bootstrapServers, topic);

        return new KafkaMetricsProcessor(registry, kafkaMetrics, publisher, tags);
    }

    public KafkaMetricsProcessorBuilder setKafkaMetrics(Map<MetricName,KafkaMetric> kafkaMetrics) {
        this.kafkaMetrics = kafkaMetrics;
        return this;
    }

    public KafkaMetricsProcessorBuilder setTags(HashMap<String,String> tags) {
        this.tags = tags;
        return this;
    }
}
