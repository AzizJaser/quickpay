package com.quickpay.bill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class QuickpayBillApplication {

    // When you build the EOD/pending sweep, add @EnableScheduling here
    // (same pattern as the wallet's reconciliation job).
    public static void main(String[] args) {
        SpringApplication.run(QuickpayBillApplication.class, args);
    }
}