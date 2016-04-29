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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkMarshallingError;
import org.I0Itec.zkclient.serialize.ZkSerializer;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.*;

public class DiscoveryTool {

//    private static int INTERVAL_S = 10;

    public static void main(String[] args) throws IOException {

        OptionParser parser = new OptionParser();

        parser.accepts("help", "Print usage help");
        OptionSpec<String> zookeeper = parser.accepts("zookeeper", "Address of the seed zookeeper server").withRequiredArg().required();
        OptionSpec<String> dashboard = parser.accepts("dashboard", "Grafana dashboard name to be used in all generated configs").withRequiredArg().required();
        OptionSpec<String> dashboardPath = parser.accepts("dashboard-path", "Grafana location, i.e. `./instance/.data/grafana/dashboards`").withRequiredArg();
        OptionSpec<String> topic = parser.accepts("topic", "Name of the metrics topic to consume measurements from").withRequiredArg();
        OptionSpec<String> influxdb = parser.accepts("influxdb", "InfluxDB connect URL (including user and password)").withRequiredArg();
        OptionSpec<String> interval = parser.accepts("interval", "JMX scanning interval in seconds").withRequiredArg().defaultsTo("10");
        //TODO --producer-bootstrap for truly non-intrusive agent deployment
        //TODO --influxdb-database

        if (args.length == 0 || args[0] == "-h" || args[0] == "--help") {
            parser.printHelpOn(System.err);
            System.exit(0);
        }

        OptionSet opts = parser.parse(args);

        try {

            DiscoveryTool tool = new DiscoveryTool();

            try {
                List<Broker> brokers = tool.discoverKafkaCluster(opts.valueOf(zookeeper));
                int interval_s = Integer.parseInt(opts.valueOf(interval));

                if (opts.has(dashboard) && opts.has(dashboardPath)) {
                    tool.generateDashboard(opts.valueOf(dashboard), brokers, "Kafka Metrics InfluxDB",
                       opts.valueOf(dashboardPath), interval_s)
                            .save();
                }

                if (opts.has(topic)) {
                    //producer/reporter settings
                    System.out.println("kafka.metrics.topic=" + opts.valueOf(topic));
                    System.out.println("kafka.metrics.polling.interval=" + interval_s + "s");
                    System.out.println("kafka.metrics.bootstrap.servers=" + brokers.get(0).hostPort());
                    //consumer settings
                    System.out.println("consumer.topic=" + opts.valueOf(topic));
                    System.out.println("consumer.zookeeper.connect=" + opts.valueOf(zookeeper));
                    System.out.println("consumer.group.id=kafka-metrics-"+ opts.valueOf(dashboard));
                }

                if (!opts.has(influxdb) || !opts.has(topic)) {
                    tool.generateScannerConfig(brokers, opts.valueOf(dashboard), interval_s).list(System.out);
                }

                if (opts.has(influxdb)) {
                    URL url = new URL(opts.valueOf(influxdb));
                    System.out.println("influxdb.database=metrics"); //TODO configure this
                    System.out.println("influxdb.url=" + url.toString());
                    if (url.getUserInfo() != null) {
                        System.out.println("influxdb.username=" + url.getUserInfo().split(":")[0]);
                        if (url.getUserInfo().contains(":")) {
                            System.out.println("influxdb.password=" + url.getUserInfo().split(":")[1]);
                        }
                    }
                }

                System.out.flush();
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(3);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }

    }

    public List<Broker> discoverKafkaCluster(String zkConnect) throws IOException {
        try (BrokerInfoClient client = new BrokerInfoClient(zkConnect)) {
            return client.getBrokers();
        }
    }

    public Properties generateScannerConfig(List<Broker> brokers, String name, int interval_s) throws IOException {
        Properties scannerProps = new Properties();
        for (Broker broker : brokers) {
            Integer section = Integer.parseInt(broker.id) + 1;
            scannerProps.put(String.format("jmx.%d.address", section), String.format("%s:%d", broker.host, broker.jmxPort));
            scannerProps.put(String.format("jmx.%d.query.scope", section), "kafka.*:*");
            scannerProps.put(String.format("jmx.%d.query.interval.s", section), String.valueOf(interval_s));
            scannerProps.put(String.format("jmx.%d.tag.host", section), broker.host);
            scannerProps.put(String.format("jmx.%d.tag.service", section), String.format("broker-%s", broker.id));
            scannerProps.put(String.format("jmx.%d.tag.name", section), name);
        }
        return scannerProps;
    }

    private class BrokerInfoClient extends ZkClient implements Closeable {
        private final String brokersZkPath = "/brokers/ids";

        public BrokerInfoClient(String serverstring) {
            super(serverstring, 30000, 30000, new ZkSerializer() {
                private final ObjectMapper mapper = new ObjectMapper();

                @Override
                public byte[] serialize(Object o) throws ZkMarshallingError {
                    throw new ZkMarshallingError("This is a read-only zkClient");
                }

                @Override
                public Object deserialize(byte[] bytes) throws ZkMarshallingError {
                    JsonNode json = null;
                    try {
                        return mapper.readTree(bytes);
                    } catch (IOException e) {
                        throw new ZkMarshallingError(e);
                    }
                }
            });
        }

        public List<Broker> getBrokers() throws IOException {
            List<Broker> result = new LinkedList<>();
            for (String brokerId : getChildren(brokersZkPath)) {
                result.add(getBroker(brokerId));
            }
            return result;
        }

        public Broker getFirstBroker() {
            return getBroker(getChildren(brokersZkPath).get(0));
        }

        public Broker getBroker(String brokerId) {
            JsonNode json = readData(brokersZkPath + "/" + brokerId);
            return new Broker(
                    brokerId,
                    json.get("host").asText(),
                    json.get("port").asInt(),
                    json.get("jmx_port").asInt()
            );
        }
    }

    public Dashboard generateDashboard(String name, List<Broker> brokers, String dataSource, String path, int interval_s) {
        Dashboard dash = new Dashboard(name, dataSource, path + "/" + name + ".json");

        ArrayNode clusterRow = dash.newRow(String.format("CLUSTER METRICS FOR %d broker(s)", brokers.size()), 172);

        dash.newStat(clusterRow, "Controllers", 1,
                "SELECT sum(\"Value\") FROM \"ActiveControllerCount\" " +
                        "WHERE \"group\" = 'kafka.controller' AND \"name\" = '" + name + "' AND $timeFilter " +
                        "GROUP BY time(" + interval_s + "s)")
            .put("valueFontSize", "150%");

        ObjectNode graph1 = dash.newGraph(clusterRow, "Under-Replicated Partitions", 2, false).put("bars", true);
        dash.newTarget(graph1, "$tag_service", "SELECT mean(\"Value\") FROM \"UnderReplicatedPartitions\" " +
                "WHERE \"group\" = 'kafka.server' AND \"name\" = '" + name + "' AND $timeFilter " +
                "GROUP BY time(" + interval_s + "s), \"service\"");

        dash.newTable(clusterRow, "Partition Count", 2, "avg", "$tag_service",
                "SELECT last(\"Value\") FROM \"PartitionCount\" " +
                "WHERE \"group\" = 'kafka.server' AND \"name\" = '" + name + "' AND $timeFilter " +
                "GROUP BY time(" + interval_s + "s), \"service\"")
            .put("transform", "timeseries_aggregations")
            .put("showHeader", false);

        //Total Maximum Log Flush Time
        ObjectNode graph5 = dash.newGraph(clusterRow, "Log Flush Time (98th maximum)", 2, false)
                .put("linewidth",1).put("points", false).put("fill",0);
        graph5.replace("y_formats", dash.newArray("ms", "short"));
        dash.get(graph5, "grid")
                .put("threshold1", 6).put("threshold1Color", "rgba(236, 118, 21, 0.21)")
                .put("threshold2", 12).put("threshold2Color", "rgba(234, 112, 112, 0.22)");
        dash.newTarget(graph5, "$tag_service", "SELECT max(\"98thPercentile\") as \"98thPercentile\" " +
                "FROM \"LogFlushRateAndTimeMs\" " +
                "WHERE \"group\" = 'kafka.log' AND \"name\" = '" + name + "' AND $timeFilter " +
                "GROUP BY time(1m), \"service\"");

        ObjectNode graph2 = dash.newGraph(clusterRow, "Input / Sec", 2, false).put("fill", 2).put("stack", true);
        graph2.replace("y_formats", dash.newArray("bytes", "short"));
        dash.get(graph2, "grid").put("leftMin", 0);
        dash.newTarget(graph2, "$tag_service", "SELECT mean(\"OneMinuteRate\") FROM \"BytesInPerSec\" " +
                "WHERE \"group\" = 'kafka.server' AND \"name\" = '" + name + "' AND $timeFilter " +
                "GROUP BY time(" + interval_s + "s), \"service\"");

        ObjectNode graph3 = dash.newGraph(clusterRow, "Output / Sec", 2, false).put("fill", 2).put("stack", true);
        graph3.replace("y_formats", dash.newArray("bytes", "short"));
        dash.get(graph3, "grid").put("leftMin", 0);
        dash.newTarget(graph3, "$tag_service", "SELECT mean(\"OneMinuteRate\") FROM \"BytesOutPerSec\" " +
                "WHERE \"group\" = 'kafka.server' AND \"name\" = '" + name + "' AND $timeFilter " +
                "GROUP BY time(" + interval_s + "s), \"service\"");

        dash.newStat(clusterRow, "Requests/Sec", 1,
                "SELECT mean(\"OneMinuteRate\") FROM \"RequestsPerSec\" " +
                        "WHERE \"group\" = 'kafka.network' AND \"name\" = '" + name + "' AND $timeFilter " +
                        "GROUP BY time(" + interval_s + "s)")
                .put("decimals", 1)
                .put("format", "short")
                .replace("sparkline", dash.newObject().put("show", true).put("full", false));

        for (Broker broker : brokers) {
            //TODO Memory Usage Graph
            ArrayNode brokerRow = dash.newRow(String.format("Kafka Broker ID %s @ %s", broker.id, broker.hostPort()), 250);
            ObjectNode graph6 = dash.newGraph(brokerRow, "Memory", 4, true);

            //Log Flush Time Graph
            ObjectNode graph7 = dash.newGraph(brokerRow, "Log Flush Time (mean)", 4, false)
                    .put("linewidth",1).put("points", true).put("pointradius", 1).put("fill", 0);
            graph7.replace("y_formats", dash.newArray("ms", "short"));
            dash.get(graph7, "grid")
                    .put("leftLogBase", 2)
                    .put("threshold1", 100).put("threshold1Color", "rgba(236, 118, 21, 0.21)")
                    .put("threshold2", 250).put("threshold2Color", "rgba(234, 112, 112, 0.22)");
            dash.newTarget(graph7, "$col", "SELECT mean(\"999thPercentile\") as \"999thPercentile\" " +
                    "FROM \"LogFlushRateAndTimeMs\" " +
                    "WHERE \"group\" = 'kafka.log' AND \"service\" = '" +String.format("broker-%s", broker.id)+"'" +
                    "AND \"name\" = '" + name + "' AND $timeFilter " +
                    "GROUP BY time(30s)");
            dash.newTarget(graph7, "$col", "SELECT mean(\"99thPercentile\") as \"99thPercentile\" " +
                    "FROM \"LogFlushRateAndTimeMs\" " +
                    "WHERE \"group\" = 'kafka.log' AND \"service\" = '" +String.format("broker-%s", broker.id)+"'" +
                    "AND \"name\" = '" + name + "' AND $timeFilter " +
                    "GROUP BY time(30s)");

            dash.newTarget(graph7, "$col", "SELECT mean(\"95thPercentile\") as \"95thPercentile\" " +
                    "FROM \"LogFlushRateAndTimeMs\" " +
                    "WHERE \"group\" = 'kafka.log' AND \"service\" = '" +String.format("broker-%s", broker.id)+"'" +
                    "AND \"name\" = '" + name + "' AND $timeFilter " +
                    "GROUP BY time(30s)");

            //TODO Throughput Graph
            dash.newGraph(brokerRow, "Throughput", 4, true);
        }

        //TODO for(String topic: topics) { ... } //maybe use a variable template for either '*' or '<TOPIC>'
        return dash;
    }
}