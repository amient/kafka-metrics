# Kafka Metrics  <sup><sup>:no_entry_sign: UNDER CONSTRUCTION</sup></sup>

This is a basic structure centered around a single topic 'metrics'. The basic module provides a reporter which 
can be used by kafka broker, kafka producer or consumer and other applications and services which use yammer metrics
library.
 
<a name="usage">
## Usage
</a>

## Usage in Kafka Broker

```
mvn clean package
cp stream-reporter/target/stream-reporter-<kafka-version>.jar $KAFKA_HOME/libs/
```

add following properties to config file for kafka broker, kafka prism.  

```
kafka.metrics.reporters=io.amient.kafka.metrics.TopicReporter
kafka.metrics.<CONFIGURATION-OPTIONS>...
```

## Usage in Kafka Prism and other producers
 
```
target.<id>.producer.metric.reporters=io.amient.kafka.metrics.TopicReporter
target._s.producer.kafka.metrics.<CONFIGURATION-OPTIONS>...
```

## Usage in producer/consumer applications 

...


<a name="configuration">
## Configuration Options
</a>

parameter                                  | default           | description
-------------------------------------------|-------------------|------------------------------------------------------------------------------
**kafka.metrics.polling.interval**         | `10s`             | Poll and publish frequency of metrics, llowed interval values: 1s, 10s, 1m
**kafka.metrics.topic**                    | `_metrics`        | Topic name where metrics are published
**kafka.metrics.bootstrap.servers**        | *inferred*        | Coma-separated list of kafka server addresses (host:port). When used in Brokers, `localhost` is default, in Prism targets, the same server list is used as in the target producer by default.
*kafka.metrics.tag.<tag-name>.<tag=value>* | -                 | Fixed name-value pairs that will be used as tags in the published measurement for this instance, .e.g 
*kafka.metrics.tag.service.kafka-broker-0* | |
*kafka.metrics.tag.host.my-host-01*        | |
*kafka.metrics.tag.cluster.uk-az1*         | |


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

DONE reporting interval should be fixed to 10s for dashboards to report aggregated rates correctly
DONE in any producer configuration (including prism) infer bootstrap.servers from the producer
- should `_metrics` topic represent only per cluster metric stream, NEVER aggregate, and have aggregate have `_metrics_aggregated` or something ?
   - this requires the prism feature for topic name prefix/suffix, but 
- Loader configuration file "kafka-metrics.properties"
- [Scripted Grafana dashboard](http://docs.grafana.org/reference/scripting/)  (kafka, prism) 
- Draw design doc with clear docker image boundaries
    - docker image for Kafka Metrics Instance:
        - Go 1.4
        - InfluxDB 0.9 + pre-configured metrics database
        - Grafana 2.4 - how to pre-configure dashboards ??
        - node (v0.12.0)
        - npm (v2.5.0)
        - grunt (v0.4.5)
        - java 1.6+
        - MetricsInfluxDbPublisher


