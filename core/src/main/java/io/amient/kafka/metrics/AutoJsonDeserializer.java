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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                Map<String, String> commonTags = new HashMap<String, String>();
                Iterator<Map.Entry<String, JsonNode>> tagFields = header.getFields();
                while (tagFields.hasNext()) {
                    Map.Entry<String, JsonNode> tagField = tagFields.next();
                    String tagKey = tagField.getKey();
                    if (tagKey.equals("time")) continue;
                    if (tagKey.equals("reset-time")) continue;
                    if (tagKey.equals("version")) continue;
                    commonTags.put(tagField.getKey(), tagField.getValue().getTextValue());
                }

                Iterator<Map.Entry<String, JsonNode>> metricFields = node.get("metrics").getFields();
                while (metricFields.hasNext()) {
                    Map.Entry<String, JsonNode> metricField = metricFields.next();
                    String name = metricField.getKey();

                    //group by identical tags
                    Map<Map<String, String>, Map<String, Double>> points = new HashMap<Map<String, String>, Map<String, Double>>();
                    Iterator<Map.Entry<String, JsonNode>> fieldValues = metricField.getValue().getFields();
                    while (fieldValues.hasNext()) {
                        Map.Entry<String, JsonNode> field = fieldValues.next();
                        Double value = formatter.anyValueToDouble(field.getValue().getNumberValue());
                        Map<String, String> tags = new HashMap<String, String>(commonTags);
                        if (value != null) {
                            String fieldName = tagSamzaMetricField(name, field.getKey(), tags);
                            if (fieldName != null) {
                                Map<String, Double> fields = points.get(tags);
                                if (fields == null) {
                                    fields = new HashMap<String, Double>();
                                    points.put(tags, fields);
                                }
                                fields.put(fieldName, value);
                            }
                        }
                    }

                    for(Map.Entry<Map<String, String>, Map<String, Double>> e: points.entrySet()) {
                        MeasurementV1 measurement = new MeasurementV1();
                        measurement.setName(name);
                        measurement.setTimestamp(timestamp);
                        measurement.setTags(e.getKey());
                        measurement.setFields(e.getValue());
                        result.add(measurement);
                    }
                }
            } else {
                log.warn("Unsupported Samza Metrics JSON Format Version " + version);
            }
            return result;
        }


        private Pattern fieldSystemStreamPartition = Pattern.compile("^(.+)-SystemStreamPartition \\[([^-]+), ([^-]+), ([0-9]+)\\]$");
        private Pattern systemTopicPartitionField = Pattern.compile("^([^-]+)-([^-]+)-([0-9]+)-(.+)$");
        private Pattern systemHostPortField = Pattern.compile("^([^-]+)-(.+-[0-9]+)-(.+)$");
        private Pattern taskPartitionField = Pattern.compile("^(.+)-partition\\s([0-9]+)-(.+)$");
        private Pattern systemField = Pattern.compile("^([^-]+)-(.+)$");

        private String tagSamzaMetricField(String name, String field, Map<String, String> tags) {
            if (name.startsWith("org.apache.samza.system.")) {
                Matcher m1 = fieldSystemStreamPartition.matcher(field);
                if (m1.find()) {
                    //e.g. 'buffered-message-count-SystemStreamPartition [kafkaevents, datasync, 5]'
                    tags.put("system", m1.group(2));
                    tags.put("topic", m1.group(3));
                    tags.put("partition", m1.group(4));
                    return m1.group(1);
                }
                Matcher m2 = systemTopicPartitionField.matcher(field);
                if (m2.find()) {
                    //e.g. 'kafkaevents-datasync-10-messages-behind-high-watermark=0.0'
                    tags.put("system", m2.group(1));
                    tags.put("topic", m2.group(2));
                    tags.put("partition", m2.group(3));
                    return m2.group(4);
                }
                Matcher m3 = systemHostPortField.matcher(field);
                if (m3.find()) {
                    //e.g. 'kafkayarn-bl-message-s01.visualdna.com-9092-bytes-read'
                    tags.put("system", m3.group(1));
                    tags.put("broker", m3.group(2));
                    return m3.group(3);
                }
                Matcher m4 = taskPartitionField.matcher(field);
                if (m4.find()) {
                    //e.g. 'taskname-partition 4-sends'
                    tags.put("task", m4.group(1));
                    tags.put("partition", m4.group(2));
                    return m4.group(3);
                }
                Matcher m5 = systemField.matcher(field);
                if (m5.find()) {
                    //e.g. 'buffered-message-count-SystemStreamPartition [kafkaevents, datasync, 5]'
                    tags.put("system", m5.group(1));
                    return m5.group(2);
                }
            }
            return field;
        }
    }
}
