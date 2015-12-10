# Kafka Metrics  <sup><sup>:no_entry_sign: UNDER CONSTRUCTION</sup></sup>

This is a system whose purpose is to aggregate metrics from a topology of Kafka Brokers, Prisms, Producer and Consumer
applications. It uses InfluxDB as the time series back-end which can then be used for example with Grafana front-end
or other visualisation and alerting tools.

### Contents

1. [Overivew](#overview)
2. [InfluxDB Loader](#usage-loader)
	- [Quickstart](#quickstart) 
	- [Configuration Options](#configuration-loader)
		- [InfluxDB Backend](#configuration-loader-influxdb)
		- [JMX Connectors](#configuration-loader-jmx)
		- [Metrics Consumer](#configuration-loader-consumer)
3. [TopicReporter](#usage-reporter)
	- [Usage in Kafka Broker, Kafka Prism, Kafka Producer (pre 0.8.2), Kafka Consumer (pre 0.9)](#usage-reporter-kafka-old)
	- [Usage in Kafka NEW Producer (0.8.2+) and Consumer (0.9+)](#usage-reporter-kafka-new)
	- [Usage in any application using dropwizard metrics (formerly yammer metrics)](#usage-reporter-dropwizard)
	- [Configuration Options](#configuration-reporter)
4. [Operations & Troubleshooting](#operations)
5. [Development](#development)

<a name="overvuew">
## Overview
</a>

![overview](doc/metrics.jpg)

There are 2 primary ways of how the aggregation of metrics from several components is achieved. 

For smaller infrastructures consisting of small number of clusters in proximity to each other, direct JMX scanner tasks 
can be configured for each JMX Server exposed in the infrastructure and application landscape. This method doesn't
require to include any extra code in the monitored applications as long as they already expsoe JMX MBeans.

For larger, globally distributed infrastructures, or if more fault-tolerance is required for the metrics aggregation, 
a TopicReporter which implements Yammer Metrics Reporter interface as well as Kafka-specific internal reporters. 
This reporter publishes all the metrics to configured, most often local kafka topic `_metrics`. The JMX Scanner 
aggregator is then replaced with Kafka Metrics Consumer  aggregator which then publishes these metrics into the InfluxDB.
For multi-DC, potentially global deployments, Kafka Prism or Kafka Mirror Maker maker can placed between 
the Kafka Metrics Consumer and the disparate metrics streams, first aggregating them in a single cluster.

<a name="usage-loader">
## InfluxDB Loader Usage
</a>

<a name="quickstart">
### Quickstart
</a>

You'll need to install at least InfluxDB Server with defaults, then package all modules:

```
./gradlew build
```

If you have a Kafka Broker running locally which has a JMX Server enabled say on port 19092, you can use 
 the following default config file for jmx scanner local aggrgator: 

```
./influxdb-loader/bin/run-influxdb-loader.sh influxdb-loader/conf/local-jmx.properties
```

<a name="configuration-loader">
### Configuration Options for InfluxDB Loader
</a>

<a name="configuration-loader-influxdb">
### InfluxDB back options
</a>

parameter                                  | default                | description
-------------------------------------------|------------------------|------------------------------------------------------------------------------
**influxdb.database**                      | `metrics`              | InfluxDB Database Name where to publish the measurements 
**influxdb.url**                           | `http://localhost:8086`| URL of the InfluxDB API Instance
**influxdb.username**                      | `root`                 | Authentication username for API calls
**influxdb.password**                      | `root`                 | Authentication passord for API calls


<a name="configuration-loader-jmx">
### JMX Scanner Options
</a>

parameter                                  | default                | description
-------------------------------------------|------------------------|------------------------------------------------------------------------------
jmx.{ID}.address                  | -                      | Address of the JMX Service Endpoint 
jmx.{ID}.query.scope              | `kafka`                | this will be used to filer object names in the JMX Server registry, i.e. `kafka.*:*`
jmx.{ID}.query.interval.s         | 10                     | how frequently to query the JMX Service 
jmx.{ID}.tag.{TAG-1}              | -                      | optinal tags which will be attached to each measurement  
jmx.{ID}.tag.{TAG-2}              | -                      | ...
jmx.{ID}.tag.{TAG-n}              | -                      | ...

<a name="configuration-loader-consumer">
### Metrics Consumer Options
</a>

parameter                                  | default                | description
-------------------------------------------|------------------------|------------------------------------------------------------------------------
consumer.topic                             | `_metrics`             | Topic to consumer (where measurements are published by Reporter)
consumer.numThreads                        | `1`                    | Number of consumer threads
consumer.zookeeper.connect                 | `localhost:2181`       | As per [Kafka Consumer Configuration](http://kafka.apache.org/documentation.html#consumerconfigs)
consumer.group.id                          | -                      | As per Any [Kafka Consumer Configuration](http://kafka.apache.org/documentation.html#consumerconfigs)
consumer....                               | -                      | Any other [Kafka Consumer Configuration](http://kafka.apache.org/documentation.html#consumerconfigs)

 
<a name="usage-reporter">
## Usage of the TopicReporter
</a>

Due to different stage of maturity of various kafka components, watch out for subtle differences when adding 
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

<a name="configuration-reporter">
### Configuration Options for the TopicReporter
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
 

- DOC: draw different deployment setups 
- DOC: provide recipe and bin script for local setup with influxdb and grafana
- DOC: sphinx documentation using generated versions in the examples
- TODO: more robust connection error handling, e.g. when one of the cluster is not reachable, warn once and try reconnecting quietly
- TODO: expose all except serde configs for kafka producer (NEW) configuration properties
- TODO: configurable log4j.properties file location and enironment var overrides for configs
- DESIGN: explore back-port to kafka 0.7
- DESIGN: explore influxdb retention options
- DESIGN: [Scripted Grafana dashboard](http://docs.grafana.org/reference/scripting/)  (kafka, prism)
- DESIGN: should `_metrics` topic represent only per cluster metric stream, NEVER aggregate, and have aggregate have `_metrics_aggregated` or something ?
   - this requires the prism feature for topic name prefix/suffix 
- TODO: go+influxdb+npm+grafana for debian, consider writing the influxdb-loader as golang kafka consumer which would lead to a kafka-metrics instance
    - Go 1.4
    - MetricsInfluxDbPublisher (Go)
    - InfluxDB 0.9 (Go)
    - Grafana 2.4 (Go)
    - node (v0.12.0)
    - npm (v2.5.0)
    - grunt (v0.4.5)


