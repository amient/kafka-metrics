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

import com.yammer.metrics.core.*;
import com.yammer.metrics.reporting.AbstractPollingReporter;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class KafkaMetricsProcessor extends AbstractPollingReporter implements MetricProcessor<Long> {
    private static final Logger log = LoggerFactory.getLogger(KafkaMetricsProcessor.class);

    private final MeasurementPublisher publisher;
    private final Clock clock;

    private final Map<org.apache.kafka.common.MetricName, KafkaMetric> kafkaMetrics;
    private final Map<String, String> fixedTags;

    public KafkaMetricsProcessor(
            MetricsRegistry metricsRegistry,
            Map<org.apache.kafka.common.MetricName, KafkaMetric> kafkaMetrics,
            MeasurementPublisher publisher,
            Map<String, String> fixedTags
            ) {
        super(metricsRegistry, "streaming-reporter");
        this.kafkaMetrics = kafkaMetrics;
        this.clock = Clock.defaultClock();
        this.fixedTags = fixedTags;
        this.publisher = publisher;
    }

    private MeasurementV1 createMeasurement(com.yammer.metrics.core.MetricName name,
            Long timestamp, Map<String,String> tags, Map<String,Double> fields) {
        MeasurementV1 measurement = new MeasurementV1();
        measurement.setTimestamp(timestamp);
        measurement.setName(name.getName());
        measurement.setTags(new HashMap<CharSequence, CharSequence>(tags));
        if (name.getGroup() != null && !name.getGroup().isEmpty()) measurement.getTags().put("group", name.getGroup());
        if (name.getType() != null && !name.getType().isEmpty()) measurement.getTags().put("type", name.getType());
        if (name.getScope() != null && !name.getScope().isEmpty()) measurement.getTags().put("scope", name.getScope());
        measurement.setFields(new HashMap<CharSequence, Double>(fields));
        return measurement;
    }

    public void publish(MeasurementV1 m) {
        publisher.publish(m);
    }

    @Override
    public void start(long timeout, TimeUnit unit) {
        super.start(timeout, unit);
    }

    @Override
    public void shutdown() {
        try {
            super.shutdown();
        } finally {
            if (publisher != null) publisher.close();
        }
    }

    @Override
    public void run() {
        final Long timestamp = clock.time();
        //process kafka metrics
        for(Map.Entry<org.apache.kafka.common.MetricName, org.apache.kafka.common.metrics.KafkaMetric> m
                : kafkaMetrics.entrySet()) {
            Double value = m.getValue().value();
            if (!value.isNaN() && !value.isInfinite()) {
                Map<String,String> tags = new HashMap<String, String>(fixedTags);
                tags.put("group", m.getKey().group());
                for (Map.Entry<String, String> tag : m.getValue().metricName().tags().entrySet()) {
                    tags.put(tag.getKey(), tag.getValue());
                }
                Map<String, Double> fields = new HashMap<String, Double>();
                fields.put("value", value);
                MeasurementV1 measurement = new MeasurementV1();
                measurement.setTimestamp(timestamp);
                measurement.setName(m.getKey().name());
                measurement.setTags(new HashMap<CharSequence, CharSequence>(tags));
                measurement.setFields(new HashMap<CharSequence, Double>(fields));
                publish(measurement);
            }
        }
        //process yammer metrics
        final Set<Map.Entry<MetricName, Metric>> metrics = getMetricsRegistry().allMetrics().entrySet();
        try {
            for (Map.Entry<MetricName, Metric> entry : metrics) {
                final MetricName metricName = entry.getKey();
                final Metric metric = entry.getValue();
                if (MetricPredicate.ALL.matches(metricName, metric)) {
                    metric.processWith(this, entry.getKey(), timestamp);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void processMeter(MetricName name, Metered meter, Long timestamp) {
        Map<String, Double> fields = new HashMap<String, Double>();
        fields.put("count", Double.valueOf(meter.count()));
        fields.put("mean-rate", meter.meanRate());
        fields.put("15-minute-rate", meter.fifteenMinuteRate());
        fields.put("5-minute-rate", meter.fiveMinuteRate());
        fields.put("1-minute-rate", meter.oneMinuteRate());

        publish(createMeasurement(name, timestamp, fixedTags, fields));
    }

    @Override
    public void processCounter(MetricName name, Counter counter, Long timestamp) {
        Map<String, Double> fields = new HashMap<String, Double>();
        fields.put("count", Double.valueOf(counter.count()));
        publish(createMeasurement(name, timestamp, fixedTags, fields));
    }

    @Override
    public void processGauge(MetricName name, Gauge<?> gauge, Long timestamp) {
        Map<String, Double> fields = new HashMap<String, Double>();
        try {
            if (gauge.value() instanceof Double) {
                Double value = ((Double) gauge.value());
                if (!value.isNaN() && !value.isInfinite()) {
                    fields.put("value", value);
                }
            } else if ((gauge.value() instanceof Long) || (gauge.value() instanceof Integer)) {
                fields.put("value", Double.valueOf(gauge.value().toString()));
            } else if ((gauge.value() instanceof Float)) {
                Float value = ((Float) gauge.value());
                if (!value.isNaN() && !value.isInfinite()) {
                    fields.put("value", ((Float) gauge.value()).doubleValue());
                }
            } else {
                return;
            }
            publish(createMeasurement(name, timestamp, fixedTags, fields));
        } catch (Exception e) {
            log.warn("Could not process gauge for metric " + name +": " + e.getMessage());
        }
    }

    @Override
    public void processHistogram(MetricName name, Histogram histogram, Long timestamp) {
        Map<String, Double> fields = new HashMap<String, Double>();
        fields.put("count", Double.valueOf(histogram.count()));
        fields.put("max", histogram.max());
        fields.put("mean", histogram.mean());
        fields.put("min", histogram.min());
        fields.put("stdDev", histogram.stdDev());
        fields.put("sum", histogram.sum());
        publish(createMeasurement(name, timestamp, fixedTags, fields));
    }

    @Override
    public void processTimer(MetricName name, Timer timer, Long timestamp) {
        Map<String, Double> fields = new HashMap<String, Double>();
        fields.put("count", Double.valueOf(timer.count()));
        fields.put("mean-rate", timer.meanRate());
        fields.put("15-minute-rate", timer.fifteenMinuteRate());
        fields.put("5-minute-rate", timer.fiveMinuteRate());
        fields.put("1-minute-rate", timer.oneMinuteRate());
        fields.put("max", timer.max());
        fields.put("mean", timer.mean());
        fields.put("min", timer.min());
        fields.put("stdDev", timer.stdDev());
        fields.put("sum", timer.sum());
        publish(createMeasurement(name, timestamp, fixedTags, fields));
    }



}


