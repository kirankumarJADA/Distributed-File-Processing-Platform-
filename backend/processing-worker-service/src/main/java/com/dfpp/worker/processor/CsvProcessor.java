package com.dfpp.worker.processor;

import com.dfpp.common.event.FileUploadedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/** Real CSV analysis: header, row count, column count, per-column null counts. */
@Component
public class CsvProcessor implements FileProcessor {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean supports(String fileType) {
        return "CSV".equalsIgnoreCase(fileType);
    }

    @Override
    public String process(FileUploadedEvent event, IntConsumer progress) throws Exception {
        Path path = Path.of(event.storagePath());
        progress.accept(10);

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        long rows = 0;
        List<String> headers;
        Map<String, Long> emptyCounts = new LinkedHashMap<>();

        try (Reader reader = Files.newBufferedReader(path);
             CSVParser parser = format.parse(reader)) {
            headers = new ArrayList<>(parser.getHeaderNames());
            headers.forEach(h -> emptyCounts.put(h, 0L));

            for (CSVRecord rec : parser) {
                rows++;
                for (String h : headers) {
                    String v = rec.isMapped(h) ? rec.get(h) : null;
                    if (v == null || v.isBlank()) {
                        emptyCounts.merge(h, 1L, Long::sum);
                    }
                }
                if (rows % 5000 == 0) {
                    progress.accept(Math.min(90, 10 + (int) (rows / 1000)));
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "CSV");
        result.put("rowCount", rows);
        result.put("columnCount", headers.size());
        result.put("headers", headers);
        result.put("emptyCellsPerColumn", emptyCounts);
        progress.accept(100);
        return mapper.writeValueAsString(result);
    }
}
