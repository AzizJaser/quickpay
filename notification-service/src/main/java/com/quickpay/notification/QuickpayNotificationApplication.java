package com.quickpay.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class QuickpayNotificationApplication {

    // Note: @RabbitListener works without any extra annotation here —
    // spring-boot-starter-amqp auto-configures the listener container.
    public static void main(String[] args) {
        SpringApplication.run(QuickpayNotificationApplication.class, args);
    }
}