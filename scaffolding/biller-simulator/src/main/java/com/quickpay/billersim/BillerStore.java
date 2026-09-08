package com.quickpay.billersim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The brain of the mock biller (dev/test only; all state in memory, lost on restart).
 *
 * Two jobs:
 *  1. IDEMPOTENCY BY REFERENCE — the contract that makes the bill service's retries safe.
 *     Once a client reference (ref A) reaches a definite outcome (PAID or FAILED) we store it;
 *     any later "pay" with the same reference replays the SAME result instead of paying again.
 *  2. FAULT INJECTION — a tester can force SUCCESS / FAIL / SERVER_ERROR / TIMEOUT (or leave it
 *     NORMAL, which uses the configured failure-rate) so every branch of the bill-payment flow
 *     can be exercised deterministically (predict-then-run).
 */
@Component
public class BillerStore {

    // ref A -> settled result. Presence means "this attempt definitely landed" (PAID or FAILED).
    private final Map<String, PaymentResult> settledByReference = new ConcurrentHashMap<>();

    private volatile Outcome forcedMode = Outcome.NORMAL;

    private final double failureRate;
    private volatile long delayMs;
    /**
     * How long TIMEOUT mode sleeps before deciding. Runtime-settable (S11): `delayMs` slows
     * BOTH pay() and inquire() — that is deliberate and correct (S02b: a biller under load is
     * slow on every endpoint), but it makes "slow to pay, fast to look up" impossible to
     * express. S11's first attempt used delayMs=90000 and accidentally blocked the sweep's
     * inquiries too, so `resolve` was never reached and the bill stranded at Reserved —
     * reproducing the S01/S04 mechanism instead of testing the settlement window.
     */
    private volatile long timeoutSleepMs;

    /**
     * THE OTHER HALF OF THE SETTLEMENT-WINDOW CONTRACT (added for sabotage scenario S11).
     *
     * S09 proved the window was never actually an agreement: `biller-settlement-window-ms`
     * lived only in the bill service's config, so this simulator settled PAID 30 s after the
     * platform had already reverted and refunded the customer. Every view was internally
     * consistent and the contradiction existed only between the two systems — a unilateral
     * timeout wearing a contract's clothes.
     *
     * This is that number, known to the biller too. Past it, the biller REFUSES to settle.
     *
     * ⚠️ The two sides start their clocks at different moments: the bill service measures
     * from `bill.created_at`, this simulator from the instant the pay request arrived —
     * strictly later. So the biller's window closes strictly LATER than the platform's, and
     * the gap between them is a sliver where the platform has reverted but the biller would
     * still settle. Agreeing on the NUMBER does not agree on the CLOCK.
     */
    private final long settlementWindowMs;

    private static final Logger logger = LoggerFactory.getLogger(BillerStore.class);

    public BillerStore(@Value("${biller.failure-rate:0.0}") double failureRate,
                       @Value("${biller.default-delay-ms:0}") long defaultDelayMs,
                       @Value("${biller.timeout-sleep-ms:65000}") long timeoutSleepMs,
                       @Value("${biller.settlement-window-ms:60000}") long settlementWindowMs) {
        this.failureRate = failureRate;
        this.delayMs = defaultDelayMs;
        this.timeoutSleepMs = timeoutSleepMs;
        this.settlementWindowMs = settlementWindowMs;
    }

