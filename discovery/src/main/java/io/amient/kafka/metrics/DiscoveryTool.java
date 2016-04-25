package io.amient.kafka.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkMarshallingError;
import org.I0Itec.zkclient.serialize.ZkSerializer;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class DiscoveryTool {

    public static void printHelp() {
        System.out.println("Usage: ");
        System.out.println("./metrics-discovery/build/scripts/metrics-discovery <OPTIONS> | <MODULE>");
        System.out.println("--zookeeper      -z <HOST:PORT>      Address of the zookeeper for the kafka cluster");
        System.out.println("--dashboard      -d <NAME>           Grafana dashboard name to be used");
        System.out.println("--dashboard-path -p <PATH>           Grafana location, i.e. `./instance/.data/grafana/dashboards`");
        System.out.println("--influxdb       -i <URL>            InfluxDB connect URL (including user and password)");
        System.out.println("--topic          -t <TOPIC-NAME>     Name of the metrics topic to consume measurements from");
        //TODO --producer-bootstrap for truly non-intrusive agent deployment
        //TODO --influxdb-database
        System.out.println("                                (If not provided - jmx scanners will be configured instead)");
        System.out.println("--help           -h                  Print help");
        System.exit(1);
    }

    public static void main(String[] args) {
        String zookeeper = null;
        String influxdb = null;
        String topic = null;
        String dashboard = null;
        String dashboardPath = null;
        Iterator<String> it = Arrays.asList(args).iterator();
        while (it.hasNext()) {
            switch (it.next()) {
                case "--help":
                case "-h":
                    printHelp();
                    break;
                case "--zookeeper":
                case "-z": zookeeper = it.next();
                    break;
                case "--topic":
                case "-t": topic = it.next();
                    break;
                case "--dashboard":
                case "-d": dashboard = it.next();
                    break;
                case "--dashboard-path":
                case "-p": dashboardPath = it.next();
                    break;
                case "--influxdb":
                case "-i": influxdb = it.next();
                    break;
            }
        }
        try {
            DiscoveryTool tool = new DiscoveryTool(
                    zookeeper,
                    topic,
                    influxdb,
                    dashboard,
                    "Local InfluxDB", //TODO configure this
                    dashboardPath);
            try {
                tool.discoverAndOutputConfiguration();
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(3);
            } finally {
                tool.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }

    }

    private final BrokerInfoClient zkClient;
    private final Dashboard dashboard;
    private final String topic;
    private final URL influxdb;
    private final Properties scanners;

    public DiscoveryTool(String zkConnect, String topic, String influxdb,
                         String dashboard, String dataSource, String path) throws IOException {
        this.zkClient = (zkConnect != null) ? new BrokerInfoClient(zkConnect) : null;
        List<Broker> brokers = zkClient != null ? zkClient.getBrokers() : null;
        this.dashboard = path != null && dashboard != null && brokers != null
                ? generateDashboard(dashboard, brokers, dataSource, path) : null;
        this.topic = zkClient != null && topic != null ? topic : null;
        this.scanners = dashboard != null && brokers != null ? generateScannerConfig(brokers, dashboard) : null;
        this.influxdb = influxdb != null ? new URL(influxdb) : null;

    }


    private void discoverAndOutputConfiguration() throws IOException {

        if (dashboard != null) {
            dashboard.save();
        }

        if (topic != null) {
            System.out.println("kafka.metrics.topic=" + topic);
            System.out.println("kafka.metrics.polling.interval=5s");
            System.out.println("kafka.metrics.bootstrap.servers=" + zkClient.getFirstBroker().hostPort());
        }

        if (scanners != null && (influxdb == null || topic == null)) {
            scanners.list(System.out);
        }

        if (influxdb != null) {
            System.out.println("influxdb.database=metrics"); //TODO configure this
            System.out.println("influxdb.url=" + influxdb.toString());
            if (influxdb.getUserInfo() != null) {
                System.out.println("influxdb.username=" + influxdb.getUserInfo().split(":")[0]);
                if (influxdb.getUserInfo().contains(":")) {
                    System.out.println("influxdb.password=" + influxdb.getUserInfo().split(":")[1]);
                }
            }
        }

        System.out.flush();

    }

    public Dashboard generateDashboard(String name, List<Broker> brokers, String dataSource, String path) {
        Dashboard dash = new Dashboard(name, dataSource, path + "/" + name + ".json");

        ArrayNode clusterRow = dash.newRow(String.format("CLUSTER METRICS FOR %d broker(s)", brokers.size()), 200);
        ObjectNode graph1 = dash.newGraph(clusterRow, "Under-Replicated Partitions", 6, false);
        dash.newTarget(graph1, "$tag_service", "SELECT mean(\"Value\") AS \"Value\" FROM \"UnderReplicatedPartitions\" " +
                "WHERE \"group\" = 'kafka.server' " +
                "AND \"name\" = '" + name + "' " +
                "AND $timeFilter GROUP BY time($interval), \"service\"");

        for (Broker broker : brokers) {
            ArrayNode brokerRow = dash.newRow(String.format("Kafka Broker ID %s @ %s", broker.id, broker.hostPort()), 250);
            dash.newGraph(brokerRow, "Memory", 4, true);
            dash.newGraph(brokerRow, "LogFlush", 4, true);
            dash.newGraph(brokerRow, "Throughput", 4, true);
        }
        return dash;
    }

    public Properties generateScannerConfig(List<Broker> brokers, String name) throws IOException {
        Properties scannerProps = new Properties();
        for (Broker broker : brokers) {
            //jmx scanner for broker
            Integer section = Integer.parseInt(broker.id) + 1;
            scannerProps.put(String.format("jmx.%d.address", section), String.format("%s:%d", broker.host, broker.jmxPort));
            scannerProps.put(String.format("jmx.%d.query.scope", section), "kafka.*:*");
            scannerProps.put(String.format("jmx.%d.query.interval.s", section), "10");
            scannerProps.put(String.format("jmx.%d.tag.host", section), broker.host);
            scannerProps.put(String.format("jmx.%d.tag.service", section), String.format("broker-%s", broker.id));
            scannerProps.put(String.format("jmx.%d.tag.name", section), name);
        }
        return scannerProps;
    }

    private void close() {
        zkClient.close();
    }

    private class BrokerInfoClient extends ZkClient {
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
}