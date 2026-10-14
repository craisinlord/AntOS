package com.craisinlord.antos.content.network;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ComputerRequestLimiter {
    private final double capacity;
    private final double refillPerSecond;
    private final Map<UUID, Bucket> buckets = new HashMap<>();

    ComputerRequestLimiter(double capacity, double refillPerSecond) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
    }

    synchronized boolean tryAcquire(UUID player, int cost) {
        if (cost <= 0) return true;
        Bucket bucket = refill(player);
        if (bucket.tokens < cost) return false;
        bucket.tokens -= cost;
        return true;
    }

    synchronized boolean acquireWithDebt(UUID player, int cost) {
        if (cost <= 0) return true;
        Bucket bucket = refill(player);
        if (bucket.tokens <= -capacity) return false;
        bucket.tokens -= cost;
        return true;
    }

    private Bucket refill(UUID player) {
        long now = System.nanoTime();
        Bucket bucket = buckets.computeIfAbsent(player, ignored -> new Bucket(capacity, now));
        double elapsed = Math.max(0L, now - bucket.updated) / 1_000_000_000.0D;
        bucket.tokens = Math.min(capacity, bucket.tokens + elapsed * refillPerSecond);
        bucket.updated = now;
        return bucket;
    }

    synchronized void forget(UUID player) {
        buckets.remove(player);
    }

    synchronized void retain(Collection<UUID> online) {
        buckets.keySet().retainAll(online);
    }

    private static final class Bucket {
        private double tokens;
        private long updated;

        private Bucket(double tokens, long updated) {
            this.tokens = tokens;
            this.updated = updated;
        }
    }
}
