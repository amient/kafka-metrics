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

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MeasurementDeserializer implements Deserializer<List<MeasurementV1>> {

    private InternalAvroSerde internalAvro = new InternalAvroSerde();
    private AutoJsonDeserializer autoJsonDeserializer = new AutoJsonDeserializer();


    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {

    }

    @Override
    public List<MeasurementV1> deserialize(String topic, byte[] bytes) {
        switch(bytes[0]) {
            case 0x0: throw new SerializationException("Schema Registry doesn't support maps and arrays yet.");
            case 0x1: return Arrays.asList(internalAvro.fromBytes(bytes));
            case '{': return autoJsonDeserializer.fromBytes(bytes);
            default: throw new SerializationException("Serialization MAGIC_BYTE not recognized: " + bytes[0]);
        }
    }

    @Override
    public void close() {

    }
}

