package com.dfpp.upload.file;

import com.dfpp.common.model.ProcessingStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "file_metadata", indexes = {
        @Index(name = "idx_file_owner", columnList = "ownerId"),
        @Index(name = "idx_file_jobid", columnList = "jobId", unique = true)
})
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String jobId;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 64)
    private String ownerName;

    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private String storagePath;

    @Column(nullable = false, length = 128)
    private String contentType;

    @Column(nullable = false, length = 16)
    private String fileType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ProcessingStatus status = ProcessingStatus.PENDING;

    @Column(nullable = false)
    private int progress = 0;

    @Column(length = 1024)
    private String statusMessage;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String resultJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected FileMetadata() {
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public static FileMetadata create(String jobId, Long ownerId, String ownerName,
                                      String originalName, String storagePath,
                                      String contentType, String fileType,
                                      long sizeBytes, String sha256) {
        FileMetadata m = new FileMetadata();
        m.jobId = jobId;
        m.ownerId = ownerId;
        m.ownerName = ownerName;
        m.originalName = originalName;
        m.storagePath = storagePath;
        m.contentType = contentType;
        m.fileType = fileType;
        m.sizeBytes = sizeBytes;
        m.sha256 = sha256;
        m.status = ProcessingStatus.QUEUED;
        m.statusMessage = "Uploaded and queued for processing";
        return m;
    }

    public Long getId() {
        return id;
    }

    public String getJobId() {
        return jobId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFileType() {
        return fileType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public ProcessingStatus getStatus() {
        return status;
    }

    public void setStatus(ProcessingStatus status) {
        this.status = status;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
