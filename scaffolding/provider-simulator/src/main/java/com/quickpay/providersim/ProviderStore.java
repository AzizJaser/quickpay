package com.quickpay.providersim;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The brain of the mock provider (dev/test only; all state in memory, lost on restart).
 *
 * Two jobs:
 *  1. IDEMPOTENCY BY REFERENCE — once a reference has been accepted we remember it, so a
 *     retry with the same reference replays the same result instead of sending twice.
 *     Real SMS providers do this; without it, the notification service's retries would
 *     spam the customer.
 *  2. FAULT INJECTION — force SENT / FAILED / SERVER_ERROR / TIMEOUT, or leave it NORMAL
 *     and let the configured failure-rate decide. This is what makes the retry path real.
 */
@Component
public class ProviderStore {

    private final Map<String, SendResult> sentByReference = new ConcurrentHashMap<>();

    private volatile Outcome forcedMode = Outcome.NORMAL;

    private final double failureRate;
    private volatile long delayMs;
    private final long timeoutSleepMs;

    public ProviderStore(@Value("${provider.failure-rate:0.3}") double failureRate,
                         @Value("${provider.default-delay-ms:0}") long defaultDelayMs,
                         @Value("${provider.timeout-sleep-ms:5000}") long timeoutSleepMs) {
        this.failureRate = failureRate;
        this.delayMs = defaultDelayMs;
        this.timeoutSleepMs = timeoutSleepMs;
    }

    /**
     * Attempt a send.
     *  - returns a SENT result (and remembers the reference, so retries replay it)
     *  - returns a FAILED result for a definite refusal (bad number, blocked, etc.)
     *  - throws 503 for SERVER_ERROR — the caller cannot tell whether it was sent
     *  - for TIMEOUT, sleeps past the caller's read timeout and THEN accepts
     */
    public SendResult send(String channel, SendRequest req) {
        SendResult existing = sentByReference.get(key(channel, req.reference()));
        if (existing != null) {
            return existing;   // idempotent replay — do not send twice
        }

        sleep(delayMs);

        Outcome outcome = (forcedMode == Outcome.NORMAL)
                ? (ThreadLocalRandom.current().nextDouble() < failureRate ? Outcome.FAILED : Outcome.SENT)
                : forcedMode;

        switch (outcome) {
            case SERVER_ERROR ->
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            channel + " provider temporarily unavailable");
            case TIMEOUT -> {
                sleep(timeoutSleepMs);
                return accept(channel, req);
            }
            case FAILED -> {
                return new SendResult(req.reference(), req.destination(), "FAILED", null,
                        channel + " rejected by provider");
            }
            default -> {
                return accept(channel, req);
            }
        }
    }

    // ---- control panel ----

    public void setMode(Outcome mode, Long delayMsOverride) {
        this.forcedMode = (mode == null) ? Outcome.NORMAL : mode;
        if (delayMsOverride != null) {
            this.delayMs = delayMsOverride;
        }
    }

    public Map<String, Object> state() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("forcedMode", forcedMode);
        m.put("delayMs", delayMs);
        m.put("failureRate", failureRate);
        m.put("sent", sentByReference);
        return m;
    }

    public void reset() {
        sentByReference.clear();
        forcedMode = Outcome.NORMAL;
    }

    // ---- helpers ----

    private SendResult accept(String channel, SendRequest req) {
        SendResult result = new SendResult(req.reference(), req.destination(), "SENT",
                channel.toUpperCase() + "-" + UUID.randomUUID().toString().replace("-", ""), null);
        sentByReference.put(key(channel, req.reference()), result);
        return result;
    }

    /** SMS and email are separate channels, so the same reference may be sent once on each. */
    private static String key(String channel, String reference) {
        return channel + ":" + reference;
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
