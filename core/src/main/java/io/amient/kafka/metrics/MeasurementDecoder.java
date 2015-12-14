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

import kafka.serializer.Decoder;
import kafka.utils.VerifiableProperties;
import org.apache.kafka.common.errors.SerializationException;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class MeasurementDecoder implements Decoder<List<MeasurementV1>> {

    private InternalAvroSerde internalAvro = new InternalAvroSerde();
    private AutoJsonDeserializer autoJsonDeserializer = new AutoJsonDeserializer();

    public MeasurementDecoder(VerifiableProperties props) {
        this(props.props());
    }

    public MeasurementDecoder(Properties props) {
        //at the point of implementing schema registry serde this will take schema.registry.url etc.
    }

    @Override
    public List<MeasurementV1> fromBytes(byte[] bytes) {
        switch(bytes[0]) {
            case 0x0: throw new SerializationException("Schema Registry doesn't support maps and arrays yet.");
            case 0x1: return Arrays.asList(internalAvro.fromBytes(bytes));
            case '{': return autoJsonDeserializer.fromBytes(bytes);
            default: throw new SerializationException("Serialization MAGIC_BYTE not recognized: " + bytes[0]);
        }
    }

}

