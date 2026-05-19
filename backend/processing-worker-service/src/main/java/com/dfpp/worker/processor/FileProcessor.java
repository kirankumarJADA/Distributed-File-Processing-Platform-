package com.dfpp.worker.processor;

import com.dfpp.common.event.FileUploadedEvent;

import java.util.function.IntConsumer;

/**
 * A unit of real work for a given file type. Implementations must be:
 * <ul>
 *   <li><b>Deterministic</b> for a given input (so retries are safe).</li>
 *   <li><b>Pure</b> with respect to the source file (read-only).</li>
 *   <li>Able to report incremental progress via the supplied callback.</li>
 * </ul>
 */
public interface FileProcessor {

    /** @return true if this processor handles the given normalised file type. */
    boolean supports(String fileType);

    /**
     * Performs the real processing.
     *
     * @param event           the upload event (contains the storage path)
     * @param progressReporter sink for 0-100 progress updates
     * @return a JSON string describing the processing result
     * @throws Exception if processing fails (triggers the retry pipeline)
     */
    String process(FileUploadedEvent event, IntConsumer progressReporter) throws Exception;
}
