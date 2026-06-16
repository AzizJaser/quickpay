package com.quickpay.gatewaysim;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Signs a webhook body with HMAC-SHA256 and the shared secret, hex-encoded.
 * The wallet service recomputes the SAME thing over the raw body it receives
 * and compares — that's how it knows the webhook really came from this gateway.
 */
@Component
public class HmacSigner {

    public String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to HMAC-sign payload", e);
        }
    }
}