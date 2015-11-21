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

import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

public class MeasurementSerializer implements Serializer<Measurement> {

    private final EncoderFactory encoderFactory = EncoderFactory.get();

    public void configure(Map<String, ?> map, boolean b) {

    }

    public byte[] serialize(String s, Measurement measurement) {

        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            BinaryEncoder encoder = encoderFactory.directBinaryEncoder(byteStream, null);
            DatumWriter<Object> writer = new SpecificDatumWriter<Object>(measurement.getSchema());
            writer.write(measurement, encoder);
            encoder.flush();
            DatumWriter<Measurement> userDatumWriter = new SpecificDatumWriter<Measurement>(Measurement.class);
            byte[] result = byteStream.toByteArray();
            byteStream.close();
            return result;
        } catch (IOException e) {
            throw new SerializationException("Error serializing Measurement object", e);
        }
    }

    public void close() {

    }
}
