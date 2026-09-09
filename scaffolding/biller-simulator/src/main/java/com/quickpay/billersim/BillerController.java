package com.quickpay.billersim;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The mock biller's PUBLIC API — this is the "external biller network" the bill service calls.
 * Slow + flaky on purpose (see {@link BillerStore}). Pay is IDEMPOTENT on the client reference.
 */
@RestController
@RequestMapping("/biller/v1")
public class BillerController {

    private final BillerStore store;

    public BillerController(BillerStore store) {
        this.store = store;
    }

    /**
     * Pay a bill. Body: { "billNumber": "...", "amount": 12345, "reference": "A" }.
     * `reference` is the bill service's own id and is the idempotency key the biller dedupes on.
     *   200 PAID    — accepted (or idempotent replay of a prior PAID)
     *   422 FAILED  — definite failure, money NOT taken (the bill service can safely reverse)
     *   503         — server error: UNKNOWN outcome; the caller must inquire to find out
     *   (no reply)  — TIMEOUT mode never responds in time; the caller times out and must inquire
     */
    @PostMapping("/payments")
    public ResponseEntity<PaymentResult> pay(@RequestBody PaymentRequest request) {
        PaymentResult result = store.pay(request);
        HttpStatus status = "PAID".equals(result.status()) ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Inquire by the bill service's reference: "did my attempt A actually land?"
     *   200 PAID/FAILED — we settled this reference
     *   404 NOT_FOUND   — we never settled it, so it definitely did not land (safe to retry/reverse)
     */
    @GetMapping("/payments/{reference}")
    public ResponseEntity<PaymentResult> inquire(@PathVariable String reference) {
        PaymentResult result = store.inquire(reference);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new PaymentResult(reference, null, "NOT_FOUND", null, "no settlement for this reference"));
        }
        return ResponseEntity.ok(result);
    }

    /**
     * BULK inquiry — reconciliation in one round trip. Body: ["refA","refB",...].
     *
     * Always 200: the HTTP status describes the batch call, not the individual references.
     * Each entry carries its own status (PAID / FAILED / NOT_FOUND), and every requested
     * reference appears in the response — a reference the biller never settled comes back
     * as NOT_FOUND rather than being omitted, so the caller can distinguish "no record"
     * from "missing from the response".
     *
     * This is how real reconciliation works: a settlement file or a bulk status query, not
     * N polls. Per-reference polling made the bill service's sweep cost scale with its
     * backlog — 5 stranded bills cost 10 s per pass in sabotage scenario S02b, growing
     * linearly. One batched call costs the same as one single call.
     *
     * The all-or-nothing trade is deliberate: if this call times out the caller gets no
     * results at all, where per-reference polling would have returned the ones that
     * answered. That is acceptable because the retry is now cheap — a failed batch costs
     * one timeout, not N.
     */
    @PostMapping("/payments/inquiries")
    public ResponseEntity<List<PaymentResult>> inquireAll(@RequestBody List<String> references) {
        return ResponseEntity.ok(store.inquireAll(references));
    }
}