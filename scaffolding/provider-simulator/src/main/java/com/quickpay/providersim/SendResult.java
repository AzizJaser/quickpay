package com.quickpay.providersim;

/**
 * The provider's answer. status is SENT or FAILED; providerRef is set only when SENT.
 */
public record SendResult(String reference,
                         String destination,
                         String status,
                         String providerRef,
                         String message) {
}
