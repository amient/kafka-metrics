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

import com.yammer.metrics.core.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class KafkaMetricsJMXMain {

    static private final Logger log = LoggerFactory.getLogger(KafkaMetricsJMXMain.class);

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(InfluxDbPublisher.COFNIG_INFLUXDB_DATABASE, "metrics");
        props.put(InfluxDbPublisher.COFNIG_INFLUXDB_URL, "http://localhost:8086");
        props.put(InfluxDbPublisher.COFNIG_INFLUXDB_USERNAME, "root");
        props.put(InfluxDbPublisher.COFNIG_INFLUXDB_PASSWORD, "root");

        props.put("jmx.1.address", "localhost:19092");
        props.put("jmx.1.queryScope.scope", "kafka");
        props.put("jmx.1.queryScope.interval.s", "10");
        props.put("jmx.1.tag.cluster", "a");
        props.put("jmx.1.tag.host", "host-001");
        props.put("jmx.1.tag.service", "broker-0");

        props.put("jmx.2.address", "localhost:19091");
        props.put("jmx.2.queryScope.scope", "kafka");
        props.put("jmx.2.queryScope.interval.s", "10");
        props.put("jmx.2.tag.cluster", "a");
        props.put("jmx.2.tag.host", "host-002");
        props.put("jmx.2.tag.service", "broker-1");

        try {
            new KafkaMetricsJMXMain(props);
        } catch (Throwable e) {
            log.error("Failed to launch KafkaMetrics JMX Scanner", e);
        }
    }

    final private MeasurementPublisher publisher;

    public KafkaMetricsJMXMain(Properties config) throws IOException, MalformedObjectNameException, InterruptedException {
        Map<String, JMXScannerConfig> jmxConfigs = new HashMap<String, JMXScannerConfig>();
        for (Enumeration<Object> e = config.keys(); e.hasMoreElements(); ) {
            String propKey = (String) e.nextElement();
            String propVal = config.get(propKey).toString();
            if (propKey.startsWith("jmx.")) {
                propKey = propKey.substring(4);
                int idLen = propKey.indexOf('.') + 1;
                String id = propKey.substring(0, idLen - 1);
                if (!jmxConfigs.containsKey(id)) jmxConfigs.put(id, new JMXScannerConfig());
                JMXScannerConfig jmxConfig = jmxConfigs.get(id);
                propKey = propKey.substring(idLen);
                if (propKey.startsWith("tag.")) {
                    propKey = propKey.substring(4);
                    jmxConfig.setTag(propKey, propVal);
                } else if (propKey.equals("address")) {
                    jmxConfig.setAddress(propVal);
                } else if (propKey.equals("queryScope.scope")) {
                    jmxConfig.setQueryScope(propVal);
                } else if (propKey.equals("queryScope.interval.s")) {
                    jmxConfig.setQueryInterval(Long.parseLong(propVal));
                }
            }
        }

        this.publisher = new InfluxDbPublisher(config);
        try {
            ScheduledExecutorService e = Executors.newScheduledThreadPool(jmxConfigs.size());
            for (JMXScannerConfig jmxConfig : jmxConfigs.values()) {
                JMXScanner jmxScanner = new JMXScanner(jmxConfig, publisher);
                e.scheduleAtFixedRate(jmxScanner, 0, jmxConfig.getQueryIntervalSeconds(), TimeUnit.SECONDS);
            }
            while (!e.isTerminated()) {
                e.awaitTermination(10, TimeUnit.SECONDS);
            }
        } finally {
            publisher.close();
        }
    }

    public static class JMXScanner implements Runnable {
        private final JMXConnector jmxConnector;
        private final MBeanServerConnection conn;
        private final Map<String, String> tags;
        private final Clock clock;
        private final MeasurementPublisher publisher;
        private final MeasurementFormatter formatter;
        private final ObjectName all;

        public JMXScanner(JMXScannerConfig config, MeasurementPublisher publisher) throws IOException, MalformedObjectNameException {
            this.all = new ObjectName(config.getQueryScope() + ".*:*");
            JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + config.getAddress() + "/jmxrmi");
            this.jmxConnector = JMXConnectorFactory.connect(url);
            this.conn = jmxConnector.getMBeanServerConnection();
            this.tags = config.getTags();
            this.clock = Clock.defaultClock();
            this.publisher = publisher;
            this.formatter = new MeasurementFormatter();
        }

        @Override
        public void run() {
            try {
                Set<ObjectInstance> beans = conn.queryMBeans(all, null);
                for (ObjectInstance i : beans) {
                    MeasurementV1 measurement = createMeasurement(i);
                    //publisher.publish(measurement);
                    formatter.writeTo(measurement, System.out);
                }
            } catch (Exception e) {
                log.error("could not retrieve mbeans", e);
            }
        }

        private MeasurementV1 createMeasurement(ObjectInstance i)
                throws IntrospectionException, ReflectionException, InstanceNotFoundException, IOException, AttributeNotFoundException, MBeanException {
            ObjectName name = i.getObjectName();
            MeasurementV1 measurement = new MeasurementV1();
            measurement.setTimestamp(clock.time());
            measurement.setName(name.getKeyProperty("name"));
            measurement.setTags(new LinkedHashMap<CharSequence, CharSequence>(tags));
            measurement.getTags().put("group", name.getDomain());
            for (Map.Entry<String, String> k : name.getKeyPropertyList().entrySet()) {
                if (!k.getKey().equals("name")) {
                    measurement.getTags().put(k.getKey(), k.getValue());
                }
            }

            HashMap<String, Double> fields = new HashMap<String, Double>();
            MBeanInfo info = conn.getMBeanInfo(name);
            for (MBeanAttributeInfo attr : info.getAttributes()) {
                Double value = formatter.anyValueToDouble(conn.getAttribute(name, attr.getName()));
                if (value != null)
                    fields.put(attr.getName(), value);
            }

            measurement.setFields(new HashMap<CharSequence, Double>(fields));
            return measurement;

        }
    }

    private class JMXScannerConfig {

        private final Map<String, String> tags = new LinkedHashMap<String, String>();
        private String address;
        private String queryScope;
        private long queryIntervalSeconds;

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
}
