package com.quickpay.wallet.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickpay.wallet.dto.request.WebhookRequest;
import com.quickpay.wallet.exception.DuplicatedEntryException;
import com.quickpay.wallet.service.WalletService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
@RequiredArgsConstructor
public class GatewayWebhookController {
    private final WalletService walletService;

    private final ObjectMapper objectMapper;


    @Value("${gateway.webhook-secret:super-secret-key}")
    private String webhookSecret;



    @PostMapping("/v1/gateway/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String rawBody,
                                        @RequestHeader("X-Signature") String signature) throws JsonProcessingException {
        // 1. verify: HMAC-SHA256(rawBody, secret) as lowercase hex, constant-time compare to signature
        //    → if mismatch, return 401 (reject the forgery)
        if(!signatureValid(rawBody,signature)){
            return ResponseEntity.status(401).build();
        }
        WebhookRequest request = objectMapper.readValue(rawBody, WebhookRequest.class);
        try {
            walletService.topUp(request.walletNumber(),request.amount(),request.gatewayTxnId());
        }
        catch (DuplicatedEntryException e){
            // it's an intentional idempotent no-op
            //  (redelivery already processed).
        }
        // 2. parse rawBody → walletNumber, amount, gatewayTxnId
        // 3. topUp(walletNumber, amount, gatewayTxnId)   // gateway txn id = idempotency key
        return ResponseEntity.status(HttpStatus.OK).build();
        // 4. catch DuplicatedEntryException → still return 200 (so the gateway stops redelivering)
        // 5. return 200
    }

    private boolean signatureValid(String rawBody, String signature){
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmac = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hmac);
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signature == null ? new byte[0] : signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
