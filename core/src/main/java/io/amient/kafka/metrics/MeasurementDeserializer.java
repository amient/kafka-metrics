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

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.kafka.common.errors.SerializationException;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MeasurementDeserializer {

    private enum SchemaVersions {
        V1(new SpecificDatumReader<MeasurementV1>(MeasurementV1.getClassSchema()));

        public final SpecificDatumReader<?> reader;

        SchemaVersions(SpecificDatumReader<?> reader) {
            this.reader = reader;
        }
    }

    private final DecoderFactory decoderFactory = DecoderFactory.get();

    public MeasurementV1 deserialize(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        Byte version = buffer.get();

        try {
            int length = buffer.limit() - 1;
            int start = buffer.position() + buffer.arrayOffset();
            DatumReader<?> reader = SchemaVersions.values()[version - 1].reader;
            Object object = reader.read(null, decoderFactory.binaryDecoder(buffer.array(), start, length, null));
            if (object instanceof MeasurementV1) {
                return (MeasurementV1) object;
            } else {
                throw new SerializationException("Unsupported object type " + object.getClass());
            }
        } catch (IOException e) {
            throw new SerializationException("Error deserializing Measurement message version " + version, e);
        }

    }

}
