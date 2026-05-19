package com.dfpp.worker.kafka;

import com.dfpp.common.event.FileUploadedEvent;
import com.dfpp.common.event.ProcessingProgressEvent;
import com.dfpp.common.model.ProcessingStatus;
import com.dfpp.worker.idempotency.IdempotencyService;
import com.dfpp.worker.metrics.ProcessingMetrics;
import com.dfpp.worker.processor.ProcessorRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * The distributed worker. Multiple instances of this service join the same
 * consumer group ({@code processing-workers}); Kafka partitions the
 * {@code file.uploaded} topic across them, so adding replicas linearly scales
 * throughput.
 *
 * <p>Reliability pipeline per message:</p>
 * <ol>
 *   <li><b>Idempotency</b> - claim the job in Redis; skip duplicates.</li>
 *   <li><b>Process</b> - run the real {@link ProcessorRegistry} work,
 *       streaming progress events as it goes.</li>
 *   <li><b>Retry</b> - on a retryable error, re-attempt with exponential
 *       backoff up to {@code maxAttempts} (in-listener so offsets are only
 *       committed after the terminal outcome).</li>
 *   <li><b>Dead-letter</b> - after the final failed attempt, publish to the
 *       DLQ topic and emit a terminal failure progress event.</li>
 * </ol>
 * Manual acknowledgement guarantees at-least-once delivery; the idempotency
 * guard upgrades that to exactly-once <em>effect</em>.
 */
@Component
public class FileProcessingConsumer {

    private static final Logger log = LoggerFactory.getLogger(FileProcessingConsumer.class);

    private final ProcessorRegistry registry;
    private final IdempotencyService idempotency;
    private final ProgressPublisher progress;
    private final ProcessingMetrics metrics;
    private final String workerId;
    private final int maxAttempts;
    private final long backoffBaseMs;

    public FileProcessingConsumer(
            ProcessorRegistry registry,
            IdempotencyService idempotency,
            ProgressPublisher progress,
            ProcessingMetrics metrics,
            @Value("${app.worker.id:worker-${random.uuid}}") String workerId,
            @Value("${app.worker.max-attempts:4}") int maxAttempts,
            @Value("${app.worker.backoff-base-ms:1000}") long backoffBaseMs
    ) {
        this.registry = registry;
        this.idempotency = idempotency;
        this.progress = progress;
        this.metrics = metrics;
        this.workerId = workerId;
        this.maxAttempts = maxAttempts;
        this.backoffBaseMs = backoffBaseMs;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.uploaded:file.uploaded}",
            groupId = "processing-workers",
            concurrency = "${app.worker.concurrency:3}"
    )
    public void onMessage(FileUploadedEvent event, Acknowledgment ack) {
        metrics.workerEntered();

        try {
            if (idempotency.alreadyCompleted(event.jobId())) {
                log.info(
                        "[{}] jobId={} already completed - skipping duplicate",
                        workerId,
                        event.jobId()
                );

                metrics.recordDuplicateSkipped();
                ack.acknowledge();
                return;
            }

            if (!idempotency.tryBegin(event.jobId(), workerId)) {
                log.info(
                        "[{}] jobId={} claimed by another worker - skipping",
                        workerId,
                        event.jobId()
                );

                metrics.recordDuplicateSkipped();
                ack.acknowledge();
                return;
            }

            runWithRetry(event);
            ack.acknowledge();

        } finally {
            metrics.workerExited();
        }
    }

    private void runWithRetry(FileUploadedEvent event) {

        Timer.Sample sample = metrics.start();

        emit(
                event,
                ProcessingStatus.PROCESSING,
                0,
                1,
                "Picked up by " + workerId,
                null
        );

        Exception lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            try {

                final int currentAttempt = attempt;

                String result = registry.dispatch(
                        event,
                        pct -> emit(
                                event,
                                ProcessingStatus.PROCESSING,
                                pct,
                                currentAttempt,
                                "Processing (" + pct + "%)",
                                null
                        )
                );

                idempotency.markCompleted(event.jobId());

                metrics.recordSuccess(sample);

                emit(
                        event,
                        ProcessingStatus.COMPLETED,
                        100,
                        attempt,
                        "Processing completed successfully",
                        result
                );

                log.info(
                        "[{}] jobId={} COMPLETED on attempt {}",
                        workerId,
                        event.jobId(),
                        attempt
                );

                return;

            } catch (Exception ex) {

                lastError = ex;

                metrics.recordFailure();

                log.warn(
                        "[{}] jobId={} attempt {}/{} failed: {}",
                        workerId,
                        event.jobId(),
                        attempt,
                        maxAttempts,
                        ex.toString()
                );

                if (attempt < maxAttempts) {

                    metrics.recordRetry();

                    long backoff =
                            backoffBaseMs * (1L << (attempt - 1));

                    emit(
                            event,
                            ProcessingStatus.PROCESSING,
                            0,
                            attempt + 1,
                            "Attempt " + attempt
                                    + " failed (" + ex.getMessage()
                                    + ") - retrying in "
                                    + backoff + "ms",
                            null
                    );

                    sleep(backoff);
                }
            }
        }

        // All attempts exhausted -> dead-letter
        idempotency.release(event.jobId());

        metrics.recordDeadLetter();

        String msg =
                "Exhausted " + maxAttempts
                        + " attempts. Last error: "
                        + (lastError != null
                        ? lastError.getMessage()
                        : "unknown");

        log.error(
                "[{}] jobId={} -> DEAD LETTER. {}",
                workerId,
                event.jobId(),
                msg
        );

        progress.publishDlq(
                ProcessingProgressEvent.of(
                        event,
                        ProcessingStatus.DEAD_LETTER,
                        0,
                        workerId,
                        maxAttempts,
                        msg,
                        null
                )
        );
    }

    private void emit(
            FileUploadedEvent event,
            ProcessingStatus status,
            int pct,
            int attempt,
            String message,
            String result
    ) {

        progress.publish(
                ProcessingProgressEvent.of(
                        event,
                        status,
                        pct,
                        workerId,
                        attempt,
                        message,
                        result
                )
        );
    }

    private void sleep(long ms) {

        try {
            Thread.sleep(ms);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}