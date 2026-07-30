package com.quickpay.billersim;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Control panel for the mock biller (dev/test only). Force the next outcome so you can exercise
 * each branch of the bill-payment flow deterministically, inspect state, or reset between runs.
 */
@RestController
@RequestMapping("/simulate")
public class SimulatorController {

    private final BillerStore store;

    public SimulatorController(BillerStore store) {
        this.store = store;
    }

    /**
     * POST /simulate/mode  body: { "outcome": "SUCCESS|FAIL|SERVER_ERROR|TIMEOUT|NORMAL", "delayMs": 0 }
     * Forces the outcome of subsequent pay calls. NORMAL = use the configured failure-rate.
     * delayMs is optional; when present it overrides the artificial latency on each pay call.
     */
    @PostMapping("/mode")
    public Map<String, Object> mode(@RequestBody ModeRequest request) {
        Outcome outcome = (request.outcome() == null)
                ? Outcome.NORMAL
                : Outcome.valueOf(request.outcome().trim().toUpperCase());
        store.setMode(outcome, request.delayMs());
        return store.state();
    }

    /** GET /simulate/state → current mode, delay, failure-rate, and the settled references. */
    @GetMapping("/state")
    public Map<String, Object> state() {
        return store.state();
    }

    /** POST /simulate/reset → clear settlements and return to NORMAL mode. */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        store.reset();
        return store.state();
    }

    public record ModeRequest(String outcome, Long delayMs) {
    }
}