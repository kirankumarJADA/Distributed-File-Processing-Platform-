package com.dfpp.common.event;

/** Single source of truth for Kafka topic names across services. */
public final class Topics {

    private Topics() {
    }

    /** A new file has been accepted and is ready to be processed. */
    public static final String FILE_UPLOADED = "file.uploaded";

    /** Progress / state-change events emitted by workers during processing. */
    public static final String FILE_PROGRESS = "file.processing.progress";

    /** Dead-letter topic for jobs that exhausted all retry attempts. */
    public static final String FILE_DLQ = "file.processing.dlq";
}
