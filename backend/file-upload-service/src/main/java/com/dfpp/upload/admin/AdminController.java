package com.dfpp.upload.admin;

import com.dfpp.common.model.ProcessingStatus;
import com.dfpp.upload.file.FileDtos;
import com.dfpp.upload.file.FileMetadataRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Platform-wide statistics (ROLE_ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final FileMetadataRepository repo;

    public AdminController(FileMetadataRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/stats")
    @Operation(summary = "Aggregate counts by processing status")
    public Map<String, Object> stats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ProcessingStatus s : ProcessingStatus.values()) {
            byStatus.put(s.name(), repo.countByStatus(s));
        }
        return Map.of(
                "totalFiles", repo.count(),
                "byStatus", byStatus);
    }

    @GetMapping("/recent")
    @Operation(summary = "Most recently updated files across all users")
    public List<FileDtos.FileView> recent() {
        return repo.findTop20ByOrderByUpdatedAtDesc().stream()
                .map(FileDtos.FileView::from)
                .toList();
    }
}
