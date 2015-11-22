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

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class StreamingReporter extends AbstractPollingReporter implements MetricProcessor<Long> {

    static final String CONFIG_REPORTER_HOST = "kafka.metrics.StreamingReporter.host";
    static final String CONFIG_REPORTER_SERVICE = "kafka.metrics.StreamingReporter.service";
    static final String CONFIG_BOOTSTRAP_SERVERS = "kafka.metrics.StreamingReporter.bootstrap.servers";
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

    public void processMeter(MetricName name, Metered meter, Long timestamp) {
        MeasurementV1 measurement = MeasurementFactory.createMeasurement(host, service, name, timestamp);
        measurement.getFields().put("count", Double.valueOf(meter.count()));
        measurement.getFields().put("mean-rate", meter.meanRate());
        measurement.getFields().put("15-minute-rate", meter.fifteenMinuteRate());
        measurement.getFields().put("5-minute-rate", meter.fiveMinuteRate());
        measurement.getFields().put("1-minute-rate", meter.oneMinuteRate());
        publisher.publish(measurement);
    }

    public void processCounter(MetricName name, Counter counter, Long timestamp) {
        MeasurementV1 measurement = MeasurementFactory.createMeasurement(host, service, name, timestamp);
        measurement.getFields().put("count", Double.valueOf(counter.count()));
        publisher.publish(measurement);
    }

    public void processGauge(MetricName name, Gauge<?> gauge, Long timestamp) {
        MeasurementV1 measurement = MeasurementFactory.createMeasurement(host, service, name, timestamp);
        if (gauge.value() instanceof Double) {
            measurement.getFields().put("value", (Double)(gauge.value()));
            publisher.publish(measurement);
        } else if (gauge.value() instanceof Long || gauge.value() instanceof Float) {
            measurement.getFields().put("value", Double.valueOf(gauge.value().toString()));
            publisher.publish(measurement);
        }
    }

    public void processHistogram(MetricName name, Histogram histogram, Long timestamp) {
        MeasurementV1 measurement = MeasurementFactory.createMeasurement(host, service, name, timestamp);
        measurement.getFields().put("count", Double.valueOf(histogram.count()));
        measurement.getFields().put("max", histogram.max());
        measurement.getFields().put("mean", histogram.mean());
        measurement.getFields().put("min", histogram.min());
        measurement.getFields().put("stdDev", histogram.stdDev());
        measurement.getFields().put("sum", histogram.sum());
        publisher.publish(measurement);
    }

    public void processTimer(MetricName name, Timer timer, Long timestamp) {
        MeasurementV1 measurement = MeasurementFactory.createMeasurement(host, service, name, timestamp);
        measurement.getFields().put("count", Double.valueOf(timer.count()));
        measurement.getFields().put("mean-rate", timer.meanRate());
        measurement.getFields().put("15-minute-rate", timer.fifteenMinuteRate());
        measurement.getFields().put("5-minute-rate", timer.fiveMinuteRate());
        measurement.getFields().put("1-minute-rate", timer.oneMinuteRate());
        measurement.getFields().put("max", timer.max());
        measurement.getFields().put("mean", timer.mean());
        measurement.getFields().put("min", timer.min());
        measurement.getFields().put("stdDev", timer.stdDev());
        measurement.getFields().put("sum", timer.sum());
        publisher.publish(measurement);
    }

}


