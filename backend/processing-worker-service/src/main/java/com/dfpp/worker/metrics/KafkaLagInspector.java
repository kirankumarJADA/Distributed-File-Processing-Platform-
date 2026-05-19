package com.dfpp.worker.metrics;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Computes real Kafka consumer-group lag for the {@code processing-workers}
 * group: {@code lag = logEndOffset - committedOffset} summed across partitions.
 * This is what "queue lag monitoring" surfaces on the dashboard and Grafana.
 */
@Component
public class KafkaLagInspector {

    private static final Logger log = LoggerFactory.getLogger(KafkaLagInspector.class);

    private final String bootstrap;
    private final String topic;
    private final String group = "processing-workers";

    public KafkaLagInspector(@Value("${spring.kafka.bootstrap-servers}") String bootstrap,
                             @Value("${app.kafka.topic.uploaded:file.uploaded}") String topic) {
        this.bootstrap = bootstrap;
        this.topic = topic;
    }

    public Map<String, Object> lag() {
        Map<String, Object> out = new HashMap<>();
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");

        try (AdminClient admin = AdminClient.create(props)) {
            Map<TopicPartition, OffsetAndMetadata> committed = admin
                    .listConsumerGroupOffsets(group)
                    .partitionsToOffsetAndMetadata()
                    .get(5, TimeUnit.SECONDS);

            if (committed.isEmpty()) {
                out.put("topic", topic);
                out.put("totalLag", 0L);
                out.put("partitions", Map.of());
                return out;
            }

            Map<TopicPartition, OffsetSpec> latestSpec = new HashMap<>();
            committed.keySet().forEach(tp -> latestSpec.put(tp, OffsetSpec.latest()));
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> end = admin
                    .listOffsets(latestSpec)
                    .all()
                    .get(5, TimeUnit.SECONDS);

            long total = 0;
            Map<String, Long> perPartition = new HashMap<>();
            for (var e : committed.entrySet()) {
                long endOffset = end.get(e.getKey()).offset();
                long lag = Math.max(0, endOffset - e.getValue().offset());
                perPartition.put(e.getKey().topic() + "-" + e.getKey().partition(), lag);
                total += lag;
            }
            out.put("topic", topic);
            out.put("group", group);
            out.put("totalLag", total);
            out.put("partitions", perPartition);
        } catch (Exception ex) {
            log.warn("Unable to compute Kafka lag: {}", ex.toString());
            out.put("topic", topic);
            out.put("totalLag", -1);
            out.put("error", ex.getMessage());
        }
        return out;
    }
}
