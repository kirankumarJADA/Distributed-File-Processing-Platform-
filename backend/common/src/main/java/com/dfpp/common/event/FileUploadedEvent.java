package com.dfpp.common.event;

import java.io.Serializable;

/**
 * Emitted by the upload service onto {@link Topics#FILE_UPLOADED}.
 *
 * @param jobId        unique processing job id (also the Kafka message key, used for idempotency)
 * @param fileId       persisted file metadata id
 * @param ownerId      id of the user that uploaded the file
 * @param ownerName    username of the uploader
 * @param originalName original client filename
 * @param storagePath  absolute path on the shared volume where the bytes live
 * @param contentType  detected MIME type
 * @param sizeBytes    file size in bytes
 * @param fileType     normalised type: PDF / IMAGE / CSV / ZIP
 */
public record FileUploadedEvent(
        String jobId,
        Long fileId,
        Long ownerId,
        String ownerName,
        String originalName,
        String storagePath,
        String contentType,
        long sizeBytes,
        String fileType
) implements Serializable {
}
