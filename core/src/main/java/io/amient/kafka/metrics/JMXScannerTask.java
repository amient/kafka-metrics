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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class JMXScannerTask implements Runnable {

    static private final Logger log = LoggerFactory.getLogger(JMXScannerTask.class);

    private final JMXConnector jmxConnector;
    private final MBeanServerConnection conn;
    private final Map<String, String> tags;
    private final MeasurementPublisher publisher;
    private final MeasurementFormatter formatter;
    private final ObjectName pattern;

    public static class JMXScannerConfig {

        private final Map<String, String> tags = new LinkedHashMap<String, String>();
        private String address;
        private String queryScope = "*:*";
        private long queryIntervalSeconds = 10;

        public void setTag(String propKey, String propVal) {
            this.tags.put(propKey, propVal);
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public void setQueryScope(String query) {
            this.queryScope = query;
        }

        public String getAddress() {
            return address;
        }

        public Map<String, String> getTags() {
            return tags;
        }

        public String getQueryScope() {
            return queryScope;
        }

        public void setQueryInterval(long intervalSeconds) {
            this.queryIntervalSeconds = intervalSeconds;
        }

        public long getQueryIntervalSeconds() {
            return queryIntervalSeconds;
        }

    }

    public JMXScannerTask(JMXScannerConfig config, MeasurementPublisher publisher) throws IOException, MalformedObjectNameException {
        this.pattern = new ObjectName(config.getQueryScope());
        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + config.getAddress() + "/jmxrmi");
        this.jmxConnector = JMXConnectorFactory.connect(url);
        this.conn = jmxConnector.getMBeanServerConnection();
        this.tags = config.getTags();
        this.publisher = publisher;
        this.formatter = new MeasurementFormatter();
        log.info("url=" + url + ", scope=" + config.queryScope);
    }

    @Override
    public void run() {
        try {
            final long timestamp = System.currentTimeMillis();
            Set<ObjectInstance> beans = conn.queryMBeans(pattern, null);
            for (ObjectInstance i : beans) {
                if (log.isDebugEnabled()) {
                    log.debug(i.getObjectName().toString());
                }
                MeasurementV1[] measurements = extractMeasurements(i, timestamp);
                for (MeasurementV1 measurement : measurements) {
                    if (publisher != null && measurement.getFields().size() > 0) {
                        publisher.publish(measurement);
                    }
                }

            }
        } catch (Exception e) {
            log.error("could not retrieve mbeans", e);
        }
    }

    private MeasurementV1[] extractMeasurements(ObjectInstance i, Long timestamp)
            throws IntrospectionException, ReflectionException, InstanceNotFoundException, IOException, AttributeNotFoundException, MBeanException {
        ObjectName name = i.getObjectName();

        if (name.getKeyProperty("name") == null) {
            return extractAttributesAsMeasurements(i, timestamp);
        }

        MeasurementV1 measurement = new MeasurementV1();
        measurement.setTimestamp(timestamp);
        measurement.setName(name.getKeyProperty("name"));
        measurement.setTags(new LinkedHashMap<String, String>(tags));
        measurement.getTags().put("group", name.getDomain());
        for (Map.Entry<String, String> k : name.getKeyPropertyList().entrySet()) {
            if (!k.getKey().equals("name")) {
                measurement.getTags().put(k.getKey(), k.getValue());
            }
        }

        HashMap<String, Double> fields = new HashMap<String, Double>();
        MBeanInfo info = conn.getMBeanInfo(name);
        for (MBeanAttributeInfo attr : info.getAttributes()) {
            try {
                Object anyVal = conn.getAttribute(name, attr.getName());
                try {
                    Double value = formatter.anyValueToDouble(anyVal);
                    if (value != null)
                        fields.put(attr.getName(), value);
                } catch (RuntimeMBeanException e) {
                    log.warn("could not cast value " + anyVal + " of attribute " + attr + " of " + name +" into double value ", e);
                }
            } catch (RuntimeMBeanException e) {
                String msg = "failed to get attribute name=" + attr.getName() + " type=" + attr.getType() + " of " + name;
                if (log.isDebugEnabled()) {
                    log.debug(msg, e.getCause());
                } else {
                    log.warn(msg + " due to " + e.getCause());
                }
            }
        }

        measurement.setFields(new HashMap<String, Double>(fields));
        return new MeasurementV1[]{measurement};

    }

    private MeasurementV1[] extractAttributesAsMeasurements(ObjectInstance i, Long timestamp)
            throws IntrospectionException, ReflectionException, InstanceNotFoundException, IOException, AttributeNotFoundException, MBeanException {
        ObjectName name = i.getObjectName();
        MBeanInfo info = conn.getMBeanInfo(name);
        MBeanAttributeInfo[] attributes = info.getAttributes();
        MeasurementV1[] result = new MeasurementV1[attributes.length];
        int k = 0;
        for (MBeanAttributeInfo attr : info.getAttributes()) {
            MeasurementV1 measurement = new MeasurementV1();
            measurement.setTimestamp(timestamp);
            measurement.setName(attr.getName());
            measurement.setTags(new LinkedHashMap<String, String>(tags));
            measurement.getTags().put("group", name.getDomain());
            for (Map.Entry<String, String> tag: name.getKeyPropertyList().entrySet()) {
                measurement.getTags().put(tag.getKey(), tag.getValue());
            }

            Double value = formatter.anyValueToDouble(conn.getAttribute(name, attr.getName()));
            HashMap<String, Double> fields = new HashMap<String, Double>();
            if (value != null)
                fields.put("Value", value);
            measurement.setFields(new HashMap<String, Double>(fields));
            result[k++] = measurement;
        }

        return result;
    }
}