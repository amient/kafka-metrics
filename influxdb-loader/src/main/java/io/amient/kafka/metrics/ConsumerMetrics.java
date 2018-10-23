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

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ConsumerMetrics {

    static private final Logger log = LoggerFactory.getLogger(ConsumerMetrics.class);

    static private final String CONFIG_PREFIX = "consumer.";
    static final String COFNIG_CONSUMER_TOPIC = CONFIG_PREFIX + "topic";
    static final String COFNIG_CONSUMER_THREADS = CONFIG_PREFIX + "numThreads";

    static final String DEFAULT_CLIENT_ID = "kafka-metrics";

    private KafkaConsumer<String, List<MeasurementV1>> consumer = null;

    volatile private Boolean terminated = false;

    public ConsumerMetrics(Properties props) {
        String topic = props.getProperty(COFNIG_CONSUMER_TOPIC, "metrics");
        Integer numThreads = Integer.parseInt(props.getProperty(COFNIG_CONSUMER_THREADS, "1"));

        Properties consumerProps = new Properties();
        consumerProps.put("client.id", DEFAULT_CLIENT_ID);
        for (Enumeration<Object> e = props.keys(); e.hasMoreElements(); ) {
            String propKey = (String) e.nextElement();
            String propVal = props.get(propKey).toString();
            if (propKey.startsWith(CONFIG_PREFIX)) {
                propKey = propKey.substring(9);
                consumerProps.put(propKey, propVal);
                log.info(propKey + "=" + propVal);
            }
        }
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, MeasurementDeserializer.class.getName());

        if (consumerProps.size() <= 1) {
            log.info("ConsumerMetrics disabled");
            return;
        }

        consumer = new KafkaConsumer<>(consumerProps);

        addShutdownHook();

        try {
            consumer.subscribe(Arrays.asList(topic));

            new Task(new InfluxDbPublisher(props), consumer).run();

        } finally {
            terminated = true;
        }

        shutdown();

    }

    private void shutdown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                shutdown();
            }
        }));
    }


    public boolean isTerminated() {
        return terminated;
    }

    public static class Task implements Runnable {

        final private MeasurementFormatter formatter;
        final private MeasurementPublisher publisher;
        final private KafkaConsumer<String, List<MeasurementV1>> consumer;

        public Task(MeasurementPublisher publisher, KafkaConsumer<String, List<MeasurementV1>> consumer) {
            this.consumer = consumer;
            this.formatter = new MeasurementFormatter();
            this.publisher = publisher;
        }

        public void run() {

            try {
                while (true) {
                    Iterator<ConsumerRecord<String, List<MeasurementV1>>> it = consumer.poll(250).iterator();
                    while (it.hasNext()) {
                        try {
                            ConsumerRecord<String, List<MeasurementV1>> m = it.next();
                            if (m.value() != null) {
                                for (MeasurementV1 measurement : m.value()) {
                                    try {
                                        publisher.publish(measurement);
                                    } catch (RuntimeException e) {

                                        log.error("Unable to publish measurement " + formatter.toString(measurement)
                                                        + "tag count=" + measurement.getFields().size()
                                                        + ", field count=" + measurement.getFields().size()
                                                , e);

                                    }
                                }
                            }
                        } catch (Throwable e) {
                            e.printStackTrace();
                            return;
                        }
                    }
                }
            } finally {
                System.out.println("Finished metrics consumer task");
                publisher.close();
            }
        }
    }

}
