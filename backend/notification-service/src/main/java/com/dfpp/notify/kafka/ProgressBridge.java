package com.dfpp.notify.kafka;

import com.dfpp.common.event.ProcessingProgressEvent;
import com.dfpp.common.event.Topics;
import com.dfpp.common.model.ProcessingStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Bridges Kafka -> WebSocket. Every progress event is pushed to both the
 * global topic (for the admin live view) and a per-user topic (so a user only
 * sees their own files). Completion / dead-letter events additionally trigger
 * a discrete "alert" message.
 */
@Component
public class ProgressBridge {

    private static final Logger log = LoggerFactory.getLogger(ProgressBridge.class);

    private final SimpMessagingTemplate ws;
    private final Counter pushed;

    public ProgressBridge(SimpMessagingTemplate ws, MeterRegistry registry) {
        this.ws = ws;
        this.pushed = Counter.builder("dfpp.notifications.pushed.total")
                .description("Progress events pushed to WebSocket clients")
                .register(registry);
    }

    @KafkaListener(topics = Topics.FILE_PROGRESS, groupId = "notification-fanout")
    public void onProgress(ProcessingProgressEvent ev) {
        ws.convertAndSend("/topic/progress", ev);
        ws.convertAndSend("/topic/progress/" + ev.ownerId(), ev);

        if (ev.status() == ProcessingStatus.COMPLETED
                || ev.status() == ProcessingStatus.DEAD_LETTER
                || ev.status() == ProcessingStatus.FAILED) {
            ws.convertAndSend("/topic/alerts/" + ev.ownerId(), ev);
        }
        pushed.increment();
        log.debug("Fanned out jobId={} status={} progress={}",
                ev.jobId(), ev.status(), ev.progress());
    }
}
