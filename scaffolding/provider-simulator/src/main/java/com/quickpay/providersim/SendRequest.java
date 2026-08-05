package com.quickpay.providersim;

/**
 * What the notification service sends.
 * `destination` is the phone number or email address; `message` is the rendered text.
 * `reference` is the caller's own id for this send (the AMQP message id) — the provider
 * echoes it back so the caller can correlate, and dedupes on it.
 */
public record SendRequest(String destination, String message, String reference) {
}
