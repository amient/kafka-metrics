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

import kafka.common.MessageFormatter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.SerializationException;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

public class MeasurementFormatter implements MessageFormatter {

    private static final DateFormat date = new SimpleDateFormat("dd/MM/yyyy G 'at' HH:mm:ss z");

    private MeasurementDecoder decoder = null;

    @Override
    public void init(Properties props) {
        decoder = new MeasurementDecoder(props);
    }

    @Override
    public void writeTo(ConsumerRecord<byte[], byte[]> consumerRecord, PrintStream output) {
        try {
            for(MeasurementV1 measurement: decoder.fromBytes(consumerRecord.value())) {
                writeTo(measurement, output);
            }
        } catch (SerializationException e) {
            output.append(e.getMessage());
            output.append("\n\n");
        }
    }

    public void writeTo(MeasurementV1 measurement, PrintStream output) {
        output.append(measurement.getName());
        for (java.util.Map.Entry<String, String> tag : measurement.getTags().entrySet()) {
            output.append(",");
            output.append(tag.getKey());
            output.append("=");
            output.append(tag.getValue());
        }
        output.append(" [" + date.format(new Date(measurement.getTimestamp())) + "] ");
        output.append("\n");
        for (java.util.Map.Entry<String, Double> field : measurement.getFields().entrySet()) {
            output.append(field.getKey());
            output.append("=");
            output.append(field.getValue().toString());
            output.append("\t");
        }
        output.append("\n\n");
    }

    @Override
    public void close() {

    }

    public Double anyValueToDouble(Object anyValue) {
        if (anyValue instanceof Double) {
            Double value = ((Double) anyValue);
            if (!value.isNaN() && !value.isInfinite()) {
                return value;
            }
        } else if ((anyValue instanceof Float)) {
            Float value = ((Float) anyValue);
            if (!value.isNaN() && !value.isInfinite()) {
                return ((Float) anyValue).doubleValue();
            }
        } else if ((anyValue instanceof Long)
                || (anyValue instanceof Integer)
                || (anyValue instanceof Short)
                || (anyValue instanceof Byte)) {
            return Double.valueOf(anyValue.toString());
        }
        return null;

    }

    public String toString(MeasurementV1 measurement) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(os);
        writeTo(measurement, ps);
        return os.toString();
    }
}