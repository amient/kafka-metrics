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

    public static void main(String[] args) throws IOException {

        OptionParser parser = new OptionParser();

        parser.accepts("help", "Print usage help");
        OptionSpec<String> zookeeper = parser.accepts("zookeeper", "Address of the seed zookeeper server").withRequiredArg().required();
        OptionSpec<String> dashboard = parser.accepts("dashboard", "Grafana dashboard name to be used in all generated configs").withRequiredArg().required();
        OptionSpec<String> dashboardPath = parser.accepts("dashboard-path", "Grafana location, i.e. `./instance/.data/grafana/dashboards`").withRequiredArg();
        OptionSpec<String> topic = parser.accepts("topic", "Name of the metrics topic to consume measurements from").withRequiredArg();
        OptionSpec<String> influxdb = parser.accepts("influxdb", "InfluxDB connect URL (including user and password)").withRequiredArg();
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

                if (opts.has(dashboard) && opts.has(dashboardPath)) {
                    tool.generateDashboard(opts.valueOf(dashboard), brokers, "Local InfluxDB", opts.valueOf(dashboardPath))
                        .save();
                }

                if (opts.has(topic)) {
                    //producer/reporter settings
                    System.out.println("kafka.metrics.topic=" + opts.valueOf(topic));
                    System.out.println("kafka.metrics.polling.interval=5s");
                    System.out.println("kafka.metrics.bootstrap.servers=" + brokers.get(0).hostPort());
                    //consumer settings
                    System.out.println("consumer.topic=" + opts.valueOf(topic));
                    System.out.println("consumer.zookeeper.connect=" + opts.valueOf(zookeeper));
                    System.out.println("consumer.group.id=kafka-metrics-"+ opts.valueOf(dashboard));
                }

                if (!opts.has(influxdb) || !opts.has(topic)) {
                    tool.generateScannerConfig(brokers, opts.valueOf(dashboard)).list(System.out);
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
}