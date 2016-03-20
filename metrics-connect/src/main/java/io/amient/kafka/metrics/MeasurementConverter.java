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

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.storage.Converter;

import java.util.Map;

public class MeasurementConverter implements Converter {

    public Schema schema = null;

    private InternalAvroSerde internalAvro = null;

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        internalAvro = new InternalAvroSerde();
        this.schema = SchemaBuilder.struct().name("Measurement")
                .field("timestamp", Schema.INT64_SCHEMA)
                .field("name", Schema.STRING_SCHEMA)
                .field("tags", SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.STRING_SCHEMA).schema())
                .field("fields", SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.FLOAT64_SCHEMA).schema())
                .build();
    }

    @Override
    public SchemaAndValue toConnectData(String topic, byte[] value) {
        if (value == null) return null;
        MeasurementV1 measurement = internalAvro.fromBytes(value);
        Struct result = new Struct(schema);
        result.put("timestamp", measurement.getTimestamp());
        result.put("name", measurement.getName());
        result.put("tags", measurement.getTags());
        result.put("fields", measurement.getFields());
        return new SchemaAndValue(schema, result);
    }

    @Override
    public byte[] fromConnectData(String topic, Schema schema, Object logicalValue) {
        if (logicalValue == null) return null;
        return internalAvro.toBytes(fromConnectData(schema, logicalValue));
    }

    public MeasurementV1 fromConnectData(Schema schema, Object logicalValue) {
        Struct struct = (Struct) logicalValue;
        MeasurementV1.Builder builder = MeasurementV1.newBuilder();
        builder.setTimestamp((long) struct.get("timestamp"));
        builder.setName((String) struct.get("name"));
        builder.setTags((Map<String, String>) struct.get("tags"));
        builder.setFields((Map<String, Double>) struct.get("fields"));
        return builder.build();
    }

}
