package com.dfpp.worker.kafka;

import com.dfpp.common.event.ProcessingProgressEvent;
import com.dfpp.common.event.Topics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProgressPublisher {

    private final KafkaTemplate<String, Object> kafka;

    public ProgressPublisher(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    public void publish(ProcessingProgressEvent event) {
        kafka.send(Topics.FILE_PROGRESS, event.jobId(), event);
    }

    public void publishDlq(ProcessingProgressEvent event) {
        kafka.send(Topics.FILE_DLQ, event.jobId(), event);
        kafka.send(Topics.FILE_PROGRESS, event.jobId(), event);
    }
}
