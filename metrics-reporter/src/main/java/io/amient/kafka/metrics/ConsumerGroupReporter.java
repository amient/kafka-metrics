package io.amient.kafka.metrics;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.*;
import com.yammer.metrics.reporting.AbstractPollingReporter;
import kafka.utils.VerifiableProperties;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class ConsumerGroupReporter implements kafka.metrics.KafkaMetricsReporter,
        io.amient.kafka.metrics.ConsumerGroupReporterMBean {

    private static final Logger log = LoggerFactory.getLogger(ConsumerGroupReporter.class);

    private static final String CONFIG_POLLING_INTERVAL = "kafka.metrics.polling.interval";
    private boolean initialized;
    private Properties props;
    private long pollingIntervalSeconds;
    private int brokerId;
    private boolean running;
    private X underlying;

    @Override
    public String getMBeanName() {
        return "kafka:type=io.amient.kafka.metrics.ConsumerGroupReporter";
    }

    @Override
    public void init(VerifiableProperties props) {
        if (!initialized) {

            this.props = new Properties();
            if (props.containsKey(CONFIG_POLLING_INTERVAL)) {
                this.pollingIntervalSeconds = props.getInt(CONFIG_POLLING_INTERVAL);
            } else {
                this.pollingIntervalSeconds = 10;
            }

            this.brokerId = Integer.parseInt(props.getProperty("broker.id"));
            log.info("Building ConsumerGroupReporter: polling.interval=" + pollingIntervalSeconds);
            Enumeration<Object> keys = props.props().keys();
            while (keys.hasMoreElements()) {
                String key = keys.nextElement().toString();
                if (key.startsWith("kafka.metrics.")) {
                    String subKey = key.substring(14);
                    this.props.put(subKey, props.props().get(key));
                    log.info("Building ConsumerGroupReporter: " + subKey + "=" + this.props.get(subKey));
                }
            }
            initialized = true;
            this.underlying = new X(Metrics.defaultRegistry());
            startReporter(pollingIntervalSeconds);

        }
    }


    public void startReporter(long pollingPeriodSecs) {
        if (initialized && !running) {
            underlying.start(pollingPeriodSecs, TimeUnit.SECONDS);
            running = true;
            log.info("Started TopicReporter instance with polling period " + pollingPeriodSecs + "  seconds");
        }
    }

    public void stopReporter() {
        if (initialized && running) {
            running = false;
            underlying.shutdown();
            log.info("Stopped TopicReporter instance");
            underlying = new X(Metrics.defaultRegistry());
        }
    }


    private class X extends AbstractPollingReporter {

        final GroupMetrics<ConsumerGauge> consumerOffsets = new GroupMetrics("ConsumerOffset", ConsumerGauge.class, getMetricsRegistry());
        final GroupMetrics<ConsumerGauge> consumerLags = new GroupMetrics("ConsumerLag", ConsumerGauge.class, getMetricsRegistry());
        private final AdminClient admin;
        private Clock clock;

        protected X(MetricsRegistry registry) {
            super(registry, "consumer-groups-reporter");
            this.clock = Clock.defaultClock();
            this.admin = AdminClient.create(props);
        }

        @Override
        public void shutdown() {
            try {
                super.shutdown();
            } finally {
                admin.close();
            }
        }

        @Override
        public void run() {
            final Long timestamp = clock.time();
            //process extra consumer metrics
            try {
                int controllerId = admin.describeCluster().controller().get(pollingIntervalSeconds, TimeUnit.SECONDS).id();
                if (brokerId == controllerId) {
                    final Map<TopicPartition, Long> logEndOffsets = new HashMap<>();
                    final Set<Map.Entry<MetricName, Metric>> metrics = getMetricsRegistry().allMetrics().entrySet();
                    try {
                        for (Map.Entry<MetricName, Metric> entry : metrics) {
                            final MetricName name = entry.getKey();
                            if (name.getGroup().equals("kafka.log") && name.getName().equals("LogEndOffset")) {
                                /*
                                 * Decompose kafka metrics tags which uses yammer metrics Scope to "squash" all tags together
                                 */
                                String topic = null;
                                Integer partition = null;
                                String[] scope = name.getScope().split("\\.");

                                for (int s = 0; s < scope.length; s += 2) {
                                    String field = scope[s];
                                    String value = scope[s + 1];
                                    switch(field) {
                                        case "topic": topic = value; break;
                                        case "partition": partition = Integer.parseInt(value); break;
                                    }
                                }
                                if (topic != null && partition != null) {
                                    Gauge<Long> m = (Gauge<Long>) entry.getValue();
                                    logEndOffsets.put(new TopicPartition(topic, partition), m.value());
                                }
                            }

                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Collection<ConsumerGroupListing> consumerGroups = admin.listConsumerGroups().all().get(pollingIntervalSeconds, TimeUnit.SECONDS);

                    consumerGroups.parallelStream().
                            filter(group -> !group.groupId().startsWith("console-consumer")).
                            forEach(group -> {
                                try {
                                    Map<TopicPartition, OffsetAndMetadata> offsets = admin.listConsumerGroupOffsets(group.groupId()).partitionsToOffsetAndMetadata().get(pollingIntervalSeconds, TimeUnit.SECONDS);
                                    for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : offsets.entrySet()) {
                                        TopicPartition tp = entry.getKey();
                                        if (logEndOffsets.containsKey(tp)) {
                                            long logEndOffset = logEndOffsets.get(tp);

                                            long consumerOffset = entry.getValue().offset();
                                            ConsumerGauge offsetGauge = consumerOffsets.get(group.groupId(), tp);
                                            offsetGauge.value.set(consumerOffset);

                                            ConsumerGauge lagGauge = consumerLags.get(group.groupId(), tp);
                                            lagGauge.value.set(Math.max(0, logEndOffset - consumerOffset));
                                        }
                                    }
                                } catch (Exception e) {
                                    log.error("error while fetching offsets for group " + group, e);
                                }
                            });
                }
            } catch (Exception e) {
                log.error("error while processing conusmer offsets", e);
            }
        }

    }


    public static class ConsumerGauge extends Gauge<Long> {
        AtomicLong value = new AtomicLong(0);

        @Override
        public Long value() {
            return value.get();
        }
    }




}
