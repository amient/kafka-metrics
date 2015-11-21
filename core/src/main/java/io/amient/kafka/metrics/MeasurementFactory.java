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

import com.yammer.metrics.core.MetricName;

import java.util.HashMap;


public class MeasurementFactory {

    public static Measurement createMeasurement(String host, String service, MetricName name, Long timestamp) {
        Measurement measurement = new Measurement();
        measurement.setVersion(1);
        measurement.setTimestamp(timestamp);
        measurement.setHost(host);
        measurement.setService(service);
        measurement.setName(name.getName());
        measurement.setTags(new HashMap<CharSequence, CharSequence>());
        measurement.setFields(new HashMap<CharSequence, Double>());
        if (name.getGroup() != null && !name.getGroup().isEmpty()) measurement.getTags().put("group", name.getGroup());
        if (name.getType() != null && !name.getType().isEmpty()) measurement.getTags().put("type", name.getType());
        if (name.getScope() != null && !name.getScope().isEmpty()) measurement.getTags().put("scope", name.getScope());
        return measurement;
    }

}
