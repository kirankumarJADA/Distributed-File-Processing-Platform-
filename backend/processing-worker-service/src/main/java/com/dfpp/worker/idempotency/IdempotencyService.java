package com.dfpp.worker.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Guarantees exactly-once <em>effect</em> even under at-least-once Kafka
 * delivery. Each job id is recorded in Redis with a SETNX-style operation:
 *
 * <ul>
 *   <li>{@link #tryBegin} - atomically claims a job; returns false if it was
 *       already claimed (duplicate delivery / rebalance replay).</li>
 *   <li>{@link #markCompleted} - records terminal success so future replays
 *       are skipped permanently (kept for 7 days).</li>
 *   <li>{@link #release} - frees an in-flight claim after a retryable failure
 *       so another worker / attempt can pick it up.</li>
 * </ul>
 */
@Service
public class IdempotencyService {

    private static final String INFLIGHT = "dfpp:job:inflight:";
    private static final String DONE = "dfpp:job:done:";
    private static final Duration INFLIGHT_TTL = Duration.ofMinutes(15);
    private static final Duration DONE_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;

    public IdempotencyService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean alreadyCompleted(String jobId) {
        return Boolean.TRUE.equals(redis.hasKey(DONE + jobId));
    }

    public boolean tryBegin(String jobId, String workerId) {
        if (alreadyCompleted(jobId)) {
            return false;
        }
        Boolean claimed = redis.opsForValue()
                .setIfAbsent(INFLIGHT + jobId, workerId, INFLIGHT_TTL);
        return Boolean.TRUE.equals(claimed);
    }

    public void markCompleted(String jobId) {
        redis.opsForValue().set(DONE + jobId, "1", DONE_TTL);
        redis.delete(INFLIGHT + jobId);
    }

    public void release(String jobId) {
        redis.delete(INFLIGHT + jobId);
    }
}
