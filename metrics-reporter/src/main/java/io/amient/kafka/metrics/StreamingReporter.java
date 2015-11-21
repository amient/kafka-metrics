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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class StreamingReporter extends AbstractPollingReporter implements MetricProcessor<Long> {

    static final String CONFIG_REPORTER_HOST = "kafka.metrics.StreamingReporter.host";
    static final String CONFIG_REPORTER_SERVICE = "kafka.metrics.StreamingReporter.service";
    static final String CONFIG_BOOTSTRAP_SERVERS = "kafka.metrics.StreamingReporter.bootstrap.servers";
    static final String CONFIG_SCHEMA_REGISTRY_URL = "kafka.metrics.StreamingReporter.schema.registry.url";
    static final String CONFIG_POLLING_INTERVAL_S = "kafka.metrics.StreamingReporter.polling.interval.s";

    private final MeasurementPublisher publisher;
    private final String host;
    private final String service;
    private final Clock clock;

    public StreamingReporter(MetricsRegistry metricsRegistry, Properties config) {
        super(metricsRegistry, "streaming-reporter");
        this.host = config.getProperty(CONFIG_REPORTER_HOST);
        this.service = config.getProperty(CONFIG_REPORTER_SERVICE);
        this.clock = Clock.defaultClock();
        this.publisher = new ProducerPublisher(config);
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

    public void run() {
        final Long time = clock.time();
        final Set<Map.Entry<MetricName, Metric>> metrics = getMetricsRegistry().allMetrics().entrySet();
        try {
            for (Map.Entry<MetricName, Metric> entry : metrics) {
                final MetricName metricName = entry.getKey();
                final Metric metric = entry.getValue();
                if (MetricPredicate.ALL.matches(metricName, metric)) {
                    metric.processWith(this, entry.getKey(), time);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private double convert(double value, TimeUnit unit) {
        switch (unit) {
            case NANOSECONDS:
                return value * 1000000000.0;
            case MICROSECONDS:
                return value * 1000000.0;
            case MILLISECONDS:
                return value * 1000.0;
            case MINUTES:
                return value / 60.0;
            case HOURS:
                return value / 3600.0;
            case DAYS:
                return value / 86400.0;
            default:
                return value;
        }
    }

    private Measurement createMeasurement(MetricName name, Long timestamp) {
        Measurement measurement = new Measurement();
        measurement.setTimestamp(timestamp);
        measurement.setHost(host);
        measurement.setService(service);
        measurement.setName(name.getName());
        measurement.setTags(new HashMap<CharSequence, CharSequence>());
        measurement.setFields(new HashMap<CharSequence, Double>());
        if (name.getGroup() != null && !name.getGroup().isEmpty()) measurement.getTags().put("group", name.getGroup());
        if (name.getType() != null && !name.getType().isEmpty()) measurement.getTags().put("type", name.getType());
        if (name.getScope() != null && !name.getScope().isEmpty()) measurement.getTags().put("scope", name.getScope());
        return measurement;
    }

    public void processMeter(MetricName name, Metered meter, Long timestamp) {
        Measurement measurement = createMeasurement(name, timestamp);
        measurement.getFields().put("count", Double.valueOf(meter.count()));
        measurement.getFields().put("mean-rate-per-sec", convert(meter.meanRate(), meter.rateUnit()));
        measurement.getFields().put("15-minute-rate-per-sec", convert(meter.fifteenMinuteRate(), meter.rateUnit()));
        measurement.getFields().put("5-minute-rate-per-sec", convert(meter.fiveMinuteRate(), meter.rateUnit()));
        measurement.getFields().put("1-minute-rate-per-sec", convert(meter.oneMinuteRate(), meter.rateUnit()));
        publisher.publish(measurement);
    }

    public void processCounter(MetricName name, Counter counter, Long timestamp) {
//        String key = name.getGroup();
//        MetricMessage value = new MetricMessage();
//        value.setGroup(name.getGroup());
//        value.setType(name.getType());
//        value.setName(name.getName());
    }

    public void processGauge(MetricName name, Gauge<?> gauge, Long timestamp) {

//        final String header = "# time,finalue";
//        final Producer producer = context.getProducer();
//        final String topic = prefix + "-metrics-gauge";
//        final String message = gauge.value().toString();
//        send(producer, header, topic, message);
    }

    public void processHistogram(MetricName name, Histogram histogram, Long timestamp) {
//        final String header = "# time,min,max,mean,median,stddev,95%,99%,99.9%";
//        final Producer producer = context.getProducer();
//        final Snapshot snapshot = histogram.getSnapshot();
//        final String topic = prefix + "-metrics-histogram";
//        final String message = valueOf(histogram.min()) + ',' + histogram.max() + ',' + histogram.mean() + ','
//                + snapshot.getMedian() + ',' + histogram.stdDev() + ',' + snapshot.get95thPercentile() + ',' + snapshot.get99thPercentile() + ','
//                + snapshot.get999thPercentile();
//        send(producer, header, topic, message);
    }

    public void processTimer(MetricName name, Timer timer, Long nanotime) {
//        final String header = "# time,min,max,mean,median,stddev,95%,99%,99.9%";
//        final Producer producer = context.getProducer();
//        final Snapshot snapshot = timer.getSnapshot();
//        final String topic = prefix + "-metrics-timer";
//        final String message = valueOf(timer.min()) + ',' + timer.max() + ',' + timer.mean() + ',' + snapshot.getMedian() + ','
//                + timer.stdDev() + ',' + snapshot.get95thPercentile() + ',' + snapshot.get99thPercentile() + ',' + snapshot.get999thPercentile();
//        send(producer, header, topic, message);
    }

}


