package com.dfpp.worker;

import com.dfpp.common.event.FileUploadedEvent;
import com.dfpp.worker.processor.CsvProcessor;
import com.dfpp.worker.processor.ZipProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessorTest {

    private FileUploadedEvent event(String path, String type) {
        return new FileUploadedEvent("job-1", 1L, 1L, "demo", "f", path,
                "application/octet-stream", 0, type);
    }

    @Test
    void csvProcessorCountsRows(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("data.csv");
        Files.writeString(csv, "id,name\n1,alice\n2,bob\n3,\n");
        String json = new CsvProcessor().process(event(csv.toString(), "CSV"), p -> {});
        assertTrue(json.contains("\"rowCount\":3"));
        assertTrue(json.contains("\"columnCount\":2"));
    }

    @Test
    void zipProcessorEnumeratesEntries(@TempDir Path dir) throws Exception {
        Path zip = dir.resolve("a.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("hello.txt"));
            zos.write("hello world".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("nested/data.txt"));
            zos.write("more data".getBytes());
            zos.closeEntry();
        }
        String json = new ZipProcessor().process(event(zip.toString(), "ZIP"), p -> {});
        assertTrue(json.contains("\"entryCount\":2"));
    }
}
