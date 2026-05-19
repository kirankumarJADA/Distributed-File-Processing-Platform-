package com.dfpp.common.model;

/** Lifecycle states of a file processing job. */
public enum ProcessingStatus {
    PENDING,
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    DEAD_LETTER
}
