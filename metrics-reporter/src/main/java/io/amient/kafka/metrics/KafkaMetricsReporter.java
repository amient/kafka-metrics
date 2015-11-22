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

import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.MetricsRegistry;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricsReporter;

import java.util.List;
import java.util.Map;
import java.util.Properties;


public class KafkaMetricsReporter implements MetricsReporter {

    private MetricsRegistry registry;
    private StreamingReporter reporter;

    @Override
    public void configure(final Map<String, ?> configs) {
        this.registry = new MetricsRegistry();
        Properties props = new Properties() {{
            putAll(configs);
        }};
        reporter = new StreamingReporter(registry, props);
    }

    @Override
    public void init(List<KafkaMetric> metrics) {
        for (KafkaMetric metric : metrics) {
            metricChange(metric);
        }
        reporter.start();
    }

    @Override
    public void metricChange(final KafkaMetric metric) {
        registry.newGauge(new com.yammer.metrics.core.MetricName(
                        metric.metricName().group(),
                        "KafkaProducer",
                        metric.metricName().name()),
                new Gauge<Double>() {
                    private KafkaMetric m = metric;

                    @Override
                    public Double value() {
                        return m.value();
                    }
                }
        );
    }

    @Override
    public void close() {
        reporter.shutdown();
    }

}
