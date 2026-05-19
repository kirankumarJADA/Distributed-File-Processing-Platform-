package com.dfpp.common.event;

import com.dfpp.common.model.ProcessingStatus;

import java.io.Serializable;
import java.time.Instant;

/**
 * Emitted by workers onto {@link Topics#FILE_PROGRESS} and fanned out to
 * browsers over WebSocket by the notification service.
 *
 * @param jobId      processing job id
 * @param fileId     file metadata id
 * @param ownerId    uploader id (used to route the WS message to the right user)
 * @param ownerName  uploader username
 * @param status     current lifecycle status
 * @param progress   completion percentage 0-100
 * @param worker     identifier of the worker instance that produced this update
 * @param attempt    current attempt number (1-based)
 * @param message    human-readable status / result / error summary
 * @param resultJson processing result as JSON (null until COMPLETED)
 * @param timestamp  event time
 */
public record ProcessingProgressEvent(
        String jobId,
        Long fileId,
        Long ownerId,
        String ownerName,
        ProcessingStatus status,
        int progress,
        String worker,
        int attempt,
        String message,
        String resultJson,
        Instant timestamp
) implements Serializable {

    public static ProcessingProgressEvent of(FileUploadedEvent src, ProcessingStatus status,
                                              int progress, String worker, int attempt,
                                              String message, String resultJson) {
        return new ProcessingProgressEvent(
                src.jobId(), src.fileId(), src.ownerId(), src.ownerName(),
                status, progress, worker, attempt, message, resultJson, Instant.now());
    }
}
