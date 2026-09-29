package io.github.ilic02.coderunner.queue;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    private static final int BUCKET_CAPACITY = 10;
    private static final double TOKENS_PER_MS = 10.0 / 60_000; // 10 tokena u minuti

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** Vraća true ako zahtev sme da prođe, false ako je limit dostignut. */
    public boolean allow(String clientId) {
        Bucket bucket = buckets.computeIfAbsent(clientId, id -> new Bucket());
        return bucket.tryConsume();
    }

    private static class Bucket {
        private double tokens = BUCKET_CAPACITY;
        private long lastRefill = System.currentTimeMillis();
        private final ReentrantLock lock = new ReentrantLock();

        boolean tryConsume() {
            lock.lock();
            try {
                long now = System.currentTimeMillis();
                double elapsed = now - lastRefill;
                tokens = Math.min(BUCKET_CAPACITY, tokens + elapsed * TOKENS_PER_MS);
                lastRefill = now;

                if (tokens >= 1) {
                    tokens -= 1;
                    return true;
                }
                return false;
            } finally {
                lock.unlock();
            }
        }
    }
}