package com.quickpay.billersim;

/**
 * What the mock biller does on a pay call.
 * NORMAL = decide by the configured failure-rate; the others force a specific behaviour.
 */
public enum Outcome {
    NORMAL,        // use biller.failure-rate to pick SUCCESS or FAIL
    SUCCESS,       // settle PAID
    FAIL,          // settle FAILED (definite failure — money not taken; 4xx)
    SERVER_ERROR,  // 5xx, nothing settled -> UNKNOWN outcome to the caller
    TIMEOUT        // sleep past the caller's timeout, then settle PAID (it landed, you didn't hear back)
}