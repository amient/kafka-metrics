package io.amient.kafka.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkMarshallingError;
import org.I0Itec.zkclient.serialize.ZkSerializer;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

public class DiscoveryTool {
    private final String name;
    private final String path;
    private final BrokerInfoClient client;
    private final String dataSource;

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.out.println("Usage: ");
            System.out.println("./metrics-discovery/build/scripts/metrics-discovery <ZOOKEEPER> [<NAME>]");
            System.exit(1);
        }
        DiscoveryTool tool = new DiscoveryTool(
                args[0],
                args[1],
                "Local InfluxDB",
                "./instance/.data/grafana/dashboards");
        try {
            tool.discoverAndStartWithLocalInfluxDB();
        } finally {
            tool.close();
        }

    }

    private void discoverAndStartWithMetricsProducer() {
        discoverAndStart(new ProducerPublisher(new Properties() {{
            put("kafka.metrics.topic", "metrics");
            put("kafka.metrics.polling.interval", "5s");
            put("kafka.metrics.bootstrap.servers", client.getFirstBroker().hostPort());
        }}));
    }

    private void discoverAndStartWithLocalInfluxDB() {
        discoverAndStart(new InfluxDbPublisher(new Properties() {{
            put("influxdb.database", "metrics");
            put("influxdb.url", "http://localhost:8086");
            put("influxdb.username", "root");
            put("influxdb.password", "root");
        }}));
    }


    public DiscoveryTool(String zkConnect, String name, String dataSource, String path) {
        this.name = name;
        this.dataSource = dataSource;
        this.path = path;
        this.client = new BrokerInfoClient(zkConnect);

    }

    public void discoverAndStart(MeasurementPublisher publisher) {
        try {
            List<Broker> brokers = client.getBrokers();

            Dashboard dash = new Dashboard(name, dataSource, path + "/" + name+".json");
            Properties scannerProps = new Properties();

            ArrayNode clusterRow = dash.newRow(String.format("CLUSTER METRICS FOR %d broker(s)", brokers.size()), 200);
            ObjectNode graph1 = dash.newGraph(clusterRow, "Under-Replicated Partitions", 6, false);
            dash.newTarget(graph1, "$tag_service", "SELECT mean(\"Value\") AS \"Value\" FROM \"UnderReplicatedPartitions\" " +
                    "WHERE \"group\" = 'kafka.server' " +
                    "AND \"name\" = '" + name +"' " +
                    "AND $timeFilter GROUP BY time($interval), \"service\"");

            for (Broker broker : brokers) {
                //jmx scanner for broker
                Integer section = Integer.parseInt(broker.id) + 1;
                scannerProps.put(String.format("jmx.%d.address", section), String.format("%s:%d", broker.host, broker.jmxPort));
                scannerProps.put(String.format("jmx.%d.query.scope", section), "kafka.*:*");
                scannerProps.put(String.format("jmx.%d.query.interval.s", section), "10");
                scannerProps.put(String.format("jmx.%d.tag.host", section), broker.host);
                scannerProps.put(String.format("jmx.%d.tag.service", section), String.format("broker-%s", broker.id));
                scannerProps.put(String.format("jmx.%d.tag.name", section), name);

                //dashboard row for broker
                ArrayNode brokerRow = dash.newRow(String.format("Kafka Broker ID %s @ %s", broker.id, broker.hostPort()), 250);
                dash.newGraph(brokerRow, "Memory", 4, true);
                dash.newGraph(brokerRow, "LogFlush", 4, true);
                dash.newGraph(brokerRow, "Throughput", 4, true);

            }

            dash.save();

            JMXScanner jmxScannerInstance = new JMXScanner(scannerProps, publisher);
            while (!jmxScannerInstance.isTerminated()) {
                Thread.sleep(5000);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void close() {
        client.close();
    }

    private class BrokerInfoClient extends ZkClient {
        private final String brokersZkPath = "/brokers/ids";

        public BrokerInfoClient(String serverstring) {
            super(serverstring, 30000, 30000, new ZkSerializer() {
                private final ObjectMapper mapper = new ObjectMapper();

                @Override
                public byte[] serialize(Object o) throws ZkMarshallingError {
                    throw new ZkMarshallingError("This is a read-only client");
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