package com.dfpp.notify;

import com.dfpp.common.event.ProcessingProgressEvent;
import com.dfpp.common.model.ProcessingStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressEventTest {

    @Test
    void eventCarriesRoutingInfo() {
        var ev = new ProcessingProgressEvent("job-9", 3L, 7L, "demo",
                ProcessingStatus.COMPLETED, 100, "worker-1", 1,
                "done", "{}", Instant.now());
        assertEquals(7L, ev.ownerId());
        assertEquals(ProcessingStatus.COMPLETED, ev.status());
        assertEquals(100, ev.progress());
    }
}
