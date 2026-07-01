package org.ai4j.factory.bi.clarification;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class ClarificationStore {

    private static final Logger log = LoggerFactory.getLogger(ClarificationStore.class);
    static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    static final int DEFAULT_MAX_CAPACITY = 1000;

    private final Duration ttl;
    private final int maxCapacity;
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    private record Entry(PendingClarification clarification, Instant expiresAt) {}

    public ClarificationStore() {
        this(DEFAULT_TTL, DEFAULT_MAX_CAPACITY);
    }

    ClarificationStore(Duration ttl, int maxCapacity) {
        this.ttl = ttl;
        this.maxCapacity = maxCapacity;
    }

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "clarification-store-cleanup");
            t.setDaemon(true);
            return t;
        });
        long cleanupInterval = Math.max(ttl.toMillis() / 2, 1000);
        scheduler.scheduleAtFixedRate(this::cleanup, cleanupInterval, cleanupInterval, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public void put(String sessionId, PendingClarification clarification) {
        if (store.size() >= maxCapacity) {
            store.entrySet().stream()
                    .min(Comparator.comparing(e -> e.getValue().expiresAt))
                    .ifPresent(e -> store.remove(e.getKey()));
        }
        store.put(sessionId, new Entry(clarification, Instant.now().plus(ttl)));
    }

    public Optional<PendingClarification> get(String sessionId) {
        Entry entry = store.get(sessionId);
        if (entry == null) return Optional.empty();
        if (Instant.now().isAfter(entry.expiresAt)) {
            store.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(entry.clarification());
    }

    private void cleanup() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt));
    }
}
