package com.dfpp.worker.processor;

import com.dfpp.common.event.FileUploadedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Real ZIP analysis: enumerate entries, total compressed/uncompressed size,
 * and detect a potential zip-bomb via compression ratio.
 */
@Component
public class ZipProcessor implements FileProcessor {

    private static final long MAX_TOTAL_UNCOMPRESSED = 1_073_741_824L; // 1 GiB
    private static final int MAX_ENTRIES = 10_000;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean supports(String fileType) {
        return "ZIP".equalsIgnoreCase(fileType);
    }

    @Override
    public String process(FileUploadedEvent event, IntConsumer progress) throws Exception {
        Path path = Path.of(event.storagePath());
        long compressedTotal = Files.size(path);
        progress.accept(10);

        long uncompressedTotal = 0;
        int entries = 0;
        List<Map<String, Object>> sample = new ArrayList<>();

        byte[] buf = new byte[8192];
        try (InputStream fis = Files.newInputStream(path);
             ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ENTRIES) {
                    throw new IllegalStateException("ZIP exceeds maximum entry count (possible zip bomb)");
                }
                long entrySize = 0;
                int n;
                while ((n = zis.read(buf)) != -1) {
                    entrySize += n;
                    uncompressedTotal += n;
                    if (uncompressedTotal > MAX_TOTAL_UNCOMPRESSED) {
                        throw new IllegalStateException(
                                "ZIP uncompressed size exceeds 1 GiB (possible zip bomb)");
                    }
                }
                if (sample.size() < 50) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getName());
                    m.put("directory", e.isDirectory());
                    m.put("uncompressedBytes", entrySize);
                    sample.add(m);
                }
                zis.closeEntry();
                if (entries % 100 == 0) {
                    progress.accept(Math.min(90, 10 + entries / 50));
                }
            }
        }

        double ratio = compressedTotal == 0 ? 0
                : Math.round((double) uncompressedTotal / compressedTotal * 100) / 100.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "ZIP");
        result.put("entryCount", entries);
        result.put("compressedBytes", compressedTotal);
        result.put("uncompressedBytes", uncompressedTotal);
        result.put("compressionRatio", ratio);
        result.put("entriesSample", sample);
        progress.accept(100);
        return mapper.writeValueAsString(result);
    }
}
