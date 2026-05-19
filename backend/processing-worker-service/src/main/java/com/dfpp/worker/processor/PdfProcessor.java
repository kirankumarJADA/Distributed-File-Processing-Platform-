package com.dfpp.worker.processor;

import com.dfpp.common.event.FileUploadedEvent;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Real PDF analysis: page count, metadata, extracted text length & word count. */
@Component
public class PdfProcessor implements FileProcessor {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean supports(String fileType) {
        return "PDF".equalsIgnoreCase(fileType);
    }

    @Override
    public String process(FileUploadedEvent event, IntConsumer progress) throws Exception {
        progress.accept(10);
        try (PDDocument doc = Loader.loadPDF(new File(event.storagePath()))) {
            int pages = doc.getNumberOfPages();
            progress.accept(40);

            PDDocumentInformation info = doc.getDocumentInformation();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            progress.accept(85);

            int words = text.isBlank() ? 0 : text.trim().split("\\s+").length;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "PDF");
            result.put("pageCount", pages);
            result.put("encrypted", doc.isEncrypted());
            result.put("title", safe(info != null ? info.getTitle() : null));
            result.put("author", safe(info != null ? info.getAuthor() : null));
            result.put("producer", safe(info != null ? info.getProducer() : null));
            result.put("characterCount", text.length());
            result.put("wordCount", words);
            progress.accept(100);
            return mapper.writeValueAsString(result);
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
