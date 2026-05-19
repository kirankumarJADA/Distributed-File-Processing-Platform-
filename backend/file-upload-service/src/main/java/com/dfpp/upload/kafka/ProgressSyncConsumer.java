package com.dfpp.upload.kafka;

import com.dfpp.common.event.ProcessingProgressEvent;
import com.dfpp.common.event.Topics;
import com.dfpp.common.model.ProcessingStatus;
import com.dfpp.upload.file.FileMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The upload service owns the {@code file_metadata} table, so it also listens
 * to progress events and keeps the persisted status/progress in sync. This is
 * what powers "upload history" and the per-file status REST endpoints.
 */
@Component
public class ProgressSyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProgressSyncConsumer.class);
    private final FileMetadataRepository repo;

    public ProgressSyncConsumer(FileMetadataRepository repo) {
        this.repo = repo;
    }

    @KafkaListener(topics = Topics.FILE_PROGRESS, groupId = "upload-progress-sync")
    @Transactional
    public void onProgress(ProcessingProgressEvent ev) {
        repo.findByJobId(ev.jobId()).ifPresentOrElse(meta -> {
            // Monotonic guard: never move a COMPLETED job backwards.
            if (meta.getStatus() == ProcessingStatus.COMPLETED) {
                return;
            }
            meta.setStatus(ev.status());
            meta.setProgress(ev.progress());
            meta.setStatusMessage(ev.message());
            if (ev.resultJson() != null) {
                meta.setResultJson(ev.resultJson());
            }
            repo.save(meta);
        }, () -> log.warn("Progress event for unknown jobId={}", ev.jobId()));
    }
}
