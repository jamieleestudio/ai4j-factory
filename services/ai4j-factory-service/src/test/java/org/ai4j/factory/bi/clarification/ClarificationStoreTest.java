package org.ai4j.factory.bi.clarification;

import org.junit.jupiter.api.Test;
import org.ai4j.factory.sse.ClarificationOption;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClarificationStoreTest {

    private PendingClarification pending(String question) {
        return new PendingClarification(
                question, "question_unclear",
                List.of(new ClarificationOption(question, question, "desc")),
                null, java.time.Instant.now()
        );
    }

    @Test
    void putAndGetReturnsPendingClarification() {
        ClarificationStore store = new ClarificationStore(Duration.ofMinutes(5), 100);
        store.put("session-1", pending("question 1"));

        assertTrue(store.get("session-1").isPresent());
        assertEquals("question 1", store.get("session-1").get().originalQuestion());
    }

    @Test
    void getReturnsEmptyForUnknownSessionId() {
        ClarificationStore store = new ClarificationStore(Duration.ofMinutes(5), 100);
        assertTrue(store.get("nonexistent").isEmpty());
    }

    @Test
    void expiresEntryAfterTtl() throws InterruptedException {
        ClarificationStore store = new ClarificationStore(Duration.ofMillis(50), 100);
        store.put("session-1", pending("question 1"));
        assertTrue(store.get("session-1").isPresent());

        Thread.sleep(100);

        assertTrue(store.get("session-1").isEmpty());
    }

    @Test
    void evictsOldestWhenCapacityExceeded() throws InterruptedException {
        ClarificationStore store = new ClarificationStore(Duration.ofMinutes(5), 2);
        store.put("session-1", pending("question 1"));
        Thread.sleep(10);
        store.put("session-2", pending("question 2"));
        Thread.sleep(10);
        store.put("session-3", pending("question 3"));

        // session-1 was the oldest, should be evicted
        assertTrue(store.get("session-1").isEmpty());
        assertTrue(store.get("session-2").isPresent());
        assertTrue(store.get("session-3").isPresent());
    }
}
