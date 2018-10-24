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
//        org.apache.kafka.common.metrics.MetricsReporter, //FIXME this other interface is a bit odd but it seems to have some useful metrics
        kafka.metrics.KafkaMetricsReporter,
        io.amient.kafka.metrics.TopicReporterMBean {
    private static final Logger log = LoggerFactory.getLogger(TopicReporter.class);

    final private Map<MetricName, KafkaMetric> kafkaMetrics = new ConcurrentHashMap<>();
    private KafkaMetricsProcessorBuilder builder;
    private KafkaMetricsProcessor underlying;
    private Properties config;
    volatile private boolean running;
    volatile private boolean initialized;

    public TopicReporter() {
        log.info("INIT TopicReporter");
    }

    /**
     * Builder for programmatic configuration into an existing Yammer Metrics registry
     * @param registry metrics registry to which to attach the reporter
     * @return a builder instance for the reporter
     */
    public static KafkaMetricsProcessorBuilder forRegistry(MetricsRegistry registry) {
        return new KafkaMetricsProcessorBuilder(registry);
    }

    public String getMBeanName() {
        return "kafka:type=io.amient.kafka.metrics.TopicReporter";
    }

    public void init(VerifiableProperties kafkaConfig) {

        if (!initialized) {
            initialized = true;
            this.config = kafkaConfig.props();
            this.builder = forRegistry(Metrics.defaultRegistry());
            builder.configure(config);
            underlying = builder.build();
            startReporter(underlying.getPollingIntervaSeconds());
        }
    }

    public void startReporter(long pollingPeriodSecs) {
        if (initialized && !running) {
            underlying.start(pollingPeriodSecs, TimeUnit.SECONDS);
            running = true;
            log.info("Started TopicReporter instance with polling period " + pollingPeriodSecs + "  seconds");
        }
    }

    public void stopReporter() {
        if (initialized && running) {
            running = false;
            underlying.shutdown();
            log.info("Stopped TopicReporter instance");
            underlying = builder.build();
        }
    }


//    @Override
//    public void configure(Map<String, ?> configs) {
//        config = new Properties();
//        config.putAll(configs);
//    }
//
//    @Override
//    public void init(List<org.apache.kafka.common.metrics.KafkaMetric> metrics) {
//        for (org.apache.kafka.common.metrics.KafkaMetric metric : metrics) {
//            metricChange(metric);
//        }
//        if (! initialized) {
//            builder = forRegistry(new MetricsRegistry());
//            builder.configure(config);
//            builder.setKafkaMetrics(kafkaMetrics);
//            underlying = builder.build();
//            startReporter(underlying.getPollingIntervaSeconds());
//        }
//    }
//
//    @Override
//    public void metricChange(org.apache.kafka.common.metrics.KafkaMetric metric) {
//        kafkaMetrics.put(metric.metricName(), metric);
//    }
//
//    @Override
//    public void metricRemoval(KafkaMetric metric) {
//        kafkaMetrics.remove(metric.metricName());
//    }
//
//    @Override
//    public void close() {
//        stopReporter();
//    }

}
