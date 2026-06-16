package com.quickpay.gatewaysim;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Control panel for the mock gateway. Call these to make the gateway send,
 * redeliver, or forge a webhook to the wallet service. Each response includes
 * the wallet's HTTP status so you can see what happened.
 */
@RestController
@RequestMapping("/simulate")
public class SimulatorController {

    private final WebhookSender sender;

    public SimulatorController(WebhookSender sender) {
        this.sender = sender;
    }

    /** POST /simulate/payment?walletNumber=...&amount=5000  → sends a valid signed webhook */
    @PostMapping("/payment")
    public Map<String, Object> payment(@RequestParam String walletNumber, @RequestParam long amount) {
        return sender.sendPayment(walletNumber, amount);
    }

    /** POST /simulate/redeliver/{gatewayTxnId}  → re-sends the same webhook (tests idempotency) */
    @PostMapping("/redeliver/{txnId}")
    public Map<String, Object> redeliver(@PathVariable String txnId) {
        return sender.redeliver(txnId);
    }

    /** POST /simulate/forge?walletNumber=...&amount=5000  → bad-signature webhook (tests rejection) */
    @PostMapping("/forge")
    public Map<String, Object> forge(@RequestParam String walletNumber, @RequestParam long amount) {
        return sender.sendForged(walletNumber, amount);
    }
}