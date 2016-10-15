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

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Properties;

public class ProducerPublisher implements MeasurementPublisher {

    static private final Logger log = LoggerFactory.getLogger(ProducerPublisher.class);
    public static final String CONFIG_BOOTSTRAP_SERVERS = "kafka.metrics.bootstrap.servers";
    public static final String CONFIG_METRICS_TOPIC = "kafka.metrics.topic";

    private static final int DEFAULT_BACK_OFF_MS = 10000;
    static final String DEFAULT_CLIENT_ID = "kafka-metrics";

    private final KafkaProducer producer;
    private final String topic;

    volatile private long failureTimestamp = 0;

    public ProducerPublisher(Properties props) {
        this(
            props.getProperty(ProducerPublisher.CONFIG_BOOTSTRAP_SERVERS, "localhost:9092"),
            props.getProperty(ProducerPublisher.CONFIG_METRICS_TOPIC, "metrics")
        );
    }

    public ProducerPublisher(final String kafkaBootstrapServers, final String topic) {
        this.topic = topic;
        if (kafkaBootstrapServers == null) throw new IllegalArgumentException("Missing configuration: " + CONFIG_BOOTSTRAP_SERVERS);
        if (topic == null) throw new IllegalArgumentException("Missing configuration: " + CONFIG_METRICS_TOPIC);
        this.producer = new KafkaProducer<String, Object>(new Properties() {{
            put("bootstrap.servers", kafkaBootstrapServers);
            put("compression.type", "gzip");
            put("batch.size", "250");
            put("linger.ms", "1000");
            put("key.serializer", IntegerSerializer.class);
            put("value.serializer", io.amient.kafka.metrics.MeasurementSerializer.class);
            put("client.id", DEFAULT_CLIENT_ID);
        }});
        addProducerShutdownHook();
    }

    public void publish(MeasurementV1 m) {
        Long time = m.getTimestamp();
        if (failureTimestamp > 0) {
            if (failureTimestamp + DEFAULT_BACK_OFF_MS < time) return; else failureTimestamp = 0;
        }
        try {
            tryPublish(m);
        } catch (Throwable e) {
            log.warn("Failed to publish measurement to kafka topic, will retry...", e);
            failureTimestamp = time;
        }
    }

    public void tryPublish(MeasurementV1 m) {
        producer.send(new ProducerRecord<Integer, Object>(topic, m.getName().hashCode() + m.getTags().hashCode(), m));
    }

    @Override
    public void close() {
    	if (producer != null) {
            close();
      	}
    }
    
    private void addProducerShutdownHook(){
    	   Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

    	            @Override
    	            public void run() {
    	                  close();
    	            }
    	        }));
     }

    public static class IntegerSerializer implements Serializer<Integer> {
        @Override
        public void configure(Map<String, ?> configs, boolean isKey) {}

        @Override
        public byte[] serialize(String topic, Integer data) {
            if (data == null)
                return null;
            else {
                ByteBuffer result = ByteBuffer.allocate(4);
                result.putInt(data);
                return result.array();
            }
        }

        @Override
        public void close() {}
    }
}
