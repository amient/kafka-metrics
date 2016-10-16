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

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConsumerMetrics {

    static private final Logger log = LoggerFactory.getLogger(ConsumerMetrics.class);

    static private final String CONFIG_PREFIX = "consumer.";
    static final String COFNIG_CONSUMER_TOPIC = CONFIG_PREFIX + "topic";
    static final String COFNIG_CONSUMER_THREADS = CONFIG_PREFIX + "numThreads";

    static final String DEFAULT_CLIENT_ID = "kafka-metrics";

    private final ExecutorService executor;
    private ConsumerConnector consumer = null;

    public ConsumerMetrics(Properties props) {
        String topic = props.getProperty(COFNIG_CONSUMER_TOPIC, "metrics");
        Integer numThreads = Integer.parseInt(props.getProperty(COFNIG_CONSUMER_THREADS, "1"));
        executor = Executors.newFixedThreadPool(numThreads);

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

        if (consumerProps.size() <= 1) {
            log.info("ConsumerMetrics disabled");
            return;
        }

        VerifiableProperties config = new VerifiableProperties(consumerProps);
        consumer = kafka.consumer.Consumer.createJavaConsumerConnector(
                new ConsumerConfig(config.props()));

        addShutdownHook();
        
        Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
        topicCountMap.put(topic, new Integer(numThreads));
        Map<String, List<KafkaStream<String, List<MeasurementV1>>>> consumerMap
                = consumer.createMessageStreams(topicCountMap, new StringDecoder(config), new MeasurementDecoder(config));

        List<KafkaStream<String, List<MeasurementV1>>> streams = consumerMap.get(topic);

        for (final KafkaStream<String, List<MeasurementV1>> stream : streams) {
            executor.submit(new Task(new InfluxDbPublisher(props), stream));
        }
        
        shutdown();

    }

	private void shutdown() {
		if (executor != null) {
			executor.shutdown();
		}
		if (consumer != null) {
            consumer.shutdown();
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
        return executor.isTerminated();
    }

    public static class Task implements Runnable {
        final private KafkaStream<String, List<MeasurementV1>> stream;
        final private MeasurementFormatter formatter;
        final private MeasurementPublisher publisher;

        public Task(MeasurementPublisher publisher, KafkaStream<String, List<MeasurementV1>> stream) {
            this.stream = stream;
            this.formatter = new MeasurementFormatter();
            this.publisher = publisher;
        }

        public void run() {
            ConsumerIterator<String, List<MeasurementV1>> it = stream.iterator();
            try {
                while (it.hasNext()) {
                    try {
                        MessageAndMetadata<String, List<MeasurementV1>> m = it.next();
                        if (m.message() != null) {
                            for (MeasurementV1 measurement : m.message()) {
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
            } finally {
                System.out.println("Finished metrics consumer task");
                publisher.close();
            }
        }
    }

}
