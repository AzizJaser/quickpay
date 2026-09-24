package com.quickpay.customer;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class QuickpayCustomerApplication {

    public static void main(String[] args){
        SpringApplication.run(QuickpayCustomerApplication.class,args);
    }


}
