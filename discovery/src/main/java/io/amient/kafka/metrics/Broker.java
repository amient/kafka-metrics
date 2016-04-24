package io.amient.kafka.metrics;

public class Broker {
    public final String host;
    public final int jmxPort;
    public final String id;
    public final int port;

    public Broker(String id, String host, int port, int jmxPort) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.jmxPort = jmxPort;
    }

    public String hostPort() {
        return host + ":" + port;
    }
}

