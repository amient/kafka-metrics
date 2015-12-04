# Kafka Metrics  <sup><sup>:no_entry_sign: UNDER CONSTRUCTION</sup></sup>

This is a basic structure centered around a single topic 'metrics'. The basic module provides a reporter which 
can be used by kafka broker, kafka producer or consumer and other applications and services which use yammer metrics
library.
 
<a name="usage">
## Usage
</a>

Due to different stage of maturity of various kafka components, watch out for subtle differences when adding 
TopicReporter class. To be able to use the reporter as plug-in for kafka brokers and tools you need to put the
pacakaged jar in the kafka/libs directory:

```
mvn clean package
cp stream-reporter/target/stream-reporter-<kafka-version>.jar $KAFKA_HOME/libs/
```

### Usage in Kafka Broker, Kafka Prism, Kafka Producer (pre 0.8.2), Kafka Consumer (pre 0.9)


add following properties to the configuration for the component  

```
kafka.metrics.reporters=io.amient.kafka.metrics.TopicReporter
kafka.metrics.<CONFIGURATION-OPTIONS>...
```

###  Usage in Kafka NEW Producer (0.8.2+) and Consumer (0.9+) NEW APIs 

```
metric.reporters=io.amient.kafka.metrics.TopicReporter
kafka.metrics.<CONFIGURATION-OPTIONS>...
```

### Usage in any application using dropwizard metrics (formerly yammer metrics)

Like any other yammer metrics reporter, given an instance (and configuration), once started, the reporter
will produce kafka-metrics messages to a configured topic every given time interval. Scala-Maven Example:

``` pom.xml
...
<dependency>
   <groupId>io.amient.kafka.metrics</groupId>
   <artifactId>metrics-reporter</artifactId>
   <version>${kafka.version}</version>
</dependency>
...
```

... Using builder for programmatic initialization

``` 
val registry = MetricsRegistry.defaultRegistry()
val reporter = TopicReporter.forRegistry(registry)
    .setTopic("_metrics) //this is also default
    .setBootstrapServers("kafka1:9092,kafka2:9092")
    .setTag("host", "my-host-xyz")
    .setTag("app", "my-app-name")
    .build()
reporter.start(10, TimeUnit.SECONDS);
```

... OR Using config properties:
 
```
val registry = MetricsRegistry.defaultRegistry()
val config = new java.util.Properties(<CONFIGURATION-OPTIONS>)
val reporter = TopicReporter.forRegistry(registry).configure(config).build()
reporter.start(10, TimeUnit.SECONDS);
```

<a name="configuration">
## Configuration Options
</a>

parameter                                  | default           | description
-------------------------------------------|-------------------|------------------------------------------------------------------------------
**kafka.metrics.topic**                    | `_metrics`        | Topic name where metrics are published
**kafka.metrics.polling.interval**         | `10s`             | Poll and publish frequency of metrics, llowed interval values: 1s, 10s, 1m
**kafka.metrics.bootstrap.servers**        | *inferred*        | Coma-separated list of kafka server addresses (host:port). When used in Brokers, `localhost` is default.
*kafka.metrics.tag.<tag-name>.<tag=value>* | -                 | Fixed name-value pairs that will be used as tags in the published measurement for this instance, .e.g `kafka.metrics.tag.host.my-host-01` or `kafka.metrics.tag.dc.uk-az1`  


<a name="operations">
## Operations & Troubleshooting
</a>


### Inspecting the metrics topic  

Using kafka console consumer with a formatter for kafka-metrics:

```
./bin/kafka-console-consumer.sh --zookeeper localhost --topic _metrics --formatter io.amient.kafka.metrics.MeasurementFormatter
```

<a name="development">
## Development
</a>

- TODO: Decompose dot-separated scope created by kafka yammer reporter into tags 
- TODO: sphinx documentation using generated versions in the examples and try to back-port to kafka 0.7 and forward port to kafka 0.9
- TODO: expose all except serde configs for kafka producer (NEW) configuration properties - namespace them all with kafka.metrics.producer...
- DESIGN: should `_metrics` topic represent only per cluster metric stream, NEVER aggregate, and have aggregate have `_metrics_aggregated` or something ?
   - this requires the prism feature for topic name prefix/suffix 
- Complete configuration file "kafka-metrics.properties" with env.var overrides
- [Scripted Grafana dashboard](http://docs.grafana.org/reference/scripting/)  (kafka, prism)
- Draw design doc 
- Consider writing the influxdb-loader as golang kafka consumer which would lead to a kafka-metrics instance
    - Go 1.4
    - MetricsInfluxDbPublisher (Go)
    - InfluxDB 0.9 (Go)
    - Grafana 2.4 (Go)
    - node (v0.12.0)
    - npm (v2.5.0)
    - grunt (v0.4.5)


