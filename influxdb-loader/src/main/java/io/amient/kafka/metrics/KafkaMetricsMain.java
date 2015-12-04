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

import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.consumer.KafkaStream;
import kafka.message.MessageAndMetadata;
import kafka.serializer.StringDecoder;
import kafka.utils.VerifiableProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KafkaMetricsMain {

    static private final Logger log = LoggerFactory.getLogger(KafkaMetricsMain.class);

    public static void main(String[] args) throws InterruptedException {
        String topic = "_metrics";
        Integer numThreads = 1;
        Properties props = new Properties();
        props.put("zookeeper.connect", "localhost:2181");
        props.put("group.id", "kafka-metric-collector");
        props.put("zookeeper.session.timeout.ms", "2000");
        props.put("zookeeper.sync.time.ms", "200");
        props.put("auto.commit.interval.ms", "10000");
        props.put("auto.offset.reset", "smallest");
        props.put(InfluxDbPublisher.COFNIG_INFLUXDB_DATABASE, "metrics");
        props.put(InfluxDbPublisher.COFNIG_INFLUXDB_URL, "http://localhost:8086");
        props.put(InfluxDbPublisher.COFNIG_INFLUXDB_USERNAME, "root");
        props.put(InfluxDbPublisher.COFNIG_INFLUXDB_PASSWORD, "root");

        VerifiableProperties config = new VerifiableProperties(props);
        ConsumerConnector consumer = kafka.consumer.Consumer.createJavaConsumerConnector(
                new ConsumerConfig(config.props()));

        Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
        topicCountMap.put(topic, new Integer(numThreads));
        Map<String, List<KafkaStream<String, MeasurementV1>>> consumerMap
                = consumer.createMessageStreams(topicCountMap, new StringDecoder(config), new MeasurementDecoder(config));

        List<KafkaStream<String, MeasurementV1>> streams = consumerMap.get(topic);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (final KafkaStream<String, MeasurementV1> stream : streams) {
            executor.submit(new Task(props, stream));
        }
        executor.shutdown();

        while (!executor.isTerminated()) {
            Thread.sleep(5000);
        }

    }

    public static class Task implements Runnable {
        final private KafkaStream<String, MeasurementV1> stream;
        final private MeasurementFormatter formatter;
        final private MeasurementPublisher publisher;

        public Task(Properties props, KafkaStream<String, MeasurementV1> stream) {
            this.stream = stream;
            this.formatter = new MeasurementFormatter();
            this.publisher = new InfluxDbPublisher(props);
        }

        public void run() {
            ConsumerIterator<String, MeasurementV1> it = stream.iterator();
            try {
                while (it.hasNext()) {
                    try {
                        MessageAndMetadata<String, MeasurementV1> m = it.next();
                        publisher.publish(m.message());
                        //formatter.writeTo(m.message(), System.out);
                    } catch (RuntimeException e) {
                        log.error("Unable to publish measurement", e);
                    } catch (Throwable e) {
                        e.printStackTrace();
                        return;
                    }
                }
            } finally {
                System.out.println("Finished metrics consumer task");
                publisher.close();
            }
        }
    }

}
