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
import kafka.javaapi.producer.Producer;
import kafka.producer.ProducerConfig;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class StreamingReporter extends AbstractPollingReporter implements MetricProcessor<Producer> {

    private final StreamingKafkaMetricsConfig metricsConfig;
    private final Producer producer;
    private final MetricPredicate predicate = MetricPredicate.ALL;

    public StreamingReporter(MetricsRegistry metricsRegistry, final StreamingKafkaMetricsConfig metricsConfig) {
        super(metricsRegistry, "streaming-reporter");
        this.metricsConfig = metricsConfig;

        Properties producerConfig = new Properties();

        this.producer = new Producer(new ProducerConfig(new Properties() {{
            put("metadata.broker.list", "localhost:" + metricsConfig.brokerPort);
        }}));

    }

    //    private final Clock clock = Clock.defaultClock();
//    private Long startTime = 0L;
    @Override
    public void start(long period, TimeUnit unit) {
//        this.startTime = clock.time();
        super.start(period, unit);
    }

    @Override
    public void shutdown() {
        try {
            super.shutdown();
        } finally {
            if (producer != null) producer.close();
        }
    }

    public void run() {
        final Set<Map.Entry<MetricName, Metric>> metrics = getMetricsRegistry().allMetrics().entrySet();
        try {
            for (Map.Entry<MetricName, Metric> entry : metrics) {
                final MetricName metricName = entry.getKey();
                final Metric metric = entry.getValue();
                if (predicate.matches(metricName, metric)) {
                    metric.processWith(this, entry.getKey(), producer);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void processGauge(MetricName name, Gauge<?> gauge, Producer producer) {
//        final String header = "# time,finalue";
//        final Producer producer = context.getProducer();
//        final String topic = prefix + "-metrics-gauge";
//        final String message = gauge.value().toString();
//        send(producer, header, topic, message);
    }

    public void processCounter(MetricName name, Counter counter, Producer producer) {
//        final String header = "# time,count";
//        final Producer producer = context.getProducer();
//        final String topic = prefix + "-metrics-counter";
//        final String message = valueOf(counter.count());
//        send(producer, header, topic, message);
    }

    public void processMeter(MetricName name, Metered meter, Producer producer) {
//        final String header = "# name,time,count,1 min rate,mean rate,5 min rate,15 min rate";
//        final String topic = prefix + "-metrics-meter";
//        final String message = name.getName() + "," + valueOf(meter.count()) + ',' + meter.oneMinuteRate() + ',' + meter.meanRate() + ','
//                + meter.fiveMinuteRate() + ',' + meter.fifteenMinuteRate();
//        send(producer, header, topic, message);
    }

    public void processHistogram(MetricName name, Histogram histogram, Producer producer) {
//        final String header = "# time,min,max,mean,median,stddev,95%,99%,99.9%";
//        final Producer producer = context.getProducer();
//        final Snapshot snapshot = histogram.getSnapshot();
//        final String topic = prefix + "-metrics-histogram";
//        final String message = valueOf(histogram.min()) + ',' + histogram.max() + ',' + histogram.mean() + ','
//                + snapshot.getMedian() + ',' + histogram.stdDev() + ',' + snapshot.get95thPercentile() + ',' + snapshot.get99thPercentile() + ','
//                + snapshot.get999thPercentile();
//        send(producer, header, topic, message);
    }

    public void processTimer(MetricName name, Timer timer, Producer producer) {
//        final String header = "# time,min,max,mean,median,stddev,95%,99%,99.9%";
//        final Producer producer = context.getProducer();
//        final Snapshot snapshot = timer.getSnapshot();
//        final String topic = prefix + "-metrics-timer";
//        final String message = valueOf(timer.min()) + ',' + timer.max() + ',' + timer.mean() + ',' + snapshot.getMedian() + ','
//                + timer.stdDev() + ',' + snapshot.get95thPercentile() + ',' + snapshot.get99thPercentile() + ',' + snapshot.get999thPercentile();
//        send(producer, header, topic, message);
    }


//    private void send(Producer producer, String header, String topic, String message) {
//        final Long time = TimeUnit.MILLISECONDS.toSeconds(clock.time() - startTime);
//        try {
//            producer.send(new KeyedMessage(topic, format("%s\n%d,%s", header, time, message).getBytes("UTF-8")));
//        } catch (UnsupportedEncodingException e) {
//            throw new RuntimeException(e);
//        }
//    }
}


