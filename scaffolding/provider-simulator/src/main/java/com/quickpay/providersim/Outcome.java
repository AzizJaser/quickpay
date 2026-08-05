package com.quickpay.providersim;

/** What the mock provider does on a send call. */
public enum Outcome {
    NORMAL,        // use provider.failure-rate to pick SENT or FAILED
    SENT,          // always accept
    FAILED,        // always reject (4xx) — a definite, non-transient refusal
    SERVER_ERROR,  // 5xx — the caller does not know whether it was sent
    TIMEOUT        // sleep past the caller's read timeout, then accept
}
