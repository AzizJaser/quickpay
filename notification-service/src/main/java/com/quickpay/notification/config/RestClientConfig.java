package com.quickpay.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient NotificationProviderRestClient(@Value("${notification.provider-base-url}") String baseUrl){ // no based URL yet
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}
