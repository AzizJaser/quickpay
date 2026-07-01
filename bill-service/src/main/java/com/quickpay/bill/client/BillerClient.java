package com.quickpay.bill.client;

import com.quickpay.bill.dto.request.BillerPayRequest;
import com.quickpay.bill.dto.response.BillerResult;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class BillerClient {

    private final RestClient billerClient;



    public BillerClient(RestClient billerRestClient){ // the name of the param is very important here
        this.billerClient = billerRestClient;
    }

    public BillerResult pay(BillerPayRequest payRequest){
        return billerClient
                .post()
                .uri("/biller/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payRequest)
                .retrieve()
                .onStatus(status -> status.value() == 422, ((request, response) -> {
                    // nothing
                }))
                .body(BillerResult.class);
    }
}