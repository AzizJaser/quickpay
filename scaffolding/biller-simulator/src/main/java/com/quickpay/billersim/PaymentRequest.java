package com.quickpay.billersim;

/**
 * What the bill service sends to pay a bill.
 * `reference` is the bill service's OWN id for this attempt (ref A) — the biller treats it as the
 * idempotency key and dedupes on it, which is what makes the bill service's retries safe.
 */
public record PaymentRequest(String billNumber, Long amount, String reference) {
}