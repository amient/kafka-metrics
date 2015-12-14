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

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.RuntimeJsonMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class AutoJsonDeserializer {

    final private ObjectMapper mapper = new ObjectMapper();
    final private SamzaJsonMetricsDecoder samzaDecoder = new SamzaJsonMetricsDecoder();

    public List<MeasurementV1> fromBytes(byte[] bytes) {
        try {
            JsonNode node = mapper.readTree(bytes);
            if (node.has("header") && node.has("metrics") && node.get("header").has("samza-version")) {
                return samzaDecoder.fromJsonTree(node);
            } else {
                throw new RuntimeJsonMappingException("Unrecoginzed JSON metric format: " + node.asText());
            }
        } catch (IOException e) {
            throw new RuntimeException("Error deserializing json object", e);
        }
    }

    private static class SamzaJsonMetricsDecoder {

        final private MeasurementFormatter formatter = new MeasurementFormatter();
        private Logger log = LoggerFactory.getLogger(SamzaJsonMetricsDecoder.class);

        public List<MeasurementV1> fromJsonTree(JsonNode node) {
            List<MeasurementV1> result = new LinkedList<MeasurementV1>();
            JsonNode header = node.get("header");
            String version = header.get("version").getTextValue();
            if (version.equals("0.0.1")) {
                Long timestamp = header.get("time").getLongValue();
                Map<String, String> tags = new HashMap<String, String>();
                Iterator<Map.Entry<String, JsonNode>> tagFields = header.getFields();
                while (tagFields.hasNext()) {
                    Map.Entry<String, JsonNode> tagField = tagFields.next();
                    String tagKey = tagField.getKey();
                    if (tagKey.equals("time")) continue;
                    if (tagKey.equals("reset-time")) continue;
                    if (tagKey.equals("version")) continue;
                    tags.put(tagField.getKey(), tagField.getValue().getTextValue());
                }
                Iterator<Map.Entry<String, JsonNode>> metricFields = node.get("metrics").getFields();
                while (metricFields.hasNext()) {
                    Map.Entry<String, JsonNode> metricField = metricFields.next();
                    MeasurementV1 measurement = new MeasurementV1();
                    measurement.setName(metricField.getKey());
                    measurement.setTimestamp(timestamp);
                    measurement.setTags(tags);
                    Map<String, Double> fields = new HashMap<String, Double>();
                    Iterator<Map.Entry<String, JsonNode>> fieldValues = metricField.getValue().getFields();
                    while (fieldValues.hasNext()) {
                        Map.Entry<String, JsonNode> field = fieldValues.next();
                        Double value = formatter.anyValueToDouble(field.getValue().getNumberValue());
                        if (value != null) {
                            fields.put(field.getKey(), value);
                        }
                    }
                    if (fields.size() > 0) {
                        measurement.setFields(fields);
                        result.add(measurement);
                    }
                }
            } else {
                log.warn("Unsupported Samza Metrics JSON Format Version " + version);
            }
            return result;
        }
    }

}
