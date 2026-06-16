package com.quickpay.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QuickpayWalletApplication {

    public static void main(String[] args){
        SpringApplication.run(QuickpayWalletApplication.class);
    }
}
