# Kafka Metrics  <sup><sup>:no_entry_sign: UNDER CONSTRUCTION</sup></sup>

This is a basic structure centered around a single topic 'metrics'. The basic module provides a reporter which 
can be used by kafka broker, kafka producer or consumer and other applications and services which use yammer metrics
library.
 

# Usage in Kafka Broker

```
mvn clean package
cp stream-reporter/target/stream-reporter-<kafka-version>.jar $KAFKA_HOME/libs/
```

add following config to the server.properties for kafka broker 

```
kafka.metrics.reporters=io.amient.kafka.metrics.StreamingMetricsReporter
kafka.metrics.StreamingReporter.host=localhost
kafka.metrics.StreamingReporter.polling.interval.ms=5000
kafka.metrics.StreamingReporter.schema.registry.url=http://localhost:8081
kafka.metrics.StreamingReporter.topic=metrics
    
```

after starting the broker with this configuration you can inspect the topic 'metrics' using kafka console consumer:

```
./bin/kafka-console-consumer.sh --zookeeper localhost --topic metrics \
    --property schema.registry.url=http://localhost:8081 \
    --formatter io.confluent.kafka.formatter.AvroMessageFormatter
```

# Usage in Kafka Prism

add the following properties to the producer config

```
metric.reporters=io.amient.kafka.metrics.StreamingMetricsReporter
metric.reporters.StreamingReporter.topic=metrics
```


# Development



