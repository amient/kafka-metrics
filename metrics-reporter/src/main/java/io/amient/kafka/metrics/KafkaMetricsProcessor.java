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
import com.yammer.metrics.stats.Snapshot;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class KafkaMetricsProcessor extends AbstractPollingReporter implements MetricProcessor<Long> {
    private static final Logger log = LoggerFactory.getLogger(KafkaMetricsProcessor.class);

    private final MeasurementPublisher publisher;
    private final MeasurementFormatter formatter;
    private final Clock clock;

    private final Map<org.apache.kafka.common.MetricName, KafkaMetric> kafkaMetrics;
    private final Map<String, String> fixedTags;
    private final Integer pollingIntervalSeconds;
    private final AdminClient admin;
    private final int brokerId;
    private boolean closed = false;

    public KafkaMetricsProcessor(
            int brokerId,
            AdminClient admin,
            MetricsRegistry metricsRegistry,
            Map<org.apache.kafka.common.MetricName, KafkaMetric> kafkaMetrics,
            MeasurementPublisher publisher,
            Map<String, String> fixedTags,
            Integer pollingIntervalSeconds
    ) {
        super(metricsRegistry, "streaming-reporter");
        this.brokerId = brokerId;
        this.kafkaMetrics = kafkaMetrics;
        this.clock = Clock.defaultClock();
        this.fixedTags = fixedTags;
        this.publisher = publisher;
        this.formatter = new MeasurementFormatter();
        this.pollingIntervalSeconds = pollingIntervalSeconds;
        this.admin = admin;
    }

    public Integer getPollingIntervaSeconds() {
        return pollingIntervalSeconds;
    }

    private MeasurementV1 createMeasurement(com.yammer.metrics.core.MetricName name,
                                            Long timestamp, Map<String, String> tags, Map<String, Double> fields) {
        MeasurementV1 measurement = new MeasurementV1();
        measurement.setTimestamp(timestamp);
        measurement.setName(name.getName());
        measurement.setTags(new HashMap<String, String>(tags));
        if (name.getGroup() != null && !name.getGroup().isEmpty()) measurement.getTags().put("group", name.getGroup());
        if (name.getType() != null && !name.getType().isEmpty()) measurement.getTags().put("type", name.getType());
        if (name.getScope() != null && !name.getScope().isEmpty()) {
            if (name.getGroup() != null && name.getGroup().startsWith("kafka.") && name.getScope().contains(".")) {
                /*
                 * Decompose old kafka metrics tags which uses yammer metrics Scope to "squash" all tags together
                 */
                String[] scope = name.getScope().split("\\.");
                for (int s = 0; s < scope.length; s += 2) {
                    measurement.getTags().put(scope[s], scope[s + 1]);
                }
            } else {
                measurement.getTags().put("scope", name.getScope());
            }
        }
        measurement.setFields(new HashMap<String, Double>(fields));
        return measurement;
    }

    public void publish(MeasurementV1 m) {
        if (!closed) {
            publisher.publish(m);
        }
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
            closed = true;
            if (publisher != null) publisher.close();
        }
    }

    @Override
    public void run() {
        final Long timestamp = clock.time();
        //process kafka metrics
        if (kafkaMetrics != null)
            for (Map.Entry<org.apache.kafka.common.MetricName, org.apache.kafka.common.metrics.KafkaMetric> m
                    : kafkaMetrics.entrySet()) {
                Double value = m.getValue().value();
                if (!value.isNaN() && !value.isInfinite()) {
                    MeasurementV1 measurement = new MeasurementV1();
                    measurement.setTimestamp(timestamp);
                    measurement.setName(m.getKey().name());
                    Map<String, String> tags = new HashMap<String, String>(fixedTags);
                    tags.put("group", m.getKey().group());
                    for (Map.Entry<String, String> tag : m.getValue().metricName().tags().entrySet()) {
                        tags.put(tag.getKey(), tag.getValue());
                    }
                    Map<String, Double> fields = new HashMap<String, Double>();
                    fields.put("Value", value);
                    measurement.setTags(tags);
                    measurement.setFields(fields);
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
        //process extra consumer metrics
        try {
            int controllerId = admin.describeCluster().controller().get(pollingIntervalSeconds, TimeUnit.SECONDS).id();
            if (brokerId == controllerId) {
                Collection<ConsumerGroupListing> consumerGroups = admin.listConsumerGroups().all().get(pollingIntervalSeconds, TimeUnit.SECONDS);

                consumerGroups.parallelStream().forEach(group -> {
                    try {
                        Map<TopicPartition, OffsetAndMetadata> offsets = admin.listConsumerGroupOffsets(group.groupId()).partitionsToOffsetAndMetadata().get(pollingIntervalSeconds, TimeUnit.SECONDS);
                        for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : offsets.entrySet()) {
                            MeasurementV1 measurement = new MeasurementV1();
                            measurement.setTimestamp(timestamp);
                            measurement.setName("ConsumerOffset");
                            Map<String, String> tags = new HashMap<String, String>(fixedTags);
                            tags.put("group", group.groupId());
                            tags.put("topic", entry.getKey().topic());
                            tags.put("partition", String.valueOf(entry.getKey().partition()));
                            Map<String, Double> fields = new HashMap<String, Double>();
                            fields.put("Value", (double) entry.getValue().offset());
                            measurement.setTags(tags);
                            measurement.setFields(fields);
                            publish(measurement);
                        }
                    } catch (Exception e) {
                        log.error("error while fetching offsets for group " + group, e);
                    }
                });
            }
        } catch (Exception e) {
            log.error("error while processing conusmer offsets", e);
        }
    }

    @Override
    public void processMeter(MetricName name, Metered meter, Long timestamp) {
        Map<String, Double> fields = new HashMap<String, Double>();
        fields.put("Count", Double.valueOf(meter.count()));
        fields.put("MeanRate", meter.meanRate());
        fields.put("FifteenMinuteRate", meter.fifteenMinuteRate());
        fields.put("FiveMinuteRate", meter.fiveMinuteRate());
        fields.put("OneMinuteRate", meter.oneMinuteRate());

        publish(createMeasurement(name, timestamp, fixedTags, fields));
    }

    @Override
    public void processCounter(MetricName name, Counter counter, Long timestamp) {
        Map<String, Double> fields = new HashMap<String, Double>();
        fields.put("Count", Double.valueOf(counter.count()));
        publish(createMeasurement(name, timestamp, fixedTags, fields));
    }

    @Override
    public void processGauge(MetricName name, Gauge<?> gauge, Long timestamp) {
        Map<String, Double> fields = new HashMap<String, Double>();
        try {
            Double value = formatter.anyValueToDouble(gauge.value());
            if (value != null) {
                fields.put("Value", value);
                publish(createMeasurement(name, timestamp, fixedTags, fields));
            }
        } catch (Exception e) {
            log.warn("Could not process gauge for metric " + name + ": " + e.getMessage());
        }
    }

    @Override
    public void processHistogram(MetricName name, Histogram histogram, Long timestamp) {
        Map<String, Double> fields = new HashMap<String, Double>();
        fields.put("Count", Double.valueOf(histogram.count()));
        fields.put("Max", histogram.max());
        fields.put("Mean", histogram.mean());
        fields.put("Min", histogram.min());
        fields.put("StdDev", histogram.stdDev());
        fields.put("Sum", histogram.sum());
        Snapshot snapshot = histogram.getSnapshot();
        fields.put("95thPercentile", snapshot.get95thPercentile());
        fields.put("98thPercentile", snapshot.get98thPercentile());
        fields.put("99thPercentile", snapshot.get99thPercentile());
        fields.put("999thPercentile", snapshot.get999thPercentile());
        publish(createMeasurement(name, timestamp, fixedTags, fields));
    }

    @Override
    public void processTimer(MetricName name, Timer timer, Long timestamp) {
        Map<String, Double> fields = new HashMap<String, Double>();
        fields.put("Count", Double.valueOf(timer.count()));
        fields.put("MeanRate", timer.meanRate());
        fields.put("FifteenMinuteRate", timer.fifteenMinuteRate());
        fields.put("FiveMinuteRate", timer.fiveMinuteRate());
        fields.put("OneMinuteRate", timer.oneMinuteRate());
        fields.put("Max", timer.max());
        fields.put("Mean", timer.mean());
        fields.put("Min", timer.min());
        fields.put("StdDev", timer.stdDev());
        fields.put("Sum", timer.sum());
        publish(createMeasurement(name, timestamp, fixedTags, fields));
    }


}


