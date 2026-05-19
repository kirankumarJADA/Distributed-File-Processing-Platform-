package com.dfpp.upload.file;

import com.dfpp.common.model.ProcessingStatus;

import java.time.Instant;

public class FileDtos {

    public record FileView(
            Long id,
            String jobId,
            String originalName,
            String fileType,
            String contentType,
            long sizeBytes,
            ProcessingStatus status,
            int progress,
            String statusMessage,
            String resultJson,
            Instant createdAt,
            Instant updatedAt) {

        public static FileView from(FileMetadata m) {
            return new FileView(m.getId(), m.getJobId(), m.getOriginalName(),
                    m.getFileType(), m.getContentType(), m.getSizeBytes(),
                    m.getStatus(), m.getProgress(), m.getStatusMessage(),
                    m.getResultJson(), m.getCreatedAt(), m.getUpdatedAt());
        }
    }

    public record UploadResponse(Long fileId, String jobId, String status, String message) {
    }
}
