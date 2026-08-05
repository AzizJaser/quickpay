package com.quickpay.providersim;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Control panel (dev/test only). Force an outcome so each branch of the notification
 * flow can be exercised deterministically — predict-then-run.
 */
@RestController
@RequestMapping("/simulate")
public class SimulatorController {

    private final ProviderStore store;

    public SimulatorController(ProviderStore store) {
        this.store = store;
    }

    /**
     * POST /simulate/mode  body: { "outcome": "SENT|FAILED|SERVER_ERROR|TIMEOUT|NORMAL", "delayMs": 0 }
     * NORMAL = use the configured failure-rate. delayMs is optional and overrides the latency.
     */
    @PostMapping("/mode")
    public Map<String, Object> mode(@RequestBody ModeRequest request) {
        Outcome outcome = (request.outcome() == null)
                ? Outcome.NORMAL
                : Outcome.valueOf(request.outcome().trim().toUpperCase());
        store.setMode(outcome, request.delayMs());
        return store.state();
    }

    /** GET /simulate/state → current mode, delay, failure-rate, and everything sent so far. */
    @GetMapping("/state")
    public Map<String, Object> state() {
        return store.state();
    }

    /** POST /simulate/reset → forget all sends and return to NORMAL. */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        store.reset();
        return store.state();
    }

    public record ModeRequest(String outcome, Long delayMs) {
    }
}
