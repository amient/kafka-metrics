This is a basic structure centered around a set of kafka topics 'metrics-...'. 
The basic module provides a producer which can be used by applications and services and is also used in the module 
yammer-reporter to ingest kafka broker metrics.
 

 
# Usage in Kafka Broker

```
mvn clean package
cp stream-reporter/target/stream-reporter-<kafka-version>.jar $KAFKA_HOME/libs/
```

add following config to the server.properties for kafka broker 

```
kafka.metrics.polling.interval.secs=5
kafka.metrics.reporters=io.amient.kafka.metrics.StreamingReporter
kafka.metrics.reporters.StreamingReporter.topic=metrics
```

# Usage in Kafka Prism

add the following properties to the producer config

```
metric.reporters=io.amient.kafka.metrics.StreamingReporter
metric.reporters=io.amient.kafka.metrics.StreamingReporter
metric.reporters.StreamingReporter.topic=metrics
```


# Development

- cooridantes : x=host, y=service > partition by host
- generalise reporter so that it can be used in broker, consumer and producers   