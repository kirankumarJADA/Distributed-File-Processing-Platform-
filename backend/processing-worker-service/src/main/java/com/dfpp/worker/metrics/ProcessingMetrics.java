package com.dfpp.worker.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/** Centralised processing metrics exposed via /actuator/prometheus. */
@Component
public class ProcessingMetrics {

    private final Counter processed;
    private final Counter failed;
    private final Counter retried;
    private final Counter deadLettered;
    private final Counter duplicatesSkipped;
    private final Timer processingTimer;
    private final AtomicInteger activeWorkers = new AtomicInteger(0);

    public ProcessingMetrics(MeterRegistry registry) {
        this.processed = Counter.builder("dfpp.jobs.processed.total")
                .description("Successfully processed jobs").register(registry);
        this.failed = Counter.builder("dfpp.jobs.failed.total")
                .description("Job attempts that threw an error").register(registry);
        this.retried = Counter.builder("dfpp.jobs.retried.total")
                .description("Job attempts that were retried").register(registry);
        this.deadLettered = Counter.builder("dfpp.jobs.deadlettered.total")
                .description("Jobs routed to the dead-letter queue").register(registry);
        this.duplicatesSkipped = Counter.builder("dfpp.jobs.duplicates.skipped.total")
                .description("Duplicate deliveries skipped by idempotency guard").register(registry);
        this.processingTimer = Timer.builder("dfpp.jobs.processing.duration")
                .description("End-to-end processing duration").register(registry);
        registry.gauge("dfpp.workers.active", activeWorkers);
    }

    public Timer.Sample start() {
        return Timer.start();
    }

    public void recordSuccess(Timer.Sample sample) {
        sample.stop(processingTimer);
        processed.increment();
    }

    public void recordFailure() {
        failed.increment();
    }

    public void recordRetry() {
        retried.increment();
    }

    public void recordDeadLetter() {
        deadLettered.increment();
    }

    public void recordDuplicateSkipped() {
        duplicatesSkipped.increment();
    }

    public void workerEntered() {
        activeWorkers.incrementAndGet();
    }

    public void workerExited() {
        activeWorkers.decrementAndGet();
    }

    public double processedCount() {
        return processed.count();
    }

    public double failedCount() {
        return failed.count();
    }

    public double deadLetterCount() {
        return deadLettered.count();
    }

    public double retriedCount() {
        return retried.count();
    }

    public int activeWorkers() {
        return activeWorkers.get();
    }
}
