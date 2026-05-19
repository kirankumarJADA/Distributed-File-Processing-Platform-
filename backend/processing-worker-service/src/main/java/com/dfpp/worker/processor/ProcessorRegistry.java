package com.dfpp.worker.processor;

import com.dfpp.common.event.FileUploadedEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.IntConsumer;

/** Routes an upload event to the first {@link FileProcessor} that supports it. */
@Component
public class ProcessorRegistry {

    private final List<FileProcessor> processors;

    public ProcessorRegistry(List<FileProcessor> processors) {
        this.processors = processors;
    }

    public String dispatch(FileUploadedEvent event, IntConsumer progress) throws Exception {
        FileProcessor processor = processors.stream()
                .filter(p -> p.supports(event.fileType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No processor for file type: " + event.fileType()));
        return processor.process(event, progress);
    }
}
