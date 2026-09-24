package com.quickpay.customer.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    private static final String MDC_KEY = "correlationId";
    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    @Value("${quickpay.wallet-time-out-seconds}")
    private long wallet_timeout;

    @Value("${quickpay.bill-time-out-seconds}")
    private long bill_timeout;

    @Bean
    RestClient walletRestClient(@Value("${quickpay.wallet-base-url}") String baseUrl){
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(wallet_timeout))
                .withReadTimeout(Duration.ofSeconds(wallet_timeout));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .requestInterceptor(((request, body, execution) -> {
                    String key = MDC.get(MDC_KEY);
                    if(key != null){
                        request.getHeaders().add(CORRELATION_HEADER,key);
                    }

                    return execution.execute(request,body);
                }))
                .build();
    }

    @Bean
    RestClient billRestClient(@Value("${quickpay.bill-base-url}") String baseUrl){
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(bill_timeout))
                .withReadTimeout(Duration.ofSeconds(bill_timeout));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .requestInterceptor(((request, body, execution) -> {
                    String key = MDC.get(MDC_KEY);
                    if(key != null){
                        request.getHeaders().add(CORRELATION_HEADER,key);
                    }

                    return execution.execute(request,body);
                }))
                .build();

    }
}
