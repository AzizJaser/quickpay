package com.quickpay.providersim;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The provider's PUBLIC API — this is the "external SMS/email provider" the notification
 * service calls. Slow and flaky on purpose. Sends are IDEMPOTENT on `reference`.
 *
 *   200 SENT    — accepted (or an idempotent replay of a previous SENT)
 *   422 FAILED  — definite refusal; retrying the same thing will fail again
 *   503         — server error: UNKNOWN outcome, safe to retry
 *   (no reply)  — TIMEOUT mode never answers in time; the caller's read timeout fires
 */
@RestController
@RequestMapping("/provider/v1")
public class ProviderController {

    private final ProviderStore store;

    public ProviderController(ProviderStore store) {
        this.store = store;
    }

    @PostMapping("/sms")
    public ResponseEntity<SendResult> sms(@RequestBody SendRequest request) {
        return respond(store.send("sms", request));
    }

    @PostMapping("/email")
    public ResponseEntity<SendResult> email(@RequestBody SendRequest request) {
        return respond(store.send("email", request));
    }

    private ResponseEntity<SendResult> respond(SendResult result) {
        HttpStatus status = "SENT".equals(result.status()) ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(result);
    }
}
