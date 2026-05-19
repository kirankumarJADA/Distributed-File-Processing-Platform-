package com.dfpp.upload.file;

import com.dfpp.upload.web.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/files")
@Tag(name = "Files", description = "Upload files and inspect processing status / history")
@SecurityRequirement(name = "bearerAuth")
public class FileController {

    private final FileUploadService service;

    public FileController(FileUploadService service) {
        this.service = service;
    }

    @PostMapping(consumes = "multipart/form-data")
    @Operation(summary = "Upload a PDF / image / CSV / ZIP for asynchronous processing")
    public FileDtos.UploadResponse upload(@RequestParam("file") MultipartFile file) {
        return service.upload(CurrentUser.require(), file);
    }

    @GetMapping
    @Operation(summary = "Paginated upload history for the authenticated user")
    public List<FileDtos.FileView> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.history(CurrentUser.require(), page, size).getContent();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single file's metadata and current processing status")
    public FileDtos.FileView get(@PathVariable Long id) {
        return service.get(CurrentUser.require(), id);
    }
}