    /**
     * Attempt to pay a bill.
     *  - returns a PAID or FAILED settlement (and stores it for idempotent replay)
     *  - throws ResponseStatusException(503) for SERVER_ERROR (nothing stored -> unknown outcome)
     *  - for TIMEOUT, sleeps past the caller's timeout and THEN settles PAID
     *    (models "it actually went through, you just didn't hear back").
     */
    public PaymentResult pay(PaymentRequest req) {
        // 0. Start the settlement-window clock the moment the request arrives (S11).
        long receivedAtNanos = System.nanoTime();

        // 1. Idempotency: same reference seen before -> replay the stored result, never pay twice.
        PaymentResult existing = settledByReference.get(req.reference());
        if (existing != null) {
            return existing;
        }

        // 2. Artificial latency (a slow biller).
        sleep(delayMs);

        // 3. Decide what happens.
        Outcome outcome = (forcedMode == Outcome.NORMAL)
                ? (ThreadLocalRandom.current().nextDouble() < failureRate ? Outcome.FAIL : Outcome.SUCCESS)
                : forcedMode;

        switch (outcome) {
            case SERVER_ERROR ->
                // 5xx: the caller does NOT know if it landed. We store NOTHING, so a later inquiry
                // returns NOT_FOUND and a retry re-processes. "Unknown, and actually not paid."
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "biller temporarily unavailable");
            case TIMEOUT -> {
                // Sleep past the caller's timeout, THEN settle it as paid. The caller gave up, but
                // the payment really went through — a later inquiry by reference will reveal PAID.
                sleep(timeoutSleepMs);
                return settle(req, "PAID", "BILR-" + hex(), receivedAtNanos);
            }
            case FAIL -> {
                return settle(req, "FAILED", null, receivedAtNanos);   // definite failure, money not taken
            }
            default -> {
                return settle(req, "PAID", "BILR-" + hex(), receivedAtNanos);
            }
        }
    }

    /**
     * Inquiry by the bill service's reference. A null result here means NOT_FOUND (never settled).
     *
     * Applies the SAME artificial latency as pay(). A biller under load is slow on every
     * endpoint, not just the write path — modelling "slow to pay, instant to look up" left
     * the reconciliation sweep immune to biller latency, which is not how an overloaded
     * system behaves. Found in sabotage scenario S02: the sweep could not be stressed at
     * all, because inquire was a bare map lookup.
     */
    public PaymentResult inquire(String reference) {
        sleep(delayMs);
        return settledByReference.get(reference);
    }

    /**
     * BULK inquiry — the reconciliation shape. One round trip for many references.
     *
     * The artificial latency is paid ONCE for the whole batch, not per reference. That is
     * the entire point: a real scheme reconciles with a settlement file or a bulk status
     * query, not N polls. Per-reference polling made the sweep's cost scale with the
     * backlog (measured in S02b: 5 bills x 2 s read timeout = 10 s per pass, growing
     * linearly). Batched, the cost stops scaling.
     *
     * Returns one entry per requested reference, in request order. A reference the biller
     * has never settled comes back with status NOT_FOUND rather than being omitted, so the
     * caller can tell "no record" apart from "not in the response".
     */
    public List<PaymentResult> inquireAll(List<String> references) {
        sleep(delayMs);
        List<PaymentResult> results = new ArrayList<>(references.size());
        for (String reference : references) {
            PaymentResult settled = settledByReference.get(reference);
            results.add(settled != null ? settled : new PaymentResult(
                    reference, null, "NOT_FOUND", null, "no settlement for this reference"));
        }
        return results;
    }

    // ---- control panel ----

    public void setMode(Outcome mode, Long delayMsOverride, Long timeoutSleepMsOverride) {
        this.forcedMode = (mode == null) ? Outcome.NORMAL : mode;
        if (delayMsOverride != null) {
            this.delayMs = delayMsOverride;
        }
        if (timeoutSleepMsOverride != null) {
            this.timeoutSleepMs = timeoutSleepMsOverride;
        }
    }

    public Map<String, Object> state() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("forcedMode", forcedMode);
        m.put("delayMs", delayMs);
        m.put("failureRate", failureRate);
        m.put("settlementWindowMs", settlementWindowMs);
        m.put("timeoutSleepMs", timeoutSleepMs);
        m.put("settledReferences", settledByReference);
        return m;
    }

    public void reset() {
        settledByReference.clear();
        forcedMode = Outcome.NORMAL;
    }

    // ---- helpers ----

    /**
     * Record a definite outcome — UNLESS the agreed settlement window has already passed.
     *
     * S11: past the window the biller refuses and stores NOTHING. Storing nothing is what
     * makes the refusal visible to the platform through machinery that already exists: a
     * later inquiry returns NOT_FOUND, and `BillService.resolve` already reverts a bill that
     * is NOT_FOUND and past its own window. No new status, no bill-service change.
     *
     * ⚠️ Honest limitation, deliberately left in place so the run can expose it: NOT_FOUND is
     * a LIE. The biller does have a record — it received this request and refused it. The
     * platform gets the right outcome for the wrong reason, and because nothing is stored the
     * refusal is NOT idempotent: a later pay() on the same reference starts a fresh window
     * and could still settle. The honest answer is an explicit EXPIRED status, which needs a
     * new term in both sides' vocabulary — that is S11b.
     */
    private PaymentResult settle(PaymentRequest req, String status, String billerTxnId, long receivedAtNanos) {
        long elapsedMs = (System.nanoTime() - receivedAtNanos) / 1_000_000L;
        if (elapsedMs > settlementWindowMs) {
            logger.warn("REFUSING to settle reference {} (bill {}): {} ms elapsed, agreed window is {} ms — storing nothing",
                    req.reference(), req.billNumber(), elapsedMs, settlementWindowMs);
            return new PaymentResult(req.reference(), req.billNumber(), "EXPIRED", null,
                    "settlement window of " + settlementWindowMs + " ms elapsed (" + elapsedMs + " ms) — refused");
        }
        PaymentResult result = new PaymentResult(req.reference(), req.billNumber(), status, billerTxnId, null);
        settledByReference.put(req.reference(), result);
        return result;
    }

    private static String hex() {
        return UUID.randomUUID().toString().replace("-", "");
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