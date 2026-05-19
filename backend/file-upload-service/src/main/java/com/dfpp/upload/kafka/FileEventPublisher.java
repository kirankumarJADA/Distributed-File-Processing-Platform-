package com.dfpp.upload.kafka;

import com.dfpp.common.event.FileUploadedEvent;
import com.dfpp.common.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class FileEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FileEventPublisher.class);
    private final KafkaTemplate<String, Object> kafka;

    public FileEventPublisher(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    /**
     * The job id is used as the partition key so all events for a single file
     * land on the same partition, guaranteeing ordered processing and enabling
     * idempotent consumer-side deduplication.
     */
    public void publishUploaded(FileUploadedEvent event) {
        kafka.send(Topics.FILE_UPLOADED, event.jobId(), event)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish FileUploadedEvent jobId={}", event.jobId(), ex);
                    } else {
                        log.info("Published FileUploadedEvent jobId={} partition={} offset={}",
                                event.jobId(),
                                res.getRecordMetadata().partition(),
                                res.getRecordMetadata().offset());
                    }
                });
    }
}
