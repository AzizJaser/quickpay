package com.quickpay.bill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class QuickpayBillApplication {

    // When you build the EOD/pending sweep, add @EnableScheduling here
    // (same pattern as the wallet's reconciliation job).
    public static void main(String[] args) {
        SpringApplication.run(QuickpayBillApplication.class, args);
    }
}