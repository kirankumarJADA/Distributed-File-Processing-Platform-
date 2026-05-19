package com.dfpp.upload.file;

import com.dfpp.common.event.FileUploadedEvent;
import com.dfpp.common.security.AuthenticatedUser;
import com.dfpp.upload.kafka.FileEventPublisher;
import com.dfpp.upload.web.ApiException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

    private final FileMetadataRepository repo;
    private final FileValidator validator;
    private final FileEventPublisher publisher;
    private final Path storageRoot;
    private final Counter uploadCounter;

    public FileUploadService(FileMetadataRepository repo,
                             FileValidator validator,
                             FileEventPublisher publisher,
                             MeterRegistry meterRegistry,
                             @Value("${app.upload.storage-dir:/data/uploads}") String storageDir)
            throws IOException {
        this.repo = repo;
        this.validator = validator;
        this.publisher = publisher;
        this.storageRoot = Path.of(storageDir);
        Files.createDirectories(this.storageRoot);
        this.uploadCounter = Counter.builder("dfpp.uploads.total")
                .description("Total number of accepted file uploads")
                .register(meterRegistry);
    }

    @Transactional
    public FileDtos.UploadResponse upload(AuthenticatedUser user, MultipartFile file) {
        FileValidator.Validated validated = validator.validate(file);

        String jobId = UUID.randomUUID().toString();
        String safeName = sanitize(file.getOriginalFilename());
        Path userDir = storageRoot.resolve(String.valueOf(user.userId()));
        Path target = userDir.resolve(jobId + "__" + safeName);

        String sha256;
        try {
            Files.createDirectories(userDir);
            sha256 = streamToDiskWithHash(file, target);
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("Failed to persist upload for user {}", user.username(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store uploaded file");
        }

        FileMetadata meta = FileMetadata.create(
                jobId, user.userId(), user.username(), safeName,
                target.toAbsolutePath().toString(),
                validated.normalizedContentType(),
                validated.type().name(),
                file.getSize(), sha256);
        meta = repo.save(meta);

        publisher.publishUploaded(new FileUploadedEvent(
                jobId, meta.getId(), user.userId(), user.username(),
                safeName, meta.getStoragePath(), meta.getContentType(),
                meta.getSizeBytes(), meta.getFileType()));

        uploadCounter.increment();
        log.info("Accepted upload jobId={} file='{}' type={} size={}B by user={}",
                jobId, safeName, validated.type(), file.getSize(), user.username());

        return new FileDtos.UploadResponse(meta.getId(), jobId, meta.getStatus().name(),
                "File accepted and queued for distributed processing");
    }

    private String streamToDiskWithHash(MultipartFile file, Path target)
            throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream raw = file.getInputStream();
             DigestInputStream din = new DigestInputStream(raw, digest)) {
            Files.copy(din, target);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "upload.bin";
        }
        String base = name.replace("\\", "/");
        base = base.substring(base.lastIndexOf('/') + 1);
        return base.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    @Transactional(readOnly = true)
    public Page<FileDtos.FileView> history(AuthenticatedUser user, int page, int size) {
        return repo.findByOwnerIdOrderByCreatedAtDesc(
                        user.userId(), PageRequest.of(page, Math.min(size, 100)))
                .map(FileDtos.FileView::from);
    }

    @Transactional(readOnly = true)
    public FileDtos.FileView get(AuthenticatedUser user, Long id) {
        FileMetadata m = repo.findByIdAndOwnerId(id, user.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "File not found"));
        return FileDtos.FileView.from(m);
    }
}
