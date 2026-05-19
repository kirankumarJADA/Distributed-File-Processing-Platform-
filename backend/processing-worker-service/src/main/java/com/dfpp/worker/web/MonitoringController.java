package com.dfpp.worker.web;

import com.dfpp.worker.metrics.KafkaLagInspector;
import com.dfpp.worker.metrics.ProcessingMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregated monitoring surface for the real-time dashboard. Complements the
 * raw Prometheus metrics with a single JSON snapshot of throughput, failures
 * and queue lag.
 */
@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private final ProcessingMetrics metrics;
    private final KafkaLagInspector lag;

    public MonitoringController(ProcessingMetrics metrics, KafkaLagInspector lag) {
        this.metrics = metrics;
        this.lag = lag;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("processed", metrics.processedCount());
        m.put("failed", metrics.failedCount());
        m.put("retried", metrics.retriedCount());
        m.put("deadLettered", metrics.deadLetterCount());
        m.put("activeWorkers", metrics.activeWorkers());
        double total = metrics.processedCount() + metrics.failedCount();
        m.put("successRate", total == 0 ? 100.0
                : Math.round(metrics.processedCount() / total * 10000) / 100.0);
        m.put("queue", lag.lag());
        return m;
    }

    @GetMapping("/queue")
    public Map<String, Object> queue() {
        return lag.lag();
    }
}
