# Kafka Metrics  <sup><sup>:no_entry_sign: UNDER CONSTRUCTION</sup></sup>

This is a basic structure centered around a single topic 'metrics'. The basic module provides a reporter which 
can be used by kafka broker, kafka producer or consumer and other applications and services which use yammer metrics
library.
 

# Usage

## Usage in Kafka Broker

```
mvn clean package
cp stream-reporter/target/stream-reporter-<kafka-version>.jar $KAFKA_HOME/libs/
```

add following properties to config file for kafka broker, kafka prism.  

```
kafka.metrics.reporters=io.amient.kafka.metrics.TopicReporter
kafka.metrics.host=my.example.host
kafka.metrics.polling.interval.s=10
```

## Usage in Kafka Prism and other producers
 
```
target.<id>.producer.metric.reporters=io.amient.kafka.metrics.TopicReporter
target.<id>.producer.kafka.metrics.host=my.example.host
target.<id>.producer.kafka.metrics.service=kafka-prism
target.<id>.producer.kafka.metrics.bootstrap.servers=localhost:9092
target.<id>.producer.kafka.metrics.polling.interval.s=5

```

## Usage in producer/consumer applications 

...


## Inspecting the metrics topic  

Using kafka console consumer with a formatter for kafka-metrics:

```
./bin/kafka-console-consumer.sh --zookeeper localhost --topic _metrics --formatter io.amient.kafka.metrics.MeasurementFormatter
```


# Development

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


