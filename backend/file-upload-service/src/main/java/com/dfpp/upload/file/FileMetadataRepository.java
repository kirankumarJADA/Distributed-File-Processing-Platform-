package com.dfpp.upload.file;

import com.dfpp.common.model.ProcessingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    Page<FileMetadata> findByOwnerIdOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    Optional<FileMetadata> findByJobId(String jobId);

    Optional<FileMetadata> findByIdAndOwnerId(Long id, Long ownerId);

    long countByStatus(ProcessingStatus status);

    List<FileMetadata> findTop20ByOrderByUpdatedAtDesc();
}
