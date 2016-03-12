# Kafka Metrics  <sup><sup>:no_entry_sign: UNDER CONSTRUCTION</sup></sup>

This is a system whose purpose is to aggregate metrics from a topology of Kafka Brokers, Prisms, Producer and Consumer
applications. It uses InfluxDB as the time series back-end which can then be used for example with Grafana front-end
or other visualisation and alerting tools.

### Contents

1. [Overivew](#overview)
	- [Architecture](#overview)
	- [Basic Scenario](#scenario0) 
	- [Multi-Server Scenario](#scenario1)
	- [Multi-Data-Centre Scenario](#scenario3) 
	- [Multi-Enviornment Scenario](#scenario2)
2. [Usage](#usage-instance)
 	- [Bundled Instance: InfluxDB, Grafana](#usage-instance)
 	- [InfluxDB Loader](#usage-loader) 
 	- [MetricsAgent](#metrics-agent)
 	- [TopicReporter](#usage-reporter)
	    - [Usage in Kafka Broker, Kafka Prism, Kafka Producer (pre 0.8.2), Kafka Consumer (pre 0.9)](#usage-reporter-kafka-old)
	    - [Usage in Kafka NEW Producer (0.8.2+) and Consumer (0.9+)](#usage-reporter-kafka-new)
	    - [Usage in Samza](#usage-samza)
	    - [Usage in any application using dropwizard metrics (formerly yammer metrics)](#usage-reporter-dropwizard)
6. [Configuration](#configuration)
    - [InfluxDB Loader Options](#configuration-loader-influxdb)
    - [JMX Scanner Options](#configuration-scanner)
    - [Metrics Producer Options](#configuration-producer)
7. [Operations & Troubleshooting](#operations)
8. [Development](#development)

<a name="overview">
## Overview
</a>

![overview](doc/metrics.png)

There are several ways of how the aggregation of metrics from several components is achieved. 

<a name="scenario0">
### Basic Scenario
</a>

For smaller systems consisting of components on the same network or simply a localhost, direct JMX scanner tasks can be configured for each JMX Application. This method doesn't require to include any extra code in the monitored applications as long as they already expsoe JMX MBeans and in a local environment the kafka topic can also be omitted.

![scenario0](doc/kafka-metrics-scenario0.png)

<a name="scenario1">
### Multi-Server Scenario
</a>

For bigger application servers, where metrics from several hosts need to be aggregated or in cases where more fault-tolerant collection of metrics is required, a combination of pluggable TopicReproter or JMX Metrics Agent and a Kafka Topic can be deployed by configuration. The JMX Scanner used in the basic scenario is replaced with Metrics Consumer which then publishes these metrics into the InfluxDB.


![scenario0](doc/kafka-metrics-scenario1.png)

<a name="scenario2">
### Multi-Data-Centre Scenario
</a>

For multi-DC, potentially global deployments, where metrics from several disparate clusters need to be collected, each cluster has its agent which publishes into a local metrics stream and one of the existing mirroring components (Kafka Prism, Kafka Mirror Maker, ...) is deployed to aggregate local metrics topic into a single aggregated stream providing a real-time monitoring of the entire system.

![scenario0](doc/kafka-metrics-scenario2.png)

<a name="scenario3">
### Multi-Environment Scenario
</a>

Finally, in the heterogenous environments, where different kinds of application and infrastructure stacks exist, firstly any JMX-Enabled or YAMMER-Enabled application can be plugged by configuration. 

***For non-JVM applications or for JVM applications that do not expose JMX MBeans, there is a work in progress to have REST Metrics Agent which can receive http put requests and which can be deployed in all scenarios either with or without the metrics topic.***

![scenario0](doc/kafka-metrics-scenario3.png)

<a name="usage-instance">
### Launching Bundled Instance 
</a>

The following command should install and launch local instance of **InfluxDB** and **Grafana** that can be used with all
the scenarios whether for testing on development machine or deployed on a production host.
  
```
./gradlew :instance:start
```

<a name="usage-loader">
## InfluxDB Loader Usage
</a>

You'll need to install at least InfluxDB Server with defaults, then package all modules:

```
./gradlew build
```

If you have a Kafka Broker running locally which has a JMX Server enabled say on port 19092, you can use 
 the following default config file for jmx scanner local aggrgator: 

```
./bin/kafka-metrics-loader.sh influxdb-loader/conf/local-jmx.properties
```

<a name="metrics-agent">
## Metrics Agent
</a>

The purpose of agent is to move expensive metrics collection like JMX polling closer to the application and publish
these into the kafka metrics topic. 

```
./bin/kafka-metrics-agent.sh <PROPERTIES-FILE>
```

<a name="usage-reporter">
## Usage of the TopicReporter
</a>

This reporter publishes all the metrics to configured, most often local kafka topic `_metrics`. Due to different stage of maturity of various kafka components, watch out for subtle differences when adding 
TopicReporter class. To be able to use the reporter as plug-in for kafka brokers and tools you need to put the
packaged jar in their classpath, which in kafka broker means putting it in the kafka /libs directory:

```
./gradlew install
cp stream-reporter/lib/stream-reporter-*.jar $KAFKA_HOME/libs/
```

<a name="usage-reporter-kafka-old">
### Usage in Kafka Broker, Kafka Prism, Kafka Producer (pre 0.8.2), Kafka Consumer (pre 0.9)
</a>


add following properties to the configuration for the component  

```
kafka.metrics.reporters=io.amient.kafka.metrics.TopicReporter
kafka.metrics.<CONFIGURATION-OPTIONS>...
```

<a name="usage-reporter-kafka-new">
###  Usage in Kafka NEW Producer (0.8.2+) and Consumer (0.9+) 
</a>

```
metric.reporters=io.amient.kafka.metrics.TopicReporter
kafka.metrics.<CONFIGURATION-OPTIONS>...
```

<a name="usage-samza">
###  Usage in Samza (0.9+) 
</a>

The Metrics Consumer understands json messages that Samza generates using MetricsSnapshotSerdeFactory.
So just a normal samza metrics configuration without additional code, for example: 

```
metrics.reporters=topic
metrics.reporter.topic.class=org.apache.samza.metrics.reporter.MetricsSnapshotReporterFactory
metrics.reporter.topic.stream=kafkametrics._metrics
serializers.registry.metrics.class=org.apache.samza.serializers.MetricsSnapshotSerdeFactory
systems.kafkametrics.streams._metrics.samza.msg.serde=metrics
systems.kafkametrics.samza.factory=org.apache.samza.system.kafka.KafkaSystemFactory
systems.kafkametrics.consumer.zookeeper.connect=<...>
systems.kafkametrics.producer.bootstrap.servers=<...>

```


<a name="usage-reporter-dropwizard">
### Usage in any application using dropwizard metrics (formerly yammer metrics)
</a>

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
    .setTopic("_metrics") //this is also default
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
## Configuration
</a>

<a name="configuration-loader-influxdb">
### InfluxDB back options
</a>

The following configuration options can be used with the **InfluxDB Loader**:

parameter                                  | default                | description
-------------------------------------------|------------------------|------------------------------------------------------------------------------
**influxdb.database**                      | `metrics`              | InfluxDB Database Name where to publish the measurements 
**influxdb.url**                           | `http://localhost:8086`| URL of the InfluxDB API Instance
**influxdb.username**                      | `root`                 | Authentication username for API calls
**influxdb.password**                      | `root`                 | Authentication passord for API calls
consumer.topic                             | `_metrics`             | Topic to consumer (where measurements are published by Reporter)
consumer.numThreads                        | `1`                    | Number of consumer threads
consumer.zookeeper.connect                 | `localhost:2181`       | As per [Kafka Consumer Configuration](http://kafka.apache.org/documentation.html#consumerconfigs)
consumer.group.id                          | -                      | As per Any [Kafka Consumer Configuration](http://kafka.apache.org/documentation.html#consumerconfigs)
consumer....                               | -                      | Any other [Kafka Consumer Configuration](http://kafka.apache.org/documentation.html#consumerconfigs)

<a name="configuration-scanner">
### JMX Scanner Options
</a>

The following configuration options can be used with the **InfluxDB Loader** and **MetricsAgent**:

parameter                                  | default                | description
-------------------------------------------|------------------------|------------------------------------------------------------------------------
jmx.{ID}.address                  | -                      | Address of the JMX Service Endpoint 
jmx.{ID}.query.scope              | `*:*`                  | this will be used to filer object names in the JMX Server registry, i.e. `*:*` or `kafka.*:*` or `kafka.server:type=BrokerTopicMetrics,*`
jmx.{ID}.query.interval.s         | 10                     | how frequently to query the JMX Service 
jmx.{ID}.tag.{TAG-1}              | -                      | optinal tags which will be attached to each measurement  
jmx.{ID}.tag.{TAG-2}              | -                      | ...
jmx.{ID}.tag.{TAG-n}              | -                      | ...


<a name="configuration-producer">
### Metrics Producer Options
</a>

The following configuration options can be used with the TopicReporter and MetricsAgent:

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
 
- TODO: add Kapacitor (also written in Go) to the default metrics) 
- TODO: generate dashboard for clusters, brokers and prisms via grafana [scripting](http://docs.grafana.org/reference/scripting/) or [templating](http://docs.grafana.org/reference/templating/) 
- TODO: exploit the fact that decoder returns List<Measurment> - kafka metrics can be optimised to send many metrics in a single message  
- DOC: provide recipe and bin script for local setup with influxdb, grafana and kapacitor out-of-the-box 
- DOC: sphinx documentation using generated versions in the examples
- TODO: REST Metrics Agent - ideally using Kafka REST API but only if Schema Registry is optional - for non-jvm apps
- TODO: more robust connection error handling, e.g. when one of the cluster is not reachable, warn once and try reconnecting quietly
- TODO: expose all except serde configs for kafka producer (NEW) configuration properties
- TODO: configurable log4j.properties file location and enironment var overrides for configs
- TODO: explore back-port to kafka 0.7
- DESIGN: explore influxdb retention options
- DESIGN: [Scripted Grafana dashboard](http://docs.grafana.org/reference/scripting/)  (kafka, prism)
- DESIGN: should `_metrics` topic represent only per cluster metric stream, NEVER aggregate, and have aggregate have `_metrics_aggregated` or something ?
   - this requires the prism feature for topic name prefix/suffix 
- DESIGN: consider writing the influxdb-loader as golang kafka consumer which would lead to a kafka-metrics instance
    - Go 1.4
    - MetricsInfluxDbPublisher (Go)
    - InfluxDB 0.9 (Go)
    - Grafana 2.4 (Go)
    - node (v0.12.0)
    - npm (v2.5.0)
    - grunt (v0.4.5)


