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

import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;

import java.util.Collection;
import java.util.Map;
import java.util.Properties;

public class InfluxDbSinkTask extends SinkTask {

    private InfluxDbPublisher publisher = null;
    private MeasurementConverter converter = null;

    @Override
    public String version() {
        return new InfluxDbSinkConnector().version();
    }

    @Override
    public void start(Map<String, String> props) {
        Properties publisherConfig = new Properties();
        publisherConfig.putAll(props);
        publisher = new InfluxDbPublisher(publisherConfig);
        converter = new MeasurementConverter();
    }

    @Override
    public void put(Collection<SinkRecord> sinkRecords) {
        for (SinkRecord record : sinkRecords) {
            MeasurementV1 measurement = converter.fromConnectData(record.valueSchema(), record.value());
            publisher.publish(measurement);
        }
    }

    @Override
    public void flush(Map<TopicPartition, OffsetAndMetadata> offsets) {
        //nothing to flush
    }

    @Override
    public void stop() {
        if (publisher != null) publisher.close();
    }
}
