package com.dfpp.upload.file;

import com.dfpp.upload.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Defence-in-depth upload validation:
 *
 * <ol>
 *   <li>Reject empty / oversized files.</li>
 *   <li>Verify the real content via <em>magic byte</em> signatures rather than
 *       trusting the client-supplied filename or MIME type.</li>
 *   <li>Heuristic "virus-safe" scan: reject Windows PE executables, ELF binaries,
 *       shell-script shebangs and obvious script injection markers. This is a
 *       deterministic, dependency-free guard suitable for a demo; in production
 *       it would be complemented by a ClamAV sidecar (the architecture leaves a
 *       seam for that — see {@link #heuristicMalwareScan}).</li>
 * </ol>
 */
@Component
public class FileValidator {

    private static final Logger log = LoggerFactory.getLogger(FileValidator.class);

    public enum FileType {PDF, IMAGE, CSV, ZIP}

    public record Validated(FileType type, String normalizedContentType) {
    }

    private final long maxBytes;

    public FileValidator(@Value("${app.upload.max-bytes:52428800}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public Validated validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Uploaded file is empty");
        }
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "File exceeds the maximum allowed size of " + (maxBytes / (1024 * 1024)) + " MB");
        }

        byte[] head;
        try {
            head = readHead(file, 512);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unable to read uploaded file");
        }

        FileType type = sniff(head, file.getOriginalFilename());
        if (type == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported file type. Allowed: PDF, PNG/JPEG/GIF images, CSV, ZIP");
        }

        heuristicMalwareScan(head, file.getOriginalFilename());

        return new Validated(type, contentTypeFor(type));
    }

    private byte[] readHead(MultipartFile file, int n) throws IOException {
        byte[] buf = new byte[n];
        try (var in = file.getInputStream()) {
            int read = in.readNBytes(buf, 0, n);
            return Arrays.copyOf(buf, Math.max(read, 0));
        }
    }

    private FileType sniff(byte[] h, String name) {
        if (startsWith(h, new byte[]{0x25, 0x50, 0x44, 0x46})) { // %PDF
            return FileType.PDF;
        }
        if (startsWith(h, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47})       // PNG
                || startsWith(h, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}) // JPEG
                || startsWith(h, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                || startsWith(h, "GIF89a".getBytes(StandardCharsets.US_ASCII))) {
            return FileType.IMAGE;
        }
        // ZIP local file header. NOTE: order matters - check ZIP before CSV
        if (startsWith(h, new byte[]{0x50, 0x4B, 0x03, 0x04})
                || startsWith(h, new byte[]{0x50, 0x4B, 0x05, 0x06})) {
            return FileType.ZIP;
        }
        // CSV has no magic number: fall back to extension + printable-text check.
        if (name != null && name.toLowerCase().endsWith(".csv") && looksLikeText(h)) {
            return FileType.CSV;
        }
        return null;
    }

    /**
     * Deterministic malware heuristics. Rejects native executables and script
     * payloads that should never appear inside an uploaded data file.
     */
    private void heuristicMalwareScan(byte[] h, String name) {
        // Windows PE ("MZ"), ELF (0x7F 'ELF'), Mach-O, Java class
        byte[][] executableSignatures = {
                {0x4D, 0x5A},                         // MZ - DOS/PE
                {0x7F, 0x45, 0x4C, 0x46},             // ELF
                {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}, // Mach-O / class
                {(byte) 0xFE, (byte) 0xED, (byte) 0xFA, (byte) 0xCE}  // Mach-O
        };
        for (byte[] sig : executableSignatures) {
            if (startsWith(h, sig)) {
                log.warn("Rejected upload '{}' - executable signature detected", name);
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "File rejected by malware heuristics (executable content)");
            }
        }
        String prefix = new String(h, StandardCharsets.US_ASCII).toLowerCase();
        List<String> blockedMarkers = List.of("#!/bin/sh", "#!/bin/bash",
                "#!/usr/bin/env", "<?php", "<script", "powershell -enc");
        for (String marker : blockedMarkers) {
            if (prefix.contains(marker)) {
                log.warn("Rejected upload '{}' - script marker '{}' detected", name, marker);
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "File rejected by malware heuristics (script content)");
            }
        }
    }

    private boolean looksLikeText(byte[] h) {
        int printable = 0;
        for (byte b : h) {
            int v = b & 0xFF;
            if (v == 9 || v == 10 || v == 13 || (v >= 32 && v < 127)) {
                printable++;
            }
        }
        return h.length == 0 || (printable * 100 / h.length) >= 90;
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private String contentTypeFor(FileType t) {
        return switch (t) {
            case PDF -> "application/pdf";
            case IMAGE -> "image/*";
            case CSV -> "text/csv";
            case ZIP -> "application/zip";
        };
    }
}
