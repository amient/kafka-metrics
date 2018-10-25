package io.amient.kafka.metrics;

import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;
import org.apache.kafka.common.TopicPartition;

import java.util.HashMap;
import java.util.Map;

public class GroupMetrics<T extends Metric> {

    private final Class<? extends T> cls;
    private final MetricsRegistry registry;
    private final String name;
    private final Map<String, Map<TopicPartition, T>> data = new HashMap<>();

    public GroupMetrics(String metricName, Class<? extends T> cls, MetricsRegistry registry) {
        this.registry = registry;
        this.name = metricName;
        this.cls = cls;
    }

    public T get(String group, TopicPartition tp) {
        Map<TopicPartition, T> metrics = data.get(group);
        if (metrics == null) {
            metrics = new HashMap<>();
            data.put(group, metrics);
        }
        T metric = metrics.get(tp);
        if (metric == null) {
            try {
                metric = cls.newInstance();
                if (metric instanceof Gauge) {
                    registry.newGauge(NewName(group, tp), (Gauge)metric);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            metrics.put(tp, metric);
        }
        return metric;
    }

    private MetricName NewName(String group, TopicPartition tp) {
        return new MetricName(
                "kafka.groups",
                "Group",
                name,
                "",
                "kafka.consumer:type=Group,name=" + name
                        + ",group=" + group
                        + ",topic=" + tp.topic()
                        + ",partition=" + tp.partition());
    }

}
