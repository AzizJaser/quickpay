package com.quickpay.gatewaysim;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends webhooks to the wallet service, pretending to be a payment gateway.
 * Each webhook body is HMAC-signed so the wallet can verify it really came from "us".
 */
@Service
public class WebhookSender {

    private final HmacSigner signer;
    private final String secret;
    private final String walletWebhookUrl;
    private final RestClient http = RestClient.create();

    // Remember each sent webhook body so we can REDELIVER the exact same bytes (same signature).
    private final Map<String, String> sentBodies = new ConcurrentHashMap<>();

    public WebhookSender(HmacSigner signer,
                         @Value("${gateway.webhook-secret}") String secret,
                         @Value("${wallet.webhook-url}") String walletWebhookUrl) {
        this.signer = signer;
        this.secret = secret;
        this.walletWebhookUrl = walletWebhookUrl;
    }

    /** Simulate a real payment: build a signed webhook and deliver it to the wallet. */
    public Map<String, Object> sendPayment(String walletNumber, long amount) {
        String txnId = "GW-" + UUID.randomUUID();
        String body = buildBody(walletNumber, amount, txnId);
        sentBodies.put(txnId, body);
        int status = post(body, signer.sign(body, secret));
        return result(txnId, status);
    }

    /** Redeliver a previously-sent webhook (same body, same signature) — tests idempotency. */
    public Map<String, Object> redeliver(String txnId) {
        String body = sentBodies.get(txnId);
        if (body == null) {
            throw new IllegalArgumentException("Unknown gatewayTxnId (never sent): " + txnId);
        }
        int status = post(body, signer.sign(body, secret));
        return result(txnId, status);
    }

    /** Send a webhook with a BAD signature — proves the wallet rejects forgeries. */
    public Map<String, Object> sendForged(String walletNumber, long amount) {
        String txnId = "GW-FORGED-" + UUID.randomUUID();
        String body = buildBody(walletNumber, amount, txnId);
        // deliberately wrong signature
        int status = post(body, "0000000000000000000000000000000000000000000000000000000000000000");
        return result(txnId, status);
    }

    private String buildBody(String walletNumber, long amount, String txnId) {
        // Build the EXACT JSON string we sign and send. The wallet must verify the HMAC
        // over these same raw bytes — re-serializing on its side would change the signature.
        return "{\"walletNumber\":\"" + walletNumber + "\",\"amount\":" + amount
                + ",\"gatewayTxnId\":\"" + txnId + "\"}";
    }

    private int post(String body, String signature) {
        return http.post()
                .uri(walletWebhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", signature)
                .body(body)
                // exchange() lets us read the status without throwing on 4xx (so we can SEE a 401/409)
                .exchange((req, res) -> res.getStatusCode().value());
    }

    private Map<String, Object> result(String txnId, int walletStatus) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gatewayTxnId", txnId);
        m.put("walletResponseStatus", walletStatus);
        return m;
    }
}